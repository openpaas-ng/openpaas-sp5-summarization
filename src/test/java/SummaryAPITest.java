/**
 * Created by midas on 11/23/2016.
 */
import static com.jayway.restassured.RestAssured.given;

import com.google.gson.Gson;
import com.opencsv.CSVReader;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
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


        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);
        post.addHeader("Content-Type", "application/json");
        post.addHeader("id", "7");
        post.addHeader("callbackurl", "localhost");

        StringEntity entity = new StringEntity(jsonInString);
        post.setEntity(entity);

        try {
            HttpResponse response = client.execute(post);
            StatusLine status = response.getStatusLine();
            String content = EntityUtils.toString(response.getEntity());
            JSONObject json = new JSONObject(content);

        } catch (Exception e) {
            System.out.println("error");
            //listener.onFailure(new Exception(e));
        }
        //URL obj = new URL(url);
        //HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        //con.setRequestMethod("POST");
        //con.setRequestProperty("User-Agent", USER_AGENT);
        //con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        //con.setRequestProperty("callbackurl", "localhost");
        //con.setRequestProperty("id", "2");
        //String urlParameters = jsonInString;
        //con.setDoOutput(true);
        //DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        //wr.writeBytes(urlParameters);
        //wr.flush();
        //wr.close();

        //int responseCode = con.getResponseCode();
        //System.out.println("\nSending 'POST' request to URL : " + url);
        //System.out.println("Post parameters : " + urlParameters);
        //System.out.println("Response Code : " + responseCode);

        //BufferedReader in = new BufferedReader(
        //        new InputStreamReader(con.getInputStream()));
        //String inputLine;
        //StringBuffer response = new StringBuffer();

        //while ((inputLine = in.readLine()) != null) {
        //    response.append(inputLine);
        //}
        //in.close();

        //print result
        //System.out.println(response.toString());
        given().when().get("http://localhost:8080/summary?id=7").then().statusCode(200);
    }

}

