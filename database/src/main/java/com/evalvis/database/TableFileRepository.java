package com.evalvis.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

final class TableFileRepository {
    private final Path rootDirectory;

    TableFileRepository(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    void createTable(String tableName) {
        Path tablePath = tablePath(tableName);
        ensureRootDirectoryExists();
        if (Files.exists(tablePath)) {
            return;
        }
        try {
            Files.createFile(tablePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create table " + tableName, exception);
        }
    }

    void append(String tableName, String key, String value, long version) {
        Path tablePath = tablePath(tableName);
        if (!Files.exists(tablePath)) {
            throw new TableNotFoundException(tableName);
        }
        String record = JsonLineCodec.encode(key, value, version) + System.lineSeparator();
        try {
            Files.writeString(tablePath, record, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write key " + key + " in table " + tableName, exception);
        }
    }

    Optional<JsonLineRecord> findRecord(String tableName, String key) {
        Path tablePath = tablePath(tableName);
        if (!Files.exists(tablePath)) {
            throw new TableNotFoundException(tableName);
        }
        try {
            List<String> lines = Files.readAllLines(tablePath);
            for (int index = lines.size() - 1; index >= 0; index--) {
                Optional<JsonLineRecord> parsed = JsonLineCodec.decode(lines.get(index));
                if (parsed.isPresent() && parsed.get().key().equals(key)) {
                    return parsed;
                }
            }
            return Optional.empty();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read table " + tableName, exception);
        }
    }

    java.util.List<String> listTables() {
        try {
            if (!Files.exists(rootDirectory)) return java.util.Collections.emptyList();
            return Files.list(rootDirectory)
                .filter(p -> p.toString().endsWith(".jsonl"))
                .map(p -> p.getFileName().toString().replace(".jsonl", ""))
                .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            return java.util.Collections.emptyList();
        }
    }

    java.util.Map<String, String> findAll(String tableName) {
        Path tablePath = tablePath(tableName);
        if (!Files.exists(tablePath)) {
            throw new TableNotFoundException(tableName);
        }
        try {
            List<String> lines = Files.readAllLines(tablePath);
            java.util.Map<String, String> results = new java.util.HashMap<>();
            for (String line : lines) {
                Optional<JsonLineRecord> parsed = JsonLineCodec.decode(line);
                parsed.ifPresent(record -> results.put(record.key(), record.value()));
            }
            return results;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read table " + tableName, exception);
        }
    }

    private Path tablePath(String tableName) {
        return rootDirectory.resolve(tableName + ".jsonl");
    }

    private void ensureRootDirectoryExists() {
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create root directory " + rootDirectory, exception);
        }
    }
}
