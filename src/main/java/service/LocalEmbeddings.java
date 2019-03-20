package service;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.*;

public class LocalEmbeddings extends WordEmbeddingsService{

    private Map<String, WordVectors> wordVectors;

    LocalEmbeddings(){
        this.wordVectors = new HashMap<>();
    }

    public void addEmbeddings(String language, WordVectors vectors){
        wordVectors.put(language, vectors);
    }

    @Override
    public INDArray getVector(String word, String language) {
        if(wordVectors == null){
            return null;
        }
        if(!wordVectors.containsKey(language)){
            return null;
        }
        WordVectors wv = wordVectors.get(language);
        if(wv.hasWord(word)){
            return wv.getWordVectorMatrix(word);
        }
        if(word.contains("_")){
            return getMean(Arrays.asList(word.split("_")), language);
        }
        return null;
    }

    @Override
    public INDArray getMean(Collection<String> collection, String language) {
        return wordVectors.get(language).getWordVectorsMean(collection);
    }

    @Override
    public boolean isNotDefined(String language){
        return wordVectors == null || wordVectors.get(language) == null;
    }
}