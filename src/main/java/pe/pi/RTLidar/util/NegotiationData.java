package pe.pi.RTLidar.util;

/**
 *
 * @author thp
 */
public class NegotiationData {

    static long priority = 1;
    static long foundation = 1;

    public static class Offer {

        Candidate[] candidates;
        DTLSInfo dtls;
    }

    public static class Answer {

        Candidate[] candidates;
        DTLSInfo dtls;
    }

    public static class Candidate {

        String usernameFragment;
        String password;
        String address;
        Integer port;
        String type;

        @Override
        public String toString() {
            //        // candidate:1365833797 1 udp 2113939711 2a01:348:339::57a:65d1:a0b4:a83 51309 typ host generation 0
            StringBuilder can = new StringBuilder("candidate:" + foundation + " 1 udp " + priority + " ");
            can.append(address).append(" ").append(port.toString()).append(" typ ").append(type);

            foundation++;
            priority++;

            return can.toString();
        }
    }

    public static class DTLSInfo {

        String fingerprintDigestAlgorithm;
        Integer[] fingerprint;

        public String getFingerprint() {
            StringBuilder ret = new StringBuilder();
            if (fingerprint != null) {
                for (var f : fingerprint) {
                    ret.append(String.format("%02X", (0xFF & f)));
                }
            }
            return ret.toString();
        }
    }
}
