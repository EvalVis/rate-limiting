package com.evalvis.database;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class FileDb {
    private final TableFileRepository tableFileRepository;

    public FileDb() {
        this(Path.of(System.getProperty("user.home"), "filedb"));
    }

    public FileDb(Path rootDirectory) {
        this.tableFileRepository = new TableFileRepository(rootDirectory);
    }

    public void createTable(String tableName) {
        validateValue("tableName", tableName);
        tableFileRepository.createTable(tableName);
    }

    public void put(String tableName, String key, String value) {
        put(tableName, key, value, 0L);
    }

    public void put(String tableName, String key, String value, long version) {
        validateValue("tableName", tableName);
        validateValue("key", key);
        Objects.requireNonNull(value, "value must not be null");
        tableFileRepository.append(tableName, key, value, version);
    }

    public Optional<String> get(String tableName, String key) {
        return getRecord(tableName, key).map(JsonLineRecord::value);
    }

    public Optional<JsonLineRecord> getRecord(String tableName, String key) {
        validateValue("tableName", tableName);
        validateValue("key", key);
        return tableFileRepository.findRecord(tableName, key);
    }

    public java.util.Map<String, String> findAll(String tableName) {
        validateValue("tableName", tableName);
        return tableFileRepository.findAll(tableName);
    }

    public java.util.List<String> listTables() {
        return tableFileRepository.listTables();
    }

    private void validateValue(String fieldName, String value) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
