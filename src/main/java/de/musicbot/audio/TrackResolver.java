package de.musicbot.audio;

import de.musicbot.config.BotConfig;
import de.musicbot.deps.DependencyManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
     * @param allowLocal whether absolute file paths on this PC may be played (owners only)
     */
    public List<Track> resolve(String query, String requestedBy, boolean allowLocal) throws ResolveException {
        query = query.trim();
        if (query.isEmpty()) throw new ResolveException("Empty query");

        Track local = tryLocalFile(query, requestedBy, allowLocal);
        if (local != null) return List.of(local);

        String target = toYtDlpTarget(query);

        List<String> cmd = new ArrayList<>();
        cmd.add(deps.ytDlp());
        cmd.addAll(deps.ytDlpArgs());
        cmd.addAll(List.of(
                "--no-warnings",
                "--ignore-config",
                "--encoding", "utf-8",
                "--flat-playlist",
                "--no-playlist", // watch?v=..&list=.. -> only the video, pure playlist links still load the playlist
                "--playlist-end", Integer.toString(config.maxPlaylistItems),
                "--print", "%(title)s" + SEPARATOR + "%(webpage_url,url)s" + SEPARATOR + "%(duration)s"
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Track track = parseLine(line, requestedBy);
                if (track != null) tracks.add(track);
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
        return tracks;
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
        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return query;
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
