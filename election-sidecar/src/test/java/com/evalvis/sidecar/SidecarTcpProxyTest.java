package com.evalvis.sidecar;

import com.evalvis.database.FileDbTcpServer;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidecarTcpProxyTest {

    @Test
    void readCommandForwardsToDbAndDoesNotReplicate() throws Exception {
        int dbPort = freePort();
        int proxyPort = freePort();
        SpyReplicator spy = new SpyReplicator();

        try (FileDbTcpServer db = new FileDbTcpServer(dbPort, Files.createTempDirectory("sidecar-test"));
             SidecarTcpProxy proxy = new SidecarTcpProxy("127.0.0.1", dbPort, proxyPort, () -> true, spy)) {
            db.start();
            proxy.start();

            sendCommand(proxyPort, "CREATE_TABLE items");
            spy.commands.clear();

            String response = sendCommand(proxyPort, "GET items missingKey");
            assertEquals("NOT_FOUND", response);
            assertTrue(spy.commands.isEmpty(), "GET must not be replicated");
        }
    }

    @Test
    void writeCommandAsLeaderForwardsAndReplicates() throws Exception {
        int dbPort = freePort();
        int proxyPort = freePort();
        SpyReplicator spy = new SpyReplicator();

        try (FileDbTcpServer db = new FileDbTcpServer(dbPort, Files.createTempDirectory("sidecar-test"));
             SidecarTcpProxy proxy = new SidecarTcpProxy("127.0.0.1", dbPort, proxyPort, () -> true, spy)) {
            db.start();
            proxy.start();

            assertEquals("OK", sendCommand(proxyPort, "CREATE_TABLE items"));
            assertEquals("OK", sendCommand(proxyPort, "PUT items key1 value1"));

            assertEquals(List.of("CREATE_TABLE items", "PUT items key1 value1"), spy.commands);
        }
    }

    @Test
    void writeCommandAsFollowerForwardsButDoesNotReplicate() throws Exception {
        int dbPort = freePort();
        int proxyPort = freePort();
        SpyReplicator spy = new SpyReplicator();

        try (FileDbTcpServer db = new FileDbTcpServer(dbPort, Files.createTempDirectory("sidecar-test"));
             SidecarTcpProxy proxy = new SidecarTcpProxy("127.0.0.1", dbPort, proxyPort, () -> false, spy)) {
            db.start();
            proxy.start();

            assertEquals("OK", sendCommand(proxyPort, "CREATE_TABLE items"));
            assertEquals("OK", sendCommand(proxyPort, "PUT items key1 value1"));

            assertTrue(spy.commands.isEmpty(), "Follower must not replicate");
        }
    }

    private String sendCommand(int port, String command) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.write(command);
            writer.newLine();
            writer.flush();
            return reader.readLine();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    static class SpyReplicator implements Replicator {
        final List<String> commands = new ArrayList<>();

        @Override
        public void replicateAsync(String command) {
            commands.add(command);
        }
    }
}
