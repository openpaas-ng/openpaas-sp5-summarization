package service;

import com.google.gson.Gson;
import core.keywords.TextPreProcess;
import core.keywords.kcore.KCore;
import core.keywords.kcore.WeightedGraphKCoreDecomposer;
import core.keywords.wordgraph.GraphOfWords;
import core.queryexpansion.BabelExpander;
import core.queryexpansion.QueryExpander;
import core.resourceservice.EmailService;
import core.resourceservice.GoogleService;
import org.jgrapht.WeightedGraph;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import structures.*;
import structures.resources.Email;
import structures.resources.GoogleResource;
import structures.resources.Resources;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class Controller {
    private Map<String, Meeting> currentMeetings = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    /**
     * This is the offline summarization endpoint. It accepts the transcript of one meeting and generates a summary
     * which is later available at /summary endpoint.
     *
     * @param transcript It must be contained at the request body. The format of the transcript is specified at
     *                   TODO INSERT DESCRIPTION LINK
     * @param id         The meeting id as a parameter of the request
     * @param enc        optional: the encoding that must be used. Default UTF-8
     * @param nkeys      optional: the number of words that the summary will output. Default 20
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/summary", method = RequestMethod.POST)
    public String postSummary(@RequestBody String transcript, @RequestParam(value = "id") String id, @RequestParam(value = "callbackurl") String callbackurl, @RequestParam(value = "enc", defaultValue = "UTF-8") String enc, @RequestParam(value = "nkeys", defaultValue = "100") Integer nkeys) throws IOException {
        transcript = java.net.URLDecoder.decode(transcript, enc);
        Gson gson = new Gson();
        Transcript t = gson.fromJson(transcript, Transcript.class);
        String filename = "local_directory/input/meeting_" + id + ".txt";
        String infilename = "meeting_" + id + ".txt";
        PrintWriter out = new PrintWriter(filename);
        out.println(t.toString());
        out.close();

        // ensure potentially existing output files with the same name are remove
        Files.deleteIfExists(new File("local_directory/output/meeting_" + id + ".txt").toPath());
        Files.deleteIfExists(new File("local_directory/output/keywords_meeting_" + id + ".txt").toPath());

        String command = "Rscript --vanilla offline_exe.R " + infilename + " " + nkeys.toString();
        Process u = Runtime.getRuntime().exec(command);
        try {
            u.waitFor();

            String s = "";
            List<Keyword> keywordList = new ArrayList<>();

            // the process may have failed
            if (Files.exists(Paths.get("local_directory/output/meeting_" + id + ".txt"))) {
                gson = new Gson();
                byte[] encoded = Files.readAllBytes(Paths.get("local_directory/output/meeting_" + id + ".txt"));
                s = new String(encoded, enc);

                Files.readAllLines(Paths.get("local_directory/output/keywords_meeting_" + id + ".txt"), Charset.forName("utf-8")).stream()
                        .forEach(l -> {
                            String[] parts = l.split(" ");
                            String keyword = parts[0];
                            for (String tt : t.getTokens()) {
                                if (tt.contains(parts[0])) {
                                    keyword = tt;
                                    break;
                                }
                            }

                            keywordList.add(new Keyword(keyword, parts[1]));
                        });
            }

            SummaryResponse res = new SummaryResponse(s, keywordList);
            String jsonInString = gson.toJson(res);


            String url = callbackurl;
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            String body = jsonInString;
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.write(body.getBytes("UTF-8"));
            wr.close();
            con.getResponseCode();

            Files.deleteIfExists(new File("local_directory/output/meeting_" + id + ".txt").toPath());
            Files.deleteIfExists(new File("local_directory/output/keywords_meeting_" + id + ".txt").toPath());
            Files.deleteIfExists(new File("local_directory/input/meeting_" + id + ".txt").toPath());
            return "summary produced succesfully for meeting" + id;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return "got exception trying to run process";
        }
    }

    @RequestMapping(value = "/keywords", method = RequestMethod.POST)
    public String postKeywords(@RequestBody String text, @RequestParam(value = "nkeys", defaultValue = "-1") Integer nkeys, @RequestParam(value = "language", defaultValue = "none") String language) throws IOException {
        String rawText = URLDecoder.decode(text, "UTF-8");
        rawText = new TextPreProcess(rawText, language).getText();
        GraphOfWords gow = new GraphOfWords(rawText);
        WeightedGraph graph = gow.getGraph();
        WeightedGraphKCoreDecomposer decomposer = new WeightedGraphKCoreDecomposer(graph, 10, 0);

        Map<String, Double> map = decomposer.coreRankNumbers();
        map = KCore.sortByValue(map);

        if (nkeys == -1)
            nkeys = map.size();
        int maxLength = Math.min(map.size(), nkeys);
        Object[] it = map.keySet().toArray();
        List<Keyword> topKeys = new ArrayList<>();
        for (int i = 0; i < maxLength; i++) {
            String key1 = (String) it[i];
            String finalKey = key1;
            Double finalScore = map.get(key1);
            topKeys.add(new Keyword(finalKey, finalScore.toString()));
        }
        Gson gson = new Gson();
        String response = gson.toJson(topKeys);
        return response;
    }

    /**
     * @param id
     * @param enc
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/summary", method = RequestMethod.GET)
    public String getSummary(@RequestParam(value = "id") String id, @RequestParam(value = "enc", defaultValue = "UTF-8") String enc) throws IOException {
        Gson gson = new Gson();
        byte[] encoded = Files.readAllBytes(Paths.get("local_directory/output/meeting_" + id + ".txt"));
        String s = new String(encoded, enc);
        List<Keyword> keywordList = new ArrayList<>();
        Files.readAllLines(Paths.get("local_directory/output/keywords_meeting_" + id + ".txt")).stream().forEach(l ->
        {
            String[] parts = l.split(" ");
            keywordList.add(new Keyword(parts[0], parts[1]));
        });

        SummaryResponse res = new SummaryResponse(s, keywordList);
        String jsonInString = gson.toJson(res);

        return jsonInString;
    }

    /**
     * @param id
     * @param action
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/stream", method = RequestMethod.GET)
    public String initStream(@RequestParam(value = "id") String id, @RequestParam String action) {
        if (action.equals("START")) {
            currentMeetings.putIfAbsent(id, new Meeting());
            return "START SUCCESS";
        } else if (action.equals("STOP")) {
            currentMeetings.remove(id);
            return "STOP SUCCESS";
        } else
            return action + " FAIL";
    }

    /**
     * @param id
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/resources", method = RequestMethod.GET)
    public String getCurrentResources(@RequestParam(value = "id") String id, @RequestParam(value = "resources", defaultValue = "keywords;so;wiki") String resources) throws IOException {
        Resources res = new Resources();
        if (currentMeetings.containsKey(id)) {
            Meeting meeting = currentMeetings.get(id);
            if (resources.contains("email")) {
                try {
                    EmailService email = new EmailService();
                    email.setQueries(meeting.getLatestQueries());
                    List<Email> emails = email.getEmails();
                    res.setMails(emails);
                } catch (Exception e) {
                    System.err.println("Exception while fetching from emails");
                    e.printStackTrace();
                }
            }
            if (resources.contains("so") && resources.contains("wiki")) {
                GoogleService gos = new GoogleService("so");
                gos.setOptions(meeting.getLatestQueries(), meeting.getLatestEntriesText(), meeting.getLanguage());
                try {
                    res.setSoarticles(gos.getGoogleRecommendations());
                } catch (Exception e) {
                    System.err.println("Exception while fetching from SO");
                    e.printStackTrace();
                }
                gos.setType("wiki");
                try {
                    res.setWikiarticles(gos.getGoogleRecommendations());
                } catch (Exception e) {
                    System.err.println("Exception while fetching from WIKI");
                    e.printStackTrace();
                }
            }else if (resources.contains("so")) {
                try {
                    GoogleService so = new GoogleService("so");
                    so.setOptions(meeting.getLatestQueries(), meeting.getLatestEntriesText(), meeting.getLanguage());
                    res.setSoarticles(so.getGoogleRecommendations());
                } catch (Exception e) {
                    System.err.println("Exception while fetching from SO");
                    e.printStackTrace();
                }
            } else if (resources.contains("wiki")) {
                try {
                    GoogleService wikis = new GoogleService("wiki");
                    wikis.setOptions(meeting.getLatestQueries(), meeting.getLatestEntriesText(), meeting.getLanguage());
                    res.setWikiarticles(wikis.getGoogleRecommendations());
                } catch (Exception e) {
                    System.err.println("Exception while fetching from wiki");
                    e.printStackTrace();
                }
            }
            if (resources.contains("keywords")) {
                res.setKeywords(meeting.getLatestKeywords());
            }
        }
        return new Gson().toJson(res, Resources.class);
    }

    /**
     * @param message
     * @return
     * @throws Exception
     */
    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public OutputMessage send(Message message) throws Exception {
        String[] messageParts = message.getText().split("\t");
        if (currentMeetings.containsKey(message.getFrom()) && messageParts.length == 4) {
            String text = messageParts[3];
            TranscriptEntry e = new TranscriptEntry(Double.valueOf(messageParts[0]), Double.valueOf(messageParts[1]), messageParts[2], text);
            currentMeetings.get(message.getFrom()).add(e);
        }
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        //currentMeetings.putIfAbsent(message.getFrom(),message.getText());
        OutputMessage m = new OutputMessage(message.getFrom(), message.getText(), time);
        return m;
    }


    @Scheduled(fixedRate = 2000)
    public void reportCurrentTime() {
        currentMeetings.forEach((k, v) -> v.updateKeywords());
        System.out.printf("The time is now {%s} %n", new SimpleDateFormat("HH:mm:ss").format(new Date()));
    }

    /**
     * REST POST request handler that accepts incoming traffic from the Cryptpad module
     *
     * @param id   The id of the corresponding group
     * @param text An array of the words that were added since the previous request
     */
    @CrossOrigin
    @RequestMapping(value = "/pad", method = RequestMethod.POST)
    public void postPad(@RequestParam(value = "id") String id, @RequestParam(value = "words[]") String[] text) {
        id = "ge"; // TODO remove after testing
        if (currentMeetings.containsKey(id)) {
            currentMeetings.get(id).addPad(text);
            System.out.println("Text [" + Arrays.toString(text) + "] was received from Cryptpad");
        }
    }
}