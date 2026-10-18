package de.musicbot.audio;

/**
 * Very small onset detector for the bass range (kick drums).
 * <p>
 * Each 20 ms frame is low-pass filtered (~150 Hz), its energy is compared with the average of the last
 * second; a clear jump above the average counts as a beat. Runs on the voice chat microphone thread.
 */
public class BeatDetector {

    private static final int HISTORY = 50;              // 50 frames = 1 second
    private static final float LOWPASS_ALPHA = 0.0192F;  // one-pole low-pass, ~150 Hz @ 48 kHz
    private static final float THRESHOLD = 1.35F;        // energy must exceed the average by 35 %
    private static final double MIN_ENERGY = 4.0E4;      // ignore near-silence
    private static final int MIN_INTERVAL_FRAMES = 14;   // 280 ms -> max ~214 BPM

    private final double[] history = new double[HISTORY];
    private int index;
    private int filled;
    private double sum;
    private float lowpass;
    private int framesSinceBeat = MIN_INTERVAL_FRAMES;
    private volatile long beatCount;

    public void process(short[] frame) {
        double energy = 0;
        float lp = lowpass;
        for (short sample : frame) {
            lp += LOWPASS_ALPHA * (sample - lp);
            energy += (double) lp * lp;
        }
        lowpass = lp;
        energy /= frame.length;
        framesSinceBeat++;

        if (filled == HISTORY) {
            double average = sum / HISTORY;
            if (energy > average * THRESHOLD && energy > MIN_ENERGY && framesSinceBeat >= MIN_INTERVAL_FRAMES) {
                framesSinceBeat = 0;
                beatCount++;
            }
        }

        sum -= history[index];
        history[index] = energy;
        sum += energy;
        index = (index + 1) % HISTORY;
        if (filled < HISTORY) filled++;
    }

    /** Increases by one for every detected beat. */
    public long beatCount() {
        return beatCount;
    }
}
