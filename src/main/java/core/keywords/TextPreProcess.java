package core.keywords;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import org.apache.tika.language.LanguageIdentifier;
import org.tartarus.snowball.SnowballProgram;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.FrenchStemmer;
import service.Application;

import java.util.*;

/**
 * Created by Midas on 5/22/2017.
 */
public class TextPreProcess {
    private String text;
    private String language;
    private final Set<String> supportedLanguages = new HashSet<>(Arrays.asList("en", "fr"));

    public TextPreProcess(String text, String language) {
        this.text = text;
        if(!supportedLanguages.contains(language)){
            String lang = new LanguageIdentifier(text).getLanguage().toLowerCase();
            System.out.println("Detected language: " + lang);
            if (supportedLanguages.contains(lang)) {
                this.language = lang;
                process();
            } else {
                this.language = "fr";
                process();
                this.language = language;
            }
        } else {
            this.language = language;
            process();
        }
    }

    private void process() {
        String cleanText = "";
        Annotation annotation = new Annotation(text);
        List<List<String>> stopwords;
        SnowballProgram stemmer;
        switch (language) {
            case "en":
                Application.enPOSpipeline.annotate(annotation);
                stopwords = new ArrayList<>(Arrays.asList(Application.stopWordsEnglish, Application.fillerWordsEnglish));
                stemmer = new EnglishStemmer();
                break;
            case "fr":
                Application.frenchPOSpipeline.annotate(annotation);
                stopwords = new ArrayList<>(Arrays.asList(Application.stopWordsFrench, Application.fillerWordsFrench, Application.stopWordsFrench2));
                stemmer = new FrenchStemmer();
                break;
            default:
                return;
        }

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences == null) {
            return;
        }
        for (CoreMap sentence : sentences) {
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                if (isStopword(word, stopwords)) {
                    continue;
                }
                stemmer.setCurrent(word);
//                stemmer.stem();
                word = stemmer.getCurrent();
                if (cleanText.endsWith("_" + word + " ")) {
                    continue;
                }
                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);

                if (!pos.startsWith("V") && pos.startsWith("N")) {
                    cleanText += word + " ";
                }
            }
        }
        this.text = cleanText.trim();
    }

    private boolean isStopword(String word, List<List<String>> stopwords) {
        if (word.equalsIgnoreCase("") || stopwords == null) {
            return false;
        }
        for (List<String> sublist : stopwords) {
            if (sublist.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getVocabulary(){
        return new HashSet<>(Arrays.asList(this.text.split("\\s+|_")));
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
