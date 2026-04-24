# Gemini Project Context: Distributed System

This project is a distributed, leader-elected, replicated key-value store architecture composed of several specialized components. It demonstrates core distributed systems concepts like leader election, TCP proxying, replication, and load balancing.

## Project Overview

- **Core Langauges:** Java (database, election-sidecar, ratelimiter, tests) and Kotlin (server, loadbalancer).
- **Frameworks:** Spring Boot (server, loadbalancer, ratelimiter, tests), Maven (build system).
- **Communication:** TCP (database, sidecar proxy) and HTTP (REST APIs, replication).
- **Infrastructure:** Docker/Testcontainers for integration testing.

## Architecture

The system follows a layered architecture:

```
[Client]
    ↓ (HTTP)
[LoadBalancer]          – Round-robin or consistent-hash across Server instances
    ↓ (HTTP)
[RateLimiter]           – Token/leaky bucket per IP or JWT role
    ↓ (HTTP)
[Server 1..N]           – Stateless gateway; discovers leader via sidecars, routes reads/writes
    ↓ (TCP)
[SidecarTcpProxy 1..N]  – Election protocol + TCP proxy to local DB + replication
    ↓ (TCP)
[Database 1..N]         – Pure storage (JSONL-based), no cluster awareness
```

### Key Distributed Patterns
- **Leader Election:** Implemented in `election-sidecar` using a Raft-inspired protocol with epochs and heartbeats. The highest-ID node in a quorum becomes the leader.
- **Replication:** On write, the leader's sidecar proxy forwards to the local DB and asynchronously replicates to peers via HTTP POST `/replicate`.
- **Leader Discovery:** The `server` component polls `/status` on all sidecars and caches the leader address.

## Building and Running

Each module is a self-contained Maven project.

### Core Commands

- **Build a module:** `cd <module> && mvn clean package`
- **Run unit tests:** `mvn test`
- **Run integration tests:** Requires building all upstream JARs first.
  ```bash
  mvn -f database/pom.xml package
  mvn -f election-sidecar/pom.xml package
  mvn -f server/pom.xml package
  mvn -f loadbalancer/pom.xml package
  mvn -f ratelimiter/pom.xml package
  cd tests && mvn verify
  ```

### Testcontainers on Windows (Docker Desktop)
The project includes a workaround for Docker Desktop 4.x named pipe routing. Ensure `~/.testcontainers.properties` contains:
```
docker.host=npipe:////./pipe/docker_engine_linux
```

## Module Details

| Module | Purpose |
|--------|---------|
| `database` | Standalone TCP key-value server with file-backed persistence (JSONL). |
| `election-sidecar` | Manages leader election state and proxies TCP traffic with replication logic. |
| `server` | HTTP gateway that routes client requests to the appropriate sidecar/database. |
| `loadbalancer` | HTTP reverse proxy supporting multiple balancing strategies. |
| `ratelimiter` | Rate limiting service with support for token/leaky bucket and JWT roles. |
| `tests` | Comprehensive integration tests using Testcontainers. |

## Development Conventions

### Coding Style
- **Abstractions:** Heavy use of `@FunctionalInterface` for core logic (e.g., `LeaderState`, `Replicator`, `BackendTargetSelector`).
- **Tests:** 
    - `*Test.java/kt`: Unit tests (fast, no external dependencies).
    - `*IT.java/kt`: Integration tests (uses Failsafe plugin, may require Docker).
- **Concurrency:** Uses `ExecutorService` and `Atomic` variables for thread-safe operations in sidecars and servers.

### TCP Protocol (database)
Commands are newline-delimited strings:
- `CREATE_TABLE <tableName>`
- `PUT <tableName> <key> <value>`
- `GET <tableName> <key>`

Responses: `OK`, `VALUE <value>`, `NOT_FOUND`, or `ERROR <reason>`.

## Key Configuration

### Election Sidecar Environment Variables
- `NODE_ID`: Unique integer ID (Required).
- `PEERS`: `id:host:port,...` list (Required).
- `PROXY_PORT`: TCP port for the sidecar proxy (Default: 7380).
- `ELECTION_PORT`: HTTP port for election management (Default: 8090).
- `HEALTH_CHECK_INTERVAL_MS`: Heartbeat interval (Default: 500ms).
