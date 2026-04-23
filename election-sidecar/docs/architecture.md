# Election Sidecar — Architecture

## Role in the System

The election sidecar runs as a separate process alongside each database node. The database knows nothing about clustering — it is a plain TCP key-value server. The sidecar handles everything else: electing a leader, proxying client commands, replicating writes to peers, and answering discovery queries from stateless servers.

```mermaid
flowchart TD
    Server["Server (HTTP)\nGET → read DB\nPUT → leader DB"]

    Server -->|reads\nvia local proxy| SC1
    Server -->|writes\nvia discovered leader| SC3

    subgraph node1 [Node 1]
        SC1["Sidecar 1\nFOLLOWER\nTCP :7379  HTTP :8090"]
        DB1["Database 1\n:7379 internal"]
        SC1 -->|forward| DB1
    end

    subgraph node2 [Node 2]
        SC2["Sidecar 2\nFOLLOWER\nTCP :7379  HTTP :8090"]
        DB2["Database 2\n:7379 internal"]
        SC2 -->|forward| DB2
    end

    subgraph node3 [Node 3]
        SC3["Sidecar 3\nLEADER\nTCP :7379  HTTP :8090"]
        DB3["Database 3\n:7379 internal"]
        SC3 -->|forward| DB3
    end

    SC3 -->|POST /replicate| SC1
    SC3 -->|POST /replicate| SC2

    SC1 <-->|POST /heartbeat\nPOST /election\nPOST /coordinator| SC2
    SC1 <-->|POST /heartbeat\nPOST /election\nPOST /coordinator| SC3
    SC2 <-->|POST /heartbeat\nPOST /election\nPOST /coordinator| SC3
```

Each sidecar proxy listens on the same port as the database it guards. To a client, the sidecar is indistinguishable from the database — it speaks the same TCP protocol. The real database is accessible only locally, on an internal alias.

---

## Components

### ElectionNode — State Machine

Implements a simplified bully algorithm with epochs.

```mermaid
stateDiagram-v2
    [*] --> LOOKING : start()

    LOOKING --> LOOKING : startElection - higher peer alive, wait and retry
    LOOKING --> LEADER : startElection - no higher peer responds, becomeLeader()
    LOOKING --> FOLLOWER : receiveCoordinator - epoch >= currentEpoch

    LEADER --> LEADER : sendHeartbeats every 500 ms

    FOLLOWER --> LOOKING : checkHeartbeat - elapsed > heartbeatTimeout
    FOLLOWER --> FOLLOWER : receiveHeartbeat - reset lastHeartbeatMs
```

**Key state fields:**
- `currentEpoch` — logical term number, incremented on each election
- `currentLeaderId` — node ID of the acknowledged leader (-1 if unknown)
- `lastHeartbeatMs` — timestamp of last heartbeat received from leader

**Timing** (from `ElectionConfig.healthCheckIntervalMs`, default 500 ms):

| Timer | Formula | Default |
|-------|---------|---------|
| Election timeout | `healthCheckIntervalMs × 3` | 1 500 ms |
| Heartbeat timeout | `healthCheckIntervalMs × 2 + 150` | 1 150 ms |
| Heartbeat send rate | `healthCheckIntervalMs` | 500 ms |

### ElectionHttpServer — Coordination API

Exposes HTTP endpoints consumed by peer sidecars and by stateless servers.

| Method | Path | Caller | Purpose |
|--------|------|--------|---------|
| `GET` | `/status` | Server (discovery) | Returns this node's current state and leader coordinates |
| `POST` | `/election` | Peer sidecar | Peer announces candidacy |
| `POST` | `/coordinator` | Peer sidecar (new leader) | Leader announces itself after winning election |
| `POST` | `/heartbeat` | Peer sidecar (leader) | Leader proves it is alive |
| `POST` | `/replicate` | Peer sidecar (leader) | Leader sends a write command to apply on this node's local DB |

### SidecarTcpProxy — Command Gateway

```mermaid
flowchart TD
    Client -->|TCP command| Proxy

    Proxy -->|forward| LocalDB["Local Database\ninternal alias"]
    LocalDB -->|response| Proxy
    Proxy -->|response| Client

    Proxy --> Check{write command\nAND isLeader?}
    Check -->|yes| RC["ReplicationClient\n.replicateAsync()"]
    Check -->|no| Done["done"]
    RC -->|POST /replicate async| Peer1["Peer Sidecar 1"]
    RC -->|POST /replicate async| Peer2["Peer Sidecar 2"]
```

Write commands are `PUT` and `CREATE_TABLE`. Followers also accept writes through their proxy — the write is applied to the local replica — but followers do **not** call `replicateAsync`.

### ReplicationClient — Async Write Fan-Out

When called by a leader proxy, fires HTTP `POST /replicate` to every peer sidecar with the raw command as the request body. Uses a cached thread pool; requests are fire-and-forget with a 2-second timeout. Failures are logged but not retried.

---

## Startup Order

```mermaid
flowchart LR
    EN["ElectionNode\nconstructed"]
    RC["ReplicationClient\nconstructed"]
    TP["SidecarTcpProxy\nconstructed"]
    HS["ElectionHttpServer\nconstructed"]
    PS["proxy.start()\naccepts TCP"]
    SS["httpServer.start()\naccepts HTTP"]
    ES["electionNode.start()\nschedules tasks"]

    EN --> TP
    RC --> TP
    EN --> HS
    TP -->|"proxy::applyToLocalDb\n(for /replicate)"| HS
    TP --> PS
    HS --> SS
    PS --> ES
    SS --> ES
```

The proxy and HTTP server start before election begins so that peers can reach this node during the initial election.
