package com.acme.export.infra;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Database {

    private final String host;
    private final int port;

    public Database(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/exports";
    }

    /** Abre uma ligacao TCP ao servidor e fecha-a logo. Serve de verificacao de que a base esta de pe. */
    public void checkReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reach the database at " + host + ":" + port, e);
        }
    }
}
