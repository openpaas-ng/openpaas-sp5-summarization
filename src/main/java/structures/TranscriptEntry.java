package structures;

import java.util.Objects;

/**
 * Created by midas on 11/23/2016.
 */
public class TranscriptEntry {
    private Double from;
    private Double until;
    private String speaker;
    private String text;

    public TranscriptEntry(Double from, Double until, String speaker, String text) {
        this.from = from;
        this.until = until;
        this.speaker = speaker;
        this.text = cleanText(text);
    }

    public TranscriptEntry(String[] entry) {
        this.from = Double.valueOf(entry[0]);
        this.until = Double.valueOf(entry[1]);
        this.speaker = entry[2];
        this.text = cleanText(entry[3]);
    }

    private String cleanText(String text) {
        text = text.replaceAll("'", " ");
        text = text.replaceAll("<noise>", "");
        text = text.replaceAll("<spoken-noise>", "");
        text = text.replaceAll("<laugh>", "");
        text = text.replaceAll("<UNK>", "");
        text = text.replaceAll("<!sil>", "");
        text = text.replaceAll("-", "");
        return text.toLowerCase();
    }

    public Double getFrom() {
        return from;
    }

    public void setFrom(Double from) {
        this.from = from;
    }

    public Double getUntil() {
        return until;
    }

    public void setUntil(Double until) {
        this.until = until;
    }

    public String getSpeaker() {
        return speaker;
    }

    public void setSpeaker(String speaker) {
        this.speaker = speaker;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = cleanText(text);
    }

    @Override
    public String toString() {
        return this.from + "\t" + this.until + "\t" + this.speaker + "\t" + this.text + "\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TranscriptEntry that = (TranscriptEntry) o;
        return Objects.equals(from, that.from) &&
                Objects.equals(until, that.until) &&
                Objects.equals(speaker, that.speaker) &&
                Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, until, speaker, text);
    }
}
