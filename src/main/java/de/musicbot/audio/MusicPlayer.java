package de.musicbot.audio;

import de.musicbot.MusicBotClient;
import de.musicbot.config.BotConfig;
import de.musicbot.deps.DependencyManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Queue, repeat mode and playback state. Thread-safe.
 */
public class MusicPlayer {

    public enum RepeatMode {
        OFF, ONE, ALL
    }

    private final BotConfig config;
    private final DependencyManager deps;
    private final BeatDetector beatDetector = new BeatDetector();
    private final List<Track> queue = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().daemon().name("musicbot-player").unstarted(r));
    private volatile AudioStream current;
    private volatile boolean paused;
    private volatile int volume;
    private RepeatMode repeat = RepeatMode.OFF;
    private volatile Consumer<String> announcer = msg -> {
    };

    public MusicPlayer(BotConfig config, DependencyManager deps) {
        this.config = config;
        this.deps = deps;
        this.volume = config.volume;
    }

    /** Receives status messages (now playing, errors) that should be sent to the controlling player. */
    public void setAnnouncer(Consumer<String> announcer) {
        this.announcer = announcer;
    }

    // ---------------------------------------------------------------- audio thread

    /**
     * Called by the Simple Voice Chat microphone thread for every 20 ms frame.
     *
     * @return a PCM frame to merge into the microphone, or {@code null} to send nothing
     */
    public short[] provideFrame() {
        AudioStream stream = current;
        if (stream == null || paused) return null;
        short[] frame = stream.poll();
        if (frame == null) {
            if (stream.isFinished()) {
                try {
                    worker.execute(() -> onStreamFinished(stream));
                } catch (RejectedExecutionException ignored) {
                    // shutting down
                }
                return null;
            }
            // Buffer underrun mid-song: keep the voice stream alive with silence instead of stuttering
            return stream.hasStarted() ? new short[AudioStream.FRAME_SIZE] : null;
        }
        beatDetector.process(frame);
        int vol = volume;
        if (vol != 100) {
            float gain = vol / 100F;
            for (int i = 0; i < frame.length; i++) {
                frame[i] = (short) Math.clamp((int) (frame[i] * gain), Short.MIN_VALUE, Short.MAX_VALUE);
            }
        }
        return frame;
    }

    public BeatDetector beatDetector() {
        return beatDetector;
    }

    public boolean isTransmitting() {
        AudioStream stream = current;
        return stream != null && !paused && stream.hasStarted();
    }

    // ---------------------------------------------------------------- control

    /**
     * Adds tracks to the queue.
     *
     * @param front insert at the start of the queue instead of the end
     * @return number of tracks actually added
     */
    public synchronized int enqueue(List<Track> tracks, boolean front) {
        int space = config.maxQueueSize - queue.size();
        List<Track> toAdd = tracks.subList(0, Math.max(0, Math.min(space, tracks.size())));
        if (front) {
            queue.addAll(0, toAdd);
        } else {
            queue.addAll(toAdd);
        }
        if (current == null) {
            startNext();
        }
        return toAdd.size();
    }

    /** Skips the current track and the next {@code count - 1} queued ones. Returns the skipped current track. */
    public synchronized Track skip(int count) {
        AudioStream stream = current;
        if (stream == null) return null;
        for (int i = 1; i < count && !queue.isEmpty(); i++) {
            Track removed = queue.removeFirst();
            if (repeat == RepeatMode.ALL) queue.add(removed);
        }
        stream.close();
        current = null;
        if (repeat == RepeatMode.ALL) queue.add(stream.track());
        paused = false;
        startNext();
        return stream.track();
    }

    public synchronized void stop() {
        queue.clear();
        AudioStream stream = current;
        current = null;
        paused = false;
        if (stream != null) stream.close();
    }

    public synchronized boolean seek(long seconds) {
        AudioStream stream = current;
        if (stream == null) return false;
        stream.close();
        current = null;
        return play(stream.track(), seconds, false);
    }

    public synchronized boolean setPaused(boolean paused) {
        if (current == null) return false;
        this.paused = paused;
        return true;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setVolume(int volume) {
        this.volume = Math.clamp(volume, 0, 200);
    }

    public int getVolume() {
        return volume;
    }

    public synchronized void setRepeat(RepeatMode repeat) {
        this.repeat = repeat;
    }

    public synchronized RepeatMode getRepeat() {
        return repeat;
    }

    public synchronized void shuffle() {
        Collections.shuffle(queue);
    }

    public synchronized int clearQueue() {
        int size = queue.size();
        queue.clear();
        return size;
    }

    /** @param index 1-based queue position */
    public synchronized Track remove(int index) {
        if (index < 1 || index > queue.size()) return null;
        return queue.remove(index - 1);
    }

    /** Moves a track between two 1-based queue positions. */
    public synchronized Track move(int from, int to) {
        if (from < 1 || from > queue.size() || to < 1 || to > queue.size()) return null;
        Track track = queue.remove(from - 1);
        queue.add(to - 1, track);
        return track;
    }

    public synchronized List<Track> queueSnapshot() {
        return List.copyOf(queue);
    }

    /** @return the currently playing track or {@code null} */
    public Track currentTrack() {
        AudioStream stream = current;
        return stream == null ? null : stream.track();
    }

    public long positionSec() {
        AudioStream stream = current;
        return stream == null ? 0 : stream.positionSec();
    }

    public synchronized void shutdown() {
        stop();
        worker.shutdownNow();
    }

    // ---------------------------------------------------------------- internals

    private synchronized void onStreamFinished(AudioStream stream) {
        if (current != stream) return; // already skipped / handled
        current = null;
        stream.close();

        String error = stream.error();
        if (error != null) {
            announcer.accept("Error playing \"" + stream.track().title() + "\": " + error);
        } else if (repeat == RepeatMode.ONE) {
            if (play(stream.track(), 0, false)) return;
        } else if (repeat == RepeatMode.ALL) {
            queue.add(stream.track());
        }
        if (!startNext() && error == null) {
            announcer.accept("The queue is empty - playback finished.");
        }
    }

    /** Starts the next queued track that can be launched. */
    private boolean startNext() {
        while (!queue.isEmpty()) {
            if (play(queue.removeFirst(), 0, config.announceTracks)) return true;
        }
        return false;
    }

    private boolean play(Track track, long offset, boolean announce) {
        AudioStream stream = new AudioStream(track, offset);
        try {
            stream.start(config, deps);
        } catch (IOException e) {
            MusicBotClient.LOGGER.error("Failed to start playback of {}", track.source(), e);
            announcer.accept("Cannot play \"" + track.title() + "\": " + e.getMessage());
            return false;
        }
        current = stream;
        if (announce) {
            announcer.accept("▶ " + track.title() + " [" + track.formattedDuration() + "]");
        }
        return true;
    }
}
