package structures;

import java.util.List;

/**
 * Created by midas on 11/23/2016.
 */
public class TranscriptEntry {
    String id;
    Double from;
    Double until;
    String speaker;
    String role;
    String annot1;
    Integer annot2;
    Integer annot3;
    String text;

    public TranscriptEntry(String id, Double from, Double until, String speaker, String role, String annot1, Integer annot2, Integer annot3, String text) {
        this.id = id;
        this.from = from;
        this.until = until;
        this.speaker = speaker;
        this.role = role;
        this.annot1 = annot1;
        this.annot2 = annot2;
        this.annot3 = annot3;
        this.text = text;
    }
    public TranscriptEntry(String[] e) {
        this.id = e[0];
        this.from = Double.valueOf(e[1]);
        this.until = Double.valueOf(e[2]);
        this.speaker = e[3];
        this.role = e[4];
        this.annot1 = e[5];
        this.annot2 = Integer.valueOf(e[6]);
        this.annot3 = Integer.valueOf(e[7]);
        this.text = e[8];
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAnnot1() {
        return annot1;
    }

    public void setAnnot1(String annot1) {
        this.annot1 = annot1;
    }

    public Integer getAnnot2() {
        return annot2;
    }

    public void setAnnot2(Integer annot2) {
        this.annot2 = annot2;
    }

    public Integer getAnnot3() {
        return annot3;
    }

    public void setAnnot3(Integer annot3) {
        this.annot3 = annot3;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return this.id+"\t"+this.from+"\t"+this.until+"\t"+this.speaker+"\t"+this.role+"\t"+this.annot1+"\t"+this.annot2+"\t"+this.annot3+"\t"+this.text+"\n";
    }
}
