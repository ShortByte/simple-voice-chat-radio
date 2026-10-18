package de.musicbot.deps;

import de.musicbot.MusicBotClient;
import de.musicbot.config.BotConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Finds yt-dlp, ffmpeg and Deno, and downloads missing ones into {@code <game dir>/musicbot/bin}.
 * Every download is verified against the SHA-256 checksum published with the release.
 */
public class DependencyManager {

    public enum State {
        CHECKING, DOWNLOADING, READY, FAILED
    }

    private static final Pattern SHA256 = Pattern.compile("(?i)\\b[0-9a-f]{64}\\b");

    private final BotConfig config;
    private final Path binDir;
    private final Platform platform = Platform.detect();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private volatile State state = State.CHECKING;
    private volatile String status = "Checking dependencies...";
    private volatile String ytDlp;
    private volatile String ffmpeg;
    private volatile String deno;
    private volatile Thread worker;

    /** @param binDir where downloaded tools are stored, usually {@code <game dir>/musicbot/bin} */
    public DependencyManager(BotConfig config, Path binDir) {
        this.config = config;
        this.binDir = binDir.toAbsolutePath();
    }

    // ---------------------------------------------------------------- public API

    /** Checks and downloads in the background. {@code notify} receives user-facing progress messages. */
    public synchronized void startAsync(Consumer<String> notify) {
        if (worker != null && worker.isAlive()) return;
        state = State.CHECKING;
        worker = Thread.ofPlatform().daemon().name("musicbot-deps").start(() -> run(notify));
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public State state() {
        return state;
    }

    public String status() {
        return status;
    }

    public String ytDlp() {
        return ytDlp;
    }

    public String ffmpeg() {
        return ffmpeg;
    }

    public Path binDir() {
        return binDir;
    }

    /** Arguments every yt-dlp call needs so it finds our ffmpeg and Deno. */
    public List<String> ytDlpArgs() {
        List<String> args = new ArrayList<>();
        String f = ffmpeg;
        if (f != null && isPath(f)) args.addAll(List.of("--ffmpeg-location", f));
        String d = deno;
        if (d != null && isPath(d)) args.addAll(List.of("--js-runtimes", "deno:" + d));
        return args;
    }

    public String summary() {
        return "yt-dlp: " + describe(ytDlp) + " | ffmpeg: " + describe(ffmpeg) + " | Deno: " + describe(deno);
    }

    /** Runs {@code yt-dlp -U} for a self-managed yt-dlp. Blocking. */
    public String updateYtDlp() {
        String exe = ytDlp;
        if (exe == null) return "yt-dlp is not available.";
        if (!isManaged(exe)) return "yt-dlp is not managed by the mod (" + exe + ") - please update it yourself.";
        try {
            Process p = new ProcessBuilder(exe, "-U").redirectErrorStream(true).start();
            p.getOutputStream().close();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor(120, TimeUnit.SECONDS);
            String last = out.isEmpty() ? "done" : out.lines().reduce((a, b) -> b).orElse(out);
            MusicBotClient.LOGGER.info("yt-dlp -U: {}", out);
            return "yt-dlp update: " + last;
        } catch (IOException e) {
            return "yt-dlp update failed: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "yt-dlp update cancelled.";
        }
    }

    // ---------------------------------------------------------------- worker

    private void run(Consumer<String> notify) {
        try {
            Files.createDirectories(binDir);
        } catch (IOException e) {
            fail("Cannot create " + binDir + ": " + e.getMessage(), notify);
            return;
        }

        List<String> errors = new ArrayList<>();

        ytDlp = locate("yt-dlp", config.ytDlpPath, "yt-dlp", "--version", this::downloadYtDlp, errors, notify);
        ffmpeg = locate("ffmpeg", config.ffmpegPath, "ffmpeg", "-version", this::downloadFfmpeg, errors, notify);
        deno = config.useDeno
                ? locate("Deno", config.denoPath, "deno", "--version", this::downloadDeno, new ArrayList<>(), notify)
                : null;

        if (ytDlp == null || ffmpeg == null) {
            fail("Missing dependencies: " + String.join("; ", errors), notify);
            return;
        }

        state = State.READY;
        status = "Ready. " + summary();
        MusicBotClient.LOGGER.info(status);

        if (config.autoUpdateYtDlp && isManaged(ytDlp)) {
            MusicBotClient.LOGGER.info(updateYtDlp());
        }
    }

    @FunctionalInterface
    private interface Downloader {
        Path download(Consumer<String> notify) throws Exception;
    }

    /**
     * Order: explicitly configured path -> previously downloaded copy -> system PATH -> download.
     */
    private String locate(String label, String configured, String defaultName, String versionArg,
                          Downloader downloader, List<String> errors, Consumer<String> notify) {
        status = "Checking " + label + "...";
        if (configured != null && !configured.isBlank() && !configured.equals(defaultName)) {
            if (works(configured, versionArg)) return configured;
            errors.add(label + " at '" + configured + "' does not work");
            return null;
        }

        Path managed = binDir.resolve(platform.executable(defaultName));
        if (Files.isRegularFile(managed) && works(managed.toString(), versionArg)) return managed.toString();
        if (works(defaultName, versionArg)) return defaultName;

        if (!config.autoDownloadDependencies) {
            errors.add(label + " not found (autoDownloadDependencies is off)");
            return null;
        }
        try {
            state = State.DOWNLOADING;
            notify.accept(label + " is missing - downloading...");
            Path path = downloader.download(notify);
            if (!works(path.toString(), versionArg)) throw new IOException("the downloaded file does not run");
            notify.accept(label + " installed.");
            return path.toString();
        } catch (Exception e) {
            MusicBotClient.LOGGER.error("Failed to download {}", label, e);
            errors.add(label + ": " + e.getMessage());
            notify.accept(label + " could not be downloaded: " + e.getMessage());
            return null;
        }
    }

    private void fail(String message, Consumer<String> notify) {
        state = State.FAILED;
        status = message;
        MusicBotClient.LOGGER.error(message);
        notify.accept(message);
    }

    // ---------------------------------------------------------------- downloads

    private Path downloadYtDlp(Consumer<String> notify) throws Exception {
        String asset = switch (platform.os()) {
            case WINDOWS -> platform.arm() ? "yt-dlp_arm64.exe" : "yt-dlp.exe";
            case LINUX -> platform.arm() ? "yt-dlp_linux_aarch64" : "yt-dlp_linux";
            case MAC -> "yt-dlp_macos";
        };
        String base = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
        String sha = fetchChecksum(base + "SHA2-256SUMS", asset);
        Path target = binDir.resolve(platform.executable("yt-dlp"));
        Path tmp = download(base + asset, sha, "yt-dlp");
        install(tmp, target);
        return target;
    }

    private Path downloadFfmpeg(Consumer<String> notify) throws Exception {
        String asset = switch (platform.os()) {
            case WINDOWS -> platform.arm() ? "ffmpeg-master-latest-winarm64-gpl.zip" : "ffmpeg-master-latest-win64-gpl.zip";
            case LINUX -> platform.arm() ? "ffmpeg-master-latest-linuxarm64-gpl.tar.xz" : "ffmpeg-master-latest-linux64-gpl.tar.xz";
            case MAC -> throw new IOException("no automatic download on macOS - please run 'brew install ffmpeg'");
        };
        String base = "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/";
        String sha = fetchChecksum(base + "checksums.sha256", asset);
        notify.accept("ffmpeg is about 200 MB, this takes a moment...");
        Path archive = download(base + asset, sha, "ffmpeg");
        Path target = binDir.resolve(platform.executable("ffmpeg"));
        try {
            if (asset.endsWith(".zip")) {
                extractFromZip(archive, platform.executable("ffmpeg"), target);
            } else {
                extractFromTarXz(archive, "ffmpeg", target);
            }
        } finally {
            Files.deleteIfExists(archive);
        }
        return target;
    }

    private Path downloadDeno(Consumer<String> notify) throws Exception {
        String triple = switch (platform.os()) {
            case WINDOWS -> "x86_64-pc-windows-msvc"; // runs emulated on Windows ARM
            case LINUX -> platform.arm() ? "aarch64-unknown-linux-gnu" : "x86_64-unknown-linux-gnu";
            case MAC -> platform.arm() ? "aarch64-apple-darwin" : "x86_64-apple-darwin";
        };
        String url = "https://github.com/denoland/deno/releases/latest/download/deno-" + triple + ".zip";
        String sha = fetchChecksum(url + ".sha256sum", null);
        Path archive = download(url, sha, "Deno");
        Path target = binDir.resolve(platform.executable("deno"));
        try {
            extractFromZip(archive, platform.executable("deno"), target);
        } finally {
            Files.deleteIfExists(archive);
        }
        return target;
    }

    /**
     * Reads a checksum file. With {@code fileName} it picks the line for that file ({@code <hash>  <name>}),
     * otherwise the first SHA-256 found (single-file checksum, any format).
     */
    private String fetchChecksum(String url, String fileName) throws IOException, InterruptedException {
        HttpResponse<String> res = http.send(request(url), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("checksum not reachable (HTTP " + res.statusCode() + ")");
        for (String line : res.body().split("\\R")) {
            if (fileName != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2 || !parts[parts.length - 1].replace("*", "").equals(fileName)) continue;
            }
            Matcher m = SHA256.matcher(line);
            if (m.find()) return m.group().toLowerCase(Locale.ROOT);
        }
        throw new IOException("no checksum for " + (fileName == null ? url : fileName) + " found");
    }

    /** Downloads to a temp file in the bin dir and verifies the SHA-256 hash. */
    private Path download(String url, String expectedSha, String label) throws IOException, InterruptedException {
        HttpResponse<InputStream> res = http.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            res.body().close();
            throw new IOException("download failed (HTTP " + res.statusCode() + ")");
        }
        long total = res.headers().firstValueAsLong("Content-Length").orElse(-1);
        Path tmp = Files.createTempFile(binDir, "download-", ".part");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new DigestInputStream(res.body(), digest); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buffer = new byte[65536];
            long done = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                done += n;
                status = "Downloading " + label + "... " + progress(done, total);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedSha)) {
            Files.deleteIfExists(tmp);
            throw new IOException("checksum mismatch - download discarded");
        }
        return tmp;
    }

    private void extractFromZip(Path zip, String fileName, Path target) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !(name.equals(fileName) || name.endsWith("/" + fileName))) continue;
                Path tmp = Files.createTempFile(binDir, "extract-", ".part");
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                install(tmp, target);
                return;
            }
        }
        throw new IOException(fileName + " not found in the archive");
    }

    /** Java can't read .xz, so use the system tar (always present on Linux). */
    private void extractFromTarXz(Path archive, String fileName, Path target) throws IOException, InterruptedException {
        Path dir = Files.createTempDirectory(binDir, "extract-");
        try {
            Process p = new ProcessBuilder("tar", "-xJf", archive.toString(), "-C", dir.toString())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(5, TimeUnit.MINUTES) || p.exitValue() != 0) throw new IOException("tar failed: " + out.trim());
            Path found;
            try (Stream<Path> files = Files.walk(dir)) {
                found = files.filter(f -> f.getFileName().toString().equals(fileName) && f.getParent().getFileName().toString().equals("bin"))
                        .findFirst().orElseThrow(() -> new IOException(fileName + " not found in the archive"));
            }
            install(found, target);
        } finally {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(f -> f.toFile().delete());
            }
        }
    }

    private void install(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        if (platform.os() != Platform.Os.WINDOWS) {
            target.toFile().setExecutable(true, false);
        }
    }

    // ---------------------------------------------------------------- helpers

    private HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "musicbot-mod")
                .timeout(Duration.ofMinutes(30))
                .GET().build();
    }

    private static boolean works(String executable, String versionArg) {
        try {
            Process p = new ProcessBuilder(executable, versionArg).redirectErrorStream(true).start();
            p.getOutputStream().close();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isManaged(String path) {
        return isPath(path) && Path.of(path).toAbsolutePath().startsWith(binDir.toAbsolutePath());
    }

    private static boolean isPath(String s) {
        return s.contains("/") || s.contains("\\");
    }

    private String describe(String path) {
        if (path == null) return "missing";
        if (isManaged(path)) return "mod folder";
        return isPath(path) ? path : "system PATH";
    }

    private static String progress(long done, long total) {
        long mb = done / (1024 * 1024);
        if (total <= 0) return mb + " MB";
        return (done * 100 / total) + "% (" + mb + "/" + total / (1024 * 1024) + " MB)";
    }

    record Platform(Os os, boolean arm) {
        enum Os {WINDOWS, LINUX, MAC}

        static Platform detect() {
            String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            Os os = name.contains("win") ? Os.WINDOWS : name.contains("mac") || name.contains("darwin") ? Os.MAC : Os.LINUX;
            return new Platform(os, arch.contains("aarch64") || arch.contains("arm"));
        }

        String executable(String name) {
            return os == Os.WINDOWS ? name + ".exe" : name;
        }
    }
}
