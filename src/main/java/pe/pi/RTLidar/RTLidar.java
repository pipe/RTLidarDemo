package pe.pi.RTLidar;

import com.ipseorama.slice.ORTC.RTCDtlsPacket;
import com.ipseorama.slice.ORTC.RTCEventData;
import com.ipseorama.slice.ORTC.RTCIceCandidate;
import com.ipseorama.slice.ORTC.RTCIceCandidatePair;
import com.ipseorama.slice.ORTC.RTCIceTransport;
import com.phono.srtplight.Log;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import org.bouncycastle.tls.DTLSTransport;
import pe.pi.RTLidar.util.AnswerMaker;
import pe.pi.RTLidar.util.CandidateTransport;
import pe.pi.RTLidar.util.OfferParser;

/**
 *
 * @author thp
 */
public abstract class RTLidar {

    ICE slice;
    DTLS dtls;
    String ffp;
    SecureRandom random = new SecureRandom();
    String session;
    Boolean gathering = false;
    static int port = 2368;
    OfferParser op;
    final Object sliceLock = new Object();

    public RTLidar(String offer) {
        Log.info("Offer: " + offer);
        op = new OfferParser(offer);
        ffp = op.getFingerprint();
        session = op.getSessionId();
        try {
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
                void onDisconnected(RTCIceTransport trans, RTCIceCandidatePair scp) {
                    Log.info("ICE was connected to server at" + scp.getFarIp());
                    dtls.stop();
                    stopped();
                }

                @Override
                void onConnected(RTCIceTransport trans, RTCIceCandidatePair scp) {
                    Log.info("ICE has connected to server at" + scp.getFarIp());
                    trans.onRTP = (rtppkt) -> {
                    };
                    final CandidateTransport cdt = new CandidateTransport(getTransport());
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
                public void onReady(DTLSTransport trans) {
                    Log.info("DTLS complete.");
                    startSendingTo(trans);
                }
            };
            dtls.mkCertNKey();

        } catch (Exception ex) {
            Log.error("can't start " + ex);
        }

    }
    DTLSTransport sendto;

    abstract void stopped();
    
    public void startSendingTo(DTLSTransport trans) {
        sendto = trans;
        Runnable recver = () -> {
            byte[] bytes = new byte[1500];
            boolean ok = true;
            while (ok) {
                try {
                    int got = sendto.receive(bytes, 0, bytes.length, 2000);
                    if (got > 0) {
                        byte[] go = new byte[got];
                        System.arraycopy(bytes, 0, go, 0, got);
                        Log.info("got message: " + new String(go));
                    }
                } catch (IOException ex) {
                    Log.info("cant recv from DTLS" + ex);
                    ok = false;
                }
            }
            dtls.stop();
            sendto = null;
            if (slice.ice != null){
                slice.ice.stop();
            }
        };

        new Thread(recver)
                .start();

    }

    String makeAnswer() throws Exception {
        String ans = "{error:}";
        gathering = true;

        slice.gather();

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
            String sessionId = op.getSessionId();

            slice.connect(rufrag, rupass, rcandy);

            ans = AnswerMaker.makeAnswer(cs, ufrag, upass, fingerprint, sessionId);
            Log.info("Ans : " + ans);
        } else {
            Log.error("cs is null ");
        }
        return ans;
    }

    void sendTo(byte[] mess, int i, int length) throws IOException {
        if (sendto != null){
            sendto.send(mess, i, length);
        }
    }

    String getSessionId() {
        return session;
    }
}
