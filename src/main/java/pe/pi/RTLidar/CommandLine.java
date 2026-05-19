/*
    A WHIP client for raspberry pi
    Copyright (C) 2021  Tim Panton

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package pe.pi.RTLidar;

import com.phono.srtplight.Log;
import com.sun.net.httpserver.HttpHandlers;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Request;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import static pe.pi.RTLidar.RTLidar.port;
import pe.pi.RTLidar.richbeam.UDPListener;
import pe.pi.RTLidar.util.OAServer;

/**
 *
 * @author thp
 */
public class CommandLine {

    static final Path CWD = Path.of("./static").toAbsolutePath();
    static final SecureRandom random = new SecureRandom();

    public static void main(String args[]) throws IOException {
        Log.setLevel(Log.INFO);
        java.security.Security.insertProviderAt(new BouncyCastleProvider(), 0);

        var fileHandler = SimpleFileServer.createFileHandler(CWD);

        Map<String, RTLidar> clients = Collections.synchronizedMap(new HashMap());

        var LOOPBACK_ADDR = new InetSocketAddress("localhost", 8001);
        Predicate<Request> IS_POST = r -> r.getRequestMethod().equals("POST");
        try {
            DTLS.mkCertNKey();
        } catch (IOException | InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | NoSuchProviderException | SignatureException | CertificateException x){
            Log.error("Can't make cert" + x.getMessage());
            System.exit(-1);
        }
        var oas = new OAServer() {
            @Override
            public String makeAnswer(String offer) {
                String ret = null;
                try {
                    RTLidar li = new RTLidar(offer) {
                        void stopped() {
                            Log.info("delete client session " + session);
                            clients.remove(session);
                        }
                    };
                    String session = li.getSessionId();
                    Log.info("added new client session " + session);
                    ret = li.makeAnswer();

                    clients.put(session, li);

                } catch (Exception ex) {
                    Log.error("Can't make answer" + ex.getMessage());
                }
                return ret;
            }

        };

        var handler = HttpHandlers.handleOrElse(IS_POST, oas, fileHandler);
        var outputFilter = SimpleFileServer.createOutputFilter(System.out, OutputLevel.VERBOSE);
        var server = HttpServer.create(LOOPBACK_ADDR, 10, "/", handler, outputFilter);
        server.start();

        Log.info(" Server started on port 8001");

        try {
            UDPListener l = new UDPListener(port) {
                @Override
                public void hark(DatagramPacket p) {
                    byte[] mess = p.getData();
                    var viewers = clients.values();
                    for (var v : viewers) {
                        try {
                            v.sendTo(mess, 0, mess.length);
                        } catch (IOException ex) {
                            Log.warn("cant send to DTLS" + ex);
                        }
                    }

                }
            };
            l.start();

        } catch (Exception ex) {
            Log.error("cant connect to Lidar " + ex);
        }

    }
}
