package service;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.opencsv.CSVReader;
import org.springframework.web.bind.annotation.*;
import com.google.gson.Gson;
import structures.Transcript;
import structures.TranscriptEntry;

@RestController
public class Controller {

    private final AtomicLong counter = new AtomicLong();

    @RequestMapping(value = "/summary", method = RequestMethod.POST)
    public String postSummary(@RequestBody String transcript, @RequestParam(value="id") String id, @RequestParam(value="enc", defaultValue = "UTF-8") String enc) throws IOException {
        String[] bodyParams = transcript.split("&");
        for(String param:bodyParams){
            if(param.startsWith("transcript=")) {
                transcript = param;
                break;
            }
        }
        transcript = java.net.URLDecoder.decode(transcript,enc);
        transcript=transcript.substring(11);
        Gson gson = new Gson();
        Transcript t=gson.fromJson(transcript,Transcript.class);
        try(  PrintWriter out = new PrintWriter( id+"_transcript.txt" )  ){
            out.println(t.toString());
        }
        //Runtime.getRuntime().exec("Rscript --vanilla /home/midas/IdeaProjects/openpaas/offline_exe.R filename.txt 20");
        //System.out.println("service");

        return "summary produced succesfully for meeting"+counter.get();
    }

    @RequestMapping(value = "/summary", method = RequestMethod.GET)
    public String getSummary(@RequestParam String id) throws IOException {
        CSVReader reader = new CSVReader(new FileReader( id+"_transcript.txt"),'\t');
        Gson gson = new Gson();
        List myEntries = reader.readAll();
        Transcript t= new Transcript();
        myEntries.stream().forEach( s->{
            String[] entry = (String[]) s;
            if(entry.length==9)
                t.add(new TranscriptEntry(entry));
        });
        String jsonInString = gson.toJson(t);
        return jsonInString;
    }

}