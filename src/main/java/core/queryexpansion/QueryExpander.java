package core.queryexpansion;

import structures.Keyword;

import java.util.*;
import java.util.stream.Collectors;

public abstract class QueryExpander {

    public List<String> expandQueries(String text, List<String> queries, String language){
        System.out.println("??? starting expand");
        Set<String> vocabulary = new HashSet<>();
        List<List<String>> qq = new ArrayList<>();
        System.out.println("#######################$$$$$$$$$$$$$$$$$$$$$$$$$1");
        for(String query: queries){
            vocabulary.addAll(Arrays.asList(query.split(" ")));
            qq.add(Arrays.asList(query.split(" ")));
        }
        System.out.println("#######################$$$$$$$$$$$$$$$$$$$$$$$$$2");
        Map<String, Set<String>> originalMap = disambiguateQueries(text, vocabulary, language);
        Map<String, Set<String>> stringSetMap = new HashMap<>();

        for(Map.Entry<String, Set<String>> entry: originalMap.entrySet()){
            for(String w: entry.getValue()){
                stringSetMap.computeIfAbsent(w, k -> new HashSet<>());
                Set<String> tmp = stringSetMap.get(w);
                tmp.add(entry.getKey());
                stringSetMap.put(w, tmp);
            }
        }



        Map<String, Set<String>> new_words = new HashMap<>(stringSetMap);
        Map<String, Set<String>> keywords = new HashMap<>(stringSetMap);
        for(String w: stringSetMap.keySet()){
            if (vocabulary.contains(w)){
                new_words.remove(w);
            } else {
                keywords.remove(w);
            }
        }

        List<String> result = new ArrayList<>();
        System.out.println("#######################$$$$$$$$$$$$$$$$$$$$$$$$$3");
        String output = "";
        for(List<String> q: qq){
            Map<String, Set<String>> tmp = new HashMap<>(new_words);
            for(String w: q){
                if(keywords.containsKey(w)) {
                    tmp.put(w, keywords.get(w));
                }
            }
            Map<String, Set<String>> map = new HashMap<>();
            for(Map.Entry<String, Set<String>> entry: tmp.entrySet()){
                for(String v: entry.getValue()){
                    map.computeIfAbsent(v, k -> new HashSet<>());
                    Set<String> tm = map.get(v);
                    tm.add(entry.getKey());
                    map.put(v, tm);
                }
            }
            String filter = filter(map, q);
            result.add(filter);
            output += filter + ", ";
        }
        System.out.println("#######################$$$$$$$$$$$$$$$$$$$$$$$$$4");
        System.out.println("#######################$$$$$$$$$$$$$$$$$$$$$$$$$");
        System.out.println(output);
        System.out.println("#######################$$$$$$$$$$$$$$$$$$$$$$$$$");
        return result;
    }

    public String expand(String text, List<String> keywords, String language) {
        text = text.trim();
        Set<String> words = new HashSet<>(keywords);

        Map<String, Set<String>> senses = disambiguate(text, words, language);
        String tmp = "";
        for(String s : senses.get("1")){
            if (s.contains("_")){
                s = "\"" + s.replace("_", " ") + "\"";
            }
            tmp += s + " ";
        }
        if(1 == 1){
            return tmp;
        }
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

    public abstract Map<String, Set<String>> disambiguateQueries(String text, Set<String> words, String language);

    public abstract String filter(Map<String, Set<String>> categories, List<String> keywords);

    protected abstract Map<String, Set<String>> disambiguate(String text, Set<String> words, String language);

//    protected abstract String combineSenses(Map<String, Set<String>> senses, String topKeyword);
    protected abstract String combineSenses(Map<String, Set<String>> senses, int maxNumber);
}
