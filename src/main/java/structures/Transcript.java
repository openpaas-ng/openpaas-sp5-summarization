package structures;

import service.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by midas on 11/23/2016.
 */
public class Transcript {
    List<TranscriptEntry> entries;
    //TODO check concurrency status of this map
    HashMap<String,Double> latestKeywords;
    public Transcript(List<TranscriptEntry> entries) {
        this.entries = entries;
        latestKeywords=new HashMap<>();
    }
    public Transcript() {
        this.entries = new ArrayList<TranscriptEntry>();
        latestKeywords=new HashMap<>();
    }

    public void updateLatestKeywords(HashMap k){
        latestKeywords.clear();
        latestKeywords.putAll(k);
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

    @Override
    public String toString() {
        String out="";
        for(TranscriptEntry e:entries){
            out+=e.toString();
        }
        return out;
    }

    public HashMap<String, Double> getLatestKeywords() {
        return latestKeywords;
    }

    public String getLatestEntriesText() {
        String out = "";
        if(!this.entries.isEmpty()) {
            Double lastEntryTime = this.entries.get(this.entries.size() - 1).getUntil();
            for (TranscriptEntry e : entries) {
                if (e.getUntil()>lastEntryTime - Settings.TIMEWINDOW)
                    out += e.getText()+" ";
            }
        }
        return out;
    }
}
