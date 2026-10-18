package de.musicbot.voice;

import de.musicbot.compat.McCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A Simple Voice Chat group invitation ({@code /voicechat invite <bot>}), parsed from the system message.
 *
 * @param joinCommand command behind the [ACCEPT] button, without leading slash
 * @param inviterUuid UUID of the inviting player (from the hover of their name), may be null
 * @param inviterName name of the inviting player
 * @param groupName   name of the group, may be null
 */
public record GroupInvite(String joinCommand, UUID inviterUuid, String inviterName, String groupName) {

    private static final String INVITE_KEY = "message.voicechat.invite";
    /** Only accept exactly what Simple Voice Chat generates: /voicechat join <uuid> ["password"] */
    private static final Pattern JOIN_COMMAND = Pattern.compile("^/voicechat join [0-9a-fA-F-]{36}( \"[^\"\\n]*\")?$");

    /** @return the invite, or {@code null} if the message is no group invite */
    public static GroupInvite parse(Component message) {
        Search search = new Search();
        search.walk(message);
        if (search.joinCommand == null) return null;

        String name = null;
        UUID uuid = null;
        String group = null;
        if (search.invite != null) {
            Object[] args = search.invite.getArgs();
            if (args.length > 0 && args[0] instanceof Component inviter) {
                Search who = new Search();
                who.walk(inviter);
                uuid = who.entityUuid;
                name = who.insertion != null ? who.insertion : inviter.getString();
            } else if (args.length > 0) {
                name = String.valueOf(args[0]);
            }
            if (args.length > 1) {
                group = args[1] instanceof Component c ? c.getString() : String.valueOf(args[1]);
            }
        } else {
            // e.g. plugin versions that send pre-rendered text: rely on the entity hover only
            uuid = search.entityUuid;
            name = search.insertion;
        }
        if (uuid == null && name == null) return null;
        return new GroupInvite(search.joinCommand.substring(1), uuid, name == null ? "?" : name.trim(), group);
    }

    private static final class Search {
        String joinCommand;
        TranslatableContents invite;
        UUID entityUuid;
        String insertion;

        void walk(Component component) {
            Style style = component.getStyle();
            checkJoinCommand(style);
            if (entityUuid == null) {
                entityUuid = McCompat.shownEntity(style);
            }
            if (style.getInsertion() != null && insertion == null) {
                insertion = style.getInsertion();
            }
            if (component.getContents() instanceof TranslatableContents translatable) {
                if (invite == null && INVITE_KEY.equals(translatable.getKey())) invite = translatable;
                for (Object arg : translatable.getArgs()) {
                    if (arg instanceof Component c) walkClickOnly(c);
                }
            }
            for (Component sibling : component.getSiblings()) {
                walk(sibling);
            }
        }

        /** Inside translation arguments only the accept button is of interest (inviter is parsed separately). */
        private void walkClickOnly(Component component) {
            checkJoinCommand(component.getStyle());
            if (component.getContents() instanceof TranslatableContents translatable) {
                for (Object arg : translatable.getArgs()) {
                    if (arg instanceof Component c) walkClickOnly(c);
                }
            }
            for (Component sibling : component.getSiblings()) {
                walkClickOnly(sibling);
            }
        }

        private void checkJoinCommand(Style style) {
            String command = McCompat.runCommand(style);
            if (command != null && joinCommand == null && JOIN_COMMAND.matcher(command).matches()) {
                joinCommand = command;
            }
        }
    }
}
