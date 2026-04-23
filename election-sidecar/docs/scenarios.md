# Election Sidecar — Scenarios

## How the Client Finds the Leader

Stateless servers never talk to the election sidecars directly during reads. For writes, they use `LeaderDiscoveryClient`:

1. **Poll `/status` on every sidecar** until one responds with a non-empty `leaderHost` and non-zero `leaderDbPort`.
2. **Cache the result** for `cache-ttl-ms` (default 5 000 ms). All writes within that window go to the same leader address without another discovery round-trip.
3. **On write failure** (e.g. the cached leader address is stale), `invalidate()` clears the cache and the next write triggers a fresh poll.

Reads always go to the server's local sidecar proxy (configured statically via `DATABASE_URL`). Because every sidecar proxy applies writes to its own local database — both from clients and from the `/replicate` endpoint — every node is an eventually consistent replica that can serve reads.

```mermaid
flowchart TD
    Client -->|HTTP PUT| Server

    Server --> CacheCheck{leaderCache\nvalid?}
    CacheCheck -->|hit| WriteLeader["write to cached\nleader address"]
    CacheCheck -->|miss| Poll["GET /status on each\nsidecar endpoint"]
    Poll -->|first with leaderHost != empty| CacheStore["cache address\n5 000 ms TTL"]
    CacheStore --> WriteLeader

    WriteLeader --> WriteOK{success?}
    WriteOK -->|yes| Done["204 to client"]
    WriteOK -->|no| Invalidate["invalidate()\nclear cache"]
    Invalidate --> Poll

    Client2["Client"] -->|HTTP GET| Server2["Server"]
    Server2 -->|always| ReadLocal["read from local\nsidecar proxy\nno discovery"]
```

The `/status` response shape:
```json
{
  "nodeId": 3,
  "state": "LEADER",
  "leaderId": 3,
  "epoch": 1,
  "selfHost": "db3",
  "selfDbPort": 7379,
  "selfElectionPort": 8090,
  "leaderHost": "db3",
  "leaderDbPort": 7379,
  "leaderElectionPort": 8090
}
```

A follower includes the leader's coordinates in its own `/status` response, so any sidecar in the cluster can answer a discovery query — not only the leader itself.

---

## Initial Election (Cluster Start)

Three nodes start with IDs 1, 2, 3. Each enters `LOOKING`.

```mermaid
sequenceDiagram
    participant N1 as Node 1 (LOOKING)
    participant N2 as Node 2 (LOOKING)
    participant N3 as Node 3 (LOOKING)

    Note over N1,N3: All nodes start staggered by rank × interval/3.<br/>Node 3 has no higher peers → wins immediately.

    N3->>N3: becomeLeader() epoch=1
    N3->>N1: POST /coordinator {leaderId:3, epoch:1}
    N3->>N2: POST /coordinator {leaderId:3, epoch:1}
    N1->>N1: state=FOLLOWER leaderId=3
    N2->>N2: state=FOLLOWER leaderId=3

    loop every 500 ms
        N3->>N1: POST /heartbeat {leaderId:3, epoch:1}
        N3->>N2: POST /heartbeat {leaderId:3, epoch:1}
        N1->>N1: reset lastHeartbeatMs
        N2->>N2: reset lastHeartbeatMs
    end
```

Node 3 wins because the bully algorithm gives victory to the highest ID that is alive and reachable. Lower-ranked nodes defer to higher-ranked ones — they respond "alive" to `/election` and let the highest reachable ID claim leadership.

---

## Leader Dies — Failover Walkthrough

Cluster is running: node 3 = leader, nodes 1 and 2 = followers.

```mermaid
sequenceDiagram
    participant N1 as Node 1 (FOLLOWER)
    participant N2 as Node 2 (FOLLOWER)
    participant N3 as Node 3 (LEADER) 💀

    Note over N3: Node 3 crashes at t=0.<br/>No more heartbeats.

    Note over N1,N2: t=1 150 ms — heartbeat timeout exceeded

    N1->>N1: state=LOOKING startElection()
    N2->>N2: state=LOOKING startElection()

    Note over N2: Node 2 asks only peers with id > 2 (node 3 only)
    N2->>N3: POST /election {candidateId:2}
    Note over N3: No response (dead)
    N2->>N2: all higher peers dead → becomeLeader() epoch=2

    Note over N1: Node 1 asks peers with id > 1 (nodes 2 and 3)
    N1->>N2: POST /election {candidateId:1}
    N2->>N1: {"alive":true}
    N1->>N1: higher peer alive → wait electionTimeout (1 500 ms)

    N2->>N1: POST /coordinator {leaderId:2, epoch:2}
    N2->>N2: POST /coordinator to self
    N1->>N1: state=FOLLOWER leaderId=2

    loop every 500 ms
        N2->>N1: POST /heartbeat {leaderId:2, epoch:2}
    end

    Note over N1,N2: Failover complete ≈ 1–2 heartbeat intervals
```

Node 2 wins because it is the highest-ID node still reachable. Node 1 defers to node 2 the same way node 2 deferred to node 3.

---

## Write Path Through a Live Cluster

```mermaid
sequenceDiagram
    participant C as Client
    participant SRV as Server 1
    participant SC3 as Sidecar 3 (LEADER)
    participant DB3 as Database 3
    participant SC1 as Sidecar 1
    participant DB1 as Database 1
    participant SC2 as Sidecar 2
    participant DB2 as Database 2

    C->>SRV: PUT /tables/items/keys/key1 (value1)
    SRV->>SRV: leaderCache hit: db3:7379
    SRV->>SC3: TCP: PUT items key1 value1

    SC3->>DB3: TCP: PUT items key1 value1
    DB3->>SC3: OK (appended to items.jsonl)
    SC3->>SRV: OK
    SRV->>C: 204

    par async replication
        SC3->>SC1: POST /replicate "PUT items key1 value1"
        SC1->>DB1: TCP: PUT items key1 value1
        DB1->>SC1: OK
    and
        SC3->>SC2: POST /replicate "PUT items key1 value1"
        SC2->>DB2: TCP: PUT items key1 value1
        DB2->>SC2: OK
    end

    Note over C,DB2: Client receives 204 before replication completes.<br/>Reads from any replica will eventually see key1.
```

---

## Write After Leader Failover (Stale Cache)

Node 3 has just died. Node 2 is the new leader. Server 1's cache still points to `db3:7379`.

```mermaid
sequenceDiagram
    participant C as Client
    participant SRV as Server 1
    participant SC3 as Sidecar 3 💀
    participant SC1 as Sidecar 1
    participant SC2 as Sidecar 2 (new LEADER)
    participant DB2 as Database 2

    C->>SRV: PUT /tables/items/keys/key3 (value3)
    SRV->>SRV: leaderCache: db3:7379 (stale)
    SRV->>SC3: TCP: PUT items key3 value3
    SC3->>SRV: connection refused

    SRV->>SRV: DatabaseClientException\ninvalidate() — clear cache

    SRV->>SC1: GET /status
    SC1->>SRV: {leaderHost:"db2", leaderDbPort:7379}
    SRV->>SRV: cache = db2:7379 (5 000 ms TTL)

    SRV->>SC2: TCP: PUT items key3 value3
    SC2->>DB2: TCP: PUT items key3 value3
    DB2->>SC2: OK
    SC2->>SRV: OK
    SRV->>C: 204

    Note over C,DB2: Extra latency = one failed attempt + one /status poll.<br/>All subsequent writes within TTL go directly to db2.
```
