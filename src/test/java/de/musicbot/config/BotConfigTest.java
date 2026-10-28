package de.musicbot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BotConfigTest {

    private static final String JSON = "{\"owners\": [\"ShortByte\"], \"systemMessagePatterns\": [\"^PN \u00bb (?<sender>[A-Za-z0-9_]+) \u2022 (?<message>.+)$\"]}";

    @TempDir
    Path dir;

    @Test
    void readsUtf8() throws IOException {
        Path file = dir.resolve("musicbot.json");
        Files.writeString(file, JSON, StandardCharsets.UTF_8);
        BotConfig config = BotConfig.read(file);
        assertEquals(List.of("ShortByte"), config.owners);
        assertEquals("^PN \u00bb (?<sender>[A-Za-z0-9_]+) \u2022 (?<message>.+)$", config.systemMessagePatterns.getFirst());
    }

    @Test
    void readsUtf8WithBom() throws IOException {
        Path file = dir.resolve("musicbot.json");
        Files.writeString(file, "\uFEFF" + JSON, StandardCharsets.UTF_8);
        assertEquals("^PN \u00bb (?<sender>[A-Za-z0-9_]+) \u2022 (?<message>.+)$", BotConfig.read(file).systemMessagePatterns.getFirst());
    }

    @Test
    void readsWindowsAnsi() throws IOException {
        Path file = dir.resolve("musicbot.json");
        Files.writeString(file, JSON, Charset.forName("windows-1252"));
        assertEquals("^PN \u00bb (?<sender>[A-Za-z0-9_]+) \u2022 (?<message>.+)$", BotConfig.read(file).systemMessagePatterns.getFirst());
    }

    @Test
    void rejectsInvalidJson() throws IOException {
        Path file = dir.resolve("musicbot.json");
        Files.writeString(file, "{\"owners\": [\"ShortByte\",}", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> BotConfig.read(file));
    }
}
