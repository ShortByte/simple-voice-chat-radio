package de.musicbot.voice;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.Bootstrap;
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?} else
//import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GroupInviteTest {

    private static final UUID PLAYER = UUID.fromString("0f3c7e6e-1b8f-4a52-9c4e-5d7b8a1f2c33");
    private static final UUID GROUP = UUID.fromString("6a1d1f0e-2c3b-4d5e-8f90-112233445566");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static HoverEvent showPlayer(String name) {
        //? if >=26.2 {
        return new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(EntityTypes.PLAYER, PLAYER, Component.literal(name)));
        //?} elif >=1.21.5 {
        /*return new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(EntityType.PLAYER, PLAYER, Component.literal(name)));
        *///?} else
        //return new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(EntityType.PLAYER, PLAYER, Component.literal(name)));
    }

    private static ClickEvent runCommand(String command) {
        //? if >=1.21.5 {
        return new ClickEvent.RunCommand(command);
        //?} else
        //return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
    }

    /** Built exactly like Simple Voice Chat's /voicechat invite command does. */
    private static Component invite(String joinCommand) {
        Component inviter = Component.literal("ShortByte").withStyle(style -> style
                .withHoverEvent(showPlayer("ShortByte"))
                .withInsertion("ShortByte"));
        Component accept = ComponentUtils.wrapInSquareBrackets(Component.translatable("message.voicechat.accept_invite")
                .withStyle(style -> style.withClickEvent(runCommand(joinCommand)))).withStyle(ChatFormatting.GREEN);
        return Component.translatable("message.voicechat.invite", inviter, Component.literal("Party").withStyle(ChatFormatting.GRAY), accept);
    }

    @Test
    void parsesInviteWithPassword() {
        GroupInvite invite = GroupInvite.parse(invite("/voicechat join " + GROUP + " \"geheim\""));
        assertNotNull(invite);
        assertEquals("voicechat join " + GROUP + " \"geheim\"", invite.joinCommand());
        assertEquals(PLAYER, invite.inviterUuid());
        assertEquals("ShortByte", invite.inviterName());
        assertEquals("Party", invite.groupName());
    }

    @Test
    void parsesInviteWithoutPassword() {
        GroupInvite invite = GroupInvite.parse(invite("/voicechat join " + GROUP));
        assertNotNull(invite);
        assertEquals("voicechat join " + GROUP, invite.joinCommand());
    }

    @Test
    void rejectsOtherCommands() {
        assertNull(GroupInvite.parse(invite("/op Evil")));
        assertNull(GroupInvite.parse(invite("/voicechat join " + GROUP + " \"x\" extra")));
        assertNull(GroupInvite.parse(invite("/voicechat leave")));
    }

    @Test
    void ignoresNormalMessages() {
        assertNull(GroupInvite.parse(Component.literal("ShortByte invited you to the group Party. [ACCEPT]")));
        assertNull(GroupInvite.parse(Component.translatable("message.voicechat.join_successful", "Party")));
    }
}
