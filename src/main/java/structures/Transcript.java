package structures;

import core.keywords.kcore.KCore;
import core.keywords.kcore.WeightedGraphKCoreDecomposer;
import core.keywords.wordgraph.GraphOfWords;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.WeightedGraph;
import org.tartarus.snowball.ext.FrenchStemmer;
import service.Application;
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

    public List<String > getTokens() {
        List<String> tokens=new ArrayList<>();
        for (TranscriptEntry e : entries) {
            Collections.addAll(tokens, e.getText().split(" "));
        }
        return tokens;
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
        String cleanText = "";
        System.out.println("-Annotation-");
        Annotation annotation = new Annotation(text);
        Application.frenchPOSpipeline.annotate(annotation);
        System.out.println("-Done-");

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        System.out.println("------------------------------");
        for (CoreMap sentence : sentences) {
            for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                // this is the POS tag of the token
                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                if(!pos.equals("V"))
                    cleanText+=word+" ";
                //System.out.println(word + "/" + pos);
            }
        }

        String[] tokens = cleanText.split(" ");
        cleanText = "";
        for(String t:tokens){
            if(!Application.stopWordsFrench.contains(t) && !Application.fillerWordsFrench.contains(t) && !Application.stopWordsFrench2.contains(t)) {
                FrenchStemmer stemmer = new FrenchStemmer();
                stemmer.setCurrent(t);
                if (stemmer.stem()) {
                    cleanText += t + " ";
                }
            }

        }


        GraphOfWords gow = new GraphOfWords(cleanText);
        WeightedGraph graph = gow.getGraph();
        WeightedGraphKCoreDecomposer decomposer = new WeightedGraphKCoreDecomposer(graph, 10, 0);

        Map<String, Double> map = decomposer.coreRankNumbers();
        map = KCore.sortByValue(map);
        LinkedHashMap<String, Double> topKeys = new LinkedHashMap<>();

        Object[] it =  map.keySet().toArray();

        for(int i=0;i<Settings.NKEYWORDS && i<it.length;i++){
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
        for (Map.Entry<String, Double> e : topKeys.entrySet()) {
            System.out.println(e.getKey()+" "+e.getValue());
            latestKeywords.put(e.getKey(), e.getValue());
            cc++;
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
