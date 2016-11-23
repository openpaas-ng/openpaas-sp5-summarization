package structures;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by midas on 11/23/2016.
 */
public class Transcript {
    List<TranscriptEntry> entries;

    public Transcript(List<TranscriptEntry> entries) {
        this.entries = entries;
    }
    public Transcript() {
        this.entries = new ArrayList<TranscriptEntry>();
    }
    public void add(TranscriptEntry e){
        this.entries.add(e);
    }
    public List<TranscriptEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<TranscriptEntry> entries) {
        this.entries = entries;
    }
}
