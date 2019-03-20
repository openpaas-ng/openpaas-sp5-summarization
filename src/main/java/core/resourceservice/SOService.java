/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core.resourceservice;

import com.google.gson.Gson;
import structures.resources.QuestionItems;
import structures.resources.StackOverflow;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * @author pmeladianos
 */
public class SOService extends resourceService {

    public List<StackOverflow> getSOQuestions() {
        String query = getStackOverflowServiceQuery();
        String response = callSOAPI(query);
        Gson gson = new Gson();
        QuestionItems so = gson.fromJson(response, QuestionItems.class);
        if (so != null) {
            System.out.println("SO query" + query);

            System.out.println("SO hits" + so.getItems().size());
            return so.getItems();
        } else return new ArrayList<>();
    }

    //TODO fix or Remove
    private String getStackOverflowServiceQuery() {
        StringBuilder tags = new StringBuilder();
        for (String key : getQueries()) {
            tags.append(key).append(";");
        }
        return "http://api.stackexchange.com/2.2/search?order=desc&sort=activity&tagged=" + tags.substring(0, tags.length() - 1) + "&site=stackoverflow&filter=!BHMIbze0EPheMk572h0ktETsgnphhV";
    }

    private String callSOAPI(String query) {
        String output = "";
        try {
            output = callAPI(new GZIPInputStream(new URL(query).openStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return output;
    }

}
