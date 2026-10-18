package de.musicbot.library;

import de.musicbot.MusicBotClient;
import de.musicbot.audio.Track;
import de.musicbot.config.BotConfig;
import de.musicbot.deps.DependencyManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Downloads tracks one after another into the {@link Library} (audio only, via yt-dlp).
 */
public class LibraryDownloader {

    private final BotConfig config;
    private final DependencyManager deps;
    private final Library library;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().daemon().name("musicbot-download").unstarted(r));
    private final AtomicInteger pending = new AtomicInteger();

    public LibraryDownloader(BotConfig config, DependencyManager deps, Library library) {
        this.config = config;
        this.deps = deps;
        this.library = library;
    }

    /** Number of downloads waiting or running. */
    public int pending() {
        return pending.get();
    }

    /**
     * Queues downloads. {@code report} receives user-facing messages (called from the download thread).
     */
    public void downloadAll(List<Track> tracks, String requestedBy, Consumer<String> report) {
        boolean single = tracks.size() == 1;
        AtomicInteger left = new AtomicInteger(tracks.size());
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        pending.addAndGet(tracks.size());
        for (Track track : tracks) {
            executor.execute(() -> {
                try {
                    int position = download(track, requestedBy);
                    saved.incrementAndGet();
                    if (single) report.accept("Saved as #" + position + ": " + track.title());
                } catch (Exception e) {
                    failed.incrementAndGet();
                    MusicBotClient.LOGGER.warn("Download failed for {}", track.source(), e);
                    if (single) report.accept("Download failed: " + e.getMessage());
                } finally {
                    pending.decrementAndGet();
                    if (!single && left.decrementAndGet() == 0) {
                        report.accept("Downloads finished: " + saved.get() + " saved" + (failed.get() > 0 ? ", " + failed.get() + " failed" : "") + ".");
                    }
                }
            });
        }
    }

    private int download(Track track, String requestedBy) throws Exception {
        int existing = library.indexOfSource(track.source());
        if (existing > 0) return existing;
        long maxSec = config.maxDownloadMinutes * 60L;
        if (maxSec > 0 && track.durationSec() > maxSec) {
            throw new IOException("longer than " + config.maxDownloadMinutes + " minutes");
        }
        if (track.durationSec() < 0 && !track.localFile()) {
            // unknown duration usually means a livestream which would never finish
            throw new IOException("live streams cannot be saved");
        }

        Path dir = library.dir();
        Files.createDirectories(dir);
        List<String> cmd = new ArrayList<>();
        cmd.add(deps.ytDlp());
        cmd.addAll(deps.ytDlpArgs());
        cmd.addAll(List.of(
                "--no-warnings", "--ignore-config", "--encoding", "utf-8", "--no-playlist",
                "--no-simulate", "--no-progress",
                "-f", "bestaudio/best", "-x",
                "-P", dir.toString(),
                "-o", "%(extractor_key)s-%(id)s.%(ext)s",
                "--print", "after_move:filepath"
        ));
        cmd.addAll(config.ytDlpExtraArgs);
        cmd.add("--");
        cmd.add(track.source());

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8", "1");
        Process process = pb.start();
        process.getOutputStream().close();

        String lastLine = null;
        String errorLine = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("ERROR")) errorLine = line;
                lastLine = line;
            }
        }
        if (!process.waitFor(30, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("timed out");
        }
        if (process.exitValue() != 0 || lastLine == null) {
            throw new IOException(errorLine != null ? errorLine : "yt-dlp error " + process.exitValue());
        }

        Path file = Path.of(lastLine).toAbsolutePath().normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw new IOException("file not found: " + lastLine);
        }
        return library.add(new Library.Entry(track.title(), track.source(), file.getFileName().toString(),
                track.durationSec(), requestedBy, System.currentTimeMillis()));
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
