package structures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Date;

class PadEntry {
    private List<String> updatedWords;
    private final long timestamp;

    PadEntry(String[] words){
        updatedWords = new ArrayList<>(Arrays.asList(words));
        timestamp = new Date().getTime();
    }

    long getTime(){
        return timestamp;
    }

    List<String> getWords(){
        return updatedWords;
    }

}
