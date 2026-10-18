package de.musicbot.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.musicbot.audio.MusicPlayer;
import de.musicbot.command.ChatSender;
import de.musicbot.command.CommandHandler;
import de.musicbot.config.BotConfig;
import de.musicbot.deps.DependencyManager;
import de.musicbot.library.Library;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WebPanelTest {

    @TempDir
    Path dir;

    private WebPanel panel;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        BotConfig config = new BotConfig();
        config.webPanelBind = "127.0.0.1";
        try (ServerSocket socket = new ServerSocket(0)) {
            config.webPanelPort = socket.getLocalPort();
        }
        config.webPanelPassword = "testpw";
        DependencyManager deps = new DependencyManager(config, dir.resolve("bin"));
        MusicPlayer player = new MusicPlayer(config, deps);
        Library library = new Library(dir.resolve("library"));
        ChatSender chat = new ChatSender(config);
        CommandHandler commands = new CommandHandler(config, player, chat, deps, library);
        panel = new WebPanel(config, player, library, deps, commands, () -> "TestBot", Runnable::run);
        chat.setWebLog(panel::log);
        panel.start();
        base = "http://127.0.0.1:" + config.webPanelPort;
    }

    @AfterEach
    void stop() {
        panel.stop();
    }

    private HttpResponse<String> get(String path, String password) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).GET();
        if (password != null) b.header("Authorization", "Bearer " + password);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String password) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).POST(HttpRequest.BodyPublishers.ofString(body));
        if (password != null) b.header("Authorization", "Bearer " + password);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesPageWithoutLogin() throws Exception {
        HttpResponse<String> res = get("/", null);
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("MusicBot Panel"));
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        assertEquals(401, get("/api/state", null).statusCode());
        assertEquals(401, get("/api/state", "wrong").statusCode());
        assertEquals(401, post("/api/command", "{\"command\":\"stop\"}", "wrong").statusCode());
    }

    @Test
    void commandChangesStateAndLogsReply() throws Exception {
        JsonObject before = JsonParser.parseString(get("/api/state", "testpw").body()).getAsJsonObject();
        assertEquals("TestBot", before.get("bot").getAsString());
        assertEquals("OFF", before.get("repeat").getAsString());
        assertEquals(0, before.getAsJsonArray("queue").size());

        assertEquals(200, post("/api/command", "{\"command\":\"!repeat all\"}", "testpw").statusCode());
        assertEquals(200, post("/api/command", "{\"command\":\"volume 80\"}", "testpw").statusCode());

        JsonObject after = JsonParser.parseString(get("/api/state", "testpw").body()).getAsJsonObject();
        assertEquals("ALL", after.get("repeat").getAsString());
        assertEquals(80, after.get("volume").getAsInt());
        String log = after.getAsJsonArray("log").toString();
        assertTrue(log.contains("> repeat all"), log);
        assertTrue(log.contains("Repeat: queue"), log);
        assertTrue(log.contains("Volume: 80%"), log);

        // only new log lines are returned with ?since=
        long lastId = after.getAsJsonArray("log").get(after.getAsJsonArray("log").size() - 1).getAsJsonObject().get("id").getAsLong();
        JsonObject next = JsonParser.parseString(get("/api/state?since=" + lastId, "testpw").body()).getAsJsonObject();
        assertEquals(0, next.getAsJsonArray("log").size());
    }

    @Test
    void rejectsBadRequests() throws Exception {
        assertEquals(400, post("/api/command", "not json", "testpw").statusCode());
        assertEquals(400, post("/api/command", "{\"command\":\"   \"}", "testpw").statusCode());
        assertEquals(405, get("/api/command", "testpw").statusCode());
        assertEquals(404, get("/secret", "testpw").statusCode());
    }
}
