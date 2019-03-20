package core.resourceservice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import service.Settings;
import structures.resources.GoogleResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by midas on 20/3/2017.
 */
public class GoogleService extends resourceService {

    private String type;

    public GoogleService(String type) {
        this.type = type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<GoogleResource> getGoogleRecommendations() throws IOException {
        System.out.println("Starting google " + type);
        long time = System.currentTimeMillis();
        String cx, key;
//        List<String> queries = getExpandedQueries(); // Use Babelfy API to disambiguate the provided text
        List<String> queries = this.getQueries();
        List<GoogleResource> results = new ArrayList<>();
        int queryNumber;
        switch (queries.size()) {
            case 3:
                queryNumber = 3;
                break;
            case 2:
                queryNumber = 5;
                break;
            default:
                queryNumber = 10;
        }

        switch (type.toLowerCase()) {
            case "so":
                if (getLanguage().equalsIgnoreCase("EN")) {
                    key = Settings.SOKEY_ALL;
                    cx = Settings.SOENCX;
                } else {
                    key = Settings.SOKEY_ALL;
                    cx = Settings.SOFRCX;
                }
                break;
            case "wiki":
                if (getLanguage().equalsIgnoreCase("EN")) {
                    key = Settings.WIKIENKEY;
                    cx = Settings.WIKIENCX;
                } else {
                    key = Settings.WIKIFRKEY;
                    cx = Settings.WIKIFRCX;
                }
                queryNumber = 1;
                queries = new ArrayList<>(splitWords(queries));
                break;
            default:
                key = Settings.SOKEY_ALL;
                cx = Settings.SOFRCX;
                break;
        }
        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            GoogleResource wikiPager = fetchWiki(queries.get(i));
            if (wikiPager != null) {
                results.add(wikiPager);
            }
            if(1==1) {
                continue;
            }
            try {
                query = URLEncoder.encode(query, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
            }
            URL url = new URL("https://www.googleapis.com/customsearch/v1?key=" + key + "&cx=" + cx +
                    "&q=" + query + "&alt=json&num=" + queryNumber + "&queriefields=queries(request(totalResults))");
            try {
                String output = fetch(url);
                JsonArray items = new JsonParser().parse(output).getAsJsonObject().getAsJsonArray("items");
                if (items != null && items.size() > 0) {
                    for (JsonElement item : items) {
                        String title = item.getAsJsonObject().get("title").getAsString();
                        String link = item.getAsJsonObject().get("formattedUrl").getAsString();
                        results.add(new GoogleResource(title, link));
                    }
                }
            } catch (IOException ioe) {
                System.out.println("Exception during Google's Custom Search Request for " + type + ". " + ioe.getMessage());
                if (type.equalsIgnoreCase("wiki")) {
                    if (key.equalsIgnoreCase(Settings.WIKIFRKEY)) {
                        key = Settings.WIKIFRKEY2;
                        cx = Settings.WIKIFRCX2;
                        i--;
                    } else {
                        GoogleResource wikiPage = fetchWiki(queries.get(i));
                        if (wikiPage != null) {
                            results.add(wikiPage);
                        }
                    }
                }
            }
        }
        System.out.println("Finished after " + (System.currentTimeMillis() - time) + ". Results found: " + results.size() + ".");
        return results;
    }

    private GoogleResource fetchWiki(String query) {
        String[] terms;
        if (query.contains("\"") && query.split(" ").length == 2) {
            terms = new String[]{query.replace("\"", "").replace(" ", "_")};
        } else {
            terms = query.split(" ");
        }
        for (String term : terms) {
            try {
                String output = new GoogleService("").fetch(new URL("https://fr.wikipedia.org/w/api.php?action=opensearch&search=" + URLEncoder.encode(term, "UTF-8") + "&limit=1&format=json"));
                JsonArray items = new JsonParser().parse(output).getAsJsonArray();
                if (items.size() > 1) {
                    String title = items.get(1).getAsJsonArray().get(0).toString().replace("\"", "");
                    String url = items.get(3).getAsJsonArray().get(0).toString().replace("\"", "");
                    return new GoogleResource(title, url);
                } else {
                    return null;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private String fetch(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        String line;
        StringBuilder content = new StringBuilder();
        while ((line = br.readLine()) != null) {
            content.append(line);
        }
        String output = content.toString();
        conn.disconnect();
        return output;
    }

    private Set<String> splitWords(List<String> queries) {
        Set<String> words = new HashSet<>();
        for (String query : queries) {
            words.addAll(splitQuery(query));
        }
        return words;
    }

    private Set<String> splitQuery(String query) {
        Set<String> words = new HashSet<>();
        for (String word : query.split(" ")) {
            if (word.contains("_")) {
                words.add("\"" + word.replace("_", " ") + "\"");
            } else {
                words.add(word);
            }
        }
        return words;
    }
}
