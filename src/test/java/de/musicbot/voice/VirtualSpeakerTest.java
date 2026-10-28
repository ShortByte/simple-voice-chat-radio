package de.musicbot.voice;

import de.maxhenkel.voicechat.voice.client.speaker.Speaker;
import de.maxhenkel.voicechat.voice.client.speaker.SpeakerManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that the speaker mixins apply to this version of Simple Voice Chat. Mixin targets are plain strings, so a
 * renamed method would otherwise only show up as a crash in game.
 */
class VirtualSpeakerTest {

    @Test
    void virtualSoundManagerCreatesVirtualSpeakers() throws Exception {
        // loading VirtualSoundManager also loads SoundManager, which fails if SoundManagerMixin does not apply
        VirtualSoundManager soundManager = new VirtualSoundManager();
        // without SpeakerManagerMixin this would create an OpenAL speaker and fail without an OpenAL context
        Speaker speaker = SpeakerManager.createSpeaker(soundManager, UUID.randomUUID());
        assertInstanceOf(VirtualSpeaker.class, speaker);
        speaker.play(new short[960], 1F, null, null, 48F);
        speaker.close();

        assertTrue(soundManager.openContext());
        soundManager.close();
        assertTrue(soundManager.isClosed());
        assertFalse(soundManager.openContext());
    }
}
