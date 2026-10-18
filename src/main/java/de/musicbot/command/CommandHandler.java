package de.musicbot.command;

import de.musicbot.MusicBotClient;
import de.musicbot.audio.MusicPlayer;
import de.musicbot.audio.MusicPlayer.RepeatMode;
import de.musicbot.audio.Track;
import de.musicbot.audio.TrackResolver;
import de.musicbot.config.BotConfig;
import de.musicbot.deps.DependencyManager;
import de.musicbot.library.Library;
import de.musicbot.library.LibraryDownloader;
import de.musicbot.web.WebPanel;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Parses and executes bot commands like {@code play <url>}.
 */
public class CommandHandler {

    private static final int PAGE_SIZE = 5;

    private final BotConfig config;
    private final MusicPlayer player;
    private final TrackResolver resolver;
    private final ChatSender chat;
    private final DependencyManager deps;
    private final Library library;
    private final LibraryDownloader downloader;
    private final ExecutorService resolveExecutor = Executors.newFixedThreadPool(2, r -> Thread.ofPlatform().daemon().name("musicbot-resolver").unstarted(r));
    private volatile CommandSender lastController;
    private WebPanel webPanel;

    public CommandHandler(BotConfig config, MusicPlayer player, ChatSender chat, DependencyManager deps, Library library) {
        this.config = config;
        this.player = player;
        this.chat = chat;
        this.deps = deps;
        this.library = library;
        this.downloader = new LibraryDownloader(config, deps, library);
        this.resolver = new TrackResolver(config, deps);
        player.setAnnouncer(msg -> Minecraft.getInstance().execute(() -> {
            CommandSender target = lastController;
            if (target != null) chat.reply(target, msg);
        }));
    }

    public void setWebPanel(WebPanel webPanel) {
        this.webPanel = webPanel;
    }

    /** Executes a command line without prefix. Must be called on the client thread. */
    public void handle(CommandSender sender, String line) {
        line = line.trim();
        if (line.isEmpty()) return;
        lastController = sender;

        int space = line.indexOf(' ');
        String cmd = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        String args = space < 0 ? "" : line.substring(space + 1).trim();

        MusicBotClient.LOGGER.info("Command from {}: {}", sender.name(), line);

        switch (cmd) {
            case "play", "p", "add" -> play(sender, args, false, false);
            case "playnext", "pn", "next" -> play(sender, args, true, false);
            case "playnow", "pnow" -> play(sender, args, true, true);
            case "skip", "s", "fs" -> skip(sender, args);
            case "stop" -> {
                player.stop();
                reply(sender, "Stopped and cleared the queue.");
            }
            case "pause" -> reply(sender, player.setPaused(true) ? "Paused." : "Nothing is playing.");
            case "resume", "unpause", "continue" -> reply(sender, player.setPaused(false) ? "Resumed." : "Nothing is playing.");
            case "queue", "q", "list" -> queue(sender, args);
            case "np", "nowplaying", "now", "current" -> nowPlaying(sender);
            case "remove", "rm", "delete" -> remove(sender, args);
            case "move", "mv" -> move(sender, args);
            case "clear" -> reply(sender, "Removed " + player.clearQueue() + " track(s) from the queue.");
            case "shuffle" -> {
                player.shuffle();
                reply(sender, "Shuffled the queue.");
            }
            case "repeat", "loop", "r" -> repeat(sender, args);
            case "volume", "vol", "v" -> volume(sender, args);
            case "seek", "jump" -> seek(sender, args);
            case "bob", "headbob", "nod" -> headBob(sender, args);
            case "join" -> join(sender, args);
            case "leave", "leavegroup" -> {
                sendGameCommand("voicechat leave");
                reply(sender, "Leaving the voice chat group.");
            }
            case "download", "dl", "save" -> download(sender, args);
            case "library", "lib", "saved" -> library(sender, args);
            case "playlib", "pl", "lp" -> playLibrary(sender, args);
            case "libremove", "librm", "unsave" -> {
                if (requireOwner(sender)) libraryRemove(sender, args);
            }
            case "web", "panel" -> {
                if (requireOwner(sender)) web(sender);
            }
            case "help", "h", "?" -> help(sender);
            case "deps", "setup", "status" -> deps(sender, args);
            case "update" -> {
                if (requireOwner(sender)) update(sender);
            }
            case "owner", "owners" -> {
                if (requireOwner(sender)) manageList(sender, args, true);
            }
            case "member", "members" -> {
                if (requireOwner(sender)) manageList(sender, args, false);
            }
            case "reload" -> {
                if (requireOwner(sender)) reload(sender);
            }
            default -> reply(sender, "Unknown command '" + cmd + "'. Type " + config.commandPrefix + "help for help.");
        }
    }

