package structures;

import core.keywords.TextPreProcess;
import core.keywords.kcore.KCore;
import core.keywords.kcore.WeightedGraphKCoreDecomposer;
import core.keywords.wordgraph.GraphOfWords;
import org.jgrapht.WeightedGraph;
import service.Settings;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by midas on 11/23/2016.
 */
public class Transcript {
    private List<TranscriptEntry> entries;
    //TODO check concurrency status of this map
    private HashMap<String, Double> latestKeywords;
    private Double lastEntryTime;
    private String language;

    public Transcript(List<TranscriptEntry> entries) {
        this.entries = entries;
        latestKeywords = new HashMap<>();
        lastEntryTime = 0.0;
        language = "none";
    }

    public Transcript() {
        this(new ArrayList<>());
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
        List<Keyword> l = new ArrayList<>();
        for (String k : latestKeywords.keySet()) {
            if (k.length() > 1)
                l.add(new Keyword(k, latestKeywords.get(k).toString()));
        }

        return l;
    }

    public void updateKeywords(List<String> padWords) {
        String text = getLatestEntriesText();
        if (text.length() == 0)
            return;
        TextPreProcess tpp = new TextPreProcess(text, this.language);
        if (this.language.equalsIgnoreCase("none")) {
            this.language = tpp.getLanguage();
        }
        String cleanText = tpp.getText();
        GraphOfWords gow = new GraphOfWords(cleanText);
        WeightedGraph graph = gow.getGraph();
        WeightedGraphKCoreDecomposer decomposer = new WeightedGraphKCoreDecomposer(graph, 10, 0);

        Map<String, Double> map = decomposer.coreRankNumbers();

        Set<String> uniquePad = new HashSet<>(padWords);
        uniquePad.addAll(permutations(uniquePad));
        for (String word : uniquePad) {
            map.computeIfPresent(word, (k, v) -> v * 2);
        }
        map = KCore.sortByValue(map);
        LinkedHashMap<String, Double> topKeys = new LinkedHashMap<>();

        Object[] it = map.keySet().toArray();

        for (int i = 0; i < Math.min(Settings.NKEYWORDS, it.length); i++) {
            String key1 = (String) it[i];
            String finalKey = key1;
            Double finalScore = map.get(key1);
            //for(int j=i+1;j<Settings.NKEYWORDS;j++){
            //    String key2 = (String) it[j];
            //    if(graph.containsEdge(key1,key2)){
            //        finalKey=key1+" "+key2;
            //        finalScore+=map.get(key2);
            //    }
            // }

            topKeys.put(finalKey, finalScore);
        }

        latestKeywords.clear();
        topKeys = normalizeKeyScores(topKeys);
        topKeys.replaceAll((key, val) -> (uniquePad.contains(key) ? 1 : -1) * val);
        latestKeywords.putAll(topKeys);
        System.out.println(latestKeywords);

    }

    private Set<String> permutations(Set<String> words){
        Set<String> permutations = new HashSet<>();
        for(String w1: words){
            for(String w2: words){
                permutations.add(w1 + "_" + w2);
            }
        }
        return permutations;
    }

    public List<String> getTokens() {
        List<String> tokens = new ArrayList<>();
        for (TranscriptEntry e : entries) {
            Collections.addAll(tokens, e.getText().split(" "));
        }
        return tokens;
    }

    private LinkedHashMap<String, Double> normalizeKeyScores(LinkedHashMap<String, Double> topKeys) {
        if (topKeys == null){
            return topKeys;
        }
        double min = Collections.min(topKeys.values());
        double max = Collections.max(topKeys.values());
        if (min == max){
            return topKeys;
        }
        topKeys.replaceAll((k, v) -> scale(v, min, max, 20.0, 35.0));
        return topKeys;
    }

    private static double scale(final double valueIn, final double baseMin, final double baseMax, final double limitMin, final double limitMax) {
        if (limitMin == limitMax)
            return valueIn;
        return ((limitMax - limitMin) * (valueIn - baseMin) / (baseMax - baseMin)) + limitMin;
    }

    public String getLatestEntriesText() {
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

    public String getLanguage() {
        return this.language;
    }
}
