package core.keywords;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import org.apache.commons.lang3.StringUtils;
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

	private HashMap<CoreMap, List<List<Integer>>> getTermsPositions(Annotation annotation, String language) {
		HashMap<CoreMap, String> sentencePosTags = new HashMap<CoreMap, String>();
		HashMap<CoreMap, List<List<Integer>>> sentenceTermsIndex = new HashMap<CoreMap, List<List<Integer>>>();
		List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			int max_index = sentence.get(CoreAnnotations.TokensAnnotation.class).size();
			String partOfSpeech = "";
			for (int i = 0; i < max_index; i++) {
				CoreLabel token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(i);
				String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
				partOfSpeech = partOfSpeech + pos + "_" + i + " ";
			}
			sentencePosTags.put(sentence, partOfSpeech.trim());
		}

		for (Entry<CoreMap, String> entry : sentencePosTags.entrySet()) {
			List<List<Integer>> termsIndex = new ArrayList<List<Integer>>();
			if (language.equalsIgnoreCase("fr")) {
				termsIndex = getFrenchTermsPositions(entry.getValue());
			} else {
				termsIndex = getEnglishTermsPositions(entry.getValue());
			}
			sentenceTermsIndex.put(entry.getKey(), termsIndex);

		}
		return sentenceTermsIndex;
	}

	private List<List<Integer>> getFrenchTermsPositions(String sentencePosTags) {
		List<List<Integer>> termsIndex = new ArrayList<List<Integer>>();
		// Regex for french terms
		Pattern pattern = Pattern.compile(
				"NOUN_[0-9]+ ADJ_[0-9]+ NOUN_[0-9]+|NOUN_[0-9]+( ADJ_[0-9]+){1,}|ADJ_[0-9]+ NOUN_[0-9]+|(NOUN_[0-9]+|PROPN_[0-9]+){1}( (NOUN_[0-9]+|PROPN_[0-9]+)){1,}|NOUN_[0-9]+ ADP_[0-9]+ NOUN_[0-9]+|NOUN_[0-9]+|PROPN_[0-9]+");

		
		Matcher matcher = pattern.matcher(sentencePosTags);
		// System.out.println(sentencePosTags);
		while (matcher.find()) {
			
			Pattern indexPattern = Pattern.compile("[0-9]+");
			Matcher indexMatcher = indexPattern.matcher(matcher.group());
			List<Integer> indexes = new ArrayList<Integer>();
			while (indexMatcher.find()) {
				
				indexes.add(Integer.parseInt(indexMatcher.group()));
			}
			termsIndex.add(indexes);

		}

		return termsIndex;
	}

	private List<List<Integer>> getEnglishTermsPositions(String sentencePosTags) {
		List<List<Integer>> termsIndex = new ArrayList<List<Integer>>();
		// Regex for english terms
		Pattern pattern = Pattern.compile(
				"JJ(R|S)*_[0-9]+ NNS*_[0-9]+ NNS*_[0-9]+|( JJ(R|S)*_[0-9]+){1,} NNS*_[0-9]+|NNS*_[0-9]+ IN_[0-9]+ NNS*_[0-9]+|NNS*_[0-9]+|NNPS*_[0-9]+( NNPS*_[0-9]+)*");

		
		Matcher matcher = pattern.matcher(sentencePosTags);
		// System.out.println(sentencePosTags);
		while (matcher.find()) {
			
			Pattern indexPattern = Pattern.compile("[0-9]+");
			Matcher indexMatcher = indexPattern.matcher(matcher.group());
			List<Integer> indexes = new ArrayList<Integer>();
			while (indexMatcher.find()) {
				// System.out.println("INDEX: " + indexMatcher.group());
				indexes.add(Integer.parseInt(indexMatcher.group()));
			}
			termsIndex.add(indexes);

		}

		return termsIndex;
	}

	private void process() {
		String cleanText = "";
		String cleanText2 = "";
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
			stopwords = new ArrayList<>(Arrays.asList(Application.stopWordsFrench, Application.fillerWordsFrench,
					Application.stopWordsFrench2));
			stemmer = new FrenchStemmer();
			break;
		default:
			return;
		}

		List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
		if (sentences == null) {
			return;
		}

		HashMap<CoreMap, List<List<Integer>>> termsPositions = getTermsPositions(annotation, language);

		for (CoreMap sentence : sentences) {

			int index = -1;
			List<List<Integer>> termsOfSentence = termsPositions.get(sentence);
			List<Integer> termsIndexes = new ArrayList<Integer>();
			List<Integer> termsEndIndexes = new ArrayList<Integer>();
			for (List<Integer> indexes : termsOfSentence) {
				termsIndexes.addAll(indexes);
				termsEndIndexes.add(indexes.get(indexes.size() - 1));
			}
			for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
				index++;
				String word = token.get(CoreAnnotations.TextAnnotation.class);
				if (isStopword(word, stopwords) && !termsIndexes.contains(index)) {
					continue;
				}
				stemmer.setCurrent(word);
//                stemmer.stem();
				word = stemmer.getCurrent();
				if (termsIndexes.contains(index)) {
					cleanText2 = cleanText2 + word;
				}
				if (termsIndexes.contains(index) && !termsEndIndexes.contains(index)
						&& termsIndexes.contains(index + 1)) {
					cleanText2 = cleanText2 + "_";
				} else {
					cleanText2 = cleanText2 + " ";
				}

			}
		}

		cleanText2 = cleanText2.replaceAll("\\s+", " ").replaceAll("^_|_$", "").trim();

//for (CoreMap sentence : sentences) {
//        	
//            int index = -1;
//            
//            int max_index = sentence.get(CoreAnnotations.TokensAnnotation.class).size();
//            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
//            	index++;
//                String word = token.get(CoreAnnotations.TextAnnotation.class);
//                if (isStopword(word, stopwords) ) {
//                    continue;
//                }
//                stemmer.setCurrent(word);
////                stemmer.stem();
//                word = stemmer.getCurrent();
//               
//                if (cleanText.endsWith("_" + word + " ")) {
//                    continue;
//                }
//                String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
//
//                if (!pos.startsWith("V") && (pos.startsWith("NN") || pos.startsWith("NOUN") || pos.startsWith("PROPN"))) {
//                    cleanText += word + " ";
//                }
//
//                if ((index + 1 < max_index) && (pos.startsWith("NN") || pos.startsWith("NOUN") || pos.startsWith("PROPN") || pos.startsWith("JJ") || pos.startsWith("ADJ"))) {
//                    CoreLabel next_token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(index + 1);
//                    String next_pos = next_token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
//                    if (!((pos.startsWith("JJ") && next_pos.startsWith("JJ")) || (pos.startsWith("ADJ") && next_pos.startsWith("ADJ"))) && (next_pos.startsWith("NN") || next_pos.startsWith("NOUN") || next_pos.startsWith("PROPN") || next_pos.startsWith("JJ") || next_pos.startsWith("ADJ"))) {
//                        String next_word = next_token.get(CoreAnnotations.TextAnnotation.class);
//                        if (isStopword(next_word, stopwords)) {
//                            continue;
//                        }
//                        stemmer.setCurrent(next_word);
////                        stemmer.stem();
//                        next_word = stemmer.getCurrent();
//                        String prefix = (pos.startsWith("JJ") || pos.startsWith("ADJ")) ? cleanText + word : StringUtils.chop(cleanText);
//                        cleanText = prefix + "_" + next_word + " ";
//                    }
//                }
//            
//               
//            }
//        }

		this.text = cleanText2;
		
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
