package de.musicbot.voice;

import de.maxhenkel.voicechat.voice.client.microphone.Microphone;
import de.musicbot.audio.AudioStream;

/**
 * A microphone without hardware: delivers silent 20 ms frames in real time.
 * <p>
 * Simple Voice Chat's microphone thread polls this exactly like a real device, so the music merged in
 * {@link MusicBotVoicechatPlugin} keeps flowing even on machines without any audio device (e.g. a PC that is
 * only reachable via Remote Desktop).
 */
public class VirtualMicrophone implements Microphone {

    private static final long NANOS_PER_SAMPLE = 1_000_000_000L / AudioStream.SAMPLE_RATE;
    /** Never build up more than this many frames after a hiccup, to avoid audio bursts. */
    private static final int MAX_BACKLOG_FRAMES = 5;

    private boolean open;
    private boolean started;
    private long startNanos;
    private long samplesRead;

    @Override
    public void open() {
        open = true;
    }

    @Override
    public void start() {
        if (!open || started) return;
        started = true;
        startNanos = System.nanoTime();
        samplesRead = 0;
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public void close() {
        stop();
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public int available() {
        if (!started) return 0;
        long produced = (System.nanoTime() - startNanos) / NANOS_PER_SAMPLE;
        long backlog = produced - samplesRead;
        long max = (long) MAX_BACKLOG_FRAMES * AudioStream.FRAME_SIZE;
        if (backlog > max) {
            // the thread stalled - drop the excess instead of catching up in a burst
            samplesRead = produced - max;
            backlog = max;
        }
        return (int) Math.max(0, backlog);
    }

    @Override
    public short[] read() {
        samplesRead += AudioStream.FRAME_SIZE;
        return new short[AudioStream.FRAME_SIZE];
    }
}
