package com.evalvis.database;

import java.util.Optional;

public interface FileDbClient {
    void createTable(String tableName);

    void put(String tableName, String key, String value);

    Optional<String> get(String tableName, String key);
}
