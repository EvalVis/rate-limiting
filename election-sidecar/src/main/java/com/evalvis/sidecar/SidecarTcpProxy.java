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
    private final String consistencyMode;
    private final int quorumW;
    private Thread acceptThread;

    public SidecarTcpProxy(String localDbHost, int localDbPort, int proxyPort,
                           LeaderState leaderState, Replicator replicator) {
        this(localDbHost, localDbPort, proxyPort, leaderState, replicator, "EVENTUAL", 1);
    }

    public SidecarTcpProxy(String localDbHost, int localDbPort, int proxyPort,
                           LeaderState leaderState, Replicator replicator,
                           String consistencyMode, int quorumW) {
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
        this.consistencyMode = consistencyMode != null ? consistencyMode.toUpperCase() : "EVENTUAL";
        this.quorumW = quorumW;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        acceptThread = new Thread(this::acceptLoop);
        acceptThread.start();
        LOG.info("Sidecar proxy started on port " + serverSocket.getLocalPort()
                + " [Mode: " + consistencyMode + ", W: " + quorumW + "] → " + localDbHost + ":" + localDbPort);
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

            String response;
            if (isWriteCommand(command) && leaderState.isLeader()) {
                if ("STRICT".equals(consistencyMode)) {
                    long version = leaderState.nextVersion();
                    String versionedCommand = convertToVersioned(command, version);
                    response = applyToLocalDb(versionedCommand);
                    if ("OK".equals(response)) {
                        boolean quorumReached = replicator.replicateSync(command, version, quorumW - 1);
                        if (!quorumReached) {
                            response = "ERROR consistency_failure";
                        }
                    }
                } else {
                    response = applyToLocalDb(command);
                    replicator.replicateAsync(command);
                }
            } else {
                // If it's a GET, server might send GET_V. Proxy just forwards it to local DB.
                response = applyToLocalDb(command);
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

    private String convertToVersioned(String command, long version) {
        if (command.startsWith("PUT ")) {
            String[] tokens = command.split("\\s+", 4);
            if (tokens.length == 4) {
                return "PUT_V " + tokens[1] + " " + tokens[2] + " " + version + " " + tokens[3];
            }
        }
        return command; // CREATE_TABLE doesn't strictly need versioning for quorum
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
