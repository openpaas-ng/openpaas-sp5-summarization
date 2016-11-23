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
    public String greeting(@RequestBody String transcript) throws IOException {

        transcript = java.net.URLDecoder.decode(transcript, "UTF-8");
        transcript=transcript.substring(11);
        try(  PrintWriter out = new PrintWriter( "filename.txt" )  ){
           out.println( transcript );
        }
        Gson gson = new Gson();
        Transcript t=gson.fromJson(transcript,Transcript.class);
        //Runtime.getRuntime().exec("Rscript myScript.R");
        //System.out.println("service");
        Meeting m=new Meeting(counter.incrementAndGet(), t);
        return "summary produced succesfully for meeting"+counter.get();
    }
}