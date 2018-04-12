package core.queryexpansion;

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

    private String disambiguationResponse;
    private String language = "";

    private static Map<String, String> requestCache;

    public BabelExpander(String text, String language) {
        try {
            this.language = language;
            requestCache = new HashMap<>();
            disambiguationResponse = makeRequest(disambiguateUrlPrefix + URLEncoder.encode(text, "UTF-8") + "&lang=" + language + urlKeyPostfix);
        } catch (UnsupportedEncodingException e) {
            disambiguationResponse = "";
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, Set<String>> disambiguateQueries(String text, Set<String> words, String language) {
        if (disambiguationResponse.equalsIgnoreCase("")) {
            return new HashMap<>();
        }
        Map<String, Set<String>> disResult = new HashMap<>();
        try {
            for (Object disambiguatedObject : (JSONArray) new JSONParser().parse(disambiguationResponse)) {
                JSONObject disambiguationJson = (JSONObject) disambiguatedObject;
                JSONObject mappedFragment = (JSONObject) disambiguationJson.get("charFragment");
                String mappedWord = text.substring(((Long) mappedFragment.get("start")).intValue(), ((Long) mappedFragment.get("end")).intValue() + 1).replaceAll(" ", "_").replaceAll("-", "_");
                if (!words.contains(mappedWord) && (Double.parseDouble(disambiguationJson.get("score").toString()) == 0.0 || Double.parseDouble(disambiguationJson.get("coherenceScore").toString()) < 0.1 || Double.parseDouble(disambiguationJson.get("globalScore").toString()) < 0.01)) {
                    if (!mappedWord.contains("_")) {
                        continue;
                    }
                }
                if (mappedWord.contains("_")) {
                    String[] splitWord = mappedWord.split("_");
                    String subword1 = splitWord[0];
                    String subword2 = splitWord[1];
                    if ((words.contains(subword1) || words.contains(subword2)) && isNotStopword(subword1, language) && isNotStopword(subword2, language)) {
                        disResult = getComplexCategoriesAbstract(mappedWord, disambiguationJson, disResult);
                    }
                } else if (words.contains(mappedWord)) {
                    disResult = getComplexCategoriesAbstract(mappedWord, disambiguationJson, disResult);
                }
            }
            disResult.remove("");
            return disResult;
        } catch (ParseException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    @Override
    public List<String> process(Map<String, Set<String>> disambiguatedMap, Set<String> words, List<List<String>> queriesWords){
        Map<String, Set<String>> reverseDisMap = revertMap(disambiguatedMap);

        Map<String, Set<String>> keywords = new HashMap<>(reverseDisMap);
        Map<String, Set<String>> new_keywords = new HashMap<>();
        for(String word: reverseDisMap.keySet()){
            if (!words.contains(word)){
                new_keywords.put(word, keywords.remove(word));
            }
        }

        List<String> result = new ArrayList<>();
        System.out.println("#######################$$$$$$$$$$$$$$$$$$$$$$$$$3");
        for(List<String> query: queriesWords){
            Map<String, Set<String>> queryMap = new HashMap<>(new_keywords);
            for(String w: query){
                if(keywords.containsKey(w)) {
                    queryMap.put(w, keywords.get(w));
                }
            }
            String filter = filter(revertMap(queryMap), query); // <--- ~10 ms
            if (filter.equalsIgnoreCase("")){
                StringBuilder queryString = new StringBuilder();
                for(String word: query){
                    queryString.append(word + " ");
                }
                filter = queryString.toString().substring(0, queryString.length() - 1);
            }
            result.add(filter);
        }

        System.out.println("#######################$$$$$$$$$$$$$$$$$$$$$$$$$");
        System.out.println(result);
        System.out.println("#######################$$$$$$$$$$$$$$$$$$$$$$$$$");
        return result;
    }

    private String filter(Map<String, Set<String>> categories, List<String> keywords){
        if(categories.isEmpty()){
            return "";
        }

        List<String> ignored = new ArrayList<>();
        for(String key: categories.keySet()){
            if(key.split("_").length > 2){
                ignored.add(key);
            }
        }
        for(String key: ignored){
            categories.remove(key);
        }

        Map.Entry<String, Set<String>> maxEntry = categories.entrySet().stream().max(Comparator.comparingInt(entry -> entry.getValue().size())).get();
        String key = maxEntry.getValue().size() != 1 ? maxEntry.getKey() : "";
        if(key.length() < 1){
            return "";
        }
        Set<String> splitWords = new HashSet<>();
        Set<String> mergedWords = new HashSet<>();
        Set<String> end = new HashSet<>();
        end.add(key);
        Set<String> collected_words = new HashSet<>();
        for(Map.Entry<String, Set<String>> entry: categories.entrySet()){
            collected_words.addAll(entry.getValue());
        }
        for (String word : collected_words) {
            if (word.contains("_")) {
                String w1 = word.split("_")[0];
                String w2 = word.split("_")[1];
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
        for (String word : splitWords) {
            if (!mergedWords.contains(word) && keywords.contains(word)) {
                end.add(word);
            }
        }
        System.out.println("Provided words: " + keywords);
        System.out.println("Key " + key + ", Connected words: " + end);
        return end.isEmpty() ? "" : end.stream().collect(Collectors.joining(" ")).toLowerCase();
    }

    private Map<String, Set<String>> getComplexCategoriesAbstract(String detectedWord, JSONObject disambiguationJson, Map<String, Set<String>> categories) {
        String synsetId = disambiguationJson.get("babelSynsetID").toString();
        categories = getCategories(detectedWord, disambiguationJson, categories);
        String synsetResponse = makeRequest("https://babelnet.io/v5/getOutgoingEdges?id=" + synsetId + urlKeyPostfix);
        for (String id : parseOutgoingEdges(synsetResponse)) {
            synsetResponse = makeRequest(synsetUrlPrefix + id + "&targetLang=" + language + urlKeyPostfix);
            for (String name : parseResponse(synsetResponse).split(" ")) {
                categories.computeIfAbsent(name, k -> new HashSet<>()).add(detectedWord);
            }
        }
        return categories;
    }

    private Map<String, Set<String>> getCategories(String detectedWord, JSONObject disambiguationJson, Map<String, Set<String>> categories) {
        String synsetId = disambiguationJson.get("babelSynsetID").toString();
        String synsetResponse = makeRequest(synsetUrlPrefix + synsetId + "&targetLang=" + language + urlKeyPostfix);
        Map<String, Set<String>> currentMap = new HashMap<>();
        for (String name : parseResponse(synsetResponse).split(" ")) {
            categories.computeIfAbsent(name, k -> new HashSet<>()).add(detectedWord);
            currentMap.computeIfAbsent(name, k -> new HashSet<>()).add(detectedWord);
        }
        return categories;
    }

    private String parseResponse(String synsetResponse) {
        Set<String> result = new HashSet<>();
        try {
            JSONObject synsetObject = ((JSONObject) new JSONParser().parse(synsetResponse));
            for (Object category : (JSONArray) synsetObject.get("categories")) {
                result.add(((JSONObject) category).get("category").toString());
            }
            JSONObject synsetDomains = (JSONObject) synsetObject.get("domains");
            for (Object domainName : synsetDomains.keySet()) {
                result.add(domainName.toString());
            }
            for (Object category : (JSONArray) synsetObject.get("senses")) {
                result.add(((JSONObject) ((JSONObject) category).get("properties")).get("fullLemma").toString());
            }
            return result.isEmpty() ? "" : result.stream().collect(Collectors.joining(" ")).toLowerCase();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return "";
    }

    private Set<String> parseOutgoingEdges(String response) {
        Set<String> categories = new HashSet<>();
        try {
            JSONArray edges = ((JSONArray) new JSONParser().parse(response));
            for (Object edge : edges) {
                String edgeType = ((JSONObject) ((JSONObject) edge).get("pointer")).get("shortName").toString();
                if (edgeType.equalsIgnoreCase("is-a") || edgeType.equalsIgnoreCase("part_of") || edgeType.equalsIgnoreCase("said_to_be_the_same_as")) {
                    categories.add(((JSONObject) edge).get("target").toString());
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return categories;
    }

    private String makeRequest(String url) {
        try {
            if (requestCache.containsKey(url)) {
                return requestCache.get(url);
            }
            URL urlAddress = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlAddress.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            requestCache.put(url, response.toString());
            return response.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
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

    private Map<String, Set<String>> revertMap(Map<String, Set<String>> originalMap) {
        Map<String, Set<String>> map = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : originalMap.entrySet()) {
            for (String v : entry.getValue()) {
                map.computeIfAbsent(v, k -> new HashSet<>());
                Set<String> tm = map.get(v);
                tm.add(entry.getKey());
                map.put(v, tm);
            }
        }
        return map;
    }
}
