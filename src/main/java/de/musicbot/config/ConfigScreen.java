package de.musicbot.config;

import de.musicbot.MusicBotClient;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Config screen for every option in {@link BotConfig}, built with Cloth Config.
 * <p>
 * Only referenced when Cloth Config is installed (see {@link ModMenuIntegration}).
 * Texts are in {@code assets/musicbot/lang/en_us.json}: {@code config.musicbot.<option>} and {@code <key>.tooltip}.
 */
public final class ConfigScreen {

    private static final String PREFIX = "config.musicbot.";
    private static final Pattern PLAYER = Pattern.compile("[A-Za-z0-9_]{1,16}|[0-9a-fA-F-]{36}");

    private final BotConfig config = MusicBotClient.config();
    private final BotConfig defaults = new BotConfig();
    private final ConfigEntryBuilder entries;

    private ConfigScreen(ConfigEntryBuilder entries) {
        this.entries = entries;
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable(PREFIX + "title"))
                .setSavingRunnable(() -> {
                    MusicBotClient.config().save();
                    // re-reads the file, so the new values are validated and applied right away
                    try {
                        MusicBotClient.reloadConfig();
                    } catch (IOException e) {
                        MusicBotClient.LOGGER.error("Failed to reload the config", e);
                    }
                });
        new ConfigScreen(builder.entryBuilder()).addCategories(builder);
        return builder.build();
    }

    private void addCategories(ConfigBuilder builder) {
        ConfigCategory access = category(builder, "access");
        access.addEntry(players("owners", config.owners, defaults.owners, v -> config.owners = v));
        access.addEntry(players("members", config.members, defaults.members, v -> config.members = v));
        access.addEntry(bool("membersCanDownload", config.membersCanDownload, defaults.membersCanDownload, v -> config.membersCanDownload = v));
        access.addEntry(bool("allowLocalFiles", config.allowLocalFiles, defaults.allowLocalFiles, v -> config.allowLocalFiles = v));

        ConfigCategory chat = category(builder, "chat");
        chat.addEntry(entries.startStrField(name("commandPrefix"), config.commandPrefix)
                .setDefaultValue(defaults.commandPrefix).setTooltip(tooltip("commandPrefix"))
                .setErrorSupplier(v -> v.isBlank() || v.contains(" ") ? error("noSpaces") : Optional.empty())
                .setSaveConsumer(v -> config.commandPrefix = v).build());
        chat.addEntry(bool("acceptPublicChat", config.acceptPublicChat, defaults.acceptPublicChat, v -> config.acceptPublicChat = v));
        chat.addEntry(bool("acceptWhispers", config.acceptWhispers, defaults.acceptWhispers, v -> config.acceptWhispers = v));
        chat.addEntry(entries.startEnumSelector(name("replyMode"), BotConfig.ReplyMode.class, config.replyMode)
                .setDefaultValue(defaults.replyMode).setTooltip(tooltip("replyMode"))
                .setEnumNameProvider(mode -> Component.translatable(PREFIX + "replyMode." + mode.name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(v -> config.replyMode = v).build());
        chat.addEntry(entries.startStrField(name("whisperCommand"), config.whisperCommand)
                .setDefaultValue(defaults.whisperCommand).setTooltip(tooltip("whisperCommand"))
                .setErrorSupplier(v -> v.contains("{player}") && v.contains("{message}") ? Optional.empty() : error("whisperPlaceholders"))
                .setSaveConsumer(v -> config.whisperCommand = v).build());
        chat.addEntry(entries.startIntField(name("replyDelayTicks"), config.replyDelayTicks)
                .setDefaultValue(defaults.replyDelayTicks).setTooltip(tooltip("replyDelayTicks")).setMin(0).setMax(1200)
                .setSaveConsumer(v -> config.replyDelayTicks = v).build());
        chat.addEntry(bool("announceTracks", config.announceTracks, defaults.announceTracks, v -> config.announceTracks = v));
        chat.addEntry(patterns("systemMessagePatterns", config.systemMessagePatterns, defaults.systemMessagePatterns, v -> config.systemMessagePatterns = v));
        chat.addEntry(patterns("chatMessagePatterns", config.chatMessagePatterns, defaults.chatMessagePatterns, v -> config.chatMessagePatterns = v));

        ConfigCategory playback = category(builder, "playback");
        playback.addEntry(entries.startIntSlider(name("volume"), config.volume, 0, 200)
                .setDefaultValue(defaults.volume).setTooltip(tooltip("volume"))
                .setTextGetter(v -> Component.literal(v + "%"))
                .setSaveConsumer(v -> config.volume = v).build());
        playback.addEntry(entries.startStrField(name("searchPrefix"), config.searchPrefix)
                .setDefaultValue(defaults.searchPrefix).setTooltip(tooltip("searchPrefix"))
                .setErrorSupplier(v -> v.endsWith(":") ? Optional.empty() : error("searchPrefix"))
                .setSaveConsumer(v -> config.searchPrefix = v).build());
        playback.addEntry(entries.startIntField(name("maxPlaylistItems"), config.maxPlaylistItems)
                .setDefaultValue(defaults.maxPlaylistItems).setTooltip(tooltip("maxPlaylistItems")).setMin(1).setMax(10_000)
                .setSaveConsumer(v -> config.maxPlaylistItems = v).build());
        playback.addEntry(bool("loadFullPlaylists", config.loadFullPlaylists, defaults.loadFullPlaylists, v -> config.loadFullPlaylists = v));
        playback.addEntry(entries.startIntField(name("maxQueueSize"), config.maxQueueSize)
                .setDefaultValue(defaults.maxQueueSize).setTooltip(tooltip("maxQueueSize")).setMin(1).setMax(100_000)
                .setSaveConsumer(v -> config.maxQueueSize = v).build());
        playback.addEntry(bool("headBob", config.headBob, defaults.headBob, v -> config.headBob = v));
        playback.addEntry(entries.startIntSlider(name("headBobStrength"), Math.round(config.headBobStrength), 1, 45)
                .setDefaultValue(Math.round(defaults.headBobStrength)).setTooltip(tooltip("headBobStrength"))
                .setTextGetter(v -> Component.literal(v + "°"))
                .setSaveConsumer(v -> config.headBobStrength = v).build());
        playback.addEntry(bool("headBobSway", config.headBobSway, defaults.headBobSway, v -> config.headBobSway = v));

        ConfigCategory library = category(builder, "library");
        library.addEntry(entries.startIntField(name("maxDownloadMinutes"), config.maxDownloadMinutes)
                .setDefaultValue(defaults.maxDownloadMinutes).setTooltip(tooltip("maxDownloadMinutes")).setMin(0).setMax(24 * 60)
                .setSaveConsumer(v -> config.maxDownloadMinutes = v).build());
        library.addEntry(bool("useLibraryCache", config.useLibraryCache, defaults.useLibraryCache, v -> config.useLibraryCache = v));

        ConfigCategory voice = category(builder, "voice");
        voice.addEntry(entries.startBooleanToggle(name("virtualMicrophone"), config.virtualMicrophone)
                .setDefaultValue(defaults.virtualMicrophone).setTooltip(tooltip("virtualMicrophone")).requireRestart()
                .setSaveConsumer(v -> config.virtualMicrophone = v).build());
        voice.addEntry(bool("virtualSpeaker", config.virtualSpeaker, defaults.virtualSpeaker, v -> config.virtualSpeaker = v));
        voice.addEntry(bool("suppressMicrophone", config.suppressMicrophone, defaults.suppressMicrophone, v -> config.suppressMicrophone = v));
        voice.addEntry(bool("autoJoinInvites", config.autoJoinInvites, defaults.autoJoinInvites, v -> config.autoJoinInvites = v));
        voice.addEntry(bool("createGroupOnServerSwitch", config.createGroupOnServerSwitch, defaults.createGroupOnServerSwitch, v -> config.createGroupOnServerSwitch = v));
        voice.addEntry(entries.startStrField(name("serverSwitchGroupName"), config.serverSwitchGroupName)
                .setDefaultValue(defaults.serverSwitchGroupName).setTooltip(tooltip("serverSwitchGroupName"))
                .setErrorSupplier(v -> BotConfig.GROUP_NAME.matcher(v).matches() ? Optional.empty() : error("groupName"))
                .setSaveConsumer(v -> config.serverSwitchGroupName = v).build());
        voice.addEntry(commandList("joinCommands", config.joinCommands, defaults.joinCommands, v -> config.joinCommands = v));
        voice.addEntry(commandList("serverSwitchCommands", config.serverSwitchCommands, defaults.serverSwitchCommands, v -> config.serverSwitchCommands = v));

        ConfigCategory web = category(builder, "web");
        web.addEntry(entries.startBooleanToggle(name("webPanel"), config.webPanel)
                .setDefaultValue(defaults.webPanel).setTooltip(tooltip("webPanel")).requireRestart()
                .setSaveConsumer(v -> config.webPanel = v).build());
        web.addEntry(entries.startStrField(name("webPanelBind"), config.webPanelBind)
                .setDefaultValue(defaults.webPanelBind).setTooltip(tooltip("webPanelBind")).requireRestart()
                .setErrorSupplier(v -> v.isBlank() || v.contains(" ") ? error("noSpaces") : Optional.empty())
                .setSaveConsumer(v -> config.webPanelBind = v).build());
        web.addEntry(entries.startIntField(name("webPanelPort"), config.webPanelPort)
                .setDefaultValue(defaults.webPanelPort).setTooltip(tooltip("webPanelPort")).setMin(1).setMax(65535).requireRestart()
                .setSaveConsumer(v -> config.webPanelPort = v).build());
        web.addEntry(entries.startStrField(name("webPanelPassword"), config.webPanelPassword)
                .setTooltip(tooltip("webPanelPassword"))
                .setErrorSupplier(v -> v.isBlank() ? Optional.empty() : v.length() < 8 ? error("passwordLength") : Optional.empty())
                .setSaveConsumer(v -> config.webPanelPassword = v).build());

        ConfigCategory tools = category(builder, "tools");
        tools.addEntry(bool("autoDownloadDependencies", config.autoDownloadDependencies, defaults.autoDownloadDependencies, v -> config.autoDownloadDependencies = v));
        tools.addEntry(bool("autoUpdateYtDlp", config.autoUpdateYtDlp, defaults.autoUpdateYtDlp, v -> config.autoUpdateYtDlp = v));
        tools.addEntry(bool("useDeno", config.useDeno, defaults.useDeno, v -> config.useDeno = v));
        tools.addEntry(path("ytDlpPath", config.ytDlpPath, defaults.ytDlpPath, v -> config.ytDlpPath = v));
        tools.addEntry(path("ffmpegPath", config.ffmpegPath, defaults.ffmpegPath, v -> config.ffmpegPath = v));
        tools.addEntry(path("denoPath", config.denoPath, defaults.denoPath, v -> config.denoPath = v));
        tools.addEntry(entries.startStrList(name("ytDlpExtraArgs"), config.ytDlpExtraArgs)
                .setDefaultValue(defaults.ytDlpExtraArgs).setTooltip(tooltip("ytDlpExtraArgs"))
                .setSaveConsumer(v -> config.ytDlpExtraArgs = new ArrayList<>(v)).build());
    }

    // ---------------------------------------------------------------- entry helpers

    private ConfigCategory category(ConfigBuilder builder, String key) {
        return builder.getOrCreateCategory(Component.translatable(PREFIX + "category." + key));
    }

    private AbstractConfigListEntry<Boolean> bool(String key, boolean value, boolean def, Consumer<Boolean> save) {
        return entries.startBooleanToggle(name(key), value).setDefaultValue(def).setTooltip(tooltip(key)).setSaveConsumer(save).build();
    }

    private AbstractConfigListEntry<String> path(String key, String value, String def, Consumer<String> save) {
        return entries.startStrField(name(key), value).setDefaultValue(def).setTooltip(tooltip(key))
                .setErrorSupplier(v -> v.isBlank() ? error("notEmpty") : Optional.empty())
                .setSaveConsumer(save).requireRestart().build();
    }

    private AbstractConfigListEntry<List<String>> players(String key, List<String> value, List<String> def, Consumer<List<String>> save) {
        return entries.startStrList(name(key), value).setDefaultValue(def).setTooltip(tooltip(key))
                .setCellErrorSupplier(v -> PLAYER.matcher(v.trim()).matches() ? Optional.empty() : error("player"))
                .setSaveConsumer(v -> save.accept(new ArrayList<>(v.stream().map(String::trim).toList()))).build();
    }

    private AbstractConfigListEntry<List<String>> commandList(String key, List<String> value, List<String> def, Consumer<List<String>> save) {
        return entries.startStrList(name(key), value).setDefaultValue(def).setTooltip(tooltip(key))
                .setCellErrorSupplier(v -> v.isBlank() || v.trim().equals("/") ? error("notEmpty") : Optional.empty())
                .setSaveConsumer(v -> save.accept(new ArrayList<>(v.stream().map(String::trim).toList()))).build();
    }

    private AbstractConfigListEntry<List<String>> patterns(String key, List<String> value, List<String> def, Consumer<List<String>> save) {
        return entries.startStrList(name(key), value).setDefaultValue(def).setTooltip(tooltip(key))
                .setCellErrorSupplier(ConfigScreen::checkPattern)
                .setSaveConsumer(v -> save.accept(new ArrayList<>(v))).build();
    }

    /** Same requirements as the chat handling: a valid regex with the groups {@code sender} and {@code message}. */
    private static Optional<Component> checkPattern(String regex) {
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return Optional.of(Component.translatable(PREFIX + "error.regex", e.getDescription()));
        }
        if (!regex.contains("(?<sender>") || !regex.contains("(?<message>")) return error("regexGroups");
        return Optional.empty();
    }

    private static Component name(String key) {
        return Component.translatable(PREFIX + key);
    }

    private static Component tooltip(String key) {
        return Component.translatable(PREFIX + key + ".tooltip");
    }

    private static Optional<Component> error(String key) {
        return Optional.of(Component.translatable(PREFIX + "error." + key));
    }
}
