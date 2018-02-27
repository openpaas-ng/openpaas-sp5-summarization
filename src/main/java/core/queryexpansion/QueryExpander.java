package core.queryexpansion;

import structures.Keyword;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class QueryExpander {

    public String expand(String text, List<String> keywords, String language) {
        text = text.trim();
        Set<String> words = keywords.stream().collect(Collectors.toSet());

        Map<String, Set<String>> senses = disambiguate(text, words, language);
//        String topKeyword = "";
//        double maxValue = -1;
//        Set<String> detectedWords = senses.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        String qry = "";
        for(String key: keywords){
            qry += key + " ";
//            if(!detectedWords.contains(key.getKey())){
//                continue;
//            }
//            double value = Math.abs(Double.parseDouble(key.getValue()));
//            if(value > maxValue){
//                topKeyword = key.getKey();
//                maxValue = value;
//            }
        }
        int maxNumber = senses.entrySet().stream().max(Comparator.comparingInt(entry -> entry.getValue().size())).get().getValue().size();
        if(maxNumber < 2 || senses.isEmpty()){
            return qry;
        }
        return combineSenses(senses, maxNumber);
    }

    protected abstract Map<String, Set<String>> disambiguate(String text, Set<String> words, String language);

//    protected abstract String combineSenses(Map<String, Set<String>> senses, String topKeyword);
    protected abstract String combineSenses(Map<String, Set<String>> senses, int maxNumber);
}
