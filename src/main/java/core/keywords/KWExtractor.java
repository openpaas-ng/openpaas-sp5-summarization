package core.keywords;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.text.WordUtils;
import org.apache.tika.language.LanguageIdentifier;
import org.jgrapht.WeightedGraph;
import core.keywords.TextPreProcess;
import core.keywords.kcore.KCore;
import core.keywords.kcore.WeightedGraphKCoreDecomposer;
import core.keywords.wordgraph.GraphOfWords;
import structures.Keyword;
import org.tartarus.snowball.SnowballProgram;
import org.tartarus.snowball.ext.EnglishStemmer;
import org.tartarus.snowball.ext.FrenchStemmer;

public class KWExtractor {
	
	public static List<Keyword> extractKeyWords(String text, String language, int nkeys){
		List<Keyword> keywords = new ArrayList<Keyword>();
		SnowballProgram stemmer = null;
		if (language.equalsIgnoreCase("none")) {
            String lang = new LanguageIdentifier(text).getLanguage();
            System.out.println("Detected language: " + lang);
            if (lang.equalsIgnoreCase("en") || lang.equalsIgnoreCase("fr")) {
                language = lang;
                String rawText = new TextPreProcess(text, language).getText();
        		        		
        		switch (language) {
        		case "en":
        			stemmer = new EnglishStemmer();
        			break;
        		case "fr":
        			stemmer = new FrenchStemmer();
        			break;
        		}
        		
                HashMap<String, String> originalTerms = new HashMap<String, String>();
                StringBuffer processedText = new StringBuffer();
                
                for(String term: rawText.split(" ")) {
                	String stemmedWord = "";
                
                	for(String word: term.split("_")) {
                	stemmer.setCurrent(word.toLowerCase());
                  stemmer.stem();
        			word = stemmer.getCurrent();
        			stemmedWord = stemmedWord + word + "_";
                }
                	stemmedWord = stemmedWord.replaceAll("_$", "");
                	//System.out.println("STEMMED WORD: " + stemmedWord);
                	originalTerms.put(stemmedWord, term);
                	processedText.append(stemmedWord + " ");
                	
                }
                 
                GraphOfWords gow = new GraphOfWords(processedText.toString().trim());
                
                WeightedGraph graph = gow.getGraph();
                WeightedGraphKCoreDecomposer decomposer = new WeightedGraphKCoreDecomposer(graph, 10, 0);

                Map<String, Double> map = decomposer.coreRankNumbers();
                map = KCore.sortByValue(map);

                if (nkeys == -1)
                    nkeys = map.size();
                int maxLength = Math.min(map.size(), nkeys);
                Object[] it = map.keySet().toArray();
                
                for (int i = 0; i < maxLength; i++) {
                    String key1 = (String) it[i];
                    String finalKey = WordUtils.capitalize(originalTerms.get(key1).replace("_", " "));
                    Double finalScore = map.get(key1);
                    keywords.add(new Keyword(finalKey, finalScore.toString()));
                }
            }
        }
		                
		return keywords;
	}

}

