package structures.resources;

import java.util.List;

/**
 * Created by midas on 30/12/2016.
 */
public class Resources {
    public List<Email> mails;
    public List<StackOverflow> soArticles;
    public List<Wikipedia> wikiArticles;

    public List<StackOverflow> getSoArticles() {
        return soArticles;
    }

    public void setSoArticles(List<StackOverflow> soArticles) {
        this.soArticles = soArticles;
    }

    public List<Wikipedia> getWikiArticles() {
        return wikiArticles;
    }

    public void setWikiArticles(List<Wikipedia> wikiArticles) {
        this.wikiArticles = wikiArticles;
    }

    public List<Email> getMails() {
        return mails;
    }

    public void setMails(List<Email> mails) {
        this.mails = mails;
    }
}
