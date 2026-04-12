# Integration tests

From this directory, after packaging upstream apps: `mvn -f ../ratelimiter/pom.xml package; mvn -f ../server/pom.xml package`, run `mvn verify` (requires Docker for `DistributedRateLimitConcurrencyIT`).
