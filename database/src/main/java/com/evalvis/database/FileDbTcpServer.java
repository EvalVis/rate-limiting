package com.evalvis.database;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FileDbTcpServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final CommandProcessor commandProcessor;
    private final ExecutorService clientExecutor;
    private final AtomicBoolean running;
    private Thread acceptThread;

    public FileDbTcpServer(int port, Path rootDirectory) {
        this(port, new FileDbCommandProcessor(new FileDb(rootDirectory)));
    }

    public FileDbTcpServer(int port, CommandProcessor commandProcessor) {
        try {
            this.serverSocket = new ServerSocket(port);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to open server socket", exception);
        }
        this.commandProcessor = commandProcessor;
        this.clientExecutor = Executors.newCachedThreadPool();
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        acceptThread = new Thread(this::acceptLoop);
        acceptThread.start();
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    @Override
    public void close() {
        running.set(false);
        try {
            serverSocket.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close server socket", exception);
        }
        if (acceptThread != null) {
            try {
                acceptThread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping server", exception);
            }
        }
        clientExecutor.shutdownNow();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                clientExecutor.submit(() -> handleClient(client));
            } catch (IOException exception) {
                if (running.get()) {
                    throw new IllegalStateException("Failed to accept connection", exception);
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket activeClient = client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(activeClient.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(activeClient.getOutputStream()))) {
            String line = reader.readLine();
            String response = commandProcessor.process(line);
            writer.write(response);
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to handle client", exception);
        }
    }
}
