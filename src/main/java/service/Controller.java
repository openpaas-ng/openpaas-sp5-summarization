package service;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    public String postSummary(@RequestBody String transcript, @RequestParam(value="id") String id, @RequestParam(value="enc", defaultValue = "UTF-8") String enc,@RequestParam(value="nkeys", defaultValue = "20") Integer nkeys) throws IOException, InterruptedException {
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
        String filename = "local_directory/input/meeting_"+ id + ".txt";
        String infilename = "meeting_"+id + ".txt";
        try(  PrintWriter out = new PrintWriter( filename)  ){
            out.println(t.toString());
        }
        Process u = Runtime.getRuntime().exec("Rscript --vanilla local_directory/offline_exe.R "+infilename+" "+nkeys.toString());
        u.waitFor();

        return "summary produced succesfully for meeting"+id;
    }

    @RequestMapping(value = "/summary", method = RequestMethod.GET)
    public String getSummary(@RequestParam String id, @RequestParam(value="enc", defaultValue = "UTF-8") String enc) throws IOException {
        Gson gson = new Gson();
        byte[] encoded = Files.readAllBytes(Paths.get("local_directory/output/meeting_"+id+".txt"));
        String s = new String(encoded, enc);
        String jsonInString = gson.toJson(s);
        return s;
    }
    @RequestMapping(value = "/stream", method = RequestMethod.GET)
    public String initStream(@RequestParam String id,@RequestParam String ip,@RequestParam String port) throws IOException {



        return "stream initialized succesfully";
    }

    @RequestMapping(value = "/resources", method = RequestMethod.GET)
    public String getCurrentResources(@RequestParam String id) throws IOException {



        return "stream initialized succesfully";
    }
}