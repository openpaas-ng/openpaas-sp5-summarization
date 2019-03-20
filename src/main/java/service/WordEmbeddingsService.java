package service;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.nd4j.linalg.api.ndarray.INDArray;
import java.util.Collection;

public abstract class WordEmbeddingsService {
    public abstract INDArray getVector(String word, String language);
    public abstract INDArray getMean(Collection<String> collection, String language);
    public abstract void addEmbeddings(String language, WordVectors vectors);
    public abstract boolean isNotDefined(String language);
}