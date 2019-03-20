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
import java.util.ArrayList;
import java.util.List;

public class QwantService extends resourceService {

    private String type;

    public QwantService(String type) {
        this.type = type;
    }

    public List<GoogleResource> getQwantRecommendations(){
        System.out.println("Starting qwant " + type);
        long time = System.currentTimeMillis();

//        List<String> queries = getExpandedQueries(); // Temporary ignore query expansion
        List<String> queries = getQueries();
        List<GoogleResource> results = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            String query = queries.get(i);
            try {
                System.out.println(query);
                query = URLEncoder.encode(query, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
            }
            try {
                String output = fetch(new URL("https://api.qwant.com/api/search/web?count=10&q=" + query + "&t=news&device=desktop&safesearch=1&locale=fr_FR&uiv=4"));
                JsonArray items = new JsonParser().parse(output).getAsJsonObject().get("data").getAsJsonObject().get("result").getAsJsonObject().getAsJsonArray("items");
                if (items != null && items.size() > 0) {
                    for (JsonElement item : items) {
                        String title = item.getAsJsonObject().get("title").getAsString();
                        String link = item.getAsJsonObject().get("url").getAsString();
                        results.add(new GoogleResource(title, link));
                    }
                }
            } catch (IOException ioe) {
                System.out.println("Exception during Qwant's Search Request for " + type + ". " + ioe.getMessage());
            }
        }
        System.out.println("Finished Qwant after " + (System.currentTimeMillis() - time) + ". Results found: " + results.size() + ".");
        return results;
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
}