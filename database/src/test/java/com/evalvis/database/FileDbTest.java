package com.evalvis.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDbTest {

    @TempDir
    Path tempDir;

    @Test
    void createTableCreatesFileInRootDirectory() {
        FileDb fileDb = new FileDb(tempDir);

        fileDb.createTable("users");

        assertTrue(Files.exists(tempDir.resolve("users.jsonl")));
    }

    @Test
    void putAndGetReturnsLatestWrittenValue() {
        FileDb fileDb = new FileDb(tempDir);
        fileDb.createTable("users");

        fileDb.put("users", "alice", "v1");
        fileDb.put("users", "alice", "v2");

        assertEquals(Optional.of("v2"), fileDb.get("users", "alice"));
    }

    @Test
    void getReturnsEmptyWhenKeyDoesNotExist() {
        FileDb fileDb = new FileDb(tempDir);
        fileDb.createTable("users");
        fileDb.put("users", "alice", "v1");

        assertEquals(Optional.empty(), fileDb.get("users", "missing"));
    }

    @Test
    void putFailsWhenTableDoesNotExist() {
        FileDb fileDb = new FileDb(tempDir);

        assertThrows(TableNotFoundException.class, () -> fileDb.put("users", "alice", "v1"));
    }

    @Test
    void getFailsWhenTableDoesNotExist() {
        FileDb fileDb = new FileDb(tempDir);

        assertThrows(TableNotFoundException.class, () -> fileDb.get("users", "alice"));
    }

    @Test
    void createdTableFileContainsJsonLineRecordAfterWrite() throws Exception {
        FileDb fileDb = new FileDb(tempDir);
        fileDb.createTable("users");

        fileDb.put("users", "alice", "v1");

        String content = Files.readString(tempDir.resolve("users.jsonl"));
        assertTrue(content.contains("\"key\":\"alice\""));
        assertTrue(content.contains("\"value\":\"v1\""));
        assertFalse(content.isBlank());
    }

    @Test
    void createTableIsIdempotent() {
        FileDb fileDb = new FileDb(tempDir);

        fileDb.createTable("users");
        fileDb.createTable("users");

        assertTrue(Files.exists(tempDir.resolve("users.jsonl")));
    }
}
