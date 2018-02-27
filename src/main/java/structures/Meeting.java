package structures;

import java.util.List;

public class Meeting {

    private Transcript transcript;
    private Pad pad;
    private String language;

    public Meeting() {
        transcript = new Transcript();
        pad = new Pad();
        language = "none";
    }

    public List<Keyword> getLatestKeywords() {
        if(this.language.equalsIgnoreCase("none")){ // Update the language field
            this.language = transcript.getLanguage();
        }
        return transcript.getLatestKeywords();
    }
    public List<String> getLatestQueries() {
        if(this.language.equalsIgnoreCase("none")){ // Update the language field
            this.language = transcript.getLanguage();
        }
        return transcript.getLatestQueries();
    }
    public String getLatestEntriesText() {
        return transcript.getLatestEntriesText();
    }

    public void add(TranscriptEntry e) {
        transcript.add(e);
    }

    public void addPad(String[] words) {
        pad.addEntry(words, language);
    }

    public void updateKeywords() {
        transcript.updateKeywords(pad.getLatestEntries());
    }

    public String getLanguage(){
        return this.language;
    }

}
