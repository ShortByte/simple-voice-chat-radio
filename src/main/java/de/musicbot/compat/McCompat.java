package de.musicbot.compat;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

//? if >=26.1 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
//?} else
//import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

import java.util.UUID;

/**
 * All Minecraft / Fabric API calls that differ between the supported game versions.
 * <p>
 * The {@code //?} comments are Stonecutter conditions: the inactive branches stay commented out and are
 * swapped in when building (or switching to) another Minecraft version. Keep version specific code in here,
 * so the rest of the mod can be written once.
 */
public final class McCompat {

    private McCompat() {
    }

    public static UUID profileId(GameProfile profile) {
        //? if >=1.21.9 {
        return profile.id();
        //?} else
        //return profile.getId();
    }

    public static String profileName(GameProfile profile) {
        //? if >=1.21.9 {
        return profile.name();
        //?} else
        //return profile.getName();
    }

    /** Shows a message only in the local chat, it is not sent to the server. */
    public static void showLocal(LocalPlayer player, Component message) {
        //? if >=26.1 {
        player.sendSystemMessage(message);
        //?} else
        //player.displayClientMessage(message, false);
    }

    /** @return the command behind a "run command" click event, or {@code null} */
    public static String runCommand(Style style) {
        //? if >=1.21.5 {
        return style.getClickEvent() instanceof ClickEvent.RunCommand(String command) ? command : null;
        //?} else {
        /*ClickEvent click = style.getClickEvent();
        return click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND ? click.getValue() : null;
        *///?}
    }

    /** @return the UUID of the entity in a "show entity" hover event, or {@code null} */
    public static UUID shownEntity(Style style) {
        //? if >=1.21.5 {
        return style.getHoverEvent() instanceof HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo info) ? info.uuid : null;
        //?} else {
        /*HoverEvent hover = style.getHoverEvent();
        HoverEvent.EntityTooltipInfo info = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_ENTITY);
        return info == null ? null : info.id;
        *///?}
    }

    public static void setScreen(Screen screen) {
        //? if >=26.2 {
        Minecraft.getInstance().gui.setScreen(screen);
        //?} else
        //Minecraft.getInstance().setScreen(screen);
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        //? if >=26.1 {
        return ClientCommands.literal(name);
        //?} else
        //return ClientCommandManager.literal(name);
    }

    public static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
        //? if >=26.1 {
        return ClientCommands.argument(name, type);
        //?} else
        //return ClientCommandManager.argument(name, type);
    }
}
