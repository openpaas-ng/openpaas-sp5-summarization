package service;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jdk.management.resource.internal.inst.InitInstrumentation;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import com.google.gson.Gson;
import structures.*;

@RestController
public class Controller {
    private ConcurrentHashMap<String,Transcript> currentMeetings=new ConcurrentHashMap();
    private final AtomicLong counter = new AtomicLong();

    /**
     * This is the offline summarization endpoint. It accepts the transcript of one meeting and generates a summary
     * which is later available at /summary endpoint.
     *
     * @param transcript It must be contained at the request body. The format of the transcript is specified at
     *                   TODO INSERT DESCRIPTION LINK
     * @param id The meeting id as a parameter of the request
     * @param enc optional: the encoding that must be used. Default UTF-8
     * @param nkeys optional: the number of words that the summary will output. Default 20
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
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
        String filename = System.getProperty("user.home")+"/local_directory/input/meeting_"+ id + ".txt";
        String infilename = "meeting_"+id + ".txt";
        try(  PrintWriter out = new PrintWriter( filename)  ){
            out.println(t.toString());
        }
        String command = "Rscript --vanilla offline_exe.R " + "asr_info_french.txt" + " " + nkeys.toString();
        Process u = Runtime.getRuntime().exec(command);
        u.waitFor();

        return "summary produced succesfully for meeting"+id;
    }

    /**
     *
     * @param id
     * @param enc
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/summary", method = RequestMethod.GET)
    public String getSummary(@RequestParam String id, @RequestParam(value="enc", defaultValue = "UTF-8") String enc) throws IOException {
        Gson gson = new Gson();
        byte[] encoded = Files.readAllBytes(Paths.get("local_directory/output/meeting_"+id+".txt"));
        String s = new String(encoded, enc);
        String jsonInString = gson.toJson(s);
        return s;
    }

    /**
     *
     * @param id
     * @param action
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/stream", method = RequestMethod.GET)
    public String initStream(@RequestParam String id,@RequestParam String action) throws IOException {
        if(action.equals("START")){
            Transcript t=new Transcript();
            currentMeetings.putIfAbsent(id,t);
            return "START SUCCESS";
        }
        else if (action.equals("STOP")){
            currentMeetings.remove(id);
            return "STOP SUCCESS";
        }
        else
            return action+" FAIL";
    }

    /**
     *
     * @param id
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/resources", method = RequestMethod.GET)
    public String getCurrentResources(@RequestParam String id) throws IOException {



        return "stream initialized succesfully";
    }

    /**
     *
     * @param message
     * @return
     * @throws Exception
     */
    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public OutputMessage send(Message message) throws Exception {
        String[] messageParts = message.getText().split("\t");
        if (currentMeetings.containsKey(message.getFrom()) &&messageParts.length==5){
            TranscriptEntry e=new TranscriptEntry(messageParts[0],Double.valueOf(messageParts[1]),Double.valueOf(messageParts[2]),messageParts[3],messageParts[4]);
            currentMeetings.get(message.getFrom()).add(e);
        }
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        //currentMeetings.putIfAbsent(message.getFrom(),message.getText());
        OutputMessage m = new OutputMessage(message.getFrom(), message.getText(), time);
        return m;
    }
    @Scheduled(fixedRate = 5000)
    public void reportCurrentTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        System.out.println("The time is now {}"+ dateFormat.format(new Date()));
    }
}