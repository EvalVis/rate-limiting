package com.evalvis.sidecar;

import com.evalvis.sidecar.election.PeerInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class ReplicationClient implements Replicator, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ReplicationClient.class.getName());

    private final List<PeerInfo> peers;
    private final HttpClient http;
    private final ExecutorService executor;

    public ReplicationClient(List<PeerInfo> peers) {
        this.peers = List.copyOf(peers);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public void replicateAsync(String command) {
        for (PeerInfo peer : peers) {
            executor.submit(() -> replicate(peer, command));
        }
    }

    private void replicate(PeerInfo peer, String command) {
        try {
            http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://" + peer.host() + ":" + peer.electionPort() + "/replicate"))
                            .POST(HttpRequest.BodyPublishers.ofString(command))
                            .header("Content-Type", "text/plain")
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );
        } catch (Exception e) {
            LOG.warning("Replication to " + peer.host() + ":" + peer.electionPort() + " failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
