package structures.resources;

/**
 * Created by midas on 20/3/2017.
 */
public class GoogleResource {
    public String title;
    public String link;

    public GoogleResource(String title, String link) {
        this.title = title;
        if(!link.startsWith("http://") && !link.startsWith("https://"))
            this.link = "http://"+link;
        else
            this.link = link;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
