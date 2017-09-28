package core.keywords;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import org.apache.tika.language.LanguageIdentifier;
import org.tartarus.snowball.ext.FrenchStemmer;
import service.Application;

import java.util.List;

/**
 * Created by Midas on 5/22/2017.
 */
public class TextPreProcess {
    String text;
    String language;

    public TextPreProcess(String text, String language) {
        this.text = text;
        this.language = language;
        process();
    }

    public TextPreProcess(String text) {
        this.text = text;
        String lang = new LanguageIdentifier(text).getLanguage();
        System.out.println(lang);
        language=lang;
        process();
    }

    private void process(){
        String cleanText = "";
        Annotation annotation = null;
        List<CoreMap> sentences =null;
        String[] tokens=null;
            switch (language){
                case "en":
                    annotation = new Annotation(text);
                    Application.enPOSpipeline.annotate(annotation);
                    sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
                    for (CoreMap sentence : sentences) {
                        for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                            String word = token.get(CoreAnnotations.TextAnnotation.class);
                            String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                            if(!pos.startsWith("V") && pos.startsWith("N"))
                                cleanText+=word+" ";
                        }
                    }

                    tokens = cleanText.split(" ");
                    cleanText = "";
                    for(String t:tokens){
                        if(!Application.stopWordsEnglish.contains(t) && !Application.fillerWordsEnglish.contains(t)) {
                            FrenchStemmer stemmer = new FrenchStemmer();
                            //cleanText += t + " ";
                            stemmer.setCurrent(t);
                            if (stemmer.stem()) {
                                String tok = t;
                                cleanText += tok + " ";
                            }
                        }
                    }
                    break;
                case "fr":
                    annotation = new Annotation(text);
                    Application.frenchPOSpipeline.annotate(annotation);
                    sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
                    for (CoreMap sentence : sentences) {
                        for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                            String word = token.get(CoreAnnotations.TextAnnotation.class);
                            String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                            if(!pos.startsWith("V") && pos.startsWith("N"))
                                cleanText+=word+" ";
                        }
                    }

                    tokens = cleanText.split(" ");
                    cleanText = "";
                    for(String t:tokens){
                        if(!Application.stopWordsFrench.contains(t) && !Application.fillerWordsFrench.contains(t) && !Application.stopWordsFrench2.contains(t)) {
                            FrenchStemmer stemmer = new FrenchStemmer();
                            //cleanText += t + " ";
                            stemmer.setCurrent(t);
                            if (stemmer.stem()) {
                                String tok = t;
                                cleanText += tok + " ";
                            }
                        }
                    }
                    break;
            }
            this.text=cleanText;
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
