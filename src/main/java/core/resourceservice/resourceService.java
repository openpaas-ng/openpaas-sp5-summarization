package core.resourceservice;

import java.util.List;

/**
 * Created by midas on 12/29/2016.
 */
public class resourceService {
    List<String> queries;
    private String text;
    private String language;

    public void setQueries(List<String> queries) {
        this.queries = queries;
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

    protected String getLanguage() {
        if(this.language.equalsIgnoreCase("none")){
            return "fr";
        }
        return this.language;
    }

}
