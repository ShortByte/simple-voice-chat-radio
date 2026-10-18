package de.musicbot.command;

import java.util.UUID;

/**
 * Whoever issued a command.
 *
 * @param name player name
 * @param uuid player UUID, may be {@code null} for commands parsed from system messages
 * @param role permission level
 */
public record CommandSender(String name, UUID uuid, Role role) {

    public enum Role {
        /** Normal music commands. */
        MEMBER,
        /** Everything, also via chat/MSG. */
        OWNER,
        /** The web panel - owner rights, replies go to the panel log. */
        WEB,
        /** The bot account itself, typing {@code /musicbot ...}. */
        LOCAL
    }

    /** True if the bot account itself typed the command. */
    public boolean local() {
        return role == Role.LOCAL;
    }

    /** True if the command came from the web panel. */
    public boolean web() {
        return role == Role.WEB;
    }

    /** True for owners, the web panel and the bot account itself. */
    public boolean isOwner() {
        return role != Role.MEMBER;
    }
}
