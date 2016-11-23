package structures;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by midas on 11/23/2016.
 */
public class Transcript {
    List<TranscriptEntry> transcript;

    public Transcript(List<TranscriptEntry> entries) {
        this.transcript = entries;
    }
    public Transcript() {
        this.transcript = new ArrayList<TranscriptEntry>();
    }
    public void add(TranscriptEntry e){
        this.transcript.add(e);
    }
    public List<TranscriptEntry> getEntries() {
        return transcript;
    }

    public void setEntries(List<TranscriptEntry> entries) {
        this.transcript = entries;
    }
}
