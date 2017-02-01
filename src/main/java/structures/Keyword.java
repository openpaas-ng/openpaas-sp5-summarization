package structures;

/**
 * Created by midas on 31/1/2017.
 */
public class Keyword {
    private String key;
    private String value;

    public Keyword(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
