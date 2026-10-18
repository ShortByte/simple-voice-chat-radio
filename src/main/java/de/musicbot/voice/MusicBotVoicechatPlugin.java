package de.musicbot.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import de.musicbot.MusicBotClient;
import de.musicbot.audio.MusicPlayer;

/**
 * Injects the music into the microphone stream of the bot account.
 * <p>
 * Simple Voice Chat fires {@link MergeClientSoundEvent} for every 20 ms microphone frame - even when the
 * microphone is muted or nobody is talking - and transmits whatever was merged. The microphone thread is our clock.
 */
public class MusicBotVoicechatPlugin implements VoicechatPlugin {

    /** Frame merged in the current microphone cycle; only touched by the voice chat microphone thread. */
    private short[] lastFrame;

    @Override
    public String getPluginId() {
        return MusicBotClient.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        MusicBotClient.LOGGER.info("Simple Voice Chat plugin initialized");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MergeClientSoundEvent.class, this::onMerge);
        registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
    }

    private void onMerge(MergeClientSoundEvent event) {
        MusicPlayer player = MusicBotClient.player();
        lastFrame = player == null ? null : player.provideFrame();
        if (lastFrame != null) {
            event.mergeAudio(lastFrame);
        }
    }

    private void onClientSound(ClientSoundEvent event) {
        if (!MusicBotClient.config().suppressMicrophone) return;
        if (lastFrame != null) {
            // Replace mic + music mix with the pure music frame
            event.setRawAudio(lastFrame);
        } else {
            // No music: don't transmit the real microphone at all
            event.cancel();
        }
    }
}
