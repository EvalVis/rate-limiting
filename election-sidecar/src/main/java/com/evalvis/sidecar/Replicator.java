package com.evalvis.sidecar;

public interface Replicator {
    void replicateAsync(String command);
    void replicateAsync(String command, long version);
    boolean replicateSync(String command, long version, int requiredAcks);
}
