package structures;

import service.Application;
import service.Settings;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Pad {

    private List<PadEntry> entries;
    private long lastEntryTime;

    Pad() {
        entries = new ArrayList<>();
    }

    public void addEntry(String[] words, String language) {
        PadEntry entry = new PadEntry(preprocess(words, language));
        entries.add(0, entry);
        lastEntryTime = entry.getTime();
    }

    private String[] preprocess(String[] words, String language) {
        List<String> processedWords = new ArrayList<>();
        switch (language) {
            case "en":
                for (String word : words) {
                    word = word.toLowerCase().replaceAll("[^a-zA-Z ]", "");
                    if (!Application.stopWordsEnglish.contains(word) && !Application.fillerWordsEnglish.contains(word)) {
                        processedWords.add(word);
                    }
                }
                break;
            case "fr":
                for (String word : words) {
                    word = word.toLowerCase().replaceAll("[^a-zA-Z ]", "");
                    if (!Application.stopWordsFrench.contains(word) && !Application.fillerWordsFrench.contains(word) && !Application.stopWordsFrench2.contains(word)) {
                        processedWords.add(word);
                    }
                }
                break;
            default:
                for (String word : words) {
                    word = word.toLowerCase().replaceAll("[^a-zA-Z ]", "");
                    processedWords.add(word);
                }
                break;
        }
        return processedWords.toArray(new String[0]);
    }

    List<String> getLatestEntries() {
        List<String> words = new ArrayList<>();
        Date window = new Date(lastEntryTime - Settings.TIMEWINDOW * 1000);
        for (PadEntry e : entries) {
            if (e.getTime() > window.getTime()) {
                words.addAll(e.getWords());
            } else {
                break;
            }
        }
        return words;
    }
}
