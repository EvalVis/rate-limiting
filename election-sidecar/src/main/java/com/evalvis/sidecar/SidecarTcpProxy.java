package com.evalvis.sidecar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class SidecarTcpProxy implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SidecarTcpProxy.class.getName());

    private final String localDbHost;
    private final int localDbPort;
    private final ServerSocket serverSocket;
    private final LeaderState leaderState;
    private final Replicator replicator;
    private final ExecutorService clientExecutor;
    private final AtomicBoolean running;
    private Thread acceptThread;

    public SidecarTcpProxy(String localDbHost, int localDbPort, int proxyPort,
                           LeaderState leaderState, Replicator replicator) {
        try {
            this.serverSocket = new ServerSocket(proxyPort);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open proxy socket on port " + proxyPort, e);
        }
        this.localDbHost = localDbHost;
        this.localDbPort = localDbPort;
        this.leaderState = leaderState;
        this.replicator = replicator;
        this.clientExecutor = Executors.newCachedThreadPool();
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        acceptThread = new Thread(this::acceptLoop);
        acceptThread.start();
        LOG.info("Sidecar proxy started on port " + serverSocket.getLocalPort()
                + " → " + localDbHost + ":" + localDbPort);
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    String applyToLocalDb(String command) throws IOException {
        try (Socket socket = new Socket(localDbHost, localDbPort);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.write(command);
            writer.newLine();
            writer.flush();
            return reader.readLine();
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                clientExecutor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.warning("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket activeClient = client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(activeClient.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(activeClient.getOutputStream()))) {
            String command = reader.readLine();
            if (command == null) return;
            String response = applyToLocalDb(command);
            if (isWriteCommand(command) && leaderState.isLeader()) {
                replicator.replicateAsync(command);
            }
            writer.write(response != null ? response : "ERROR db_unavailable");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            LOG.warning("Client handling failed: " + e.getMessage());
        }
    }

    private boolean isWriteCommand(String command) {
        return command.startsWith("PUT ") || command.startsWith("CREATE_TABLE ");
    }

    @Override
    public void close() {
        running.set(false);
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOG.warning("Failed to close proxy socket: " + e.getMessage());
        }
        if (acceptThread != null) {
            try {
                acceptThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        clientExecutor.shutdownNow();
    }
}
