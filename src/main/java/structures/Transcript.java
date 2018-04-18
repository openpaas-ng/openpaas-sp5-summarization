package structures;

import core.keywords.TextPreProcess;
import core.keywords.kcore.KCore;
import core.keywords.kcore.WeightedGraphKCoreDecomposer;
import core.keywords.wordgraph.GraphOfWords;
import core.queryexpansion.Clustering;
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
    private Map<String, Double> latestKeywords;
    private List<String> latestQueries;
    private Double lastEntryTime;
    private String language;

    public Transcript() {
        this(new ArrayList<>());
    }

    public Transcript(List<TranscriptEntry> entries) {
        this.entries = entries;
        latestKeywords = new HashMap<>();
        lastEntryTime = 0.0;
        language = "none";
    }

    public void updateKeywords(List<String> padWords) {
        String text = getLatestEntriesText();
        if (text.length() == 0) {
            return;
        }
        TextPreProcess tpp = new TextPreProcess(text, this.language);
        if (this.language.equalsIgnoreCase("none")) {
            this.language = tpp.getLanguage();
        }
        String cleanText = tpp.getText();
        if (cleanText.length() == 0) {
            return;
        }
        GraphOfWords gow = new GraphOfWords(cleanText);
        WeightedGraph graph = gow.getGraph();
        WeightedGraphKCoreDecomposer decomposer = new WeightedGraphKCoreDecomposer(graph, 1, 0);

        Map<String, Double> map = decomposer.coreRankNumbers();

        Set<String> uniquePad = new HashSet<>(padWords);
        for (String word : uniquePad) {
            map.computeIfPresent(word, (k, v) -> v * 2);
        }
        map = KCore.sortByValue(map);
        Map<String, Double> rankedKeywords = map;
        Map<String, Double> topKeys = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : rankedKeywords.entrySet()) {
            if (entry.getKey().length() > 1) {
                topKeys.put(entry.getKey(), entry.getValue());
                if (topKeys.size() > Settings.NKEYWORDS)
                    break;
            }
        }

        latestQueries = Clustering.cluster(tpp.getVocabulary(), topKeys, language);

        latestKeywords.clear();
        topKeys = normalizeKeyScores(topKeys);
        topKeys.replaceAll((key, val) -> (uniquePad.contains(key) ? 1 : -1) * val);
        latestKeywords.putAll(topKeys);
        System.out.println("Generated keywords: " + latestKeywords + ", queries: " + latestQueries);
        if (latestQueries.isEmpty()) {
            StringBuilder qry = new StringBuilder();
            for (String word : latestKeywords.keySet()) {
                qry.append(" ").append(word);
            }
            latestQueries.add(qry.toString());
        }
    }

    public List<String> getTokens() {
        List<String> tokens = new ArrayList<>();
        for (TranscriptEntry e : entries) {
            Collections.addAll(tokens, e.getText().split(" "));
        }
        return tokens;
    }

    public String getLatestEntriesText() {
        StringBuilder out = new StringBuilder();
        if (!this.entries.isEmpty()) {
            lastEntryTime = this.entries.get(this.entries.size() - 1).getUntil();
            for (TranscriptEntry e : entries) {
                if (e.getUntil() > lastEntryTime - Settings.TIMEWINDOW)
                    out.append(e.getText()).append(" ");
            }
        }
        return out.toString();
    }

    public List<Keyword> getLatestKeywords() {
        return latestKeywords.keySet().stream().map(k -> new Keyword(k, latestKeywords.get(k).toString())).collect(Collectors.toList());
    }

    public void add(TranscriptEntry e) {
        this.entries.add(e);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (TranscriptEntry e : entries) {
            out.append(e.toString());
        }
        return out.toString();
    }

    public String getLanguage() {
        return this.language;
    }

    public List<String> getLatestQueries() {
        return latestQueries;
    }

    public void setLanguage(String language) {
        this.language = language;
    }


    private Map<String, Double> normalizeKeyScores(Map<String, Double> topKeys) {
        if (topKeys == null || topKeys.isEmpty()) {
            return null;
        }
        double min = Collections.min(topKeys.values());
        double max = Collections.max(topKeys.values());
        if (min == max) {
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
}
