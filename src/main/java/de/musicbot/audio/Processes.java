package de.musicbot.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

final class Processes {

    private Processes() {
    }

    static ProcessBuilder builder(List<String> command) {
        ProcessBuilder pb = new ProcessBuilder(command);
        // Make yt-dlp (Python) write UTF-8 regardless of the Windows console code page
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8", "1");
        return pb;
    }

    static void kill(Process process) {
        if (process == null) return;
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /**
     * Continuously drains a stream (usually stderr) in a daemon thread so the child process never blocks,
     * keeping the last few lines for error messages.
     */
    static final class StreamTail {
        private final Deque<String> lines = new ArrayDeque<>();
        private final Thread thread;

        StreamTail(InputStream in, String name) {
            thread = Thread.ofPlatform().daemon().name(name).start(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        synchronized (lines) {
                            lines.addLast(line.trim());
                            if (lines.size() > 8) lines.removeFirst();
                        }
                    }
                } catch (IOException ignored) {
                }
            });
        }

        void await(long millis) {
            try {
                thread.join(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /** The most relevant line, preferring yt-dlp/ffmpeg "ERROR" lines. */
        String lastError() {
            synchronized (lines) {
                String fallback = null;
                for (String line : lines) {
                    if (line.contains("ERROR")) fallback = line;
                }
                if (fallback == null) fallback = lines.peekLast();
                return fallback == null ? "unknown error" : fallback;
            }
        }
    }
}
