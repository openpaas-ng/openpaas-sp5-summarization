package structures;

import core.keywords.TextPreProcess;
import core.keywords.kcore.KCore;
import core.keywords.kcore.WeightedGraphKCoreDecomposer;
import core.keywords.wordgraph.GraphOfWords;
import org.jgrapht.WeightedGraph;
import service.Settings;

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
        if(text.length()==0)
            return;
        String cleanText = new TextPreProcess(text).getText();
        GraphOfWords gow = new GraphOfWords(cleanText);
        WeightedGraph graph = gow.getGraph();
        WeightedGraphKCoreDecomposer decomposer = new WeightedGraphKCoreDecomposer(graph, 10, 0);

        Map<String, Double> map = decomposer.coreRankNumbers();
        map = KCore.sortByValue(map);
        LinkedHashMap<String, Double> topKeys = new LinkedHashMap<>();

        Object[] it =  map.keySet().toArray();

        for(int i=0;i<Math.min(Settings.NKEYWORDS,it.length);i++){
            String key1 = (String) it[i];
            String finalKey=key1;
            Double finalScore=map.get(key1);
            //for(int j=i+1;j<Settings.NKEYWORDS;j++){
            //    String key2 = (String) it[j];
            //    if(graph.containsEdge(key1,key2)){
            //        finalKey=key1+" "+key2;
            //        finalScore+=map.get(key2);
            //    }
            // }

            topKeys.put(finalKey,finalScore);
        }

        latestKeywords.clear();
        int cc = 0;
        topKeys=normalizeKeyScores(topKeys);
        for (Map.Entry<String, Double> e : topKeys.entrySet()) {
            System.out.println(e.getKey()+" "+e.getValue());
            latestKeywords.put(e.getKey(), e.getValue());
            cc++;
        }

    }
    public List<String > getTokens() {
        List<String> tokens=new ArrayList<>();
        for (TranscriptEntry e : entries) {
            Collections.addAll(tokens, e.getText().split(" "));
        }
        return tokens;
    }
    private LinkedHashMap<String,Double> normalizeKeyScores(LinkedHashMap<String, Double> topKeys) {
        Double min = Collections.min(topKeys.values());
        Double max = Collections.max(topKeys.values());
        topKeys.replaceAll((k, v) -> scale(v,min,max,20.0,35.0));
        return topKeys;
    }
    private static double scale(final double valueIn, final double baseMin, final double baseMax, final double limitMin, final double limitMax) {
        if(limitMin==limitMax)
            return valueIn;
        return ((limitMax - limitMin) * (valueIn - baseMin) / (baseMax - baseMin)) + limitMin;
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
