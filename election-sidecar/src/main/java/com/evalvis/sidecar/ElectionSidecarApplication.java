package com.evalvis.sidecar;

import com.evalvis.sidecar.election.ElectionConfig;
import com.evalvis.sidecar.election.ElectionHttpServer;
import com.evalvis.sidecar.election.ElectionNode;
import com.evalvis.sidecar.election.NodeConfig;
import com.evalvis.sidecar.election.PeerInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class ElectionSidecarApplication {
    private static final Logger LOG = Logger.getLogger(ElectionSidecarApplication.class.getName());

    private ElectionSidecarApplication() {}

    public static void main(String[] args) throws IOException {
        Map<String, String> env = System.getenv();

        String nodeIdStr = env.get("NODE_ID");
        String peersStr = env.get("PEERS");
        if (nodeIdStr == null || peersStr == null) {
            throw new IllegalArgumentException("NODE_ID and PEERS environment variables must be set");
        }

        int nodeId = Integer.parseInt(nodeIdStr);
        String sidecarHost = env.getOrDefault("SIDECAR_HOST", "127.0.0.1");
        String localDbHost = env.getOrDefault("LOCAL_DB_HOST", "127.0.0.1");
        int localDbPort = Integer.parseInt(env.getOrDefault("LOCAL_DB_PORT", "7379"));
        int proxyPort = Integer.parseInt(env.getOrDefault("PROXY_PORT", "7380"));
        int electionPort = Integer.parseInt(env.getOrDefault("ELECTION_PORT", "8090"));
        long healthCheckMs = Long.parseLong(env.getOrDefault("HEALTH_CHECK_INTERVAL_MS", "500"));
        
        String consistencyMode = env.getOrDefault("CONSISTENCY_MODE", "EVENTUAL");
        int quorumW = Integer.parseInt(env.getOrDefault("QUORUM_W", "1"));

        NodeConfig self = null;
        List<PeerInfo> peers = new ArrayList<>();
        for (String entry : peersStr.split(",")) {
            String[] parts = entry.trim().split(":");
            int id = Integer.parseInt(parts[0]);
            String host = parts[1];
            int peerElectionPort = Integer.parseInt(parts[2]);
            if (id == nodeId) {
                self = new NodeConfig(id, sidecarHost, proxyPort, electionPort);
            } else {
                peers.add(new PeerInfo(id, host, peerElectionPort));
            }
        }

        if (self == null) {
            throw new IllegalArgumentException("NODE_ID=" + nodeId + " not found in PEERS=" + peersStr);
        }

        ElectionConfig config = new ElectionConfig(healthCheckMs);
        ElectionNode electionNode = new ElectionNode(self, peers, config);
        ReplicationClient replicationClient = new ReplicationClient(peers);
        SidecarTcpProxy proxy = new SidecarTcpProxy(localDbHost, localDbPort, proxyPort, 
                                                   electionNode, replicationClient,
                                                   consistencyMode, quorumW);
        ElectionHttpServer httpServer = new ElectionHttpServer(electionNode, electionPort, command -> {
            try {
                return proxy.applyToLocalDb(command);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        proxy.start();
        httpServer.start();
        electionNode.start();

        LOG.info("Election sidecar started: nodeId=" + nodeId
                + " mode=" + consistencyMode + " quorumW=" + quorumW
                + " proxyPort=" + proxyPort + " electionPort=" + electionPort
                + " localDb=" + localDbHost + ":" + localDbPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            electionNode.close();
            httpServer.close();
            replicationClient.close();
            proxy.close();
        }));
    }
}
