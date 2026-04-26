package com.evalvis.sidecar;

public interface LeaderState {
    boolean isLeader();
    long nextVersion();
}
