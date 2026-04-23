package com.evalvis.sidecar.election;

public record NodeConfig(int nodeId, String host, int dbPort, int electionPort) {

    public String dbAddress() {
        return host + ":" + dbPort;
    }
}
