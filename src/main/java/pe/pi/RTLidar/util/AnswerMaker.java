/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.RTLidar.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ipseorama.slice.ORTC.RTCIceCandidate;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author thp
 */
public class AnswerMaker {

    public static String makeAnswer(ArrayList<RTCIceCandidate> cs, String ufrag, String upass, Long vssrc, Long assrc, String fingerprint,String sessionId) {
        GsonBuilder builder = new GsonBuilder();
        var gson = builder.create();
        var ans = new NegotiationData.Answer();
        ans.candidates = cs.stream().map((cano) -> { 
            var ret = new NegotiationData.Candidate();
            ret.address = cano.getIp();
            ret.password = upass;
            ret.port = (int)cano.getPort();
            ret.type = cano.getType().toString();
            ret.usernameFragment = ufrag;
            return ret;
        }).toArray(NegotiationData.Candidate[]::new);
        ans.dtls = new NegotiationData.DTLSInfo();
        ans.dtls.fingerprint =Arrays.stream(fingerprint.split(":")).map((hexS)-> Integer.parseInt(hexS, 16)).toArray(Integer[]::new);
        ans.dtls.fingerprintDigestAlgorithm= "sha-256";
        ans.sessionId = sessionId;
        var ret = gson.toJson(ans);
        return ret;
    }

}
