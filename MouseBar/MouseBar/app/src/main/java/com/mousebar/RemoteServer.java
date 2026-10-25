package com.mousebar;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class RemoteServer {

    public interface CommandHandler {
        String run(String cmd);
    }

    private final AssetManager assets;
    private final int port;
    private final String pin;
    private final CommandHandler handler;
    private ServerSocket server;
    private volatile boolean running;

    public RemoteServer(AssetManager assets, int port, String pin, CommandHandler handler) {
        this.assets = assets;
        this.port = port;
        this.pin = pin;
        this.handler = handler;
    }

    public void start() {
        running = true;
        new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }).start();
    }

    public void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private void loop() {
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(port));
            server = ss;
            while (running) {
                final Socket c = ss.accept();
                new Thread(new Runnable() {
                    public void run() {
                        handle(c);
                    }
                }).start();
            }
        } catch (IOException e) {
            // stopped, or the port is already in use
        }
    }

    private void handle(Socket c) {
        try {
            c.setSoTimeout(5000);
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"));
            String first = r.readLine();
            if (first == null) return;
            String line;
            while ((line = r.readLine()) != null && line.length() > 0) {
                // skip headers
            }
            String[] parts = first.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);

            String prefix = "/" + pin;
            String body;
            String type = "text/plain; charset=utf-8";
            int code = 200;
            if (path.equals(prefix) || path.equals(prefix + "/")) {
                body = readAsset("remote.html");
                type = "text/html; charset=utf-8";
            } else if (path.startsWith(prefix + "/cmd/")) {
                body = handler.run(path.substring(prefix.length() + 5));
            } else {
                code = 404;
                body = "Wrong address. Use the address shown on the TV.";
            }

            byte[] data = body.getBytes("UTF-8");
            String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found")
                    + "\r\nContent-Type: " + type
                    + "\r\nContent-Length: " + data.length
                    + "\r\nCache-Control: no-store"
                    + "\r\nConnection: close\r\n\r\n";
            OutputStream o = c.getOutputStream();
            o.write(head.getBytes("UTF-8"));
            o.write(data);
            o.flush();
        } catch (IOException e) {
            // client went away
        } finally {
            try {
                c.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private String readAsset(String name) {
        try {
            InputStream in = assets.open(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "Remote page missing";
        }
    }
}
