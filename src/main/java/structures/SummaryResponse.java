package structures;

import java.util.List;

/**
 * Created by midas on 31/1/2017.
 */
public class SummaryResponse {

    private String text;
    private List keywords;

    public SummaryResponse(String text, List keywords) {
        this.text = text;
        this.keywords = keywords;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List getKeywords() {
        return keywords;
    }

    public void setKeywords(List keywords) {
        this.keywords = keywords;
    }
}
