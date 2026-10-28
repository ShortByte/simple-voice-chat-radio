package de.musicbot.audio;

import de.musicbot.config.BotConfig;
import de.musicbot.deps.DependencyManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Turns a user query (URL, search text or local file) into tracks, using yt-dlp for metadata.
 */
public class TrackResolver {

    private static final String SEPARATOR = "\u001F";
    private static final long TIMEOUT_SECONDS = 60;

    private final BotConfig config;
    private final DependencyManager deps;

    public TrackResolver(BotConfig config, DependencyManager deps) {
        this.config = config;
        this.deps = deps;
    }

    /**
     * @param tracks        the resolved tracks, never empty
     * @param playlistTitle name of the playlist the tracks were loaded from, or {@code null}
     */
    public record Result(List<Track> tracks, String playlistTitle) {
    }

    /**
     * @param allowLocal    whether absolute file paths on this PC may be played (owners only)
     * @param wholePlaylist for video links inside a playlist (watch?v=..&list=..): load the playlist,
     *                      starting at that video, instead of only the video
     */
    public Result resolve(String query, String requestedBy, boolean allowLocal, boolean wholePlaylist) throws ResolveException {
        query = query.trim();
        if (query.isEmpty()) throw new ResolveException("Empty query");

        Track local = tryLocalFile(query, requestedBy, allowLocal);
        if (local != null) return new Result(List.of(local), null);

        String target = toYtDlpTarget(query);
        boolean url = isUrl(query);
        // YouTube ignores &index=N, so start the playlist there ourselves
        int start = wholePlaylist ? Math.max(1, parseIndex(queryParam(target, "index"))) : 1;

        List<String> cmd = new ArrayList<>();
        cmd.add(deps.ytDlp());
        cmd.addAll(deps.ytDlpArgs());
        cmd.addAll(List.of(
                "--no-warnings",
                "--ignore-config",
                "--encoding", "utf-8",
                "--flat-playlist",
                // --no-playlist: watch?v=..&list=.. -> only the video, pure playlist links still load the playlist
                wholePlaylist ? "--yes-playlist" : "--no-playlist",
                "--playlist-items", start + ":" + (start + config.maxPlaylistItems - 1),
                "--print", "%(title)s" + SEPARATOR + "%(webpage_url,url)s" + SEPARATOR + "%(duration)s" + SEPARATOR + "%(playlist_title)s"
        ));
        cmd.addAll(config.ytDlpExtraArgs);
        cmd.add("--");
        cmd.add(target);

        Process process;
        try {
            process = Processes.builder(cmd).start();
        } catch (IOException e) {
            throw new ResolveException("Could not start yt-dlp (" + deps.ytDlp() + "): " + e.getMessage());
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
        }
        Processes.StreamTail stderr = new Processes.StreamTail(process.getErrorStream(), "musicbot-resolve-stderr");

        List<Track> tracks = new ArrayList<>();
        String playlistTitle = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Track track = parseLine(line, requestedBy);
                if (track == null) continue;
                tracks.add(track);
                if (playlistTitle == null) playlistTitle = field(line, 3);
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new ResolveException("yt-dlp timed out");
            }
        } catch (IOException e) {
            throw new ResolveException("Error reading from yt-dlp: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResolveException("Cancelled");
        } finally {
            Processes.kill(process);
        }

        if (tracks.isEmpty()) {
            stderr.await(500);
            throw new ResolveException("Nothing found: " + stderr.lastError());
        }
        if (wholePlaylist) tracks = startingAt(tracks, videoId(target));
        // search results also report a "playlist", only links can really be one
        return new Result(tracks, url ? playlistTitle : null);
    }

    /** Whether a link points to a video inside a playlist, which plays only the video unless the whole playlist is requested. */
    public static boolean isVideoInPlaylist(String query) {
        String q = query.trim();
        return isUrl(q) && queryParam(q, "list") != null && videoId(q) != null;
    }

    /** Drops the playlist entries before the given YouTube video, if it is part of the list. */
    static List<Track> startingAt(List<Track> tracks, String videoId) {
        if (videoId == null) return tracks;
        for (int i = 0; i < tracks.size(); i++) {
            if (videoId.equals(videoId(tracks.get(i).source()))) return tracks.subList(i, tracks.size());
        }
        return tracks;
    }

    /** @return the YouTube video ID of a watch?v=.. or youtu.be/.. link, or {@code null} */
    static String videoId(String url) {
        String v = queryParam(url, "v");
        if (v != null) return v;
        String lower = url.toLowerCase(Locale.ROOT);
        int host = lower.indexOf("://youtu.be/");
        if (host < 0) return null;
        String path = url.substring(host + "://youtu.be/".length()).split("[?#/]", 2)[0];
        return path.isEmpty() ? null : path;
    }

    /** @return the decoded value of a URL query parameter, or {@code null} if it is missing or empty */
    static String queryParam(String url, String name) {
        int q = url.indexOf('?');
        if (q < 0) return null;
        int hash = url.indexOf('#', q);
        String query = url.substring(q + 1, hash < 0 ? url.length() : hash);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static int parseIndex(String s) {
        if (s == null) return 1;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String field(String line, int index) {
        String[] parts = line.split(SEPARATOR, -1);
        if (parts.length <= index) return null;
        String value = parts[index].trim();
        return value.isEmpty() || value.equals("NA") ? null : value;
    }

    private static boolean isUrl(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private Track parseLine(String line, String requestedBy) {
        String[] parts = line.split(SEPARATOR, -1);
        if (parts.length < 3) return null;
        String url = parts[1].trim();
        if (url.isEmpty() || url.equals("NA")) return null;
        String title = parts[0].trim();
        if (title.isEmpty() || title.equals("NA")) title = url;
        long duration = -1;
        try {
            duration = Math.round(Double.parseDouble(parts[2].trim()));
        } catch (NumberFormatException ignored) {
        }
        return new Track(title, url, duration, false, requestedBy);
    }

    private Track tryLocalFile(String query, String requestedBy, boolean allowLocal) throws ResolveException {
        if (query.contains("://")) return null;
        String stripped = query.startsWith("\"") && query.endsWith("\"") && query.length() > 1
                ? query.substring(1, query.length() - 1) : query;
        Path path;
        try {
            path = Path.of(stripped);
        } catch (InvalidPathException e) {
            return null;
        }
        if (!path.isAbsolute() || !Files.isRegularFile(path)) return null;
        if (!config.allowLocalFiles) throw new ResolveException("Local files are disabled");
        if (!allowLocal) throw new ResolveException("Only owners may play local files");
        return new Track(path.getFileName().toString(), path.toAbsolutePath().toString(), -1, true, requestedBy);
    }

    private String toYtDlpTarget(String query) {
        if (isUrl(query)) return query;
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.startsWith("yt:")) return "ytsearch1:" + query.substring(3).trim();
        if (lower.startsWith("sc:")) return "scsearch1:" + query.substring(3).trim();
        // already a yt-dlp search prefix like "ytsearch5:" or "scsearch:"
        if (lower.matches("^[a-z]+search\\d*:.*")) return query;
        return config.searchPrefix + query;
    }

    public static class ResolveException extends Exception {
        public ResolveException(String message) {
            super(message);
        }
    }
}
