package de.musicbot.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.musicbot.MusicBotClient;
import de.musicbot.audio.Track;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Downloaded songs stored in a folder, with an index file {@code library.json}.
 * Entries are addressed by their 1-based position. Thread-safe.
 */
public class Library {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * @param file file name inside the library folder
     */
    public record Entry(String title, String source, String file, long durationSec, String addedBy, long addedAt) {
        public String formattedDuration() {
            return durationSec < 0 ? "?" : Track.formatTime(durationSec);
        }
    }

    private final Path dir;
    private final Path index;
    private final List<Entry> entries = new ArrayList<>();

    public Library(Path dir) {
        this.dir = dir.toAbsolutePath().normalize();
        this.index = this.dir.resolve("library.json");
        load();
    }

    public Path dir() {
        return dir;
    }

    private synchronized void load() {
        entries.clear();
        if (!Files.exists(index)) return;
        try (Reader reader = Files.newBufferedReader(index, StandardCharsets.UTF_8)) {
            List<Entry> loaded = GSON.fromJson(reader, new TypeToken<List<Entry>>() {
            }.getType());
            if (loaded == null) return;
            for (Entry e : loaded) {
                // drop entries whose file was deleted by hand
                if (e != null && e.file() != null && Files.isRegularFile(dir.resolve(e.file()))) entries.add(e);
            }
        } catch (Exception e) {
            MusicBotClient.LOGGER.error("Failed to read {}", index, e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve("library.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(entries, writer);
            }
            Files.move(tmp, index, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            MusicBotClient.LOGGER.error("Failed to save {}", index, e);
        }
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int size() {
        return entries.size();
    }

    /** @return 1-based position of the entry for this source URL, or -1 */
    public synchronized int indexOfSource(String source) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).source().equals(source)) return i + 1;
        }
        return -1;
    }

    /** @return 1-based position of the new (or already existing) entry */
    public synchronized int add(Entry entry) {
        int existing = indexOfSource(entry.source());
        if (existing > 0) return existing;
        entries.add(entry);
        save();
        return entries.size();
    }

    /** @param position 1-based */
    public synchronized Entry get(int position) {
        return position < 1 || position > entries.size() ? null : entries.get(position - 1);
    }

    /** Removes the entry and deletes its file. */
    public synchronized Entry remove(int position) {
        if (position < 1 || position > entries.size()) return null;
        Entry removed = entries.remove(position - 1);
        save();
        try {
            Files.deleteIfExists(dir.resolve(removed.file()));
        } catch (IOException e) {
            MusicBotClient.LOGGER.warn("Could not delete {}", removed.file(), e);
        }
        return removed;
    }

    /** @return 1-based positions of all entries whose title contains all words of {@code query} */
    public synchronized List<Integer> search(String query) {
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String title = entries.get(i).title().toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) {
                if (!title.contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all) result.add(i + 1);
        }
        return result;
    }

    public Track toTrack(Entry entry, String requestedBy) {
        return new Track(entry.title(), dir.resolve(entry.file()).toString(), entry.durationSec(), true, requestedBy);
    }
}
