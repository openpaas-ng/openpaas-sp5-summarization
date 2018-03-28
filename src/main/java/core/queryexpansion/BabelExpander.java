package core.queryexpansion;

import core.keywords.TextPreProcess;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import service.Application;
import service.Settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

public class BabelExpander extends QueryExpander {

    private static final String disambiguateUrlPrefix = "https://babelfy.io/v1/disambiguate?text=";
    private static final String synsetUrlPrefix = "https://babelnet.io/v5/getSynset?id=";
    private static final String urlKeyPostfix = "&key=" + Settings.BABELKEY1;
    private static final String frenchLemmaPrefix = "https://babelnet.io/v5/getSenses?lemma=";
    private static final String frenchLemmaPostfix = "&searchLang=EN&targetLang=FR";

    private String disambiguationResponse;
    private String language = "";

    private static Map<String, Map<String, Set<String>>> cache;

    public Map<String, Set<String>> disambiguateQueries(String text, Set<String> words, String language) {
        if(disambiguationResponse.equalsIgnoreCase("")){
            return new HashMap<>();
        }
        try {
            Map<String, Set<String>> categories = new HashMap<>();
            for (Object disambiguatedObject : (JSONArray) new JSONParser().parse(disambiguationResponse)) {
                JSONObject disambiguationJson = (JSONObject) disambiguatedObject;
                JSONObject mappedFragment = (JSONObject) disambiguationJson.get("charFragment");
                String detectedWord = text.substring(((Long) mappedFragment.get("start")).intValue(), ((Long) mappedFragment.get("end")).intValue() + 1).replaceAll(" ", "_");

                if(!words.contains(detectedWord) && (Double.parseDouble(disambiguationJson.get("score").toString()) == 0.0  || Double.parseDouble(disambiguationJson.get("coherenceScore").toString()) < 0.1 || Double.parseDouble(disambiguationJson.get("globalScore").toString()) < 0.01)){
                    if(!detectedWord.contains("_")){
                        continue;
                    }
                }
                System.out.println(detectedWord);
                detectedWord = detectedWord.replaceAll("-", "_");
                if (detectedWord.contains("_")) {
                    String[] mergedWords = detectedWord.split("_");
                    String word1 = mergedWords[0];
                    String word2 = mergedWords[1];
                    if(1==1){
                        System.out.print("");
                    }
                    if ((words.contains(word1) || words.contains(word2)) && isNotStopword(word1, language) && isNotStopword(word2, language)) {
                        categories = getCategories(detectedWord, disambiguationJson, categories);
                    }
                } else if (words.contains(detectedWord)) {
                    categories = getCategories(detectedWord, disambiguationJson, categories);
                }
            }

            return categories;
        } catch (ParseException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public String filter(Map<String, Set<String>> categories, List<String> keywords){
        if(categories.isEmpty()){
            return "";
        }
        Map.Entry<String, Set<String>> maxEntry = categories.entrySet().stream().max(Comparator.comparingInt(entry -> entry.getValue().size())).get();
        String key = maxEntry.getValue().size() != 1 ? maxEntry.getKey() : "";
        System.out.println("Categories: " + key + " @ " + maxEntry.getValue());
        Set<String> splitWords = new HashSet<>();
        Set<String> mergedWords = new HashSet<>();
        Set<String> end = new HashSet<>();
        Set<String> collected_words = new HashSet<>();
        for(Map.Entry<String, Set<String>> entry: categories.entrySet()){
            for(String w: entry.getValue()){
                collected_words.add(w);
            }
        }
        for (String word : collected_words) {
            if (word.contains("_")) {
                String w1 = word.split("_")[0];
                String w2 = word.split("_")[1];
                if(1==1){
                    System.out.print("");
                }
                if ((keywords.contains(w1) || keywords.contains(w2)) && isNotStopword(w1, language) && isNotStopword(w2, language)) {
                    mergedWords.add(w1);
                    mergedWords.add(w2);
                    end.add(word);
                }
            } else {
                if (isNotStopword(word, language)) {
                    splitWords.add(word);
                }
            }
        }
        System.out.println("Provided words: " + keywords);
        for (String word : splitWords) {
            if (!mergedWords.contains(word) && keywords.contains(word)) {
                end.add(word);
            }
        }
        System.out.println("Connected: " + end);


        System.out.println("Key " + key);
        categories.clear();
        end.add(key);
        String tmp = "";
        for(String s : end){
            if (s.contains("_")){
                s = "\"" + s.replace("_", " ") + "\"";
            }
            tmp += s + " ";
        }
        return tmp;
    }


    public BabelExpander(String text, String language){
        try {
            this.language = language;
            System.out.println("Text to disambiguate: " + text);
            disambiguationResponse = makeRequest(disambiguateUrlPrefix + URLEncoder.encode(text, "UTF-8") + "&lang=" + language + urlKeyPostfix);
            cache = new HashMap<>();
            System.out.println(disambiguationResponse);
        } catch (UnsupportedEncodingException e) {
            disambiguationResponse = "";
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, Set<String>> disambiguate(String text, Set<String> words, String language) {
        if(disambiguationResponse.equalsIgnoreCase("")){
            return new HashMap<>();
        }

        String asas = "";
        try {
            Map<String, Set<String>> categories = new HashMap<>();
            for (Object disambiguatedObject : (JSONArray) new JSONParser().parse(disambiguationResponse)) {
                JSONObject disambiguationJson = (JSONObject) disambiguatedObject;
                JSONObject mappedFragment = (JSONObject) disambiguationJson.get("charFragment");
                String detectedWord = text.substring(((Long) mappedFragment.get("start")).intValue(), ((Long) mappedFragment.get("end")).intValue() + 1).replaceAll(" ", "_");

                if(!words.contains(detectedWord) && (Double.parseDouble(disambiguationJson.get("score").toString()) == 0.0  || Double.parseDouble(disambiguationJson.get("coherenceScore").toString()) < 0.1 || Double.parseDouble(disambiguationJson.get("globalScore").toString()) < 0.01)){
                    if(!detectedWord.contains("_")){
                        continue;
                    }
                }
                System.out.println(detectedWord);
//                detectedWord = text.substring(((Long) mappedFragment.get("start")).intValue(), ((Long) mappedFragment.get("end")).intValue() + 1).replaceAll(" ", "_");
                //                System.out.println("Detected word: " + detectedWord);
//                System.out.println("Detected: " + detectedWord + " " + getLemma(makeRequest(synsetUrlPrefix + disambiguationJson.get("babelSynsetID").toString().toLowerCase() + urlKeyPostfix)));

//                System.out.println(detectedWord + " " + disambiguationJson.get("score").toString() + " " + disambiguationJson.get("coherenceScore").toString() + " " + disambiguationJson.get("globalScore").toString());

                asas += detectedWord + " "; //@@@ Dont add all words

//                if (1 == 1) {
////                    System.exit(23);
//                    continue;
//                }
//                System.out.println("Detected word: " + detectedWord);
                if (detectedWord.contains("_")) {
//                    System.out.println(detectedWord + " " + disambiguationJson.get("score").toString() + " " + disambiguationJson.get("coherenceScore").toString() + " " + disambiguationJson.get("globalScore").toString());
                    String[] mergedWords = detectedWord.split("_");
                    String word1 = mergedWords[0];
                    String word2 = mergedWords[1];
//                    if (true || words.contains(word1) || words.contains(word2)) {
                    if ((words.contains(word1) || words.contains(word2)) && isNotStopword(word1, language) && isNotStopword(word2, language)) {
                        categories = getCategories(detectedWord, disambiguationJson, categories);
//                        categories = getCategoriesAbstract(detectedWord, disambiguationJson, categories);
//                        categories = getComplexCategoriesAbstract(detectedWord, disambiguationJson, categories);
                    }
                } else if (words.contains(detectedWord)) {
//                    System.out.println(detectedWord + " " + disambiguationJson.get("score").toString() + " " + disambiguationJson.get("coherenceScore").toString() + " " + disambiguationJson.get("globalScore").toString());
                    categories = getCategories(detectedWord, disambiguationJson, categories);
//                    categories = getCategoriesAbstract(detectedWord, disambiguationJson, categories);
//                    categories = getComplexCategoriesAbstract(detectedWord, disambiguationJson, categories);
                }
            }
            Map.Entry<String, Set<String>> maxEntry = categories.entrySet().stream().max(Comparator.comparingInt(entry -> entry.getValue().size())).get();
            String key = maxEntry.getValue().size() != 1 ? maxEntry.getKey() : "";
//            if(language.equalsIgnoreCase("fr")){
//                String transl = makeRequest(frenchLemmaPrefix + maxEntry.getKey() + frenchLemmaPostfix + urlKeyPostfix);
//                System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
//                System.out.println(transl);
//                JSONParser jsonParser = new JSONParser();
//                JSONArray synsetObject = null;
//                try {
//                    synsetObject = ((JSONArray) jsonParser.parse(transl));
//                    for (Object category : synsetObject) {
//                        key = ((JSONObject)((JSONObject)category).get("properties")).get("fullLemma").toString();
//                        System.out.println(((JSONObject)((JSONObject)category).get("properties")).get("fullLemma").toString());
//                        break;
//                    }
//                } catch (ParseException e) {
//                    e.printStackTrace();
//                }
//            }
            System.out.println("Categories: " + key + " @ " + maxEntry.getValue());
            Set<String> splitWords = new HashSet<>();
            Set<String> mergedWords = new HashSet<>();
            Set<String> end = new HashSet<>();
            for (String word : asas.split(" ")) {
                if (word.contains("_")) {
                    String w1 = word.split("_")[0];
                    String w2 = word.split("_")[1];
                    if ((words.contains(w1) || words.contains(w2)) && isNotStopword(w1, language) && isNotStopword(w2, language)) {
                        mergedWords.add(w1);
                        mergedWords.add(w2);
                        end.add(word);
                    }
                } else {
                    if (isNotStopword(word, language)) {
                        splitWords.add(word);
                    }
                }
            }
            System.out.println("Provided words: " + words);
            for (String word : splitWords) {
                if (!mergedWords.contains(word) && words.contains(word)) {
                    end.add(word);
                }
            }
            System.out.println("Connected: " + end);


            System.out.println("Key " + key);
            categories.clear();
            end.add(key);

            categories.put("1", end);
            return categories;
        } catch (ParseException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    private Map<String, Set<String>> getCategoriesAbstract(String detectedWord, JSONObject disambiguationJson, Map<String, Set<String>> categories) {
//        System.out.println("Synset analysis for word " + detectedWord);
        String synsetId = disambiguationJson.get("babelSynsetID").toString().toLowerCase().replaceAll("_", " ");
        String synsetResponse = makeRequest("https://babelnet.io/v5/getOutgoingEdges?id=" + synsetId + urlKeyPostfix);
        Map<String, Set<String>> tmp = new HashMap<>();
        for (String id : parseOutgoingEdges(synsetResponse)) {
//            System.out.println(synsetUrlPrefix + id + urlKeyPostfix);
            synsetResponse = makeRequest(synsetUrlPrefix + id + "&targetLang=" + language + urlKeyPostfix);
//            System.out.println(synsetResponse);
            String lemma = getLemma(synsetResponse);
            categories.computeIfAbsent(lemma, k -> new HashSet<>()).add(detectedWord);
            tmp.computeIfAbsent(lemma, k -> new HashSet<>()).add(detectedWord);
        }
        System.out.println(detectedWord + " " + tmp);
        return categories;
    }

    private Map<String, Set<String>> getComplexCategoriesAbstract(String detectedWord, JSONObject disambiguationJson, Map<String, Set<String>> categories) {
//        System.out.println("Synset analysis for word " + detectedWord);
        String synsetId = disambiguationJson.get("babelSynsetID").toString().toLowerCase().replaceAll("_", " ");
        String synsetResponse = makeRequest("https://babelnet.io/v5/getOutgoingEdges?id=" + synsetId + urlKeyPostfix);
        Map<String, Set<String>> tmp = new HashMap<>();
        for (String id : parseOutgoingEdges(synsetResponse)) {
//            System.out.println(synsetUrlPrefix + id + urlKeyPostfix);
            synsetResponse = makeRequest(synsetUrlPrefix + id + "&targetLang=" + language +  urlKeyPostfix);
//            System.out.println(synsetResponse);
            for (String name : parseResponse(synsetResponse).split(" ")) {
                categories.computeIfAbsent(name, k -> new HashSet<>()).add(detectedWord);
                tmp.computeIfAbsent(name, k -> new HashSet<>()).add(detectedWord);
            }
//            String lemma = getLemma(synsetResponse);
//            categories.computeIfAbsent(lemma, k -> new HashSet<>()).add(detectedWord);
//            tmp.computeIfAbsent(lemma, k -> new HashSet<>()).add(detectedWord);
        }
        System.out.println(detectedWord + " " + tmp);
        return categories;
    }

    private Map<String, Set<String>> getCategories(String detectedWord, JSONObject disambiguationJson, Map<String, Set<String>> categories) {
//        System.out.println("Synset analysis for word " + detectedWord);
        String synsetId = disambiguationJson.get("babelSynsetID").toString().toLowerCase().replaceAll("_", " ");
        if(cache.containsKey(synsetId)){
            return cache.get(synsetId);
        }
//        System.out.println(synsetUrlPrefix + synsetId + "&targetLang=FR" + urlKeyPostfix);
        String synsetResponse = makeRequest(synsetUrlPrefix + synsetId + "&targetLang=" + language +  urlKeyPostfix);
//        System.out.println(synsetResponse);
        Map<String, Set<String>> tmp = new HashMap<>();
        for (String name : parseResponse(synsetResponse).split(" ")) {
            categories.computeIfAbsent(name, k -> new HashSet<>()).add(detectedWord);
            tmp.computeIfAbsent(name, k -> new HashSet<>()).add(detectedWord);
        }
//        System.out.println(detectedWord + " @@@ " + tmp.keySet());
        cache.put(detectedWord, tmp);
        return categories;
    }


    @Override
//    public String combineSenses(Map<String, Set<String>> senses, String topKeyword) {
//        int maxNumber = senses.entrySet().stream().max(Comparator.comparingInt(entry -> entry.getValue().size())).get().getValue().size();
//        Set<String> words = new HashSet<>();
//        if (maxNumber < 2){
//            for(Map.Entry<String, Set<String>> entry: senses.entrySet()){
//                if(entry.getValue().contains(topKeyword)){
//                    words.add(entry.getKey().toLowerCase());
//                }
//            }
//            words.add(topKeyword);
//        } else {
//            for(Map.Entry<String, List<String>> senseEntry: senses.entrySet()){
//                if (senseEntry.getValue().size() == maxNumber){
//                    words.add(senseEntry.getKey().toLowerCase());
//                    words.addAll(senseEntry.getValue().stream()
//                            .map(String::toLowerCase)
//                            .collect(Collectors.toSet()));
//                }
//            }
//        }
//        return words.stream().collect(Collectors.joining(" "));
//    }
    public String combineSenses(Map<String, Set<String>> senses, int maxNumber) {
        Set<String> words = new HashSet<>();
        for (Map.Entry<String, Set<String>> senseEntry : senses.entrySet()) {
            if (senseEntry.getValue().size() == maxNumber) {
                words.add(senseEntry.getKey().toLowerCase());
                words.addAll(senseEntry.getValue().stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet()));
            }
        }
        return words.stream().collect(Collectors.joining(" "));
    }

    private String makeRequest(String url) {
        try {
            URL urlAddress = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlAddress.openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

//            System.out.println("\nSending 'GET' request to URL : " + url);
//            System.out.println("Response Code : " + connection.getResponseCode());

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            in.close();

            return response.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String parseResponse(String synsetResponse) {
        Map<String, Integer> domains = new HashMap<>();
        Set<String> categories = new HashSet<>();
        try {
            JSONParser jsonParser = new JSONParser();
            JSONObject synsetObject = ((JSONObject) jsonParser.parse(synsetResponse));

            for (Object category : (JSONArray) synsetObject.get("categories")) {
                categories.add(((JSONObject) category).get("category").toString());
            }

//            if (categories.size() != 0) {
//                return categories.stream().collect(Collectors.joining(" ")).toLowerCase();
//            }
            String cat = categories.stream().collect(Collectors.joining(" ")).toLowerCase();

            JSONObject synsetDomains = (JSONObject) synsetObject.get("domains");
            for (Object domainName : synsetDomains.keySet()) {
                int domainScore = (int) (100 * (double) synsetDomains.get(domainName));
                domains.merge(domainName.toString(), domainScore, Integer::sum);
            }

            String dom = domains.keySet().stream().collect(Collectors.joining(" ")).toLowerCase();

//            if (domains.size() != 0){
//                return Collections.max(domains.entrySet(), Comparator.comparingInt(Map.Entry::getValue)).getKey().toLowerCase();
//            }

            String senses = "";
            for (Object category : (JSONArray) synsetObject.get("senses")) {
                senses += ((JSONObject) ((JSONObject) category).get("properties")).get("fullLemma").toString().toLowerCase() + " ";
//                if (language.equalsIgnoreCase("fr")) {
//                    senses += ((JSONObject) ((JSONObject) category).get("properties")).get("fullLemma").toString().toLowerCase() + " ";
//                } else{
//                    senses += ((JSONObject) category).get("lemma").toString().toLowerCase() + " ";
//                }
            }
            Set<String> split = new HashSet<>(Arrays.asList((cat + " " + dom + " " + senses).trim().split(" ")));
            if(split.contains("")){
                split.remove("");
            }
            return split.stream().collect(Collectors.joining(" ")).toLowerCase();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getLemma(String synsetResponse) {
        try {
            JSONParser jsonParser = new JSONParser();
            JSONObject synsetObject = ((JSONObject) jsonParser.parse(synsetResponse));

            for (Object category : (JSONArray) synsetObject.get("senses")) {
                return ((JSONObject) category).get("lemma").toString();
            }
        } catch (ParseException e) {
            System.out.println(synsetResponse);
            e.printStackTrace();
        }
        return "";
    }


    private Set<String> parseOutgoingEdges(String response) {
        Set<String> categories = new HashSet<>();
        try {
            JSONParser jsonParser = new JSONParser();
            JSONArray edges = ((JSONArray) jsonParser.parse(response));

            for (Object edge : edges) {
                String name = ((JSONObject) ((JSONObject) edge).get("pointer")).get("shortName").toString();
                if (name.equalsIgnoreCase("is-a")) {
                    categories.add(((JSONObject) edge).get("target").toString());
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return categories;
    }

    private boolean isNotStopword(String word, String language) {
        switch (language) {
            case "en":
                return !Application.stopWordsEnglish.contains(word) && !Application.fillerWordsEnglish.contains(word);
            case "fr":
                return !Application.stopWordsFrench.contains(word) && !Application.fillerWordsFrench.contains(word) && !Application.stopWordsFrench2.contains(word);
            default:
                return false;
        }
    }
}
