package structures;

/**
 * Created by midas on 11/28/2016.
 */
public class OutputMessage {

    String from;
    String text;
    String time;
    public OutputMessage(Object from, Object text, String time) {
        this.from=from.toString();
        this.text=text.toString();
        this.time=time.toString();
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
