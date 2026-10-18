package de.musicbot.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every option in {@link BotConfig} needs a name and a tooltip for the config screen. When this fails after adding
 * an option, also add it to {@link ConfigScreen}.
 */
class ConfigTranslationTest {

    @Test
    void everyOptionHasNameAndTooltip() throws Exception {
        JsonObject lang;
        try (InputStream in = getClass().getResourceAsStream("/assets/musicbot/lang/en_us.json")) {
            assertNotNull(in, "en_us.json missing");
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> missing = new ArrayList<>();
        for (Field field : BotConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            String key = "config.musicbot." + field.getName();
            if (!lang.has(key)) missing.add(key);
            if (!lang.has(key + ".tooltip")) missing.add(key + ".tooltip");
        }
        for (BotConfig.ReplyMode mode : BotConfig.ReplyMode.values()) {
            String key = "config.musicbot.replyMode." + mode.name().toLowerCase(Locale.ROOT);
            if (!lang.has(key)) missing.add(key);
        }
        assertTrue(missing.isEmpty(), "Missing translations: " + missing);
    }
}
