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
    private String stemmedText;
    private String language;
    private String[] splitText;
    private final Map<String, String> patterns = new HashMap<String, String>() {{
        put("en", "JJ(R|S)*_[0-9]+ NNS*_[0-9]+ NNS*_[0-9]+|( JJ(R|S)*_[0-9]+){1,} NNS*_[0-9]+|NNS*_[0-9]+ IN_[0-9]+ NNS*_[0-9]+|NNS*_[0-9]+|NNPS*_[0-9]+( NNPS*_[0-9]+)*");
        put("fr", "NOUN_[0-9]+ ADJ_[0-9]+ NOUN_[0-9]+|NOUN_[0-9]+( ADJ_[0-9]+){1,}|ADJ_[0-9]+ NOUN_[0-9]+|(NOUN_[0-9]+|PROPN_[0-9]+){1}( (NOUN_[0-9]+|PROPN_[0-9]+)){1,}|NOUN_[0-9]+ ADP_[0-9]+ NOUN_[0-9]+|NOUN_[0-9]+|PROPN_[0-9]+");
    }};
    private Map<String, String> stemMapper;

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
        StringBuilder stemmedText = new StringBuilder();
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
                if (isStopword(word, stopwords)) {
                    if (cleanText.length() > 1 && cleanText.charAt(cleanText.length() - 1) == '_') {
                        cleanText.replace(cleanText.length() - 1, cleanText.length(), " ");
                        stemmedText.replace(stemmedText.length() - 1, stemmedText.length(), " ");
                    }
                    continue;
                }
                stemmer.setCurrent(word);
                stemmer.stem();
                stemmedText.append(stemmer.getCurrent());
                cleanText.append(word);
                if (!termsEndIndexes.contains(index) && termsIndexes.contains(index + 1)) {
                    cleanText.append("_");
                    stemmedText.append("_");
                } else {
                    cleanText.append(" ");
                    stemmedText.append(" ");
                }
            }
        }
        this.text = cleanText.toString().replaceAll("\\s+", " ").replaceAll("^_|_$", "").trim();
        this.stemmedText = stemmedText.toString().replaceAll("\\s+", " ").replaceAll("^_|_$", "").trim();
        storeSplit();
        mapStems();
    }

    private void storeSplit() {
        String[] split = this.text.split(" ");
        this.splitText = new String[split.length];
        for (int i = 0; i < split.length; i++) {
            String term = split[i].trim();
            if (term.endsWith("_")) {
                term = term.substring(0, term.length() - 1);
            }
            this.splitText[i] = term;
        }
    }

    private void mapStems() {
        Map<String, Integer> counterMap = new HashMap<>();
        for (String word : this.splitText) {
            counterMap.compute(word, (k, v) -> v == null ? 1 : v + 1);
        }
        String[] stemmed = stemmedText.split(" ");
        stemMapper = new HashMap<>();
        for (int i = 0; i < this.splitText.length; i++) {
            String stem = stemmed[i];
            String word = this.splitText[i];
            String existingWord = stemMapper.getOrDefault(stem, "");
            if (counterMap.getOrDefault(existingWord, -1) < counterMap.get(word)) {
                stemMapper.put(stem, word);
            }

        }
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

    public Map<String, String> getStemsMap() {
        return stemMapper;
    }

    public String getStemmedText() {
        return stemmedText;
    }

    public String[] getTokens() {
        return splitText;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

}
