package structures;

import core.keywords.TextPreProcess;
import core.keywords.kcore.KCore;
import core.keywords.kcore.WeightedGraphKCoreDecomposer;
import core.keywords.wordgraph.GraphOfWords;
import org.deeplearning4j.clustering.cluster.Cluster;
import org.deeplearning4j.clustering.cluster.ClusterSet;
import org.deeplearning4j.clustering.cluster.Point;
import org.deeplearning4j.clustering.kmeans.KMeansClustering;
import org.jgrapht.WeightedGraph;
import service.Application;
import service.Settings;

import java.util.*;

/**
 * Created by midas on 11/23/2016.
 */
public class Transcript {
    private List<TranscriptEntry> entries;
    //TODO check concurrency status of this map
    private HashMap<String, Double> latestKeywords;
    private List<String> latestQueries;
    private Double lastEntryTime;
    private String language;
    private Integer minClusterSize=4;
    private Integer maxClusters=3;
    private Integer minQuerySize=3;

    public void setLatestKeywords(HashMap<String, Double> latestKeywords) {
        this.latestKeywords = latestKeywords;
    }

    public List<String> getLatestQueries() {
        return latestQueries;
    }

    public void setLatestQueries(List<String> latestQueries) {
        this.latestQueries = latestQueries;
    }

    public Double getLastEntryTime() {
        return lastEntryTime;
    }

    public void setLastEntryTime(Double lastEntryTime) {
        this.lastEntryTime = lastEntryTime;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getMinClusterSize() {
        return minClusterSize;
    }

    public void setMinClusterSize(Integer minClusterSize) {
        this.minClusterSize = minClusterSize;
    }

    public Integer getMaxClusters() {
        return maxClusters;
    }

    public void setMaxClusters(Integer maxClusters) {
        this.maxClusters = maxClusters;
    }

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
        WeightedGraphKCoreDecomposer decomposer = new WeightedGraphKCoreDecomposer(graph, 1, 0);

        Map<String, Double> map = decomposer.coreRankNumbers();

        Set<String> uniquePad = new HashSet<>(padWords);
        uniquePad.addAll(permutations(uniquePad));
        for (String word : uniquePad) {
            map.computeIfPresent(word, (k, v) -> v * 2);
        }
        map = KCore.sortByValue(map);
        Map<String,Double> rankedKeywords=map;
        Map<String, Double> topKeys = new LinkedHashMap<String,Double>();
        int cc = 0;
        for(String k:rankedKeywords.keySet()){
            topKeys.put(k,rankedKeywords.get(k));
            cc++;
            if(cc>Settings.NKEYWORDS)
                break;
        }
        Set vocab =  tpp.getVocabulary();
        String distanceFunction = "cosinesimilarity";
        List<Point> pointsLst =new ArrayList<>();

        vocab.iterator().forEachRemaining(e->{
            String key1 = (String) e;
            if(Application.enWordVectors.hasWord(key1)) {
                pointsLst.add(new Point(key1,(Application.enWordVectors.getWordVectorMatrix(key1))));
            }
        });

        ClusterSet cs =null;
        Integer nClusters=maxClusters;
        for(int jj=nClusters;jj>0;jj--){
            KMeansClustering kmc = KMeansClustering.setup(jj, 25, distanceFunction);
            cs = kmc.applyTo(pointsLst);
            boolean clusterOK = evaluateClusters(cs.getClusters());
            if(clusterOK)
                break;
        }
        System.out.println("num Clusters" + cs.getClusters().size() );
        for(Cluster c: cs.getClusters()) {
            Point clusterCenter = c.getCenter();
            System.out.println("------" + clusterCenter.getId());
            c.getPoints().forEach(p -> {
                        System.out.println(p.getId());
                    }
            );
        }
        latestQueries = extractQueries(cs.getClusters(),topKeys);
        latestKeywords.clear();
        topKeys = normalizeKeyScores(topKeys);
        topKeys.replaceAll((key, val) -> (uniquePad.contains(key) ? 1 : -1) * val);
        latestKeywords.putAll(topKeys);
        System.out.println(latestKeywords);

    }

    private List<String> extractQueries(List<Cluster> clusters,Map<String,Double> topKeys) {
        List<String> queries=new ArrayList<>();
        for(Cluster c: clusters) {
            String query = "";
            int counter = 0;
            for(Point p:c.getPoints()) {
                if (topKeys.keySet().contains(p.getId())) {
                    query += p.getId() + " ";
                    counter = counter + 1;
                }
            }
            if(counter>minQuerySize){
                queries.add(query);
            }
        }
        return queries;
    }

    private boolean evaluateClusters(List<Cluster> clusters) {
        for(Cluster c: clusters) {
            if(c.getPoints().size()<minClusterSize)
                return false;
        }
        return true;
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

    private Map<String, Double> normalizeKeyScores(Map<String, Double> topKeys) {
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
