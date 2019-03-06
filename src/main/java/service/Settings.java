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

    public static String WIKIFRKEY2;
    public static String WIKIFRCX2;

    public static String SOKEY;
    public static String SOCX;

    public static String SOKEY_ALL;
    public static String SOENCX;
    public static String SOFRCX;

    public static String BABELKEY1, BABELKEY2, BABELKEY3, BABELKEY4;

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
        WIKIFRKEY2 = prop.getProperty("WIKIFRKEY2");
        WIKIFRCX2 = prop.getProperty("WIKIFRCX2");
        SOKEY = prop.getProperty("SOKEY");
        SOCX = prop.getProperty("SOCX");

        BABELKEY1 = prop.getProperty("BABELKEY1");
        BABELKEY2 = prop.getProperty("BABELKEY2");
        BABELKEY3 = prop.getProperty("BABELKEY3");
        BABELKEY4 = prop.getProperty("BABELKEY4");

        SOKEY_ALL = prop.getProperty("SOENKEY");
        SOENCX = prop.getProperty("SOENCX");
        SOFRCX = prop.getProperty("SOFRCX");

        input.close();
    }
}

