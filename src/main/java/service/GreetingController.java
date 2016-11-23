package service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @RequestMapping("/summary")
    public Greeting greeting(@RequestParam(value="id",defaultValue = "summary") String transcipt) throws IOException {
        try(  PrintWriter out = new PrintWriter( "filename.txt" )  ){
            out.println( transcipt );
        }
        //Runtime.getRuntime().exec("Rscript myScript.R");
        //System.out.println("service");
        return new Greeting(counter.incrementAndGet(),
                String.format(template, transcipt));
    }
}