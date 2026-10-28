package de.musicbot.voice;

import de.maxhenkel.voicechat.voice.client.SoundManager;
import de.maxhenkel.voicechat.voice.client.speaker.Speaker;

/**
 * An audio output without hardware, used instead of Simple Voice Chat's OpenAL output.
 * <p>
 * Simple Voice Chat disables voice chat completely when it cannot open an audio output device - including sending,
 * which the bot needs. This happens on servers without a sound system or in HeadlessMC. With this sound manager voice
 * chat stays enabled; incoming voices are discarded by {@link VirtualSpeaker}, the bot does not need to hear them.
 */
public class VirtualSoundManager extends SoundManager {

    public static final String DEVICE_NAME = "MusicBot virtual speaker";

    private volatile boolean closed;

    public VirtualSoundManager() {
        // no device, no context, no capabilities: none of the OpenAL methods below are ever called
        super(DEVICE_NAME, 0L, 0L, null, 1F);
    }

    @Override
    public void trackSpeaker(Speaker speaker) {
    }

    @Override
    public void untrackSpeaker(Speaker speaker) {
    }

    @Override
    public boolean openContext() {
        return !closed;
    }

    @Override
    public void closeContext() {
    }

    @Override
    public boolean isClosing() {
        return closed;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
