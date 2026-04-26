package com.evalvis.sidecar.election;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Logger;

public final class ElectionHttpServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ElectionHttpServer.class.getName());

    private final ElectionNode node;
    private final HttpServer httpServer;
    private final Function<String, String> commandApplier;

    public ElectionHttpServer(ElectionNode node, int port) throws IOException {
        this(node, port, null);
    }

    public ElectionHttpServer(ElectionNode node, int port, Function<String, String> commandApplier) throws IOException {
        this.node = node;
        this.commandApplier = commandApplier;
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 10);
        this.httpServer.createContext("/status", this::handleStatus);
        this.httpServer.createContext("/election", this::handleElection);
        this.httpServer.createContext("/coordinator", this::handleCoordinator);
        this.httpServer.createContext("/heartbeat", this::handleHeartbeat);
        if (commandApplier != null) {
            this.httpServer.createContext("/replicate", this::handleReplicate);
        }
        this.httpServer.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() {
        httpServer.start();
        LOG.info("Election HTTP server started on port " + httpServer.getAddress().getPort());
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        NodeConfig self = node.getSelf();
        LeaderInfo leader = node.getLeaderInfo();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nodeId", self.nodeId());
        body.put("state", node.getState().name());
        body.put("leaderId", node.getCurrentLeaderId());
        body.put("epoch", node.getCurrentEpoch());
        body.put("selfHost", self.host());
        body.put("selfDbPort", self.dbPort());
        body.put("selfElectionPort", self.electionPort());
        body.put("leaderHost", leader != null ? leader.leaderHost() : "");
        body.put("leaderDbPort", leader != null ? leader.leaderDbPort() : 0);
        body.put("leaderElectionPort", leader != null ? leader.leaderElectionPort() : 0);

        respond(exchange, 200, SimpleJson.serialize(body));
    }

    private void handleElection(HttpExchange exchange) throws IOException {
        Map<String, String> msg = SimpleJson.deserialize(readBody(exchange));
        int candidateId = Integer.parseInt(msg.getOrDefault("candidateId", "0"));
        long epoch = Long.parseLong(msg.getOrDefault("epoch", "0"));
        node.receiveElection(candidateId, epoch);
        respond(exchange, 200, "{\"alive\":true}");
    }

    private void handleCoordinator(HttpExchange exchange) throws IOException {
        Map<String, String> msg = SimpleJson.deserialize(readBody(exchange));
        int leaderId = Integer.parseInt(msg.getOrDefault("leaderId", "0"));
        String leaderHost = msg.getOrDefault("leaderHost", "");
        int leaderDbPort = Integer.parseInt(msg.getOrDefault("leaderDbPort", "0"));
        int leaderElectionPort = Integer.parseInt(msg.getOrDefault("leaderElectionPort", "0"));
        long epoch = Long.parseLong(msg.getOrDefault("epoch", "0"));
        node.receiveCoordinator(leaderId, leaderHost, leaderDbPort, leaderElectionPort, epoch);
        respond(exchange, 200, "{\"ok\":true}");
    }

    private void handleHeartbeat(HttpExchange exchange) throws IOException {
        Map<String, String> msg = SimpleJson.deserialize(readBody(exchange));
        int leaderId = Integer.parseInt(msg.getOrDefault("leaderId", "0"));
        long epoch = Long.parseLong(msg.getOrDefault("epoch", "0"));
        node.receiveHeartbeat(leaderId, epoch);
        respond(exchange, 200, "{\"ok\":true}");
    }

    private void handleReplicate(HttpExchange exchange) throws IOException {
        String command = readBody(exchange);
        String versionHeader = exchange.getRequestHeaders().getFirst("X-Record-Version");
        if (versionHeader != null && !versionHeader.equals("0")) {
            // Convert PUT table key value -> PUT_V table key version value
            if (command.startsWith("PUT ")) {
                String[] tokens = command.split("\\s+", 4);
                if (tokens.length == 4) {
                    command = "PUT_V " + tokens[1] + " " + tokens[2] + " " + versionHeader + " " + tokens[3];
                }
            } else if (command.startsWith("CREATE_TABLE ")) {
                // CREATE_TABLE is idempotent, usually no version needed but we could add if needed
            }
        }
        try {
            String result = commandApplier.apply(command);
            respond(exchange, 200, SimpleJson.serialize(Map.of("result", result)));
        } catch (Exception e) {
            LOG.warning("Replication command failed: " + e.getMessage());
            respond(exchange, 500, SimpleJson.serialize(Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown")));
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        httpServer.stop(0);
    }
}
