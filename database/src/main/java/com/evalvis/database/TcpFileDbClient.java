package com.evalvis.database;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Optional;

public final class TcpFileDbClient implements FileDbClient {
    private final String host;
    private final int port;

    public TcpFileDbClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void createTable(String tableName) {
        String response = send("CREATE_TABLE " + tableName);
        requireOk(response, tableName);
    }

    @Override
    public void put(String tableName, String key, String value) {
        String response = send("PUT " + tableName + " " + key + " " + value);
        requireOk(response, tableName);
    }

    @Override
    public Optional<String> get(String tableName, String key) {
        String response = send("GET " + tableName + " " + key);
        if ("NOT_FOUND".equals(response)) {
            return Optional.empty();
        }
        if ("ERROR table_not_found".equals(response)) {
            throw new TableNotFoundException(tableName);
        }
        if (response.startsWith("VALUE ")) {
            return Optional.of(response.substring("VALUE ".length()));
        }
        throw new IllegalStateException("Unexpected database response: " + response);
    }

    @Override
    public java.util.Map<String, String> listAll(String tableName) {
        String response = send("LIST_ALL " + tableName);
        if ("ERROR table_not_found".equals(response)) {
            throw new TableNotFoundException(tableName);
        }
        java.util.Map<String, String> result = new java.util.HashMap<>();
        if (response.startsWith("DATA ")) {
            String data = response.substring("DATA ".length());
            String[] pairs = data.split("\\s+");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    result.put(kv[0], kv[1]);
                }
            }
        }
        return result;
    }

    private void requireOk(String response, String tableName) {
        if ("OK".equals(response)) {
            return;
        }
        if ("ERROR table_not_found".equals(response)) {
            throw new TableNotFoundException(tableName);
        }
        throw new IllegalStateException("Unexpected database response: " + response);
    }

    private String send(String command) {
        System.out.println("Sending to DB [" + host + ":" + port + "]: " + command);
        try (Socket socket = new Socket(host, port);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.write(command);
            writer.newLine();
            writer.flush();
            String response = reader.readLine();
            System.out.println("Received from DB [" + host + ":" + port + "]: " + response);
            if (response == null) {
                throw new IllegalStateException("Empty database response");
            }
            return response;
        } catch (IOException exception) {
            System.err.println("DB Error: " + exception.getMessage());
            throw new IllegalStateException("Database unavailable", exception);
        }
    }
}
