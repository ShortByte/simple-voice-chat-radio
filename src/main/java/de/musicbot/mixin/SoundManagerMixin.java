package de.musicbot.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.maxhenkel.voicechat.voice.client.SoundManager;
import de.musicbot.MusicBotClient;
import de.musicbot.config.BotConfig;
import de.musicbot.voice.VirtualSoundManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Uses {@link VirtualSoundManager} instead of a real audio output when {@code virtualSpeaker} is enabled, or when
 * no output device can be opened. Otherwise Simple Voice Chat would disable voice chat, and the bot could not send.
 */
@Mixin(value = SoundManager.class, remap = false)
public class SoundManagerMixin {

    @WrapMethod(method = "create(Ljava/lang/String;)Lde/maxhenkel/voicechat/voice/client/SoundManager;")
    private static SoundManager musicbot$virtualSpeaker(String deviceName, Operation<SoundManager> original) {
        BotConfig config = MusicBotClient.config();
        if (config != null && config.virtualSpeaker) {
            MusicBotClient.LOGGER.info("Using virtual speaker (no audio output device needed)");
            return new VirtualSoundManager();
        }
        try {
            return original.call(deviceName);
        } catch (Exception e) {
            // typically a SpeakerException: no sound system (headless server, HeadlessMC) or the device is gone
            MusicBotClient.LOGGER.warn("Could not open audio output device ({}), using virtual speaker instead", e.getMessage());
            return new VirtualSoundManager();
        }
    }
}
