package com.evalvis.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileDbTcpServerTest {

    @TempDir
    Path tempDir;

    private FileDbTcpServer server;

    @BeforeEach
    void setUp() {
        server = new FileDbTcpServer(0, tempDir);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void createTablePutAndGetWorkOverTcp() throws Exception {
        assertEquals("OK", send("CREATE_TABLE users"));
        assertEquals("OK", send("PUT users alice v1"));
        assertEquals("VALUE v1", send("GET users alice"));
    }

    @Test
    void getMissingKeyReturnsNotFound() throws Exception {
        send("CREATE_TABLE users");

        assertEquals("NOT_FOUND", send("GET users missing"));
    }

    @Test
    void putMissingTableReturnsError() throws Exception {
        assertEquals("ERROR table_not_found", send("PUT users alice v1"));
    }

    private String send(String command) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port());
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.write(command);
            writer.newLine();
            writer.flush();
            return reader.readLine();
        }
    }
}
