package de.musicbot.command;

import de.musicbot.compat.McCompat;
import de.musicbot.config.BotConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Sends replies with a rate limit so the server doesn't kick the bot for spamming.
 * All methods must be called on the client thread.
 */
public class ChatSender {

    private static final int MAX_LENGTH = 256;
    private static final int MAX_PENDING = 30;

    private final BotConfig config;
    private final Deque<Runnable> pending = new ArrayDeque<>();
    private int cooldown;
    private volatile Consumer<String> webLog = text -> {
    };

    public ChatSender(BotConfig config) {
        this.config = config;
    }

    /** Every reply is also mirrored into the web panel log. */
    public void setWebLog(Consumer<String> webLog) {
        this.webLog = webLog;
    }

    public void reply(CommandSender target, String text) {
        text = sanitize(text);
        if (text.isEmpty()) return;
        if (target.web()) {
            webLog.accept(text);
            return;
        }
        webLog.accept((target.local() ? "[Bot] " : "[to " + target.name() + "] ") + text);
        if (target.local()) {
            local(text);
            return;
        }
        switch (config.replyMode) {
            case WHISPER -> {
                String prefix = config.whisperCommand.replace("{player}", target.name());
                int room = MAX_LENGTH - prefix.length() + "{message}".length();
                String command = prefix.replace("{message}", truncate(text, room));
                enqueue(() -> connection().sendCommand(command));
            }
            case CHAT -> {
                String message = truncate(text, MAX_LENGTH);
                enqueue(() -> connection().sendChat(message));
            }
            case LOCAL -> local(text);
            case NONE -> {
            }
        }
    }

    public void local(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            McCompat.showLocal(mc.player, Component.literal("[MusicBot] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
        }
    }

    public void clear() {
        pending.clear();
        cooldown = 0;
    }

    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (pending.isEmpty() || connection() == null) return;
        pending.pollFirst().run();
        cooldown = config.replyDelayTicks;
    }

    private void enqueue(Runnable action) {
        if (pending.size() >= MAX_PENDING) pending.pollFirst();
        pending.addLast(action);
    }

    private static ClientPacketListener connection() {
        return Minecraft.getInstance().getConnection();
    }

    private static String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '§' || c < 32 || c == 127) {
                sb.append(c == '\n' || c == '\t' ? ' ' : '?');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private static String truncate(String text, int max) {
        if (max <= 3) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
