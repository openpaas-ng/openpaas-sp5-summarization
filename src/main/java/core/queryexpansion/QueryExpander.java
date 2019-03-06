package core.queryexpansion;

import java.util.*;

public abstract class QueryExpander {

    public List<String> expandQueries(String text, List<String> queries, String language) {
        Set<String> words = new HashSet<>();
        List<List<String>> queriesWords = new ArrayList<>();
        for (String query : queries) {
            List<String> tmp = Arrays.asList(query.split(" "));
            words.addAll(tmp);
            queriesWords.add(tmp);
        }

        Map<String, Set<String>> disambiguatedMap = disambiguateQueries(text, words, language);

        return process(disambiguatedMap, words, queriesWords);
    }

    public abstract Map<String, Set<String>> disambiguateQueries(String text, Set<String> words, String language);

    public abstract List<String> process(Map<String, Set<String>> disambiguatedMap, Set<String> words, List<List<String>> queriesWords);
}
