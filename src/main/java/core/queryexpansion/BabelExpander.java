package core.queryexpansion;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
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
    private static final String synsetUrlPrefix = "https://babelnet.io/v4/getSynset?id=";
    private static final String urlKeyPostfix = "&key=" + Settings.BABELKEY;

    @Override
    public Map<String, Set<String>> disambiguate(String text, Set<String> words, String language) {
        System.out.println("Starting disambiguation process:");
        String disambiguationResponse;
        try {
            disambiguationResponse = makeRequest(disambiguateUrlPrefix + URLEncoder.encode(text, "UTF-8") + "&lang=" + language + urlKeyPostfix);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return new HashMap<>();
        }

        try {
            Map<String, Set<String>> categories = new HashMap<>();
            System.out.println("Text to disambiguate: " + text);
            for (Object disambiguatedObject : (JSONArray) new JSONParser().parse(disambiguationResponse)) {
                JSONObject disambiguationJson = (JSONObject) disambiguatedObject;
                JSONObject mappedFragment = (JSONObject) disambiguationJson.get("charFragment");
                String detectedWord = text.substring(((Long) mappedFragment.get("start")).intValue(), ((Long) mappedFragment.get("end")).intValue() + 1).replaceAll(" ", "_");
                System.out.println("Detected word: " + detectedWord);
                if (words.contains(detectedWord)) {
                    System.out.println("Synset analysis for word " + detectedWord);
                    String synsetId = disambiguationJson.get("babelSynsetID").toString().toLowerCase().replaceAll("_", " ");
                    String synsetResponse = makeRequest(synsetUrlPrefix + synsetId + urlKeyPostfix);
                    for (String name : parseResponse(synsetResponse).split(" ")) {
                        categories.computeIfAbsent(name, k -> new HashSet<>()).add(detectedWord);
                    }
                }
            }
            return categories;
        } catch (ParseException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
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
        for(Map.Entry<String, Set<String>> senseEntry: senses.entrySet()){
            if (senseEntry.getValue().size() == maxNumber){
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

            if (categories.size() != 0) {
                return categories.stream().collect(Collectors.joining(" "));
            }

            JSONObject synsetDomains = (JSONObject) synsetObject.get("domains");
            for (Object domainName : synsetDomains.keySet()) {
                int domainScore = (int) (100 * (double) synsetDomains.get(domainName));
                domains.merge(domainName.toString(), domainScore, Integer::sum);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return domains.size() > 0 ? Collections.max(domains.entrySet(), Comparator.comparingInt(Map.Entry::getValue)).getKey() : "";
    }

}
