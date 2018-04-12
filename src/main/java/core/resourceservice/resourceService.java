package core.resourceservice;

import core.queryexpansion.BabelExpander;
import core.queryexpansion.QueryExpander;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by midas on 12/29/2016.
 */
public class resourceService {
    private List<String> queries;
    private String text;
    private String language;
    private final Set<String> supportedLanguages = new HashSet<>(Arrays.asList("en", "fr"));
    private List<String> expandedQueries;

    public void setQueries(List<String> queries) {
        this.queries = queries;
    }

    public List<String> getQueries() {
        return queries;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return this.text;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLanguage() {
        if (!supportedLanguages.contains(language)) {
            return "fr";
        } else {
            return language;
        }
    }

    public void expandQueries() {
        QueryExpander qe = new BabelExpander(text, language);
        List<String> queries = getQueries();

//        queries.clear();
////        this.setText("Welcome to the jungle. You can find a couple of snakes, like pythons, cobras and boas.");
////        queries.add("jungle python boa");
//        this.setText("j' aime python, le langage de programmation");
//        queries.add("python programmation");

        System.out.println("Queries in Google Service: " + queries);


        List<String> result = qe.expandQueries(getText(), queries, getLanguage());

        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        System.out.println("Words generated through GoW and Clustering: " + queries);
        System.out.println("Words filtered through the Disambiguation API:  " + result);
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");


        //////////////

//        queries.clear();
////        this.setText("I always prefer writing my code in python. Other programming languages are just bad.");
////        queries.add("python code java scala languages");
//        this.setText("le python se recontre dans les forets denses et humides");
//        queries.add("python forets humides");
//
//        System.out.println("Queries in Google Service: " + queries);
//
//        qe = new BabelExpander(getText(), getLanguage());
//        result = qe.expandQueries(getText(), queries, getLanguage());
//
//        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
//        System.out.println("Words generated through GoW and Clustering: " + queries);
//        System.out.println("Words filtered through the DGoogleService so = new GoogleService("so");isambiguation API:  " + result);
//        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");

        /////////////

        if (result.isEmpty()) {
            result.addAll(queries);
        }
        this.expandedQueries = result;
    }

    public List<String> getExpandedQueries() {
        if(expandedQueries == null){
            expandQueries();
        }
        return expandedQueries;
    }

    public void setOptions(List<String> queries, String text, String language) {
        this.queries = queries;
        this.text = text;
        this.language = language;
    }

}
