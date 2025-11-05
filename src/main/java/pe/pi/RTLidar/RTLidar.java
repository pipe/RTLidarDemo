
package pe.pi.RTLidar;

import com.ipseorama.slice.ORTC.RTCDtlsPacket;
import com.ipseorama.slice.ORTC.RTCEventData;
import com.ipseorama.slice.ORTC.RTCIceCandidate;
import com.ipseorama.slice.ORTC.RTCIceCandidatePair;
import com.ipseorama.slice.ORTC.RTCIceTransport;
import com.ipseorama.slice.ORTC.RTCRtpPacket;
import com.phono.srtplight.Log;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Properties;
import pe.pi.RTLidar.util.AnswerMaker;
import pe.pi.RTLidar.util.CandidateTransport;
import pe.pi.RTLidar.util.OfferParser;

/**
 *
 * @author thp
 */
public class RTLidar {

    ICE slice;
    DTLS dtls;
    String ffp;
    SecureRandom random = new SecureRandom();
    private Long vssrc;
    private Long assrc;
    Boolean gathering = false;
    final Object sliceLock = new Object();

    public RTLidar() {

        try {
            vssrc = (long) Math.abs(random.nextInt()); // remove this if you want to run audio only
            assrc = (long) Math.abs(random.nextInt()); // remove this if you want to run video only

            slice = new ICE(random) {
                @Override
                void onGathered() {
                    Log.info("Got local ip address(es)");
                    synchronized (sliceLock) {
                        sliceLock.notify();
                        gathering = false;
                    }
                }

                @Override
                void onConnected(RTCIceTransport trans, RTCIceCandidatePair scp) {
                    Log.info("ICE has connected to server at" + scp.getFarIp());
                    trans.onRTP = (rtppkt) -> {
                        if (rtppkt instanceof RTCRtpPacket) {
                            //rtp.inbound((RTCRtpPacket) rtppkt);
                        }
                    };
                    final CandidateTransport cdt = new CandidateTransport(getTransport());
                    //rtp.setCandidateTransport(cdt);
                    trans.onDtls = (RTCEventData pkt) -> {
                        if (pkt instanceof RTCDtlsPacket) {
                            byte data[] = ((RTCDtlsPacket) pkt).data;
                            cdt.enqueue(data);
                        }
                    };
                    dtls.start(cdt, ffp);
                }
            };
            dtls = new DTLS(random) {
                @Override
                protected String makeCn() {
                    String ret = "RTCTransport-lidar";
                    return ret;
                }

                @Override
                public void onReady() {
                    Log.info("DTLS complete.");
                    Properties[] props = this.extractCryptoProps();
                }
            };
            dtls.mkCertNKey();
        } catch (Exception ex) {
            Log.error("can't start " + ex);
        }

    }

    String makeAnswer(String offer) throws Exception {
        gathering = true;
        slice.gather();
        OfferParser op = new OfferParser(offer);
        
        ArrayList<RTCIceCandidate> cs = null;
        synchronized (sliceLock) {
            sliceLock.wait(20000);
            cs = slice.getCandidates();
        }
        if (cs != null) {
            String ufrag = slice.getLfrag();
            String upass = slice.getLpass();

            String fingerprint = dtls.getPrint(true);
            String rufrag = op.getUfrag();
            String rupass = op.getUpass();
            String[] rcandy = op.getRcandy();
            
            slice.connect(rufrag, rupass, rcandy);

            return AnswerMaker.makeAnswer(cs, ufrag, upass, vssrc, assrc, fingerprint);
        }
        return "";
    }
}
