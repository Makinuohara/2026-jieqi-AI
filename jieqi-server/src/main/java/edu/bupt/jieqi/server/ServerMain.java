package edu.bupt.jieqi.server;

import java.net.InetSocketAddress;

public final class ServerMain {
    public static final int DEFAULT_PORT = 8887;

    private ServerMain() {
    }

    public static void main(String[] args) {
        int port = args.length == 0 ? DEFAULT_PORT : Integer.parseInt(args[0]);
        new JieqiWebSocketServer(new InetSocketAddress("0.0.0.0", port)).start();
    }
}