    private void play(CommandSender sender, String query, boolean front, boolean now) {
        if (query.isEmpty()) {
            // "play" without argument acts as resume
            if (player.isPaused() && player.setPaused(false)) {
                reply(sender, "Resumed.");
            } else {
                reply(sender, "Usage: " + config.commandPrefix + "play <URL or search term>");
            }
            return;
        }
        if (!requireReady(sender)) return;
        reply(sender, "Searching: " + query);
        resolveExecutor.execute(() -> {
            try {
                List<Track> tracks = withLibraryCache(resolver.resolve(query, sender.name(), sender.isOwner()));
                enqueueResolved(sender, tracks, front, now);
            } catch (TrackResolver.ResolveException e) {
                Minecraft.getInstance().execute(() -> reply(sender, e.getMessage()));
            } catch (Exception e) {
                MusicBotClient.LOGGER.error("Failed to resolve {}", query, e);
                Minecraft.getInstance().execute(() -> reply(sender, "Error: " + e.getMessage()));
            }
        });
    }

    private boolean requireReady(CommandSender sender) {
        if (deps.isReady()) return true;
        reply(sender, deps.state() == DependencyManager.State.FAILED
                ? "Not ready: " + deps.status()
                : "Not ready yet - " + deps.status());
        return false;
    }

    /** Replaces tracks that are already downloaded with the local library file. */
    private List<Track> withLibraryCache(List<Track> tracks) {
        if (!config.useLibraryCache) return tracks;
        return tracks.stream().map(t -> {
            int pos = t.localFile() ? -1 : library.indexOfSource(t.source());
            Library.Entry entry = pos > 0 ? library.get(pos) : null;
            return entry == null ? t : library.toTrack(entry, t.requestedBy());
        }).toList();
    }

    private void enqueueResolved(CommandSender sender, List<Track> tracks, boolean front, boolean now) {
        Minecraft.getInstance().execute(() -> {
            boolean wasPlaying = player.currentTrack() != null;
            int added = player.enqueue(tracks, front);
            if (added == 0) {
                reply(sender, "The queue is full (" + config.maxQueueSize + ").");
                return;
            }
            if (now && wasPlaying) player.skip(1);
            if (added == 1) {
                Track t = tracks.getFirst();
                if (wasPlaying && !now) {
                    reply(sender, "Added: " + t.title() + " [" + t.formattedDuration() + "]" + (front ? " (up next)" : " (#" + player.queueSnapshot().size() + ")"));
                } else if (!config.announceTracks) {
                    reply(sender, "Playing: " + t.title());
                }
            } else {
                reply(sender, "Added " + added + " tracks to the queue" + (added < tracks.size() ? " (queue full)" : "") + ".");
            }
        });
    }

    // ---------------------------------------------------------------- voice chat groups

    private void join(CommandSender sender, String args) {
        if (args.isEmpty()) {
            reply(sender, "Invite me with /voicechat invite " + Minecraft.getInstance().getUser().getName()
                    + " - or: " + config.commandPrefix + "join <group name> [password]");
            return;
        }
        // Group name and password are passed as quoted strings: /voicechat join "name" "password"
        String[] parts = args.split("\\s+", 2);
        String group = parts[0];
        String password = parts.length > 1 ? parts[1] : null;
        if (!isSafeArgument(group) || (password != null && !isSafeArgument(password))) {
            reply(sender, "Group name and password must not contain quotes or backslashes.");
            return;
        }
        sendGameCommand("voicechat join \"" + group + "\"" + (password == null ? "" : " \"" + password + "\""));
        reply(sender, "Joining group " + group + "...");
    }

