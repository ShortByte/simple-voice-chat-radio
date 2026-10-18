package de.musicbot.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.musicbot.MusicBotClient;
import de.musicbot.audio.MusicPlayer;
import de.musicbot.audio.Track;
import de.musicbot.command.CommandHandler;
import de.musicbot.command.CommandSender;
import de.musicbot.config.BotConfig;
import de.musicbot.deps.DependencyManager;
import de.musicbot.library.Library;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small HTTP control panel. All actions are sent as normal bot commands with owner rights.
 * <ul>
 *     <li>{@code GET /} - the panel page</li>
 *     <li>{@code GET /api/state?since=<id>} - player state, queue, library and new log lines</li>
 *     <li>{@code POST /api/command} - {@code {"command": "play ..."}}</li>
 * </ul>
 * API calls need the header {@code Authorization: Bearer <webPanelPassword>}.
 */
public class WebPanel {

    private static final int MAX_LOG = 200;
    private static final int MAX_BODY = 4096;
    private static final Gson GSON = new Gson();

    private final BotConfig config;
    private final MusicPlayer player;
    private final Library library;
    private final DependencyManager deps;
    private final CommandHandler commands;
    private final Supplier<String> botName;
    private final Executor clientThread;
    private final Deque<JsonObject> log = new ArrayDeque<>();
    private long logId;
    private HttpServer server;
    private ExecutorService executor;
    private byte[] page;

    /**
     * @param botName      name of the bot account
     * @param clientThread runs commands on the Minecraft client thread
     */
    public WebPanel(BotConfig config, MusicPlayer player, Library library, DependencyManager deps, CommandHandler commands,
                    Supplier<String> botName, Executor clientThread) {
        this.botName = botName;
        this.clientThread = clientThread;
        this.config = config;
        this.player = player;
        this.library = library;
        this.deps = deps;
        this.commands = commands;
    }

    public synchronized void start() throws IOException {
        if (server != null) return;
        try (InputStream in = WebPanel.class.getResourceAsStream("/assets/musicbot/web/index.html")) {
            if (in == null) throw new IOException("index.html missing in mod jar");
            page = in.readAllBytes();
        }
        server = HttpServer.create(new InetSocketAddress(config.webPanelBind, config.webPanelPort), 16);
        executor = Executors.newFixedThreadPool(4, r -> Thread.ofPlatform().daemon().name("musicbot-web").unstarted(r));
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        MusicBotClient.LOGGER.info("Web panel running: {}", urls());
    }

    public synchronized void stop() {
        if (server == null) return;
        server.stop(0);
        executor.shutdownNow();
        server = null;
    }

    public boolean isRunning() {
        return server != null;
    }

    /** Addresses the panel can be reached at, e.g. {@code http://192.168.1.20:8095} */
    public List<String> urls() {
        List<String> urls = new ArrayList<>();
        if (!config.webPanelBind.equals("0.0.0.0")) {
            urls.add("http://" + config.webPanelBind + ":" + config.webPanelPort);
            return urls;
        }
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        urls.add("http://" + address.getHostAddress() + ":" + config.webPanelPort);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        if (urls.isEmpty()) urls.add("http://localhost:" + config.webPanelPort);
        return urls;
    }

