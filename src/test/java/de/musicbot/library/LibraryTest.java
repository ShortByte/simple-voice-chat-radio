package de.musicbot.library;

import de.musicbot.audio.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryTest {

    @TempDir
    Path dir;

    private Library.Entry entry(String title, String source, String file) throws Exception {
        Files.writeString(dir.resolve(file), "audio");
        return new Library.Entry(title, source, file, 200, "ShortByte", 0);
    }

    @Test
    void addSearchRemoveAndReload() throws Exception {
        Library library = new Library(dir);
        assertEquals(1, library.add(entry("Daft Punk - One More Time", "https://yt/1", "a.opus")));
        assertEquals(2, library.add(entry("Daft Punk - Around the World", "https://yt/2", "b.opus")));
        assertEquals(3, library.add(entry("Rick Astley - Never Gonna Give You Up", "https://yt/3", "c.opus")));

        // same source is not stored twice
        assertEquals(1, library.add(entry("Duplicate", "https://yt/1", "d.opus")));
        assertEquals(3, library.size());

        assertEquals(List.of(1, 2), library.search("daft punk"));
        assertEquals(List.of(2), library.search("punk world"));
        assertEquals(2, library.indexOfSource("https://yt/2"));

        Track track = library.toTrack(library.get(3), "Member");
        assertTrue(track.localFile());
        assertEquals(dir.resolve("c.opus").toAbsolutePath().normalize().toString(), track.source());

        assertEquals("Daft Punk - Around the World", library.remove(2).title());
        assertFalse(Files.exists(dir.resolve("b.opus")));

        // index survives a restart, entries with deleted files are dropped
        Files.delete(dir.resolve("c.opus"));
        Library reloaded = new Library(dir);
        assertEquals(1, reloaded.size());
        assertEquals("Daft Punk - One More Time", reloaded.get(1).title());
    }
}
