package com.evalvis.sidecar;

import com.evalvis.sidecar.election.PeerInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
        replicateAsync(command, 0L);
    }

    @Override
    public void replicateAsync(String command, long version) {
        for (PeerInfo peer : peers) {
            executor.submit(() -> replicate(peer, command, version));
        }
    }

    @Override
    public boolean replicateSync(String command, long version, int requiredAcks) {
        if (requiredAcks <= 0) return true;
        if (peers.isEmpty()) return requiredAcks <= 0;

        CountDownLatch latch = new CountDownLatch(requiredAcks);
        for (PeerInfo peer : peers) {
            executor.submit(() -> {
                if (replicate(peer, command, version)) {
                    latch.countDown();
                }
            });
        }

        try {
            return latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean replicate(PeerInfo peer, String command, long version) {
        try {
            HttpResponse<Void> response = http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://" + peer.host() + ":" + peer.electionPort() + "/replicate"))
                            .POST(HttpRequest.BodyPublishers.ofString(command))
                            .header("Content-Type", "text/plain")
                            .header("X-Record-Version", String.valueOf(version))
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                    HttpResponse.BodyHandlers.discarding()
            );
            return response.statusCode() == 200;
        } catch (Exception e) {
            LOG.warning("Replication to " + peer.host() + ":" + peer.electionPort() + " failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
