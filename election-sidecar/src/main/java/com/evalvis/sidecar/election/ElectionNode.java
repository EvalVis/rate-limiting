package com.evalvis.sidecar.election;

import com.evalvis.sidecar.LeaderState;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class ElectionNode implements AutoCloseable, LeaderState {
    private static final Logger LOG = Logger.getLogger(ElectionNode.class.getName());

    private final NodeConfig self;
    private final List<PeerInfo> peers;
    private final ElectionConfig config;
    private final ElectionHttpClient httpClient;

    private final Object lock = new Object();
    private ElectionState state = ElectionState.LOOKING;
    private int currentLeaderId = -1;
    private LeaderInfo leaderInfo = null;
    private long lastHeartbeatMs = 0;
    private long currentEpoch = 0;
    private final java.util.concurrent.atomic.AtomicLong logicalClock = new java.util.concurrent.atomic.AtomicLong(0);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private ScheduledFuture<?> heartbeatTask = null;

    public ElectionNode(NodeConfig self, List<PeerInfo> peers, ElectionConfig config) {
        this.self = self;
        this.peers = List.copyOf(peers);
        this.config = config;
        this.httpClient = new ElectionHttpClient(config.healthCheckIntervalMs());
    }

    public void start() {
        long rank = peers.stream()
                .filter(p -> p.nodeId() > self.nodeId())
                .count();
        long startDelay = rank * config.healthCheckIntervalMs() / 3;
        scheduler.schedule(this::startElection, startDelay, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(
                this::checkHeartbeat,
                config.electionTimeoutMs(),
                config.healthCheckIntervalMs(),
                TimeUnit.MILLISECONDS
        );
    }

    public void startElection() {
        long epoch;
        synchronized (lock) {
            if (state == ElectionState.LEADER) return;
            if (state == ElectionState.FOLLOWER) return;
            currentEpoch++;
            epoch = currentEpoch;
        }
        LOG.info("Node " + self.nodeId() + " starting election epoch=" + epoch);

        List<PeerInfo> higherPeers = peers.stream()
                .filter(p -> p.nodeId() > self.nodeId())
                .toList();

        if (higherPeers.isEmpty()) {
            becomeLeader(epoch);
            return;
        }

        boolean anyHigherAlive = higherPeers.stream()
                .anyMatch(peer -> httpClient.sendElection(peer, self.nodeId(), epoch));

        if (!anyHigherAlive) {
            becomeLeader(epoch);
        } else {
            long capturedEpoch = epoch;
            scheduler.schedule(() -> {
                synchronized (lock) {
                    if (state == ElectionState.LOOKING && currentEpoch == capturedEpoch) {
                        scheduler.execute(this::startElection);
                    }
                }
            }, config.electionTimeoutMs(), TimeUnit.MILLISECONDS);
        }
    }

    private void becomeLeader(long epoch) {
        synchronized (lock) {
            state = ElectionState.LEADER;
            currentLeaderId = self.nodeId();
            leaderInfo = new LeaderInfo(self.nodeId(), self.host(), self.dbPort(), self.electionPort());
            currentEpoch = epoch;
            logicalClock.set(epoch * 1_000_000L); // Seed clock with epoch to avoid collisions
            if (heartbeatTask != null) heartbeatTask.cancel(false);
            heartbeatTask = scheduler.scheduleAtFixedRate(
                    this::sendHeartbeats, 0, config.healthCheckIntervalMs(), TimeUnit.MILLISECONDS
            );
        }
        LOG.info("Node " + self.nodeId() + " became LEADER epoch=" + epoch);
        peers.forEach(peer -> httpClient.sendCoordinator(peer, leaderInfo, epoch));
    }

    @Override
    public long nextVersion() {
        return logicalClock.incrementAndGet();
    }

    void receiveElection(int candidateId, long epoch) {
        if (self.nodeId() > candidateId) {
            synchronized (lock) {
                if (state != ElectionState.LEADER) state = ElectionState.LOOKING;
            }
            scheduler.execute(this::startElection);
        }
    }

    void receiveCoordinator(int leaderId, String leaderHost, int leaderDbPort, int leaderElectionPort, long epoch) {
        synchronized (lock) {
            if (epoch >= currentEpoch) {
                state = ElectionState.FOLLOWER;
                currentLeaderId = leaderId;
                leaderInfo = new LeaderInfo(leaderId, leaderHost, leaderDbPort, leaderElectionPort);
                lastHeartbeatMs = System.currentTimeMillis();
                currentEpoch = epoch;
            }
        }
        LOG.info("Node " + self.nodeId() + " following leader=" + leaderId + " epoch=" + epoch);
    }

    void receiveHeartbeat(int leaderId, long epoch) {
        synchronized (lock) {
            if (state != ElectionState.LEADER && (leaderId == currentLeaderId || epoch >= currentEpoch)) {
                lastHeartbeatMs = System.currentTimeMillis();
                currentLeaderId = leaderId;
                if (state == ElectionState.LOOKING) state = ElectionState.FOLLOWER;
            }
        }
    }

    private void checkHeartbeat() {
        synchronized (lock) {
            if (state != ElectionState.FOLLOWER) return;
            long elapsed = System.currentTimeMillis() - lastHeartbeatMs;
            if (elapsed <= config.heartbeatTimeoutMs()) return;
            LOG.info("Node " + self.nodeId() + " heartbeat timeout (" + elapsed + "ms), starting election");
            state = ElectionState.LOOKING;
        }
        startElection();
    }

    private void sendHeartbeats() {
        long epoch;
        synchronized (lock) {
            if (state != ElectionState.LEADER) return;
            epoch = currentEpoch;
        }
        peers.forEach(peer -> httpClient.sendHeartbeat(peer, self.nodeId(), epoch));
    }

    @Override
    public boolean isLeader() {
        synchronized (lock) { return state == ElectionState.LEADER; }
    }

    public ElectionState getState() {
        synchronized (lock) { return state; }
    }

    public int getCurrentLeaderId() {
        synchronized (lock) { return currentLeaderId; }
    }

    public LeaderInfo getLeaderInfo() {
        synchronized (lock) { return leaderInfo; }
    }

    public NodeConfig getSelf() { return self; }

    public long getCurrentEpoch() {
        synchronized (lock) { return currentEpoch; }
    }

    public List<PeerInfo> getPeers() { return peers; }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
