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
        if (language.equalsIgnoreCase("none")) {
            String lang = new LanguageIdentifier(text).getLanguage();
            System.out.println("Detected language: " + lang);
            if (supportedLanguages.contains(lang)) {
                language = lang;
            }
        }
        this.text = text;
        this.language = language;
        if(!language.equalsIgnoreCase("none")) {
            process();
        }
    }

    private void process() {
        String cleanText = "";
        Annotation annotation = new Annotation(text); // Remove punctuation? text.replaceAll("[^A-Za-z0-9]", " ")
        List<List<String>> stopwords = null;
        SnowballProgram stemmer = null;
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
            int index = -1;
            int max_index = sentence.get(CoreAnnotations.TokensAnnotation.class).size();
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                index++;
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

                //if ((index + 1 < max_index) && (pos.startsWith("N") || pos.startsWith("JJ"))) {
                //   CoreLabel next_token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(index + 1);
                    //String next_pos = next_token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                    //if (!(pos.startsWith("JJ") && next_pos.startsWith("JJ")) && (next_pos.startsWith("N") || next_pos.startsWith("JJ"))) {
                    //    String next_word = next_token.get(CoreAnnotations.TextAnnotation.class);
                    //   if (isStopword(next_word, stopwords)) {
                    //        continue;
                    //    }
                    //    stemmer.setCurrent(next_word);
//                  //      stemmer.stem();
                    //    next_word = stemmer.getCurrent();
                    //    String prefix = pos.startsWith("JJ") ? cleanText + word : StringUtils.chop(cleanText);
                    //    cleanText = prefix + "_" + next_word + " ";
                    //}
                //}
            }
        }
        this.text = cleanText;
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

    public HashSet getVocabulary(){
        HashSet words = new HashSet(Arrays.asList(this.text.split("\\s+|_")));
        return words;
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
