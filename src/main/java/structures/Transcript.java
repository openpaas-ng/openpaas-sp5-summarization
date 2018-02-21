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
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import service.Application;
import service.Settings;

import java.util.*;
import java.util.stream.Collectors;

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
        final Map<String,Double> rankedKeywords=map;
        Map<String, Double> topKeys = new LinkedHashMap<>();

        Object[] it =  rankedKeywords.keySet().toArray();
        int size = Math.min(Settings.NKEYWORDS, it.length);
        String distanceFunction = "cosinesimilarity";
        KMeansClustering kmc = KMeansClustering.setup(2, 15, distanceFunction);
        List<Point> pointsLst =new ArrayList<>();
        INDArray mean = Application.enWordVectors.getWordVectorsMean(rankedKeywords.keySet());

        for(int i=0;i<size;i++){
            String key1 = (String) it[i];
            if(Application.enWordVectors.hasWord(key1)) {
                pointsLst.add(new Point(key1,(Application.enWordVectors.getWordVectorMatrix(key1))));
            }
        }
        INDArray X = Application.enWordVectors.getWordVectors(rankedKeywords.keySet());
        X.mul(X.transpose());
        ClusterSet cs = kmc.applyTo(pointsLst);
        List<Cluster> clsterLst = cs.getClusters();
        System.out.println("\nCluster Centers:");
        double maxClusterScore = 0;

        for(Cluster c: clsterLst) {
            double score=0;
            Point clusterCenter = c.getCenter();
            System.out.println("------"+clusterCenter.getId());
            double similarirty = Transforms.cosineSim(clusterCenter.getArray(), mean);
            score=c.getPoints().stream().mapToDouble(p->rankedKeywords.get(p.getId())).sum()*similarirty;
            System.out.println(score);
            c.getPoints().forEach(p->{
                System.out.println(p.getId()+" "+rankedKeywords.get(p.getId()));
                    }
            );
            if(score>maxClusterScore){
                maxClusterScore=score;
                topKeys.clear();
                topKeys=c.getPoints().stream().collect(Collectors.toMap(x->x.getId(),x->rankedKeywords.get(x.getId())));
            }

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
    private Map<String,Double> normalizeKeyScores(Map<String, Double> topKeys) {
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
