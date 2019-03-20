package structures;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


class PadEntry {
    private List<String> words;

    /**
     * Constructor of a pad entry. Each entry is consisted of a list of words
     *
     * @param words A preprocessed list of words that were transmitted from a CryptPad Pad
     */
    PadEntry(String[] words) {
        this.words = new ArrayList<>(Arrays.asList(words));
    }

    /**
     * Getter method for the stored list of words
     *
     * @return The stored list of words
     */
    List<String> getWords() {
        return words;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PadEntry padEntry = (PadEntry) o;
        return words.equals(padEntry.words);
    }

    @Override
    public int hashCode() {
        return Objects.hash(words);
    }
}
