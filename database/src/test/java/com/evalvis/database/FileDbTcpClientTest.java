package com.evalvis.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDbTcpClientTest {

    @TempDir
    Path tempDir;

    private FileDbTcpServer server;
    private FileDbClient client;

    @BeforeEach
    void setUp() {
        server = new FileDbTcpServer(0, tempDir);
        server.start();
        client = new TcpFileDbClient("127.0.0.1", server.port());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void createTablePutAndGetUsingClient() {
        client.createTable("users");
        client.put("users", "alice", "v1");

        assertEquals(Optional.of("v1"), client.get("users", "alice"));
    }

    @Test
    void getReturnsEmptyForMissingKey() {
        client.createTable("users");

        assertEquals(Optional.empty(), client.get("users", "missing"));
    }

    @Test
    void putThrowsWhenTableMissing() {
        assertThrows(TableNotFoundException.class, () -> client.put("users", "alice", "v1"));
    }
}
