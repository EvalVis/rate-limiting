package com.evalvis.database;

public final class TableNotFoundException extends RuntimeException {
    public TableNotFoundException(String tableName) {
        super("Table does not exist: " + tableName);
    }
}
