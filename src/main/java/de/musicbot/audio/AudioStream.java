package de.musicbot.audio;

import de.musicbot.MusicBotClient;
import de.musicbot.config.BotConfig;
import de.musicbot.deps.DependencyManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Decodes one track to 48 kHz mono 16 bit PCM frames of 20 ms.
 * <p>
 * Pipeline: {@code yt-dlp -o - <url> | ffmpeg -i pipe:0 -f s16le pipe:1}, or ffmpeg directly for local files.
 * Frames are buffered in a small bounded queue; the voice chat microphone thread pulls one frame every 20 ms,
 * which naturally paces the decoder.
 */
public class AudioStream {

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = 960; // 20 ms @ 48 kHz, same as Simple Voice Chat
    private static final short[] END = new short[0];

    private final Track track;
    private final long startOffsetSec;
    private final BlockingQueue<short[]> frames = new ArrayBlockingQueue<>(150); // 3 seconds
    private final List<Process> processes = new CopyOnWriteArrayList<>();
    private volatile boolean closed;
    private volatile boolean finished;
    private volatile boolean started;
    private volatile String error;
    private volatile long framesPlayed;

    public AudioStream(Track track, long startOffsetSec) {
        this.track = track;
        this.startOffsetSec = Math.max(0, startOffsetSec);
    }

    public Track track() {
        return track;
    }

    /** Starts the decoder processes. Throws if the executables can't be launched. */
    public void start(BotConfig config, DependencyManager deps) throws IOException {
        List<String> ffmpeg = new ArrayList<>(List.of(deps.ffmpeg(),"-hide_banner", "-loglevel", "error", "-nostdin"));
        if (track.localFile()) {
            if (startOffsetSec > 0) ffmpeg.addAll(List.of("-ss", Long.toString(startOffsetSec)));
            ffmpeg.addAll(List.of("-i", track.source()));
        } else {
            ffmpeg.addAll(List.of("-i", "pipe:0"));
            if (startOffsetSec > 0) ffmpeg.addAll(List.of("-ss", Long.toString(startOffsetSec)));
        }
        ffmpeg.addAll(List.of("-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ac", "1", "-ar", Integer.toString(SAMPLE_RATE), "pipe:1"));

        Process ffmpegProcess = Processes.builder(ffmpeg).start();
        processes.add(ffmpegProcess);
        Processes.StreamTail ffmpegErr = new Processes.StreamTail(ffmpegProcess.getErrorStream(), "musicbot-ffmpeg-stderr");

        Processes.StreamTail ytErr = null;
        if (track.localFile()) {
            ffmpegProcess.getOutputStream().close();
        } else {
            List<String> ytdlp = new ArrayList<>(List.of(deps.ytDlp(),
                    "--no-warnings", "--ignore-config", "--quiet", "--no-playlist", "--no-part",
                    "-f", "bestaudio/best", "-o", "-"));
            ytdlp.addAll(deps.ytDlpArgs());
            ytdlp.addAll(config.ytDlpExtraArgs);
            ytdlp.add("--");
            ytdlp.add(track.source());

            Process ytProcess;
            try {
                ytProcess = Processes.builder(ytdlp).start();
            } catch (IOException e) {
                Processes.kill(ffmpegProcess);
                throw e;
            }
            processes.add(ytProcess);
            ytProcess.getOutputStream().close();
            ytErr = new Processes.StreamTail(ytProcess.getErrorStream(), "musicbot-ytdlp-stderr");
            Thread.ofPlatform().daemon().name("musicbot-pipe").start(() -> pipe(ytProcess.getInputStream(), ffmpegProcess.getOutputStream()));
        }

        Processes.StreamTail finalYtErr = ytErr;
        Thread.ofPlatform().daemon().name("musicbot-decoder").start(() -> decode(ffmpegProcess, ffmpegErr, finalYtErr));
    }

    private void pipe(InputStream in, OutputStream out) {
        byte[] buffer = new byte[16384];
        try (in; out) {
            int n;
            while (!closed && (n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        } catch (IOException ignored) {
            // ffmpeg closed its input or the stream was stopped
        }
    }

    private void decode(Process ffmpeg, Processes.StreamTail ffmpegErr, Processes.StreamTail ytErr) {
        byte[] buffer = new byte[FRAME_SIZE * 2];
        long produced = 0;
        try (InputStream in = ffmpeg.getInputStream()) {
            while (!closed) {
                int read = in.readNBytes(buffer, 0, buffer.length);
                if (read <= 0) break;
                short[] frame = new short[FRAME_SIZE];
                for (int i = 0; i < read / 2; i++) {
                    frame[i] = (short) ((buffer[i * 2] & 0xFF) | (buffer[i * 2 + 1] << 8));
                }
                while (!closed && !frames.offer(frame, 100, TimeUnit.MILLISECONDS)) {
                    // wait until the mic thread consumed frames
                }
                produced++;
                if (read < buffer.length) break;
            }
        } catch (IOException | InterruptedException e) {
            if (!closed) MusicBotClient.LOGGER.warn("Decoder error for {}", track.title(), e);
        }

        if (!closed && produced == 0) {
            if (ytErr != null) ytErr.await(1000);
            ffmpegErr.await(1000);
            error = ytErr != null && !ytErr.lastError().equals("unknown error") ? ytErr.lastError() : ffmpegErr.lastError();
        }
        try {
            while (!closed && !frames.offer(END, 100, TimeUnit.MILLISECONDS)) {
                // wait for space for the end marker
            }
        } catch (InterruptedException ignored) {
        }
        processes.forEach(Processes::kill);
    }

    /**
     * Called by the microphone thread every 20 ms.
     *
     * @return the next frame, {@code null} if no data is buffered yet; check {@link #isFinished()} afterwards
     */
    public short[] poll() {
        if (finished) return null;
        short[] frame = frames.poll();
        if (frame == null) return null;
        if (frame == END) {
            finished = true;
            return null;
        }
        started = true;
        framesPlayed++;
        return frame;
    }

    /** True once the first frame was played - used to send silence instead of nothing during buffer underruns. */
    public boolean hasStarted() {
        return started;
    }

    public boolean isFinished() {
        return finished;
    }

    /** Error message if the stream ended without producing any audio. */
    public String error() {
        return error;
    }

    public long positionSec() {
        return startOffsetSec + framesPlayed * FRAME_SIZE / SAMPLE_RATE;
    }

    public void close() {
        closed = true;
        frames.clear();
        processes.forEach(Processes::kill);
    }
}
