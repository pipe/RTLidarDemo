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
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import pe.pi.RTLidar.util.OAServer;

/**
 *
 * @author thp
 */
public class CommandLine {

    public static void main(String args[]) throws IOException {
        Log.setLevel(Log.DEBUG);
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8001), 0);
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);

        RTLidar li = new RTLidar();
        
        var oas = new OAServer(){
            @Override
            public String makeAnswer(String offer) {
                String ret = null;
                try {
                    ret = li.makeAnswer(offer);
                } catch (Exception ex) {
                    Log.error("Can't make answer"+ ex.getMessage());
                }
                return ret;
            }
            
            
        };
        
        server.createContext("/demo", oas);

        server.setExecutor(threadPoolExecutor);

        server.start();

        Log.info(" Server started on port 8001");

    }
}
