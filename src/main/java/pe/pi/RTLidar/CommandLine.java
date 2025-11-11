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
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import pe.pi.RTLidar.util.OAServer;

/**
 *
 * @author thp
 */
public class CommandLine {

    static final Path CWD = Path.of("./static").toAbsolutePath();

    public static void main(String args[]) throws IOException {
        Log.setLevel(Log.INFO);
        java.security.Security.insertProviderAt(new BouncyCastleProvider(), 0);

        var fileHandler = SimpleFileServer.createFileHandler(CWD);

        var LOOPBACK_ADDR = new InetSocketAddress("localhost", 8001);
        Predicate<Request> IS_POST = r -> r.getRequestMethod().equals("POST");

        var oas = new OAServer() {
            @Override
            public String makeAnswer(String offer) {
                String ret = null;
                try {
                    RTLidar li = new RTLidar();
                    ret = li.makeAnswer(offer);
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

    }
}
