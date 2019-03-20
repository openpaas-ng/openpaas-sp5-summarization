package structures;

import com.google.common.cache.*;
import service.Application;
import service.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

class Pad {

    /**
     * List of stored Pad Entries. Each entry is automatically expiring after a
     * predetermined time period (defined on the configuration properties).
     */
    private LoadingCache<PadEntry, String> padEntries;

    /**
     * Default constructor representing a CryptPad pad. Is used to store incoming Pad Entries.
     */
    Pad() {
        padEntries = CacheBuilder.newBuilder()
                .maximumSize(1000)  // Maximum number of entries per period
                .expireAfterWrite(Settings.PADTIMEWINDOW, TimeUnit.SECONDS) // The time after which an Entry expires
//                .removalListener((RemovalListener<PadEntry, String>) notification -> {})
                .build(new CacheLoader<PadEntry, String>() {
                    public String load(PadEntry words) {
                        return "";
                    }
                }); // Default value for each Entry is ignored
    }

    /**
     * Method used to store incoming words into the Pad.
     * The words are first preprocessed to remove stopwords and then
     * a Pad Entry is generated and stored into a cache collection.
     *
     * @param words    A list of words to be added into the Pad.
     * @param language The language of the text.
     */
    void addEntry(String[] words, String language) {
        PadEntry entry = new PadEntry(removeStopwords(words, language));
        try {
            padEntries.get(entry);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes the stopwords from the given list of words
     *
     * @param words    A list of words.
     * @param language The language of the text.
     * @return The list of words after removing stopwords.
     */
    private String[] removeStopwords(String[] words, String language) {
        List<String> processedWords = new ArrayList<>();
        Set<String> stopwords = Application.languageStopwords.get(language);

        for (String word : words) {
            if (!isStopword(word.toLowerCase().replaceAll("[^a-zA-Z ]", ""), stopwords)) {
                processedWords.add(word);
            }
        }
        return processedWords.toArray(new String[0]);
    }

    /**
     * Retrieve the Pad Entries that are stored into the cache.
     * Entries are expiring automatically after a set duration (defined on the configuration properties).
     *
     * @return Stored Pad Entries
     */
    List<String> getLatestEntries() {
        List<String> words = new ArrayList<>();
        for (PadEntry e : padEntries.asMap().keySet()) {
            words.addAll(e.getWords());
        }
        return words;
    }

    /**
     * Uses a provided list of stopwords and returns true if the given word appears inside one of them.
     * @param word
     * @param stopwords
     * @return
     */
    private boolean isStopword(String word, Set<String> stopwords) {
        if (word.equalsIgnoreCase("") || stopwords == null) {
            return false;
        }
        return stopwords.contains(word);
    }
}
