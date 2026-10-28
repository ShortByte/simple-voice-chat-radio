package de.musicbot.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.net.ClientServerNetManager;
import de.maxhenkel.voicechat.net.CreateGroupPacket;
import de.musicbot.MusicBotClient;
import de.musicbot.config.BotConfig;
import net.minecraft.client.Minecraft;

/**
 * Creates an open voice chat group after a server switch behind a proxy (Velocity, BungeeCord), so players on the new
 * server can join the bot. Enabled with {@link BotConfig#createGroupOnServerSwitch}.
 * <p>
 * Groups only exist on the backend server, so the group is gone after every switch. The group can only be created
 * once Simple Voice Chat has connected to the new server, which happens shortly after joining it.
 */
public final class ServerSwitchGroup {

    private static volatile boolean pending;

    private ServerSwitchGroup() {
    }

    /** Creates the group as soon as voice chat is connected to the new server. */
    public static void onServerSwitch() {
        pending = MusicBotClient.config().createGroupOnServerSwitch;
    }

    public static void onDisconnect() {
        pending = false;
    }

    /** Called by {@link MusicBotVoicechatPlugin} on the voice chat thread. */
    static void onVoicechatConnected() {
        if (!pending) return;
        pending = false;
        Minecraft.getInstance().execute(() -> {
            BotConfig config = MusicBotClient.config();
            if (!config.createGroupOnServerSwitch) return;
            String name = config.serverSwitchGroupName;
            MusicBotClient.LOGGER.info("Server switch: creating open voice chat group '{}'", name);
            ClientServerNetManager.sendToServer(new CreateGroupPacket(name, null, Group.Type.OPEN));
        });
    }
}
