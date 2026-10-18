package de.musicbot;

import de.musicbot.audio.MusicPlayer;
import de.musicbot.config.BotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Nods the bot's head on every detected beat. The rotation is sent to the server with the normal
 * movement packets, so every player sees it.
 */
public class HeadBobber {

    private static final float DECAY = 0.55F; // how fast the head comes back up per tick

    private final BotConfig config;
    private final MusicPlayer player;
    private long lastBeat;
    private float envelope;
    private float applied;  // pitch offset we added last tick
    private boolean sway;   // alternate a slight left/right turn

    public HeadBobber(BotConfig config, MusicPlayer player) {
        this.config = config;
        this.player = player;
    }

    public void tick(Minecraft mc) {
        LocalPlayer self = mc.player;
        long beats = player.beatDetector().beatCount();
        boolean newBeat = beats != lastBeat;
        lastBeat = beats;

        if (self == null) {
            envelope = 0;
            applied = 0;
            return;
        }

        if (newBeat && config.headBob && player.isTransmitting()) {
            envelope = 1;
            sway = !sway;
            if (config.headBobSway) {
                self.setYRot(self.getYRot() + (sway ? 1 : -1) * config.headBobStrength * 0.25F);
            }
        } else {
            envelope *= DECAY;
            if (envelope < 0.01F) envelope = 0;
        }

        // Remove last tick's offset, so mouse movement / teleports keep working, then add the new one
        float offset = envelope * config.headBobStrength;
        float base = self.getXRot() - applied;
        float pitch = Mth.clamp(base + offset, -90F, 90F);
        self.setXRot(pitch);
        applied = pitch - base;
    }
}
