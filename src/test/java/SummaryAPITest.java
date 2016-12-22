/**
 * Created by midas on 11/23/2016.
 */
import static com.jayway.restassured.RestAssured.given;

import com.google.gson.Gson;
import com.opencsv.CSVReader;

import org.junit.Test;
import structures.Transcript;
import structures.TranscriptEntry;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
public class SummaryAPITest {
    private final String USER_AGENT = "Mozilla/5.0";

    @Test
    public void makeSureThatBatchAPIWorks() throws Exception {
        String USER_AGENT = "Mozilla/5.0";

        CSVReader reader = new CSVReader(new FileReader("local_directory/input/asr_info_english.txt"),'\t');
        Gson gson = new Gson();

        List myEntries = reader.readAll();
        Transcript t= new Transcript();
        myEntries.stream().forEach( s->{
            String[] array = (String[]) s;
            if(array.length==4)
                t.add(new TranscriptEntry(array));
            else
                System.out.println(s);
            });
        String jsonInString = gson.toJson(t);
        String url = "http://localhost:8080/summary";

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        String urlParameters = "id=1&locale=&nkeys=10&transcript="+jsonInString;
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + urlParameters);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        System.out.println(response.toString());
        given().when().get("http://localhost:8080/summary?id=1").then().statusCode(200);
    }

}