    /** Adds a line to the panel log. Thread-safe. */
    public synchronized void log(String text) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", ++logId);
        entry.addProperty("time", System.currentTimeMillis());
        entry.addProperty("text", text);
        log.addLast(entry);
        while (log.size() > MAX_LOG) log.removeFirst();
    }

    // ---------------------------------------------------------------- HTTP

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().add("Cache-Control", "no-store");

            if (path.equals("/") && method.equals("GET")) {
                send(exchange, 200, "text/html; charset=utf-8", page);
                return;
            }
            if (!path.startsWith("/api/")) {
                send(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!authorized(exchange)) {
                // slow down password guessing
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                sendJson(exchange, 401, error("Wrong password"));
                return;
            }
            switch (path) {
                case "/api/state" -> {
                    if (!method.equals("GET")) {
                        sendJson(exchange, 405, error("GET expected"));
                        return;
                    }
                    sendJson(exchange, 200, state(parseSince(exchange.getRequestURI().getQuery())));
                }
                case "/api/command" -> {
                    if (!method.equals("POST")) {
                        sendJson(exchange, 405, error("POST expected"));
                        return;
                    }
                    command(exchange);
                }
                default -> sendJson(exchange, 404, error("Unknown endpoint"));
            }
        } catch (Exception e) {
            MusicBotClient.LOGGER.warn("Web panel request failed", e);
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        byte[] given = header.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        String password = config.webPanelPassword;
        // never accept an empty password, even if the config was left in that state
        if (password == null || password.isBlank()) return false;
        byte[] expected = password.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, expected);
    }

    private void command(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            sendJson(exchange, 413, error("Too long"));
            return;
        }
        String line;
        try {
            line = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject().get("command").getAsString().trim();
        } catch (Exception e) {
            sendJson(exchange, 400, error("Expected: {\"command\": \"...\"}"));
            return;
        }
        if (line.startsWith(config.commandPrefix)) line = line.substring(config.commandPrefix.length());
        if (line.isEmpty()) {
            sendJson(exchange, 400, error("Empty command"));
            return;
        }
        String finalLine = line;
        log("> " + finalLine);
        CommandSender sender = new CommandSender("WebPanel", null, CommandSender.Role.WEB);
        clientThread.execute(() -> commands.handle(sender, finalLine));
        JsonObject ok = new JsonObject();
        ok.addProperty("ok", true);
        sendJson(exchange, 200, ok);
    }

    private JsonObject state(long since) {
        JsonObject state = new JsonObject();
        state.addProperty("bot", botName.get());
        state.addProperty("ready", deps.isReady());
        state.addProperty("status", deps.isReady() ? deps.summary() : deps.status());

        Track current = player.currentTrack();
        if (current != null) {
            JsonObject now = track(current);
            now.addProperty("position", player.positionSec());
            state.add("current", now);
        }
        state.addProperty("paused", player.isPaused());
        state.addProperty("volume", player.getVolume());
        state.addProperty("repeat", player.getRepeat().name());
        state.addProperty("headBob", config.headBob);
        state.addProperty("prefix", config.commandPrefix);

        JsonArray queue = new JsonArray();
        List<Track> snapshot = player.queueSnapshot();
        for (int i = 0; i < Math.min(snapshot.size(), 300); i++) queue.add(track(snapshot.get(i)));
        state.add("queue", queue);
        state.addProperty("queueSize", snapshot.size());

        JsonArray lib = new JsonArray();
        List<Library.Entry> entries = library.entries();
        for (int i = 0; i < entries.size(); i++) {
            Library.Entry e = entries.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("nr", i + 1);
            o.addProperty("title", e.title());
            o.addProperty("duration", e.durationSec());
            o.addProperty("addedBy", e.addedBy());
            lib.add(o);
        }
        state.add("library", lib);

        JsonArray lines = new JsonArray();
        synchronized (this) {
            for (JsonObject entry : log) {
                if (entry.get("id").getAsLong() > since) lines.add(entry);
            }
        }
        state.add("log", lines);
        return state;
    }

    private static JsonObject track(Track t) {
        JsonObject o = new JsonObject();
        o.addProperty("title", t.title());
        o.addProperty("duration", t.durationSec());
        o.addProperty("requestedBy", t.requestedBy());
        o.addProperty("local", t.localFile());
        if (!t.localFile()) o.addProperty("url", t.source());
        return o;
    }

    private static long parseSince(String query) {
        if (query == null) return 0;
        for (String part : query.split("&")) {
            if (part.startsWith("since=")) {
                try {
                    return Long.parseLong(part.substring(6));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    private static void sendJson(HttpExchange exchange, int status, JsonObject json) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
