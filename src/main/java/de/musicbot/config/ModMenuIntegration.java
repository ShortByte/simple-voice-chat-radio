package de.musicbot.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.musicbot.compat.McCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds the config button in Mod Menu. Mod Menu and Cloth Config are both optional: without Cloth Config the button
 * explains what is missing instead of crashing, and the config file can still be edited by hand.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return ConfigScreen::create;
        }
        return parent -> new AlertScreen(() -> McCompat.setScreen(parent),
                Component.translatable("config.musicbot.title"),
                Component.translatable("config.musicbot.clothMissing"));
    }
}
