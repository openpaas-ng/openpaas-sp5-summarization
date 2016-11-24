package service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.*;
import com.google.gson.Gson;
import structures.Transcript;

@RestController
public class Controller {

    private final AtomicLong counter = new AtomicLong();

    @RequestMapping(value = "/summary", method = RequestMethod.POST)
    public String postSummary(@RequestBody String transcript) throws IOException {

        transcript = java.net.URLDecoder.decode(transcript, "UTF-8");
        transcript=transcript.substring(11);
        Gson gson = new Gson();
        Transcript t=gson.fromJson(transcript,Transcript.class);
        try(  PrintWriter out = new PrintWriter( "filename.txt" )  ){
            out.println(t.toString());
        }
        Runtime.getRuntime().exec("Rscript --vanilla /home/midas/IdeaProjects/openpaas/offline_exe.R filename.txt 20");
        //System.out.println("service");
        Meeting m=new Meeting(counter.incrementAndGet(), t);
        return "summary produced succesfully for meeting"+counter.get();
    }

    @RequestMapping(value = "/summary", method = RequestMethod.GET)
    public String getSummary(@RequestBody String transcript) throws IOException {


        return "summary produced succesfully for meeting"+counter.get();
    }

}