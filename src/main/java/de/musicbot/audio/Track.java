package de.musicbot.audio;

/**
 * A queued track.
 *
 * @param title       display title
 * @param source      page URL (resolved by yt-dlp) or absolute local file path
 * @param durationSec duration in seconds, or -1 if unknown (e.g. livestreams)
 * @param localFile   true if {@link #source} is a local file that is fed to ffmpeg directly
 * @param requestedBy name of the player who requested it
 */
public record Track(String title, String source, long durationSec, boolean localFile, String requestedBy) {

    public String formattedDuration() {
        return durationSec < 0 ? "LIVE" : formatTime(durationSec);
    }

    public static String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0 ? "%d:%02d:%02d".formatted(h, m, s) : "%d:%02d".formatted(m, s);
    }
}
