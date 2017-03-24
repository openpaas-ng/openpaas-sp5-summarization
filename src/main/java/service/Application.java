package service;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static List<String> fillerWordsFrench;
    public static List<String> fillerWordsEnglish;
    public static List<String> stopWordsFrench;
    public static List<String> stopWordsFrench2;
    public static List<String> stopWordsEnglish;
    public static StanfordCoreNLP frenchPOSpipeline;
    public static void main(String[] args) throws IOException {
        Settings.init();
        loadResources();
        SpringApplication.run(Application.class, args);
    }

    public static void loadResources() {
        //Properties props = PropertiesUtils.asProperties("props", "StanfordCoreNLP-french.properties");
        Properties props = StringUtils.argsToProperties(
                new String[]{"-props", "StanfordCoreNLP-french.properties"});
        props.setProperty("annotators","tokenize, ssplit, pos");

        frenchPOSpipeline = new StanfordCoreNLP(props);
        fillerWordsFrench = null;
        try {
            fillerWordsFrench = Files.readAllLines(Paths.get("local_directory/resources/filler_words_french.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        fillerWordsEnglish = null;
        try {
            fillerWordsEnglish = Files.readAllLines(Paths.get("local_directory/resources/filler_words.csv"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopWordsFrench = null;
        try {
            stopWordsFrench = Files.readAllLines(Paths.get("local_directory/resources/custom_stopwords_full_french.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopWordsEnglish = null;
        try {
            stopWordsEnglish = Files.readAllLines(Paths.get("local_directory/resources/custom_stopwords_full.csv"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            stopWordsFrench2 = Files.readAllLines(Paths.get("local_directory/resources/stopwords_french.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}