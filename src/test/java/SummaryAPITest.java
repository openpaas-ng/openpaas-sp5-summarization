/**
 * Created by midas on 11/23/2016.
 */
import static com.jayway.restassured.RestAssured.given;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import org.junit.Test;
import structures.Transcript;
import structures.TranscriptEntry;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class SummaryAPITest {

    @Test
    public void makeSureThatBatchAPIWorks() throws IOException {
        CSVReader reader = new CSVReader(new FileReader("src/test/java/ES2002a.2.da"),'\t');
        Gson gson = new Gson();

        List myEntries = reader.readAll();
        Transcript t= new Transcript();
        myEntries.stream().forEach( s->{t.add(new TranscriptEntry((String[]) s));});
        String jsonInString = gson.toJson(t);
        given().when().get("http://localhost:8080/summary").then().statusCode(200);
    }

}