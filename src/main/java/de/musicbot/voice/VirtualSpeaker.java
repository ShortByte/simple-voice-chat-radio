package de.musicbot.voice;

import de.maxhenkel.voicechat.voice.client.speaker.Speaker;
import net.minecraft.world.phys.Vec3;

/**
 * Speaker of {@link VirtualSoundManager}: accepts the voices of other players and discards them.
 */
public class VirtualSpeaker implements Speaker {

    @Override
    public void open() {
    }

    @Override
    public void play(short[] data, float volume, Vec3 position, String category, float maxDistance) {
    }

    @Override
    public void close() {
    }
}
