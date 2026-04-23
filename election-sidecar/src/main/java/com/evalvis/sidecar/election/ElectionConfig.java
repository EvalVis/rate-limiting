package com.evalvis.sidecar.election;

public record ElectionConfig(long healthCheckIntervalMs) {

    public long electionTimeoutMs() {
        return healthCheckIntervalMs * 3;
    }

    public long heartbeatTimeoutMs() {
        return healthCheckIntervalMs * 2 + 150;
    }
}
