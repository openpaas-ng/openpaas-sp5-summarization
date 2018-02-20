package service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by midas on 1/12/2016.
 * Add only immutable properties here
 */
public class Settings {
    public static Integer TIMEWINDOW;
    public static Integer NKEYWORDS;
    public static String WIKIENKEY;
    public static String WIKIENCX;

    public static String WIKIFRKEY;
    public static String WIKIFRCX;

    public static String SOKEY;
    public static String SOCX;

    public static String BABELKEY;

    public static void init() throws IOException {
        Properties prop = new Properties();
        InputStream input = null;

        input = new FileInputStream("config.properties");

        // load a properties file
        prop.load(input);
        TIMEWINDOW = Integer.valueOf(prop.getProperty("TIMEWINDOW"));
        NKEYWORDS = Integer.valueOf(prop.getProperty("NKEYWORDS"));
        WIKIENKEY = prop.getProperty("WIKIENKEY");
        WIKIENCX = prop.getProperty("WIKIENCX");
        WIKIFRKEY = prop.getProperty("WIKIFRKEY");
        WIKIFRCX = prop.getProperty("WIKIFRCX");
        SOKEY = prop.getProperty("SOKEY");
        SOCX = prop.getProperty("SOCX");
        BABELKEY = prop.getProperty("BABELKEY");

        input.close();
    }
}

