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
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Midas on 5/22/2017.
 */
public class TextPreProcess {
    private String text;
    private String language;
    private final Map<String, String> patterns = new HashMap<String, String>() {{
        put("en", "JJ(R|S)*_[0-9]+ NNS*_[0-9]+ NNS*_[0-9]+|( JJ(R|S)*_[0-9]+){1,} NNS*_[0-9]+|NNS*_[0-9]+ IN_[0-9]+ NNS*_[0-9]+|NNS*_[0-9]+|NNPS*_[0-9]+( NNPS*_[0-9]+)*");
        put("fr", "NOUN_[0-9]+ ADJ_[0-9]+ NOUN_[0-9]+|NOUN_[0-9]+( ADJ_[0-9]+){1,}|ADJ_[0-9]+ NOUN_[0-9]+|(NOUN_[0-9]+|PROPN_[0-9]+){1}( (NOUN_[0-9]+|PROPN_[0-9]+)){1,}|NOUN_[0-9]+ ADP_[0-9]+ NOUN_[0-9]+|NOUN_[0-9]+|PROPN_[0-9]+");
    }};

    public TextPreProcess(String text, String language) {
        this.text = text;
        if (!Application.languageStopwords.containsKey(language)) { // Verify that the defined language is supported by the application
            String detectedLanguage = new LanguageIdentifier(text).getLanguage().toLowerCase();
            System.out.println("Detected language: " + detectedLanguage);
            if (Application.languageStopwords.containsKey(detectedLanguage)) { // Verify that the detected language is supported by the application
                this.language = detectedLanguage;
                process();
            } else {
                this.language = "fr";
                process();
            }
        } else {
            this.language = language;
            process();
        }
    }

    private void process() {
        Annotation annotation = new Annotation(text); // Remove punctuation? text.replaceAll("[^A-Za-z0-9]", " ")
        Set<String> stopwords = Application.languageStopwords.get(language);
        SnowballProgram stemmer;
        switch (language) {
            case "en":
                Application.enPOSpipeline.annotate(annotation);
                stemmer = new EnglishStemmer();
                break;
            case "fr":
                Application.frenchPOSpipeline.annotate(annotation);
                stemmer = new FrenchStemmer();
                break;
            default:
                return;
        }

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences == null) {
            return;
        }
        Map<CoreMap, List<List<Integer>>> termsPositions = getTermsIndex(annotation);
        StringBuilder cleanText = new StringBuilder();
        for (CoreMap sentence : sentences) {
            List<List<Integer>> termsOfSentence = termsPositions.get(sentence);
            List<Integer> termsIndexes = new ArrayList<>();
            List<Integer> termsEndIndexes = new ArrayList<>();
            for (List<Integer> indexes : termsOfSentence) {
                termsIndexes.addAll(indexes);
                termsEndIndexes.add(indexes.get(indexes.size() - 1));
            }
            for (int index : termsIndexes) {
                String word = sentence.get(CoreAnnotations.TokensAnnotation.class).get(index).get(CoreAnnotations.TextAnnotation.class);
                if (isStopword(word, stopwords) && !termsIndexes.contains(index)) {
                    continue;
                }
                stemmer.setCurrent(word);
//                stemmer.stem(); // Stemming disabled
                word = stemmer.getCurrent();
                cleanText.append(word);

                if (!termsEndIndexes.contains(index) && termsIndexes.contains(index + 1)) {
                    cleanText.append("_");
                } else {
                    cleanText.append(" ");
                }
            }
        }
        this.text = cleanText.toString().replaceAll("\\s+", " ").replaceAll("^_|_$", "").trim();
    }

    private Map<CoreMap, List<List<Integer>>> getTermsIndex(Annotation annotation) {
        Map<CoreMap, String> sentencePosTags = new HashMap<>();
        Map<CoreMap, List<List<Integer>>> sentenceTermsIndex = new HashMap<>();
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            StringBuilder pos = new StringBuilder();
            for (int i = 0; i < sentence.get(CoreAnnotations.TokensAnnotation.class).size(); i++) {
                CoreLabel token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(i);
                pos.append(token.get(CoreAnnotations.PartOfSpeechAnnotation.class)).append("_").append(i).append(" ");
            }
            sentencePosTags.put(sentence, pos.toString().trim());
        }

        for (Entry<CoreMap, String> entry : sentencePosTags.entrySet()) {
            sentenceTermsIndex.put(entry.getKey(), getTermsPositions(entry.getValue()));
        }
        return sentenceTermsIndex;
    }

    private List<List<Integer>> getTermsPositions(String sentencePosTags) {
        List<List<Integer>> termsIndex = new ArrayList<>();
        Pattern pattern = Pattern.compile(this.patterns.get(this.language));
        Matcher matcher = pattern.matcher(sentencePosTags);
        Pattern indexPattern = Pattern.compile("[0-9]+");
        while (matcher.find()) {
            Matcher indexMatcher = indexPattern.matcher(matcher.group());
            List<Integer> indexes = new ArrayList<>();
            while (indexMatcher.find()) {
                indexes.add(Integer.parseInt(indexMatcher.group()));
            }
            termsIndex.add(indexes);
        }
        return termsIndex;
    }

    private boolean isStopword(String word, Set<String> stopwords) {
        if (word.equalsIgnoreCase("") || stopwords == null) {
            return false;
        }
        return stopwords.contains(word);
    }

    public Set<String> getVocabulary() {
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
