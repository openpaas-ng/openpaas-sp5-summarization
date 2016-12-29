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
public class RegisterTest {
    private final String USER_AGENT = "Mozilla/5.0";

    @Test
    public void register() throws Exception {
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
        String url = "http://localhost:8080/stream?id=1&action=START";
        given().when().get(url).then().statusCode(200);
    }

}

