package structures.resources;

import structures.Keyword;

import java.util.List;

/**
 * Created by midas on 30/12/2016.
 */
public class Resources {
    public List<Keyword> keywords;
    public List<Email> mails;
    public List<StackOverflow> soarticles;
    public List<Wikipedia> wikiarticles;

    public List<Keyword> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<Keyword> keywords) {
        this.keywords = keywords;
    }

    public List<StackOverflow> getSoarticles() {
        return soarticles;
    }

    public void setSoarticles(List<StackOverflow> soarticles) {
        this.soarticles = soarticles;
    }

    public List<Wikipedia> getWikiarticles() {
        return wikiarticles;
    }

    public void setWikiarticles(List<Wikipedia> wikiarticles) {
        this.wikiarticles = wikiarticles;
    }

    public List<Email> getMails() {
        return mails;
    }

    public void setMails(List<Email> mails) {
        this.mails = mails;
    }
}
