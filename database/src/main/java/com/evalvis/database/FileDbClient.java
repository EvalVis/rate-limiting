package com.evalvis.database;

import java.util.Optional;

public interface FileDbClient {
    void createTable(String tableName);

    void put(String tableName, String key, String value);
    
    void putVersioned(String tableName, String key, String value, long version);

    Optional<String> get(String tableName, String key);
    
    Optional<JsonLineRecord> getRecord(String tableName, String key);

    java.util.Map<String, String> listAll(String tableName);

    java.util.List<String> listTables();
}
