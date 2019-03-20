package service;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static Map<String, Set<String>> languageStopwords;
    public static StanfordCoreNLP enPOSpipeline, frenchPOSpipeline;
    public static WordEmbeddingsService wordEmbeddings;

    public static void main(String[] args) throws IOException {
        Settings.init();
        System.out.println("Start loading static resources...");
        long loadStartTime = System.currentTimeMillis();
        loadResources();
        System.out.println("Resources loading completed in " + (System.currentTimeMillis() - loadStartTime) / 1000 + " seconds.");
        SpringApplication.run(Application.class, args);
    }

    /**
     * Initialize resources like Stanford's CoreNLP, Stopword list and Word Embeddings.
     * Code for CoreNLP should be updated to support more languages according to the list:
     * https://stanfordnlp.github.io/CoreNLP/human-languages.html
     */
    private static void loadResources() {
        Properties frenchProps = StringUtils.argsToProperties("-props", "StanfordCoreNLP-french.properties");
        frenchProps.setProperty("annotators", "tokenize, ssplit, pos");

        frenchPOSpipeline = new StanfordCoreNLP(frenchProps);


        Properties enProps = new Properties();
        enProps.setProperty("annotators", "tokenize, ssplit, pos");
        enPOSpipeline = new StanfordCoreNLP(enProps);

        wordEmbeddings = new LocalEmbeddings();
        languageStopwords = new HashMap<>();

        // Word embeddings for English are available at https://code.google.com/archive/p/word2vec/
        // Word embeddings for French are available at https://github.com/facebookresearch/fastText/blob/master/pretrained-vectors.md
        Settings.languageConfigurations.forEach((langCode, languageConf) -> {
            String stopwordsFiles = languageConf.get(0);
            Set<String> stopwords = new HashSet<>();
            for (String file : stopwordsFiles.split(", ")) {
                try {
                    stopwords.addAll(Files.readAllLines(Paths.get(file)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (stopwords.isEmpty()) {
                System.err.println("Stopwords file is missing for language '" + langCode + "'.");
                System.exit(-1);
            }
            languageStopwords.put(langCode, stopwords);
            String embeddingsFile = languageConf.get(1);
            if (embeddingsFile != null) {
                String[] split = embeddingsFile.split(", ");
                Word2Vec word2Vec = null;
                if (split[1].equals("true")) {
                    try {
                        word2Vec = WordVectorSerializer.readWord2VecModel(new File(split[0]));
                    } catch (ND4JIllegalStateException nise) {
                        System.out.println("Failed to load binary '" + langCode + "' embeddings");
                    }
                } else {
                    try {
                        word2Vec = WordVectorSerializer.readWord2VecModel(new File(split[0]));
                    } catch (ND4JIllegalStateException nise) {
                        System.out.println("Failed to load '" + langCode + "' embeddings");
                    }
                }
                wordEmbeddings.addEmbeddings(langCode, word2Vec);
            }
        });
    }
}