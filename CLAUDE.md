# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Each module is a self-contained Maven project. Build and test from within its directory.

```bash
# Build a module (produces target/<artifactId>-0.0.1-SNAPSHOT.jar)
cd <module> && mvn clean package

# Unit tests only (excludes *IT.java)
mvn test

# Single unit test class
mvn test -Dtest=ElectionNodeTest

# Single unit test method
mvn test -Dtest=ElectionNodeTest#highestIdNodeBecomesLeader
```

**Integration tests** require all upstream JARs to be built first:

```bash
mvn -f database/pom.xml package
mvn -f election-sidecar/pom.xml package
mvn -f server/pom.xml package
mvn -f loadbalancer/pom.xml package
mvn -f ratelimiter/pom.xml package
cd tests && mvn verify
```

Run a single integration test class:
```bash
cd tests && mvn failsafe:integration-test -Dit.test=LeaderElectionIT -Dfailsafe.failIfNoSpecifiedTests=false
```

## Modules at a Glance

| Module | Lang | Role |
|--------|------|------|
| `database` | Java | Standalone TCP key-value server, file-backed persistence |
| `election-sidecar` | Java | Leader election state machine + TCP proxy + replication |
| `server` | Kotlin + Spring Boot | HTTP gateway; discovers leader via sidecar, routes reads/writes |
| `loadbalancer` | Kotlin + Spring Boot | Round-robin or consistent-hash HTTP reverse proxy |
| `ratelimiter` | Java + Spring Boot | Token/leaky bucket rate limiter with optional Redis and JWT |
| `tests` | Java + Spring Boot | Testcontainers integration tests (no production code) |

## Architecture

```
[Client]
    ↓
[LoadBalancer]  – round-robin or consistent-hash across Server instances
    ↓
[RateLimiter]   – token/leaky bucket per IP (or JWT role)
    ↓
[Server 1..N]   – stateless HTTP; polls sidecar /status to discover leader
    ↓
[SidecarTcpProxy 1..N]  – election protocol + TCP proxy to local DB + replication
    ↓
[Database 1..N]  – pure storage, no cluster awareness
```

**Leader election** (election-sidecar): Raft-inspired with epochs and heartbeats. `ElectionNode` runs as LOOKING → LEADER or FOLLOWER. The highest-ID node in a quorum becomes leader. On write, the proxy forwards to the local DB and replicates to peers via HTTP POST `/replicate`.

**Reads vs. writes** (server): reads go to the sidecar proxy on the local DB (`database.url`); writes go to the current leader discovered via `LeaderDiscoveryClient` which polls `/status` on all sidecars and caches the leader address (configurable TTL via `database.leader-discovery.cache-ttl-ms`).

## Key Abstractions

**database**
- `CommandProcessor` (interface) — processes TCP commands; `FileDbCommandProcessor` is the production implementation
- `FileDbClient` (interface) — TCP client; `TcpFileDbClient` is the implementation

**election-sidecar**
- `LeaderState` (`@FunctionalInterface`) — `isLeader()` predicate; implemented by `ElectionNode`, substituted with lambdas in tests
- `Replicator` (interface) — `replicateAsync(command)`; implemented by `ReplicationClient`, substituted with `SpyReplicator` in tests
- `ElectionHttpServer` — HTTP endpoints: `/status`, `/election`, `/coordinator`, `/heartbeat`, `/replicate`

**loadbalancer**
- `BackendTargetSelector` (`@FunctionalInterface`) — `selectTarget(request) → baseUrl`; implementations: `RoundRobinTargetPicker`, `ConsistentHashBackendSelector`

**ratelimiter**
- `RateLimitKeyResolver` — maps `HttpServletRequest` to a string key (default: client IP)
- `RateLimiterSelector` — picks which `RateLimiter` to apply (default: fixed; JWT-aware: by role)
- `RateLimitMediator` — composes key resolver + limiter selector + `tryAcquire`

## TCP Protocol (database)

Commands are newline-delimited strings. Responses: `OK`, `VALUE <value>`, `NOT_FOUND`, `ERROR table_not_found`, `ERROR invalid_command`.

```
CREATE_TABLE <tableName>
PUT <tableName> <key> <value>
GET <tableName> <key>
```

Storage: one JSONL file per table under `DATA_DIR`; reads scan from bottom-up for latest value.

## Election Sidecar Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `NODE_ID` | required | Unique integer node ID |
| `PEERS` | required | `id:host:port,...` for all nodes |
| `SIDECAR_HOST` | `127.0.0.1` | This node's advertised host |
| `LOCAL_DB_HOST` | `127.0.0.1` | Host of the co-located database |
| `LOCAL_DB_PORT` | `7379` | Port of the co-located database |
| `PROXY_PORT` | `7380` | Port the sidecar TCP proxy listens on |
| `ELECTION_PORT` | `8090` | Port the HTTP election server listens on |
| `HEALTH_CHECK_INTERVAL_MS` | `500` | Heartbeat / election timeout interval |

## Testcontainers on Windows (Docker Desktop)

Docker Desktop 4.x changed its named pipe routing. The `tests/pom.xml` failsafe configuration already contains the required workaround:

```xml
<argLine>--add-opens java.base/java.lang=ALL-UNNAMED -Dapi.version=1.47</argLine>
<environmentVariables>
    <DOCKER_HOST>npipe:////./pipe/docker_engine_linux</DOCKER_HOST>
</environmentVariables>
```

If tests fail with `BadRequestException Status 400` connecting to Docker, ensure `~/.testcontainers.properties` contains:
```
docker.host=npipe:////./pipe/docker_engine_linux
```

The env var `DOCKER_API_VERSION` is ignored by shaded docker-java inside Testcontainers; only the JVM system property `-Dapi.version` works.
