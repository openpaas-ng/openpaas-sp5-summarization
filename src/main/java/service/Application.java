package service;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.StringUtils;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static List<String> fillerWordsEnglish, fillerWordsFrench;
    public static List<String> stopWordsEnglish, stopWordsFrench, stopWordsFrench2;
    public static StanfordCoreNLP enPOSpipeline, frenchPOSpipeline;
    public static WordEmbeddingsService wordEmbeddings;

    public static void main(String[] args) throws IOException {
        Settings.init();
        System.out.println("loading static resources");
        loadResources();
        SpringApplication.run(Application.class, args);
    }

    private static void loadResources() {
        //Properties props = PropertiesUtils.asProperties("props", "StanfordCoreNLP-french.properties");
        Properties frenchProps = StringUtils.argsToProperties("-props", "StanfordCoreNLP-french.properties");
        frenchProps.setProperty("annotators", "tokenize, ssplit, pos");

        frenchPOSpipeline = new StanfordCoreNLP(frenchProps);


        Properties enProps = new Properties();
        enProps.setProperty("annotators", "tokenize, ssplit, pos");
        enPOSpipeline = new StanfordCoreNLP(enProps);

        fillerWordsEnglish = null;
        fillerWordsFrench = null;
        stopWordsFrench = null;
        stopWordsFrench2 = null;
        stopWordsEnglish = null;

        try {
            fillerWordsFrench = Files.readAllLines(Paths.get("local_directory/resources/filler_words_french.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fillerWordsEnglish = Files.readAllLines(Paths.get("local_directory/resources/filler_words.csv"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            stopWordsFrench = Files.readAllLines(Paths.get("local_directory/resources/custom_stopwords_full_french.txt"));
            stopWordsFrench2 = Files.readAllLines(Paths.get("local_directory/resources/stopwords_french.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            stopWordsEnglish = Files.readAllLines(Paths.get("local_directory/resources/custom_stopwords_full.csv"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        wordEmbeddings = new LocalEmbeddings();
        try {
            // Word embeddings for English are available at https://code.google.com/archive/p/word2vec/
            wordEmbeddings.addEmbeddings("en", WordVectorSerializer.loadGoogleModel(new File("local_directory/resources/word_embeddings/GoogleNews-vectors-negative300.bin"), true));
        } catch (IOException e) {
            System.out.println("Failed to load English embeddings");
        }
        try {
            // Word embeddings for French are available at https://github.com/facebookresearch/fastText/blob/master/pretrained-vectors.md
            wordEmbeddings.addEmbeddings("fr", WordVectorSerializer.loadTxtVectors(new File("local_directory/resources/word_embeddings/wiki.fr.vec")));
        } catch (IOException e) {
            System.out.println("Failed to load French embeddings");
        }
        System.out.println("Resources loading completed");
    }
}