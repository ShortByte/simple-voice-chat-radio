package de.musicbot;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.musicbot.audio.MusicPlayer;
import de.musicbot.command.ChatSender;
import de.musicbot.command.CommandHandler;
import de.musicbot.command.CommandSender;
import de.musicbot.compat.McCompat;
import de.musicbot.config.BotConfig;
import de.musicbot.deps.DependencyManager;
import de.musicbot.library.Library;
import de.musicbot.voice.GroupInvite;
import de.musicbot.voice.ServerSwitchGroup;
import de.musicbot.web.WebPanel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MusicBotClient implements ClientModInitializer {

    public static final String MOD_ID = "musicbot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static BotConfig config;
    private static MusicPlayer player;
    private static ChatSender chat;
    private static CommandHandler commands;
    private static DependencyManager deps;
    private static List<Pattern> systemPatterns = List.of();
    private static List<Pattern> chatPatterns = List.of();
    /** In the play phase of a server; stays set while a proxy moves the bot to another server. */
    private static boolean inGame;

    @Override
    public void onInitializeClient() {
        config = BotConfig.load();
        compilePatterns();
        deps = new DependencyManager(config, FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve("bin"));
        player = new MusicPlayer(config, deps);
        chat = new ChatSender(config);
        Library library = new Library(FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve("library"));
        commands = new CommandHandler(config, player, chat, deps, library);
        WebPanel webPanel = new WebPanel(config, player, library, deps, commands,
                () -> Minecraft.getInstance().getUser().getName(), task -> Minecraft.getInstance().execute(task));
        chat.setWebLog(webPanel::log);
        commands.setWebPanel(webPanel);
        if (config.webPanel) {
            try {
                webPanel.start();
            } catch (Exception e) {
                LOGGER.error("Could not start web panel on {}:{}", config.webPanelBind, config.webPanelPort, e);
            }
        }
        deps.startAsync(msg -> {
            LOGGER.info("[deps] {}", msg);
            Minecraft.getInstance().execute(() -> chat.local(msg));
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) ->
                onChat(message, signedMessage, sender, params));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            if (!onGroupInvite(message)) onSystemMessage(message);
        });

        HeadBobber headBobber = new HeadBobber(config, player);
        ClientTickEvents.START_CLIENT_TICK.register(headBobber::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> chat.tick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // A proxy server switch goes through the configuration phase again: JOIN without DISCONNECT in between
            if (inGame) {
                ServerSwitchGroup.onServerSwitch();
                client.execute(() -> config.serverSwitchCommands.forEach(chat::command));
                return;
            }
            inGame = true;
            client.execute(() -> {
                config.joinCommands.forEach(chat::command);
                if (!deps.isReady()) chat.local(deps.status());
                if (webPanel.isRunning()) chat.local("Web panel: " + String.join(" | ", webPanel.urls()) + " (password: /musicbot web)");
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            inGame = false;
            ServerSwitchGroup.onDisconnect();
            player.stop();
            client.execute(chat::clear);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            webPanel.stop();
            player.shutdown();
            commands.shutdown();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                McCompat.literal("musicbot")
                        .executes(ctx -> {
                            commands.handle(localSender(), "help");
                            return 1;
                        })
                        .then(McCompat.argument("command", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    commands.handle(localSender(), StringArgumentType.getString(ctx, "command"));
                                    return 1;
                                }))));

        LOGGER.info("MusicBot loaded. Owners: {}, Members: {}", config.owners, config.members);
    }

    public static MusicPlayer player() {
        return player;
    }

    public static BotConfig config() {
        return config;
    }

    /** @throws IOException if the config file is invalid; the current settings are kept then */
    public static void reloadConfig() throws IOException {
        BotConfig fresh = BotConfig.reload();
        // Copy into the existing instance so all components keep their reference
        config.owners = fresh.owners;
        config.members = fresh.members;
        config.commandPrefix = fresh.commandPrefix;
        config.acceptPublicChat = fresh.acceptPublicChat;
        config.acceptWhispers = fresh.acceptWhispers;
        config.systemMessagePatterns = fresh.systemMessagePatterns;
        config.chatMessagePatterns = fresh.chatMessagePatterns;
        config.replyMode = fresh.replyMode;
        config.whisperCommand = fresh.whisperCommand;
        config.replyDelayTicks = fresh.replyDelayTicks;
        config.announceTracks = fresh.announceTracks;
        config.ytDlpPath = fresh.ytDlpPath;
        config.ffmpegPath = fresh.ffmpegPath;
        config.denoPath = fresh.denoPath;
        config.useDeno = fresh.useDeno;
        config.autoDownloadDependencies = fresh.autoDownloadDependencies;
        config.autoUpdateYtDlp = fresh.autoUpdateYtDlp;
        config.ytDlpExtraArgs = fresh.ytDlpExtraArgs;
        config.searchPrefix = fresh.searchPrefix;
        config.maxPlaylistItems = fresh.maxPlaylistItems;
        config.loadFullPlaylists = fresh.loadFullPlaylists;
        config.maxQueueSize = fresh.maxQueueSize;
        config.volume = fresh.volume;
        config.suppressMicrophone = fresh.suppressMicrophone;
        config.allowLocalFiles = fresh.allowLocalFiles;
        config.virtualMicrophone = fresh.virtualMicrophone;
        config.virtualSpeaker = fresh.virtualSpeaker;
        config.autoJoinInvites = fresh.autoJoinInvites;
        config.createGroupOnServerSwitch = fresh.createGroupOnServerSwitch;
        config.serverSwitchGroupName = fresh.serverSwitchGroupName;
        config.joinCommands = fresh.joinCommands;
        config.serverSwitchCommands = fresh.serverSwitchCommands;
        config.membersCanDownload = fresh.membersCanDownload;
        config.maxDownloadMinutes = fresh.maxDownloadMinutes;
        config.useLibraryCache = fresh.useLibraryCache;
        config.headBob = fresh.headBob;
        config.headBobStrength = fresh.headBobStrength;
        config.headBobSway = fresh.headBobSway;
        // the password applies immediately; webPanel, webPanelBind and webPanelPort need a restart
        config.webPanelPassword = fresh.webPanelPassword;
        player.setVolume(config.volume);
        compilePatterns();
    }

    private static void compilePatterns() {
        systemPatterns = compile(config.systemMessagePatterns, "systemMessagePatterns");
        chatPatterns = compile(config.chatMessagePatterns, "chatMessagePatterns");
    }

    private static List<Pattern> compile(List<String> regexes, String option) {
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : regexes) {
            try {
                patterns.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                LOGGER.error("Invalid {} entry '{}': {}", option, regex, e.getMessage());
            }
        }
        return patterns;
    }

    private static CommandSender localSender() {
        Minecraft mc = Minecraft.getInstance();
        return new CommandSender(mc.getUser().getName(), mc.getUser().getProfileId(), CommandSender.Role.LOCAL);
    }

    private static void onChat(Component message, PlayerChatMessage signedMessage, GameProfile sender, ChatType.Bound params) {
        if (sender == null) return;
        if (McCompat.profileId(sender).equals(Minecraft.getInstance().getUser().getProfileId())) return;

        boolean whisper = params.chatType().is(ChatType.MSG_COMMAND_INCOMING);
        if (params.chatType().is(ChatType.MSG_COMMAND_OUTGOING) || params.chatType().is(ChatType.TEAM_MSG_COMMAND_OUTGOING)) return;
        if (whisper ? !config.acceptWhispers : !config.acceptPublicChat) return;

        String text = signedMessage != null ? signedMessage.signedContent() : message.getString();
        dispatch(McCompat.profileName(sender), McCompat.profileId(sender), text, whisper);
    }

    /**
     * Server plugins that send chat/PMs as system messages. Private message patterns are checked first
     * (prefix optional), then public chat patterns (prefix required).
     */
    private static void onSystemMessage(Component message) {
        if (systemPatterns.isEmpty() && chatPatterns.isEmpty()) return;
        String text = message.getString();
        if (config.acceptWhispers && matchPatterns(systemPatterns, text, true)) return;
        if (config.acceptPublicChat) matchPatterns(chatPatterns, text, false);
    }

    /** @return true if a pattern matched (even if the sender was not allowed) */
    private static boolean matchPatterns(List<Pattern> patterns, String text, boolean whisper) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.matches()) continue;
            String name;
            String content;
            try {
                name = matcher.group("sender");
                content = matcher.group("message");
            } catch (IllegalArgumentException e) {
                LOGGER.error("Pattern '{}' needs the groups (?<sender>...) and (?<message>...)", pattern.pattern());
                continue;
            }
            if (name == null || content == null) continue;
            if (name.equalsIgnoreCase(Minecraft.getInstance().getUser().getName())) return true;
            dispatch(name, lookupUuid(name), content, whisper);
            return true;
        }
        return false;
    }

    /**
     * Accepts Simple Voice Chat group invites from owners and members.
     *
     * @return true if the message was a group invite
     */
    private static boolean onGroupInvite(Component message) {
        GroupInvite invite = GroupInvite.parse(message);
        if (invite == null) return false;

        UUID uuid = invite.inviterUuid() != null ? invite.inviterUuid() : lookupUuid(invite.inviterName());
        String name = invite.inviterName();
        boolean allowed = config.isOwner(uuid, name) || config.isMember(uuid, name);
        String group = invite.groupName() == null ? "?" : invite.groupName();
        if (!config.autoJoinInvites || !allowed) {
            LOGGER.info("Ignoring group invite to '{}' from {} ({})", group, name, allowed ? "autoJoinInvites is off" : "not owner/member");
            return true;
        }

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return true;
        LOGGER.info("Accepting group invite to '{}' from {}", group, name);
        connection.sendCommand(invite.joinCommand());
        CommandSender.Role role = config.isOwner(uuid, name) ? CommandSender.Role.OWNER : CommandSender.Role.MEMBER;
        chat.reply(new CommandSender(name, uuid, role), "Joined the group " + group + ".");
        return true;
    }

    private static UUID lookupUuid(String name) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return null;
        PlayerInfo info = connection.getPlayerInfo(name);
        return info == null ? null : McCompat.profileId(info.getProfile());
    }

    /**
     * Handles a potential command. In private messages the prefix is optional.
     */
    private static void dispatch(String name, UUID uuid, String text, boolean whisper) {
        text = text.trim();
        String prefix = config.commandPrefix;
        if (text.startsWith(prefix)) {
            text = text.substring(prefix.length());
        } else if (!whisper) {
            return;
        }
        CommandSender.Role role = config.isOwner(uuid, name) ? CommandSender.Role.OWNER
                : config.isMember(uuid, name) ? CommandSender.Role.MEMBER
                : null;
        if (role == null) {
            if (whisper) LOGGER.info("Ignoring command from {} (neither owner nor member)", name);
            return;
        }
        commands.handle(new CommandSender(name, uuid, role), text);
    }
}
