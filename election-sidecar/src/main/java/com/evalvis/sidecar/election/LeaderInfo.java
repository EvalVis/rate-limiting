package com.evalvis.sidecar.election;

public record LeaderInfo(int leaderId, String leaderHost, int leaderDbPort, int leaderElectionPort) {

    public String leaderDbAddress() {
        return leaderHost + ":" + leaderDbPort;
    }
}
