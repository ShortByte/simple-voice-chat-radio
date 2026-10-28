package de.musicbot.mixin;

import de.maxhenkel.voicechat.voice.client.SoundManager;
import de.maxhenkel.voicechat.voice.client.speaker.Speaker;
import de.maxhenkel.voicechat.voice.client.speaker.SpeakerManager;
import de.musicbot.voice.VirtualSoundManager;
import de.musicbot.voice.VirtualSpeaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Creates {@link VirtualSpeaker}s for {@link VirtualSoundManager}, which has no OpenAL context for real speakers.
 */
@Mixin(value = SpeakerManager.class, remap = false)
public class SpeakerManagerMixin {

    @Inject(method = "createSpeaker", at = @At("HEAD"), cancellable = true)
    private static void musicbot$virtualSpeaker(SoundManager soundManager, UUID audioChannel, CallbackInfoReturnable<Speaker> cir) {
        if (soundManager instanceof VirtualSoundManager) {
            cir.setReturnValue(new VirtualSpeaker());
        }
    }
}
