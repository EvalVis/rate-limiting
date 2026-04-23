package com.evalvis.sidecar.election;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElectionNodeTest {

    @Test
    @Timeout(10)
    void highestIdNodeBecomesLeader() throws Exception {
        int electionPort1 = freePort(), electionPort2 = freePort(), electionPort3 = freePort();

        NodeConfig cfg1 = new NodeConfig(1, "127.0.0.1", 7379, electionPort1);
        NodeConfig cfg2 = new NodeConfig(2, "127.0.0.1", 7379, electionPort2);
        NodeConfig cfg3 = new NodeConfig(3, "127.0.0.1", 7379, electionPort3);

        List<PeerInfo> peers1 = List.of(
                new PeerInfo(2, "127.0.0.1", electionPort2),
                new PeerInfo(3, "127.0.0.1", electionPort3));
        List<PeerInfo> peers2 = List.of(
                new PeerInfo(1, "127.0.0.1", electionPort1),
                new PeerInfo(3, "127.0.0.1", electionPort3));
        List<PeerInfo> peers3 = List.of(
                new PeerInfo(1, "127.0.0.1", electionPort1),
                new PeerInfo(2, "127.0.0.1", electionPort2));

        ElectionConfig electionConfig = new ElectionConfig(150);

        ElectionNode node1 = new ElectionNode(cfg1, peers1, electionConfig);
        ElectionNode node2 = new ElectionNode(cfg2, peers2, electionConfig);
        ElectionNode node3 = new ElectionNode(cfg3, peers3, electionConfig);

        ElectionHttpServer srv1 = new ElectionHttpServer(node1, electionPort1);
        ElectionHttpServer srv2 = new ElectionHttpServer(node2, electionPort2);
        ElectionHttpServer srv3 = new ElectionHttpServer(node3, electionPort3);

        try {
            srv1.start(); srv2.start(); srv3.start();
            node1.start(); node2.start(); node3.start();

            waitForLeader(2000, node1, node2, node3);

            assertEquals(ElectionState.LEADER, node3.getState(), "node3 should be leader (highest ID)");
            assertEquals(ElectionState.FOLLOWER, node1.getState());
            assertEquals(ElectionState.FOLLOWER, node2.getState());
            assertEquals(3, node1.getCurrentLeaderId());
            assertEquals(3, node2.getCurrentLeaderId());
        } finally {
            for (var n : Arrays.asList(node1, node2, node3)) n.close();
            for (var s : Arrays.asList(srv1, srv2, srv3)) s.close();
        }
    }

    @Test
    @Timeout(15)
    void whenLeaderDies_remainingNodesElectNewLeader() throws Exception {
        int electionPort1 = freePort(), electionPort2 = freePort(), electionPort3 = freePort();

        NodeConfig cfg1 = new NodeConfig(1, "127.0.0.1", 7379, electionPort1);
        NodeConfig cfg2 = new NodeConfig(2, "127.0.0.1", 7379, electionPort2);
        NodeConfig cfg3 = new NodeConfig(3, "127.0.0.1", 7379, electionPort3);

        List<PeerInfo> peers1 = List.of(
                new PeerInfo(2, "127.0.0.1", electionPort2),
                new PeerInfo(3, "127.0.0.1", electionPort3));
        List<PeerInfo> peers2 = List.of(
                new PeerInfo(1, "127.0.0.1", electionPort1),
                new PeerInfo(3, "127.0.0.1", electionPort3));
        List<PeerInfo> peers3 = List.of(
                new PeerInfo(1, "127.0.0.1", electionPort1),
                new PeerInfo(2, "127.0.0.1", electionPort2));

        ElectionConfig electionConfig = new ElectionConfig(150);

        ElectionNode node1 = new ElectionNode(cfg1, peers1, electionConfig);
        ElectionNode node2 = new ElectionNode(cfg2, peers2, electionConfig);
        ElectionNode node3 = new ElectionNode(cfg3, peers3, electionConfig);

        ElectionHttpServer srv1 = new ElectionHttpServer(node1, electionPort1);
        ElectionHttpServer srv2 = new ElectionHttpServer(node2, electionPort2);
        ElectionHttpServer srv3 = new ElectionHttpServer(node3, electionPort3);

        try {
            srv1.start(); srv2.start(); srv3.start();
            node1.start(); node2.start(); node3.start();

            waitForLeader(2000, node1, node2, node3);
            assertEquals(ElectionState.LEADER, node3.getState(), "node3 should be initial leader");

            node3.close();
            srv3.close();

            waitForLeader(4000, node1, node2);

            assertEquals(ElectionState.LEADER, node2.getState(), "node2 should be new leader after node3 dies");
            assertEquals(2, node1.getCurrentLeaderId());
        } finally {
            node1.close(); node2.close();
            srv1.close(); srv2.close();
        }
    }

    private void waitForLeader(long maxWaitMs, ElectionNode... nodes) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            long leaders = Arrays.stream(nodes).filter(n -> n.getState() == ElectionState.LEADER).count();
            long followers = Arrays.stream(nodes).filter(n -> n.getState() == ElectionState.FOLLOWER).count();
            if (leaders == 1 && followers == nodes.length - 1) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Cluster did not stabilize within " + maxWaitMs + "ms");
    }

    private static int freePort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }
}
