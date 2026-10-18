package de.musicbot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.musicbot.MusicBotClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Configuration stored in {@code config/musicbot.json}.
 */
public class BotConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Player names or UUIDs with full control: every command, also via chat/MSG, and managing members. */
    public List<String> owners = new ArrayList<>();

    /** Player names or UUIDs that may use the normal music commands. */
    public List<String> members = new ArrayList<>();

    /** Prefix for chat commands, e.g. "!play ...". */
    public String commandPrefix = "!";

    /** Accept commands from public chat. */
    public boolean acceptPublicChat = true;

    /** Accept commands from private messages (/msg, /tell, /w). */
    public boolean acceptWhispers = true;

    /**
     * Optional regex patterns for servers whose chat/whisper plugins send messages as system messages.
     * Each pattern needs the named groups {@code sender} and {@code message}.
     * Example: {@code ^\[(?<sender>\w{3,16}) -> me\] (?<message>.+)$}
     */
    public List<String> systemMessagePatterns = new ArrayList<>();

    /**
     * Like {@link #systemMessagePatterns}, but for public chat sent as system message. Here the command prefix
     * is required. Private message patterns are checked first.
     * Example: {@code ^(?:[^»]* • )?(?<sender>[A-Za-z0-9_]{3,16}) » (?<message>.+)$}
     */
    public List<String> chatMessagePatterns = new ArrayList<>();

    /** How the bot answers: WHISPER (/msg to the sender), CHAT (public chat), LOCAL (only the bot sees it) or NONE. */
    public ReplyMode replyMode = ReplyMode.WHISPER;

    /** Command used for whisper replies (without slash). Placeholders: {player} {message}. */
    public String whisperCommand = "msg {player} {message}";

    /** Minimum delay between two outgoing chat messages/commands in ticks (anti-spam-kick). */
    public int replyDelayTicks = 25;

    /** Announce when a new track starts. */
    public boolean announceTracks = true;

    /** Download missing yt-dlp/ffmpeg/Deno into {@code <game dir>/musicbot/bin}. */
    public boolean autoDownloadDependencies = true;

    /** Run "yt-dlp -U" on startup when yt-dlp was downloaded by the mod (YouTube changes often). */
    public boolean autoUpdateYtDlp = true;

    /** Use Deno as JavaScript runtime for yt-dlp (needed for full YouTube support). */
    public boolean useDeno = true;

    /** yt-dlp executable. Leave "yt-dlp" for automatic detection/download, or set a full path. */
    public String ytDlpPath = "yt-dlp";

    /** ffmpeg executable. Leave "ffmpeg" for automatic detection/download, or set a full path. */
    public String ffmpegPath = "ffmpeg";

    /** Deno executable. Leave "deno" for automatic detection/download, or set a full path. */
    public String denoPath = "deno";

    /** Additional arguments passed to every yt-dlp call (e.g. "--cookies-from-browser", "firefox"). */
    public List<String> ytDlpExtraArgs = new ArrayList<>();

    /** yt-dlp search prefix used for plain text queries (ytsearch1: = YouTube, scsearch1: = SoundCloud). */
    public String searchPrefix = "ytsearch1:";

    /** Maximum number of entries loaded from a playlist. */
    public int maxPlaylistItems = 100;

    /** Maximum queue length. */
    public int maxQueueSize = 500;

    /** Start volume in percent (0-200). */
    public int volume = 60;

    /**
     * Replace the hardware microphone with a virtual one, so no audio device is needed at all
     * (e.g. Remote Desktop, headless mini PC). Requires a game restart.
     */
    public boolean virtualMicrophone = true;

    /** Never transmit the real microphone of the bot account, only music. */
    public boolean suppressMicrophone = true;

    /** Automatically accept Simple Voice Chat group invites from owners and members. */
    public boolean autoJoinInvites = true;

    /** Allow members (not only owners) to download songs into the library. */
    public boolean membersCanDownload = true;

    /** Longest song that may be downloaded into the library, in minutes (0 = unlimited). */
    public int maxDownloadMinutes = 120;

    /** Play a song from the library instead of streaming it again when the same link is requested. */
    public boolean useLibraryCache = true;

    /** Built-in web panel to control the bot from a browser (acts with owner rights). */
    public boolean webPanel = true;

    /** Address the web panel listens on. "0.0.0.0" = reachable in the local network, "127.0.0.1" = only this PC. */
    public String webPanelBind = "0.0.0.0";

    public int webPanelPort = 8095;

    /** Password for the web panel. Generated randomly on first start if empty. */
    public String webPanelPassword = "";

    /** Nod the bot's head to the beat while music is playing. */
    public boolean headBob = true;

    /** How far the head nods down per beat, in degrees. */
    public float headBobStrength = 12F;

    /** Additionally turn the head slightly left/right on alternating beats. */
    public boolean headBobSway = true;

    /** Allow playing local files by absolute path. Only owners can use this. */
    public boolean allowLocalFiles = true;

    public enum ReplyMode {
        WHISPER, CHAT, LOCAL, NONE
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("musicbot.json");
    }

    public static BotConfig load() {
        Path file = path();
        BotConfig config = null;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, BotConfig.class);
            } catch (Exception e) {
                MusicBotClient.LOGGER.error("Failed to read {}, using defaults", file, e);
            }
        }
        if (config == null) {
            config = new BotConfig();
        }
        config.sanitize();
        config.save();
        return config;
    }

    public synchronized void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            MusicBotClient.LOGGER.error("Failed to save {}", file, e);
        }
    }

    private void sanitize() {
        if (owners == null) owners = new ArrayList<>();
        if (members == null) members = new ArrayList<>();
        if (systemMessagePatterns == null) systemMessagePatterns = new ArrayList<>();
        if (chatMessagePatterns == null) chatMessagePatterns = new ArrayList<>();
        if (ytDlpExtraArgs == null) ytDlpExtraArgs = new ArrayList<>();
        if (commandPrefix == null || commandPrefix.isBlank()) commandPrefix = "!";
        if (replyMode == null) replyMode = ReplyMode.WHISPER;
        if (whisperCommand == null || whisperCommand.isBlank()) whisperCommand = "msg {player} {message}";
        if (ytDlpPath == null || ytDlpPath.isBlank()) ytDlpPath = "yt-dlp";
        if (ffmpegPath == null || ffmpegPath.isBlank()) ffmpegPath = "ffmpeg";
        if (denoPath == null || denoPath.isBlank()) denoPath = "deno";
        if (searchPrefix == null || searchPrefix.isBlank()) searchPrefix = "ytsearch1:";
        replyDelayTicks = Math.max(0, replyDelayTicks);
        maxPlaylistItems = Math.max(1, maxPlaylistItems);
        maxQueueSize = Math.max(1, maxQueueSize);
        volume = Math.clamp(volume, 0, 200);
        headBobStrength = Math.clamp(headBobStrength, 1F, 45F);
        if (webPanelBind == null || webPanelBind.isBlank()) webPanelBind = "0.0.0.0";
        if (webPanelPort < 1 || webPanelPort > 65535) webPanelPort = 8095;
        if (webPanelPassword == null || webPanelPassword.isBlank()) webPanelPassword = randomPassword();
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    public synchronized boolean isOwner(UUID uuid, String name) {
        return contains(owners, uuid, name);
    }

    public synchronized boolean isMember(UUID uuid, String name) {
        return contains(members, uuid, name);
    }

    public synchronized boolean addOwner(String owner) {
        return add(owners, owner);
    }

    public synchronized boolean removeOwner(String owner) {
        return remove(owners, owner);
    }

    public synchronized boolean addMember(String member) {
        return add(members, member);
    }

    public synchronized boolean removeMember(String member) {
        return remove(members, member);
    }

    private static boolean contains(List<String> list, UUID uuid, String name) {
        for (String entry : list) {
            String e = entry.trim();
            if (uuid != null && e.equalsIgnoreCase(uuid.toString())) return true;
            if (name != null && e.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private boolean add(List<String> list, String entry) {
        String lower = entry.toLowerCase(Locale.ROOT);
        if (list.stream().anyMatch(e -> e.toLowerCase(Locale.ROOT).equals(lower))) return false;
        list.add(entry);
        save();
        return true;
    }

    private boolean remove(List<String> list, String entry) {
        boolean removed = list.removeIf(e -> e.equalsIgnoreCase(entry));
        if (removed) save();
        return removed;
    }
}
