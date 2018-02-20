package core.resourceservice;

import structures.Keyword;

import java.util.List;

/**
 * Created by midas on 12/29/2016.
 */
public class resourceService {
    List<Keyword> keywords;

    private String text;
    private String language;

    public void setKeywords(List<Keyword> keywords) {
        this.keywords = keywords;
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
