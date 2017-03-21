package structures;

import core.keywords.kcore.KCore;
import core.keywords.kcore.WeightedGraphKCoreDecomposer;
import core.keywords.wordgraph.GraphOfWords;
import org.apache.commons.lang3.StringUtils;
import service.Settings;

import java.text.Normalizer;
import java.util.*;

/**
 * Created by midas on 11/23/2016.
 */
public class Transcript {
    List<TranscriptEntry> entries;
    //TODO check concurrency status of this map
    HashMap<String, Double> latestKeywords;
    Double lastEntryTime;

    public Transcript(List<TranscriptEntry> entries) {
        this.entries = entries;
        latestKeywords = new HashMap<>();
        lastEntryTime = 0.0;
    }

    public Transcript() {
        this.entries = new ArrayList<TranscriptEntry>();
        latestKeywords = new HashMap<>();
        lastEntryTime = 0.0;
    }


    public void add(TranscriptEntry e) {
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
        String out = "";
        for (TranscriptEntry e : entries) {
            out += e.toString();
        }
        return out;
    }

    public List<Keyword> getLatestKeywords() {
        List<Keyword> l=new ArrayList<Keyword>();
        for(String k:latestKeywords.keySet()){
            if(k.length()>1)
                l.add(new Keyword(k,latestKeywords.get(k).toString()));
        }

        return l;
    }

    public void updateKeywords() {
        String text = getLatestEntriesText();
        GraphOfWords gow = new GraphOfWords(text);
        WeightedGraphKCoreDecomposer decomposer = new WeightedGraphKCoreDecomposer(gow.getGraph(), 10, 0);

        Map<String, Double> map = decomposer.coreRankNumbers();
        map = KCore.sortByValue(map);
        latestKeywords.clear();
        int cc = 0;
        for (Map.Entry<String, Double> e : map.entrySet()) {
            System.out.println(e.getKey()+" "+e.getValue());
            latestKeywords.put(StringUtils.stripAccents(e.getKey()), e.getValue());
            cc++;
            if (cc == Settings.NKEYWORDS)
                break;
        }

    }

    private String getLatestEntriesText() {
        String out = "";
        if (!this.entries.isEmpty()) {
            lastEntryTime = this.entries.get(this.entries.size() - 1).getUntil();
            for (TranscriptEntry e : entries) {
                if (e.getUntil() > lastEntryTime - Settings.TIMEWINDOW)
                    out += e.getText() + " ";
            }
        }
        return out;
    }
}
