package core.resourceservice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.queryexpansion.BabelExpander;
import core.queryexpansion.QueryExpander;
import service.Settings;
import structures.resources.GoogleResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Created by midas on 20/3/2017.
 */
public class GoogleService extends resourceService {

    String type;

    public GoogleService(String type) {
        this.type = type;
    }

    public List<GoogleResource> getGoogleRecommendations() throws IOException, URISyntaxException {
        String key = null;
        String cx = null;
        switch (type.toLowerCase()) {
            case "wiki":
                if(getLanguage().equalsIgnoreCase("EN")){
                    key = Settings.WIKIENKEY;
                    cx = Settings.WIKIENCX;
                } else {
                    key = Settings.WIKIFRKEY;
                    cx = Settings.WIKIFRCX;
                }
                break;
            case "so":
                if(getLanguage().equalsIgnoreCase("EN")) {
                    key = Settings.SOKEY_ALL;
                    cx = Settings.SOENCX;
                } else {
                    key = Settings.SOKEY_ALL;
                    cx = Settings.SOFRCX;
                }
                break;
            default:
                key = Settings.SOKEY_ALL;
                cx = Settings.SOFRCX;
                break;
        }

        List<String> queries = getGoogleServiceQueries();
        ArrayList results = new ArrayList();
        int queryNumber = 10;
        switch (queries.size()){
            case 3:
                queryNumber = 3;
                break;
            case 2:
                queryNumber = 5;
                break;
            default:
                queryNumber = 10;
        }
        for (String query : queries) {
//            if(1==1){
//                return results;
//            }
            URL url = new URL(
                    "https://www.googleapis.com/customsearch/v1?key=" + key + "&cx=" + cx + "&q=" + query + "&alt=json&num=" + queryNumber + "&queriefields=queries(request(totalResults))");
            String output = fetch(url);
            JsonParser parser = new JsonParser();
            JsonObject o = parser.parse(output).getAsJsonObject();
            JsonArray items = o.getAsJsonArray("items");
            if (items != null && items.size() > 0) {
                for (JsonElement item : items) {
                    String title = item.getAsJsonObject().get("title").getAsString();
                    String link = item.getAsJsonObject().get("formattedUrl").getAsString();
                    GoogleResource g = new GoogleResource(title, link);
                    results.add(g);
                }
            }
        }
        return results;
    }

    private String fetch(URL url) throws IOException, URISyntaxException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        String line;
        StringBuffer content = new StringBuffer();
        while ((line = br.readLine()) != null) {
            content.append(line);
        }
        String output = content.toString();
        conn.disconnect();
        return output;
    }

    private List<String> getGoogleServiceQueries() throws UnsupportedEncodingException {
        List<String> result = new ArrayList<>();
        List<String> queries = this.getQueries();

        System.out.println("Queries in Google Service: " + queries);

        QueryExpander qe = new BabelExpander(getText(), getLanguage());
        for (String query : queries) {
            String remove_it_after_testing = query;

            query = qe.expand(getText(), Arrays.asList(query.split(" ")), getLanguage());

            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            System.out.println("Words generated through GoW and Clustering: " + remove_it_after_testing);
            System.out.println("Words filtered through the Disambiguation API:  " + query);
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");

            result.add(URLEncoder.encode(query, "UTF-8"));
        }

        qe.expandQueries(getText(), queries, getLanguage());

        if(result.isEmpty()){
            result.add(URLEncoder.encode(queries.get(0), "UTF-8"));
        }
        return result;
    }
}
