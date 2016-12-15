package structures;

import core.keywords.kcore.KCore;
import core.keywords.kcore.WeightedGraphKCoreDecomposer;
import core.keywords.wordgraph.GraphOfWords;
import service.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public void updateKeywords(){
        String text = getLatestEntriesText();
        GraphOfWords gow = new GraphOfWords(text);
        WeightedGraphKCoreDecomposer decomposer = new WeightedGraphKCoreDecomposer(gow.getGraph(), 10,0);
        Map<String, Double> map = decomposer.coreNumbers();
        map= KCore.sortByValue(map);
        latestKeywords.clear();
        for(int i=0;i<Settings.NKEYWORDS;i++){
            Map.Entry<String, Double> mapEntry = map.entrySet().iterator().next();
            String key = mapEntry.getKey();
            Double value = mapEntry.getValue();
            latestKeywords.put(key,value);
        }
    }

    private String getLatestEntriesText() {
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
