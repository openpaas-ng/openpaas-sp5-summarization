package core.resourceservice;

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

    public void setQueries(List<String> queries) {
        this.queries = queries;
    }

    public List<String> getQueries() {
        return queries;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() { return this.text; }

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

    public void setOptions(List<String> queries, String text, String language) {
        this.queries = queries;
        this.text = text;
        this.language = language;
    }

}
