package de.musicbot.mixin;

import de.maxhenkel.voicechat.voice.client.microphone.Microphone;
import de.maxhenkel.voicechat.voice.client.microphone.MicrophoneManager;
import de.musicbot.MusicBotClient;
import de.musicbot.config.BotConfig;
import de.musicbot.voice.VirtualMicrophone;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces Simple Voice Chat's hardware microphone with {@link VirtualMicrophone} when enabled.
 */
@Mixin(value = MicrophoneManager.class, remap = false)
public class MicrophoneManagerMixin {

    @Inject(method = "createMicrophone", at = @At("HEAD"), cancellable = true)
    private static void musicbot$virtualMicrophone(CallbackInfoReturnable<Microphone> cir) {
        BotConfig config = MusicBotClient.config();
        if (config != null && config.virtualMicrophone) {
            MusicBotClient.LOGGER.info("Using virtual microphone (no audio device needed)");
            VirtualMicrophone mic = new VirtualMicrophone();
            mic.open();
            cir.setReturnValue(mic);
        }
    }
}
