package com.evalvis.database;

import java.util.Optional;

final class FileDbCommandProcessor implements CommandProcessor {
    private final FileDb fileDb;

    FileDbCommandProcessor(FileDb fileDb) {
        this.fileDb = fileDb;
    }

    @Override
    public String process(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return "ERROR invalid_command";
        }
        if (commandLine.startsWith("CREATE_TABLE ")) {
            return createTable(commandLine);
        }
        if (commandLine.startsWith("PUT ")) {
            return put(commandLine);
        }
        if (commandLine.startsWith("PUT_V ")) {
            return putVersioned(commandLine);
        }
        if (commandLine.startsWith("GET ")) {
            return get(commandLine);
        }
        if (commandLine.startsWith("GET_V ")) {
            return getVersioned(commandLine);
        }
        if (commandLine.startsWith("LIST_ALL ")) {
            return listAll(commandLine);
        }
        if (commandLine.equals("LIST_TABLES")) {
            return listTables();
        }
        return "ERROR invalid_command";
    }

    private String listTables() {
        java.util.List<String> tables = fileDb.listTables();
        if (tables.isEmpty()) return "EMPTY";
        return "TABLES " + String.join(" ", tables);
    }

    private String listAll(String commandLine) {
        String[] tokens = commandLine.trim().split("\\s+");
        if (tokens.length != 2) {
            return "ERROR invalid_command";
        }
        try {
            java.util.Map<String, String> all = fileDb.findAll(tokens[1]);
            StringBuilder sb = new StringBuilder("DATA ");
            all.forEach((k, v) -> sb.append(k).append(":").append(v).append(" "));
            return sb.toString().trim();
        } catch (TableNotFoundException exception) {
            return "ERROR table_not_found";
        }
    }

    private String createTable(String commandLine) {
        String[] tokens = commandLine.trim().split("\\s+");
        if (tokens.length != 2) {
            return "ERROR invalid_command";
        }
        fileDb.createTable(tokens[1]);
        return "OK";
    }

    private String put(String commandLine) {
        String[] tokens = commandLine.split("\\s+", 4);
        if (tokens.length != 4) {
            return "ERROR invalid_command";
        }
        try {
            fileDb.put(tokens[1], tokens[2], tokens[3]);
            return "OK";
        } catch (TableNotFoundException exception) {
            return "ERROR table_not_found";
        }
    }

    private String putVersioned(String commandLine) {
        // PUT_V <tableName> <key> <version> <value>
        String[] tokens = commandLine.split("\\s+", 5);
        if (tokens.length != 5) {
            return "ERROR invalid_command";
        }
        try {
            long version = Long.parseLong(tokens[3]);
            fileDb.put(tokens[1], tokens[2], tokens[4], version);
            return "OK";
        } catch (NumberFormatException e) {
            return "ERROR invalid_version";
        } catch (TableNotFoundException exception) {
            return "ERROR table_not_found";
        }
    }

    private String get(String commandLine) {
        String[] tokens = commandLine.trim().split("\\s+");
        if (tokens.length != 3) {
            return "ERROR invalid_command";
        }
        try {
            Optional<String> value = fileDb.get(tokens[1], tokens[2]);
            return value.map(current -> "VALUE " + current).orElse("NOT_FOUND");
        } catch (TableNotFoundException exception) {
            return "ERROR table_not_found";
        }
    }

    private String getVersioned(String commandLine) {
        // GET_V <tableName> <key>
        String[] tokens = commandLine.trim().split("\\s+");
        if (tokens.length != 3) {
            return "ERROR invalid_command";
        }
        try {
            Optional<JsonLineRecord> record = fileDb.getRecord(tokens[1], tokens[2]);
            return record.map(r -> "VALUE_V " + r.version() + " " + r.value()).orElse("NOT_FOUND");
        } catch (TableNotFoundException exception) {
            return "ERROR table_not_found";
        }
    }
}
