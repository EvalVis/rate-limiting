# Database Sharding Design

## Class Diagram

```mermaid
classDiagram
    class BackendTargetSelector {
        <<interface>>
        +selectTarget(HttpServletRequest) String
    }

    class ShardingBackendSelector {
        -ShardRing ring
        -String keyExtractionRegex
        +selectTarget(HttpServletRequest) String
        -extractKey(String path) String
    }

    class ShardRing {
        -TreeMap~Long, Shard~ ring
        -int virtualNodes
        +assign(String key) Shard
    }

    class Shard {
        -String id
        -List~String~ backends
        -AtomicInteger counter
        +getNextBackend() String
    }

    class LoadBalancerConfig {
        +String strategy
        +ShardingConfig sharding
        +List~ShardConfig~ shards
    }

    BackendTargetSelector <|.. ShardingBackendSelector
    ShardingBackendSelector --> ShardRing
    ShardRing "1" --> "*" Shard
    ShardingBackendSelector ..> LoadBalancerConfig
```

## Sequence Diagram: Sharded Request

```mermaid
sequenceDiagram
    participant Client
    participant ProxyController
    participant ShardingSelector
    participant ShardRing
    participant Shard
    participant Backend

    Client->>ProxyController: GET /tables/users/keys/123
    ProxyController->>ShardingSelector: selectTarget(request)
    ShardingSelector->>ShardingSelector: extractKey("/tables/users/keys/123") -> "123"
    ShardingSelector->>ShardRing: assign("123")
    ShardRing-->>ShardingSelector: Shard[id=alpha]
    ShardingSelector->>Shard: getNextBackend()
    Shard-->>ShardingSelector: "http://server-a1:8080"
    ShardingSelector-->>ProxyController: "http://server-a1:8080"
    ProxyController->>Backend: GET http://server-a1:8080/tables/users/keys/123
    Backend-->>ProxyController: 200 OK {"name": "John"}
    ProxyController-->>Client: 200 OK {"name": "John"}
```

## Sequence Diagram: Broadcast Request (Create Table)

*Note: This requires modifying ProxyController to handle multiple targets for specific paths.*

```mermaid
sequenceDiagram
    participant Client
    participant ProxyController
    participant ShardingSelector
    participant Shards...

    Client->>ProxyController: POST /tables/users
    ProxyController->>ShardingSelector: selectTargets(request) [Modified]
    ShardingSelector-->>ProxyController: [ShardA, ShardB]
    par Broadcast to Shards
        ProxyController->>ShardA: POST /tables/users
        ProxyController->>ShardB: POST /tables/users
    end
    ProxyController-->>Client: 201 Created
```

## Implementation Plan

1. **Refactor `ConsistentHashRing`**: Make it more generic to support mapping to `Shard` objects.
2. **Implement `ShardingBackendSelector`**: Add regex-based key extraction.
3. **Enhance Configuration**: Update `LoadBalancerConfig` to support hierarchical shard definitions.
4. **Modify `ProxyController`**: Add support for broadcast operations (e.g., table creation).
