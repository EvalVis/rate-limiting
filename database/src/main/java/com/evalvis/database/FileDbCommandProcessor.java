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
        if (commandLine.startsWith("GET ")) {
            return get(commandLine);
        }
        return "ERROR invalid_command";
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
}
