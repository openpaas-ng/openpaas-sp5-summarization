package service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) {
//        List<String> fillerWordsFrench=null;
//        try {
//            fillerWordsFrench=Files.readAllLines(Paths.get("local_directory/resources/filler_words_french.txt"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        List<String> fillerWordsEnglish=null;
//        try {
//            fillerWordsEnglish=Files.readAllLines(Paths.get("local_directory/resources/filler_words.txt"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        List<String> stopWordsFrench=null;
//        try {
//            fillerWordsFrench=Files.readAllLines(Paths.get("local_directory/resources/custom_stopwords_full_french.txt"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        List<String> stopWordsEnglish=null;
//        try {
//            stopWordsEnglish=Files.readAllLines(Paths.get("local_directory/resources/custom_stopwords_full.txt"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        SpringApplication.run(Application.class, args);
    }
}