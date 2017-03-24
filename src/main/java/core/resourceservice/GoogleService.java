package core.resourceservice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import service.Settings;
import structures.Keyword;
import structures.resources.Email;
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
import java.util.List;

/**
 * Created by midas on 20/3/2017.
 */
public class GoogleService extends resourceService {

    String type;

    public GoogleService(String type) {
        this.type = type;
    }

    public List<GoogleResource> getGoogleRecommendations() throws IOException, URISyntaxException {
       String key=null;
       String cx=null;
        switch (type.toLowerCase()) {
            case "wikifr":
                key= Settings.WIKIFRKEY;
                cx= Settings.WIKIFRCX;
                break;
            case "wikien":
                key= Settings.WIKIENKEY;
                cx= Settings.WIKIENCX;
                break;
            case "so":
                key= Settings.SOKEY;
                cx= Settings.SOCX;
                break;
            default:
                key= Settings.WIKIFRKEY;
                cx= Settings.WIKIFRCX;
                break;
        }

        String qry = getGoogleServiceQuery();
        URL url = new URL(
                "https://www.googleapis.com/customsearch/v1?key=" + key + "&cx=" + cx + "&q=" + qry + "&alt=json&num=10&queriefields=queries(request(totalResults))");

        String output = fetch(url);
        JsonParser parser = new JsonParser();
        JsonObject o = parser.parse(output).getAsJsonObject();
        JsonArray items = o.getAsJsonArray("items");
        List<GoogleResource> results = new ArrayList();
        if(items!=null && items.size()>0){
            for (JsonElement item : items) {
                String title = item.getAsJsonObject().get("title").getAsString();
                String link = item.getAsJsonObject().get("formattedUrl").getAsString();
                GoogleResource g = new GoogleResource(title, link);
                results.add(g);
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

    private  String getGoogleServiceQuery() throws UnsupportedEncodingException {
        String q = "";
        String tags = "";
        for (Keyword key : this.keywords) {
            String s = key.getKey().toString();
            tags += s + " ";
        }
        tags=tags.substring(0,tags.length()-4);
        tags= URLEncoder.encode(tags, "UTF-8");
        return tags;
    }
}