    private static boolean isSafeArgument(String s) {
        return !s.isEmpty() && s.length() <= 64 && s.chars().noneMatch(c -> c == '"' || c == '\\' || c < 32);
    }

    private static void sendGameCommand(String command) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.sendCommand(command);
    }

    // ---------------------------------------------------------------- library

    private void download(CommandSender sender, String query) {
        if (!sender.isOwner() && !config.membersCanDownload) {
            reply(sender, "Only owners may download songs.");
            return;
        }
        if (!requireReady(sender)) return;

        if (query.isEmpty()) {
            Track current = player.currentTrack();
            if (current == null || current.localFile()) {
                reply(sender, "Usage: " + config.commandPrefix + "download <URL|search> (without argument: the current track)");
                return;
            }
            reply(sender, "Saving the current track: " + current.title());
            downloader.downloadAll(List.of(current), sender.name(), msg -> Minecraft.getInstance().execute(() -> reply(sender, msg)));
            return;
        }

        reply(sender, "Searching to save: " + query);
        resolveExecutor.execute(() -> {
            try {
                List<Track> tracks = resolver.resolve(query, sender.name(), false);
                List<Track> missing = tracks.stream().filter(t -> library.indexOfSource(t.source()) < 0).toList();
                Minecraft.getInstance().execute(() -> {
                    if (missing.isEmpty()) {
                        int pos = library.indexOfSource(tracks.getFirst().source());
                        reply(sender, tracks.size() == 1 ? "Already saved as #" + pos + "." : "All " + tracks.size() + " tracks are already saved.");
                        return;
                    }
                    if (missing.size() > 1) {
                        reply(sender, "Downloading " + missing.size() + " tracks" + (missing.size() < tracks.size() ? " (" + (tracks.size() - missing.size()) + " already saved)" : "") + "...");
                    } else {
                        reply(sender, "Downloading: " + missing.getFirst().title());
                    }
                    downloader.downloadAll(missing, sender.name(), msg -> Minecraft.getInstance().execute(() -> reply(sender, msg)));
                });
            } catch (TrackResolver.ResolveException e) {
                Minecraft.getInstance().execute(() -> reply(sender, e.getMessage()));
            } catch (Exception e) {
                MusicBotClient.LOGGER.error("Failed to resolve {}", query, e);
                Minecraft.getInstance().execute(() -> reply(sender, "Error: " + e.getMessage()));
            }
        });
    }

    /** {@code library [page]} or {@code library <search text>} */
    private void library(CommandSender sender, String args) {
        List<Library.Entry> entries = library.entries();
        if (entries.isEmpty()) {
            reply(sender, "The library is empty. Save songs with " + config.commandPrefix + "download <URL|search>.");
            return;
        }
        List<Integer> positions;
        String header;
        int page = 1;
        if (args.isEmpty() || args.matches("\\d+")) {
            page = args.isEmpty() ? 1 : parseInt(args, 1);
            positions = IntStream.rangeClosed(1, entries.size()).boxed().toList();
            header = "Library: " + entries.size() + " tracks";
        } else {
            positions = library.search(args);
            header = "Search '" + args + "': " + positions.size() + " result(s)";
            if (positions.isEmpty()) {
                reply(sender, header + ".");
                return;
            }
        }
        int pages = Math.max(1, (positions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.clamp(page, 1, pages);
        int downloading = downloader.pending();
        reply(sender, header + ", page " + page + "/" + pages + (downloading > 0 ? " | " + downloading + " download(s) running" : "")
                + " | Play: " + config.commandPrefix + "playlib <no.|all>");
        for (int i = (page - 1) * PAGE_SIZE; i < Math.min(positions.size(), page * PAGE_SIZE); i++) {
            int pos = positions.get(i);
            Library.Entry e = entries.get(pos - 1);
            reply(sender, "#" + pos + " " + e.title() + " [" + e.formattedDuration() + "]");
        }
    }

    /** {@code playlib 3}, {@code playlib 1 4 7}, {@code playlib 2-6}, {@code playlib all}, {@code playlib <search>} */
    private void playLibrary(CommandSender sender, String args) {
        if (args.isEmpty()) {
            reply(sender, "Usage: " + config.commandPrefix + "playlib <no. | no. no. ... | from-to | all | search>");
            return;
        }
        if (!requireReady(sender)) return;
        int size = library.size();
        List<Integer> positions = new ArrayList<>();
        if (args.equalsIgnoreCase("all")) {
            for (int i = 1; i <= size; i++) positions.add(i);
        } else if (args.matches("[\\d\\s,-]+")) {
            for (String part : args.split("[\\s,]+")) {
                if (part.isEmpty()) continue;
                String[] range = part.split("-", 2);
                int from = parseInt(range[0], -1);
                int to = range.length > 1 ? parseInt(range[1], -1) : from;
                for (int i = Math.max(1, from); i <= Math.min(size, to); i++) positions.add(i);
            }
        } else {
            positions.addAll(library.search(args));
        }
        List<Track> tracks = positions.stream().map(library::get).filter(Objects::nonNull)
                .map(e -> library.toTrack(e, sender.name())).toList();
        if (tracks.isEmpty()) {
            reply(sender, "Nothing found in the library. " + config.commandPrefix + "library lists all tracks.");
            return;
        }
        enqueueResolved(sender, tracks, false, false);
    }

    private void libraryRemove(CommandSender sender, String args) {
        Library.Entry removed = library.remove(parseInt(args, -1));
        reply(sender, removed == null ? "Usage: " + config.commandPrefix + "libremove <no.>" : "Deleted from the library: " + removed.title());
    }

    private void skip(CommandSender sender, String args) {
        int count = args.isEmpty() ? 1 : parseInt(args, -1);
        if (count < 1) {
            reply(sender, "Usage: " + config.commandPrefix + "skip [count]");
            return;
        }
        Track skipped = player.skip(count);
        if (skipped == null) {
            reply(sender, "Nothing is playing.");
        } else if (player.currentTrack() == null) {
            reply(sender, "Skipped: " + skipped.title() + " - the queue is empty.");
        } else if (!config.announceTracks) {
            reply(sender, "Skipped: " + skipped.title());
        }
    }

    private void queue(CommandSender sender, String args) {
        List<Track> queue = player.queueSnapshot();
        Track current = player.currentTrack();
        if (current == null && queue.isEmpty()) {
            reply(sender, "The queue is empty.");
            return;
        }
        int pages = Math.max(1, (queue.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(args.isEmpty() ? 1 : parseInt(args, 1), 1, pages);

        long total = queue.stream().mapToLong(t -> Math.max(0, t.durationSec())).sum();
        reply(sender, "Queue: " + queue.size() + " tracks (" + Track.formatTime(total) + "), page " + page + "/" + pages
                + " | Repeat: " + repeatName(player.getRepeat()));
        if (current != null && page == 1) {
            reply(sender, "▶ " + current.title() + " [" + Track.formatTime(player.positionSec()) + "/" + current.formattedDuration() + "]");
        }
        for (int i = (page - 1) * PAGE_SIZE; i < Math.min(queue.size(), page * PAGE_SIZE); i++) {
            Track t = queue.get(i);
            reply(sender, (i + 1) + ". " + t.title() + " [" + t.formattedDuration() + "]");
        }
    }

    private void nowPlaying(CommandSender sender) {
        Track current = player.currentTrack();
        if (current == null) {
            reply(sender, "Nothing is playing.");
            return;
        }
        reply(sender, (player.isPaused() ? "⏸ " : "▶ ") + current.title()
                + " [" + Track.formatTime(player.positionSec()) + "/" + current.formattedDuration() + "]"
                + " | Vol " + player.getVolume() + "% | Repeat: " + repeatName(player.getRepeat())
                + " | requested by " + current.requestedBy());
        if (!current.localFile()) reply(sender, current.source());
    }

    private void remove(CommandSender sender, String args) {
        Track removed = player.remove(parseInt(args, -1));
        reply(sender, removed == null ? "Usage: " + config.commandPrefix + "remove <queue no.>" : "Removed: " + removed.title());
    }

    private void move(CommandSender sender, String args) {
        String[] parts = args.split("\\s+");
        Track moved = parts.length == 2 ? player.move(parseInt(parts[0], -1), parseInt(parts[1], -1)) : null;
        reply(sender, moved == null ? "Usage: " + config.commandPrefix + "move <from> <to>" : "Moved: " + moved.title() + " -> #" + parts[1]);
    }

    private void repeat(CommandSender sender, String args) {
        RepeatMode mode = switch (args.toLowerCase(Locale.ROOT)) {
            case "" -> RepeatMode.values()[(player.getRepeat().ordinal() + 1) % RepeatMode.values().length];
            case "off", "none", "0" -> RepeatMode.OFF;
            case "one", "song", "track", "single", "1" -> RepeatMode.ONE;
            case "all", "queue", "on" -> RepeatMode.ALL;
            default -> null;
        };
        if (mode == null) {
            reply(sender, "Usage: " + config.commandPrefix + "repeat [off|one|all]");
            return;
        }
        player.setRepeat(mode);
        reply(sender, "Repeat: " + repeatName(mode));
    }

    private void volume(CommandSender sender, String args) {
        if (args.isEmpty()) {
            reply(sender, "Volume: " + player.getVolume() + "%");
            return;
        }
        int vol = parseInt(args.replace("%", ""), -1);
        if (vol < 0 || vol > 200) {
            reply(sender, "Usage: " + config.commandPrefix + "volume <0-200>");
            return;
        }
        player.setVolume(vol);
        config.volume = vol;
        config.save();
        reply(sender, "Volume: " + vol + "%");
    }

    private void headBob(CommandSender sender, String args) {
        String a = args.toLowerCase(Locale.ROOT);
        Boolean enable = switch (a) {
            case "" -> !config.headBob;
            case "on", "1", "true" -> true;
            case "off", "0", "false" -> false;
            default -> null;
        };
        if (enable == null) {
            // a number sets the strength
            try {
                float strength = Float.parseFloat(a);
                if (strength < 1 || strength > 45) throw new NumberFormatException();
                config.headBobStrength = strength;
                config.headBob = true;
                config.save();
                reply(sender, "Head bobbing on, strength " + Math.round(strength) + "°.");
            } catch (NumberFormatException e) {
                reply(sender, "Usage: " + config.commandPrefix + "bob [on|off|1-45]");
            }
            return;
        }
        config.headBob = enable;
        config.save();
        reply(sender, "Head bobbing " + (enable ? "on" : "off") + ".");
    }

    private void seek(CommandSender sender, String args) {
        long seconds = parseTime(args);
        if (seconds < 0) {
            reply(sender, "Usage: " + config.commandPrefix + "seek <seconds | mm:ss | hh:mm:ss>");
            return;
        }
        reply(sender, player.seek(seconds) ? "Jumping to " + Track.formatTime(seconds) + "." : "Nothing is playing.");
    }

    private void deps(CommandSender sender, String args) {
        if (args.equalsIgnoreCase("retry")) {
            if (!requireOwner(sender)) return;
            deps.startAsync(msg -> Minecraft.getInstance().execute(() -> reply(sender, msg)));
            reply(sender, "Checking dependencies again...");
            return;
        }
        reply(sender, deps.isReady() ? deps.summary() : deps.status());
        if (sender.local()) reply(sender, "Folder: " + deps.binDir());
    }

    private void update(CommandSender sender) {
        if (!deps.isReady()) {
            reply(sender, deps.status());
            return;
        }
        reply(sender, "Updating yt-dlp...");
        resolveExecutor.execute(() -> {
            String result = deps.updateYtDlp();
            Minecraft.getInstance().execute(() -> reply(sender, result));
        });
    }

    private boolean requireOwner(CommandSender sender) {
        if (sender.isOwner()) return true;
        reply(sender, "You need to be an owner to do that.");
        return false;
    }

    /** {@code owner|member <add|remove|list> [name]} - only reachable for owners. */
    private void manageList(CommandSender sender, String args, boolean owners) {
        String label = owners ? "Owners" : "Members";
        String usage = "Usage: " + config.commandPrefix + (owners ? "owner" : "member") + " <add|remove|list> [name|UUID]";
        String[] parts = args.split("\\s+", 2);
        String action = parts[0].toLowerCase(Locale.ROOT);
        String name = parts.length > 1 ? parts[1].trim() : "";
        boolean needsName = !action.equals("list") && !action.isEmpty();
        if (needsName && !name.matches("[A-Za-z0-9_]{1,16}|[0-9a-fA-F-]{36}")) {
            reply(sender, usage);
            return;
        }
        switch (action) {
            case "add" -> {
                boolean added = owners ? config.addOwner(name) : config.addMember(name);
                reply(sender, added ? "Added " + label.toLowerCase(Locale.ROOT) + ": " + name : name + " is already " + (owners ? "an " : "a ") + label.toLowerCase(Locale.ROOT) + ".");
            }
            case "remove", "rm", "del" -> {
                if (owners && !sender.local() && config.owners.size() <= 1 && config.isOwner(null, name)) {
                    reply(sender, "The last owner can only be removed directly on the bot client.");
                    return;
                }
                boolean removed = owners ? config.removeOwner(name) : config.removeMember(name);
                reply(sender, removed ? "Removed " + label.toLowerCase(Locale.ROOT) + ": " + name : name + " is not " + (owners ? "an " : "a ") + label.toLowerCase(Locale.ROOT) + ".");
            }
            case "list", "" -> {
                List<String> list = owners ? config.owners : config.members;
                reply(sender, label + " (" + list.size() + "): " + (list.isEmpty() ? "(none)" : String.join(", ", list)));
            }
            default -> reply(sender, usage);
        }
    }

    private void web(CommandSender sender) {
        if (webPanel == null || !webPanel.isRunning()) {
            reply(sender, "The web panel is off (webPanel in the config, restart required) or port " + config.webPanelPort + " is in use.");
            return;
        }
        reply(sender, "Web panel: " + String.join(" | ", webPanel.urls()));
        // the password is only shown on the bot's own screen, never sent via chat
        reply(sender, sender.local() ? "Password: " + config.webPanelPassword : "Password: see /musicbot web on the bot or config/musicbot.json");
    }

    private void reload(CommandSender sender) {
        MusicBotClient.reloadConfig();
        reply(sender, "Config reloaded.");
    }

    private void help(CommandSender sender) {
        String p = config.commandPrefix;
        reply(sender, p + "play <URL|search> | " + p + "playnext <..> | " + p + "playnow <..> (search: yt:/sc: prefix possible)");
        reply(sender, p + "skip [n] | " + p + "stop | " + p + "pause | " + p + "resume | " + p + "seek <mm:ss>");
        reply(sender, p + "queue [page] | " + p + "np | " + p + "remove <no.> | " + p + "move <from> <to> | " + p + "clear | " + p + "shuffle");
        reply(sender, p + "repeat [off|one|all] | " + p + "volume <0-200> | " + p + "bob [on|off|1-45] | " + p + "status");
        reply(sender, "Library: " + p + "download [URL|search] | " + p + "library [page|search] | " + p + "playlib <no.|1-5|all|search>");
        reply(sender, "Groups: /voicechat invite " + Minecraft.getInstance().getUser().getName() + " | " + p + "join <group> [password] | " + p + "leave");
        if (sender.isOwner()) {
            reply(sender, "Owner: " + p + "member <add|remove|list> <name> | " + p + "owner <add|remove|list> <name> | "
                    + p + "libremove <no.> | " + p + "web | " + p + "update | " + p + "reload | " + p + "status retry");
        }
    }

    private void reply(CommandSender sender, String message) {
        chat.reply(sender, message);
    }

    private static String repeatName(RepeatMode mode) {
        return switch (mode) {
            case OFF -> "off";
            case ONE -> "track";
            case ALL -> "queue";
        };
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Parses "90", "1:30" or "1:02:03" into seconds, -1 if invalid. */
    static long parseTime(String s) {
        s = s.trim();
        if (s.isEmpty()) return -1;
        long total = 0;
        String[] parts = s.split(":");
        if (parts.length > 3) return -1;
        for (String part : parts) {
            try {
                long v = Long.parseLong(part);
                if (v < 0) return -1;
                total = total * 60 + v;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return total;
    }

    public void shutdown() {
        resolveExecutor.shutdownNow();
        downloader.shutdown();
    }
}
