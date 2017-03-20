/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core.resourceservice;

import com.google.gson.Gson;
import structures.Keyword;
import structures.resources.StackOverflow;
import structures.resources.QuestionItems;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author pmeladianos
 */
public class SOService extends resourceService {



    public  List<StackOverflow> getSOQuestions() {
        String query = getStackOverflowServiceQuery();
        String response = callSOAPI(query);
        Gson gson = new Gson();
        QuestionItems so = gson.fromJson(response,QuestionItems.class);
        if (so!=null){
            System.out.println("SO query" + query);

            System.out.println("SO hits" + so.getItems().size());
            return so.getItems();
        }
        else return new ArrayList<StackOverflow>();
    }

    private  String getStackOverflowServiceQuery() {
        String tags = "";
        for (Keyword key : this.keywords) {
            String s = key.getKey().toString();
           tags += s + ";";
        }
        String query = "http://api.stackexchange.com/2.2/search?order=desc&sort=activity&tagged=" + tags.substring(0, tags.length() - 1) + "&site=stackoverflow&filter=!BHMIbze0EPheMk572h0ktETsgnphhV";
        return query;
    }

    private  String callSOAPI(String query) {
        String output = "";
        try {
            URL url = new URL(query);

            BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(url.openStream())));
            // Question q = new Gson().fromJson(in, Question.class);
            String line;
            StringBuffer content = new StringBuffer();
            while ((line = in.readLine()) != null) {
                content.append(line);
            }
            output=content.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return output;
    }

}
