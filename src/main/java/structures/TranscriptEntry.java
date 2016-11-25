package structures;

/**
 * Created by midas on 11/23/2016.
 */
public class TranscriptEntry {
    String id;
    Double from;
    Double until;
    String speaker;
    String text;

    public TranscriptEntry(String id, Double from, Double until, String speaker, String text) {
        this.id = id;
        this.from = from;
        this.until = until;
        this.speaker = speaker;
        this.text = text;
    }
    public TranscriptEntry(String[] e) {
        this.id = e[0];
        this.from = Double.valueOf(e[1]);
        this.until = Double.valueOf(e[2]);
        this.speaker = e[3];
        this.text = e[4];
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
        this.text = text;
    }

    @Override
    public String toString() {
        return this.id+"\t"+this.from+"\t"+this.until+"\t"+this.speaker+"\t"+this.text+"\n";
    }
}
