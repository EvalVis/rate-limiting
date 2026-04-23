package com.evalvis.sidecar.election;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

final class ElectionHttpClient {
    private static final Logger LOG = Logger.getLogger(ElectionHttpClient.class.getName());

    private final HttpClient http;

    ElectionHttpClient(long connectTimeoutMs) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    boolean sendElection(PeerInfo peer, int myId, long epoch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("candidateId", myId);
        body.put("epoch", epoch);
        return post(peer.host(), peer.electionPort(), "/election", SimpleJson.serialize(body));
    }

    void sendCoordinator(PeerInfo peer, LeaderInfo leader, long epoch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("leaderId", leader.leaderId());
        body.put("leaderHost", leader.leaderHost());
        body.put("leaderDbPort", leader.leaderDbPort());
        body.put("leaderElectionPort", leader.leaderElectionPort());
        body.put("epoch", epoch);
        post(peer.host(), peer.electionPort(), "/coordinator", SimpleJson.serialize(body));
    }

    void sendHeartbeat(PeerInfo peer, int leaderId, long epoch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("leaderId", leaderId);
        body.put("epoch", epoch);
        post(peer.host(), peer.electionPort(), "/heartbeat", SimpleJson.serialize(body));
    }

    private boolean post(String host, int port, String path, String jsonBody) {
        try {
            HttpResponse<Void> response = http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://" + host + ":" + port + path))
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );
            return response.statusCode() == 200;
        } catch (Exception e) {
            LOG.fine("Election HTTP call failed to " + host + ":" + port + path + ": " + e.getMessage());
            return false;
        }
    }
}
