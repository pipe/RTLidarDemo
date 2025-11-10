/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pe.pi.RTLidar.richbeam;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author thp
 */
public abstract class UDPListener {

    DatagramSocket ds;
    Thread listener;
    final static int PSIZE = 1206;
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    public UDPListener(int port) throws SocketException {
        ds = new DatagramSocket(port);
        long[] sent = new long[1];
        long then = System.currentTimeMillis();
        ds.setSoTimeout(1000);
        exec.scheduleWithFixedDelay(() ->{
            long now = System.currentTimeMillis();
            long rate = (sent[0] *1000)/(now-then);
            System.err.println("recv rate is "+rate+" packets/s");
        }, 10, 10, TimeUnit.SECONDS);
        listener = new Thread(() -> {
            System.err.println("Starting to listen on " + ds.getLocalSocketAddress());
            int n = 0;
            while (listener != null) {
                byte[] data = new byte[PSIZE];
                var p = new DatagramPacket(data, PSIZE);
                try {
                    ds.receive(p);
                    sent[0]++;
                    exec.submit(() -> {
                        hark(p);
                    });
                } catch (SocketTimeoutException ex) {
                    System.err.println("Tick " + n);
                    n++;
                } catch (IOException x) {
                    System.err.println("quitting because Exception " + x.getMessage());
                    listener = null;
                }
            }
            System.err.println("Stopped listening");
        }
        );
    }

    public void start() {
        listener.start();
    }

    public void stop() {
        listener = null;
    }

    public abstract void hark(DatagramPacket p);
}
