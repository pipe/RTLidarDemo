/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.RTLidar.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.phono.srtplight.Log;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author thp
 */
public class OfferParser {
    
    private final Gson gson;
    private final NegotiationData.Offer off;
    
    public OfferParser(String offer) {
        GsonBuilder builder = new GsonBuilder();
        gson = builder.create();
        off = gson.fromJson(offer, NegotiationData.Offer.class);
    }
    
    public String[] getRcandy() {
        String[] ret = new String[off.candidates.length];
        int i=0;
        for (var c:off.candidates){
            ret[i++]= c.toString();
        }
        return ret;
    }
    
    public String getUfrag() {
        return off.candidates[0].usernameFragment;
    }
    
    public String getUpass() {
        return off.candidates[0].password;
    }
    
    public static void main(String argv[]) {
        Log.setLevel(Log.DEBUG);
        String test = "offer.json";
        if (argv.length == 1) {
            test = argv[0];
        }
        Log.info("loading " + test);
        Path p = Paths.get(test);
        try {
            var o = Files.readString(p);
            var op = new OfferParser(o);
            String [] candy = op.getRcandy();
            for (var c:candy){
                Log.info(c);
            }
        } catch (IOException x) {
            Log.error("Can't read test because " + x.getMessage());
        }
    }
}
