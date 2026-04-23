package com.evalvis.tests;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LeaderElectionIT {

    static {
        ProcessPathSanitizer.sanitizePath();
    }

    private static final Logger LOG = Logger.getLogger(LeaderElectionIT.class.getName());
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final String PEERS = "1:db1:8090,2:db2:8090,3:db3:8090";

    private static Network network;
    // Pure DB containers — no election code, just storage
    private static GenericContainer<?> db1Db, db2Db, db3Db;
    // Sidecar containers — election + TCP proxy, co-located with their DB on the network
    private static GenericContainer<?> db1Sidecar, db2Sidecar, db3Sidecar;
    private static GenericContainer<?> server1, server2, lb;
    private static HttpClient httpClient;

    @BeforeAll
    static void start() throws Exception {
        Path databaseJar = moduleJar("database", "database");
        Path sidecarJar = moduleJar("election-sidecar", "election-sidecar");
        Path serverJar = moduleJar("server", "server");
        Path lbJar = moduleJar("loadbalancer", "loadbalancer");

        Assumptions.assumeTrue(Files.isRegularFile(databaseJar),
                "missing " + databaseJar + "; run mvn -f ../database/pom.xml package");
        Assumptions.assumeTrue(Files.isRegularFile(sidecarJar),
                "missing " + sidecarJar + "; run mvn -f ../election-sidecar/pom.xml package");
        Assumptions.assumeTrue(Files.isRegularFile(serverJar),
                "missing " + serverJar + "; run mvn -f ../server/pom.xml package");
        Assumptions.assumeTrue(Files.isRegularFile(lbJar),
                "missing " + lbJar + "; run mvn -f ../loadbalancer/pom.xml package");

        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        network = Network.newNetwork();

        // Start pure DB containers first — sidecars connect to them
        db1Db = dbContainer(databaseJar, 1);
        db2Db = dbContainer(databaseJar, 2);
        db3Db = dbContainer(databaseJar, 3);
        db1Db.start();
        db2Db.start();
        db3Db.start();

        // Start sidecars — each proxies to its co-located DB and participates in election
        db1Sidecar = sidecarContainer(sidecarJar, 1, "db1", "db1-db");
        db2Sidecar = sidecarContainer(sidecarJar, 2, "db2", "db2-db");
        db3Sidecar = sidecarContainer(sidecarJar, 3, "db3", "db3-db");
        db1Sidecar.start();
        db2Sidecar.start();
        db3Sidecar.start();

        awaitLeader(List.of(db1Sidecar, db2Sidecar, db3Sidecar));
        LOG.info("Initial leader elected");

        // Stateless servers read from their local sidecar proxy (db1/db2),
        // write to the current leader discovered via sidecar election endpoints
        server1 = statelessServerContainer(serverJar, "server1", "db1");
        server2 = statelessServerContainer(serverJar, "server2", "db2");
        server1.start();
        server2.start();

        lb = loadBalancerContainer(lbJar);
        lb.start();
    }

    @AfterAll
    static void stop() {
        stopIfRunning(lb, server2, server1, db3Sidecar, db2Sidecar, db1Sidecar, db3Db, db2Db, db1Db);
        if (network != null) network.close();
    }

    @Test
    @Order(1)
    void twoStatelessServersWriteToLeaderAndReadFromSlave() throws Exception {
        int s1Port = server1.getMappedPort(8080);
        int s2Port = server2.getMappedPort(8080);
        int lbPort = lb.getMappedPort(8080);

        // Create table — goes to leader via discovery, replicates to slaves via sidecar /replicate
        int createStatus = post("http://127.0.0.1:" + s1Port + "/tables/items");
        assertThat(createStatus).isEqualTo(201);

        // Both stateless servers write to the leader (discovered via sidecar election endpoints)
        assertThat(put("http://127.0.0.1:" + s1Port + "/tables/items/keys/key1", "value1")).isEqualTo(204);
        assertThat(put("http://127.0.0.1:" + s2Port + "/tables/items/keys/key2", "value2")).isEqualTo(204);

        // Allow replication to propagate to slaves
        Thread.sleep(1000);

        // Read via load balancer — round-robins between server1 and server2.
        // Both read from their local sidecar proxy which reads from the local DB.
        assertThat(retryGet("http://127.0.0.1:" + lbPort + "/tables/items/keys/key1", 6))
                .isEqualTo("value1");
        assertThat(retryGet("http://127.0.0.1:" + lbPort + "/tables/items/keys/key2", 6))
                .isEqualTo("value2");
    }

    @Test
    @Order(2)
    void whenLeaderDies_newLeaderElectedAndStatelessServerWritesToNewLeader() throws Exception {
        // Kill the leader sidecar and its DB (node 3 = highest ID = always initial leader)
        db3Sidecar.stop();
        db3Db.stop();
        LOG.info("Killed db3 sidecar and DB (leader)");

        // Surviving sidecars (db1, db2) detect missing heartbeat and elect new leader
        awaitLeader(List.of(db1Sidecar, db2Sidecar));
        LOG.info("New leader elected after db3 died");

        // Wait for discovery cache to expire (cache-ttl-ms=1000)
        Thread.sleep(1500);

        int s1Port = server1.getMappedPort(8080);
        int lbPort = lb.getMappedPort(8080);

        // server1 retries: cached db3 sidecar fails → re-discovers new leader (db2 sidecar) → writes succeed
        assertThat(put("http://127.0.0.1:" + s1Port + "/tables/items/keys/key3", "value3")).isEqualTo(204);

        // Allow new leader to replicate key3 to the remaining slave
        Thread.sleep(1000);

        // Both db1 and db2 should have key3 after replication
        assertThat(retryGet("http://127.0.0.1:" + lbPort + "/tables/items/keys/key3", 6))
                .isEqualTo("value3");
    }

    private static GenericContainer<?> dbContainer(Path jar, int nodeId) {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
                .withNetwork(network)
                .withNetworkAliases("db" + nodeId + "-db")
                .withEnv("DB_PORT", "7379")
                .withEnv("DATA_DIR", "/tmp/filedb-" + nodeId)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forLogMessage(".*FileDb server started.*", 1).withStartupTimeout(STARTUP_TIMEOUT));
    }

    private static GenericContainer<?> sidecarContainer(Path jar, int nodeId, String alias, String localDbAlias) {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
                .withNetwork(network)
                .withNetworkAliases(alias)
                .withEnv("NODE_ID", String.valueOf(nodeId))
                .withEnv("SIDECAR_HOST", alias)
                .withEnv("LOCAL_DB_HOST", localDbAlias)
                .withEnv("LOCAL_DB_PORT", "7379")
                .withEnv("PROXY_PORT", "7379")
                .withEnv("ELECTION_PORT", "8090")
                .withEnv("HEALTH_CHECK_INTERVAL_MS", "500")
                .withEnv("PEERS", PEERS)
                .withExposedPorts(8090)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forHttp("/status").forPort(8090).withStartupTimeout(STARTUP_TIMEOUT));
    }

    private static GenericContainer<?> statelessServerContainer(Path jar, String alias, String readDbAlias) {
        String electionEndpoints = "db1:8090,db2:8090,db3:8090";
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
                .withNetwork(network)
                .withNetworkAliases(alias)
                .withEnv("SERVER_PORT", "8080")
                .withEnv("DATABASE_URL", readDbAlias + ":7379")
                .withEnv("DATABASE_LEADER_DISCOVERY_ELECTION_ENDPOINTS", electionEndpoints)
                .withEnv("DATABASE_LEADER_DISCOVERY_CACHE_TTL_MS", "1000")
                .withExposedPorts(8080)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forLogMessage(".*Started ServerApplication.*", 1).withStartupTimeout(STARTUP_TIMEOUT));
    }

    private static GenericContainer<?> loadBalancerContainer(Path jar) {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
                .withNetwork(network)
                .withEnv("SERVER_PORT", "8080")
                .withEnv("LOADBALANCER_IPS", "http://server1:8080,http://server2:8080")
                .withEnv("LOADBALANCER_STRATEGY", "round-robin")
                .withExposedPorts(8080)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forLogMessage(".*Started LoadbalancerApplication.*", 1).withStartupTimeout(STARTUP_TIMEOUT));
    }

    private static void awaitLeader(List<GenericContainer<?>> sidecarContainers) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            for (GenericContainer<?> container : sidecarContainers) {
                if (!container.isRunning()) continue;
                try {
                    HttpResponse<String> resp = httpClient.send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create("http://127.0.0.1:" + container.getMappedPort(8090) + "/status"))
                                    .timeout(Duration.ofSeconds(2))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    );
                    if (resp.statusCode() == 200 && resp.body().contains("\"LEADER\"")) {
                        LOG.info("Leader found: " + resp.body());
                        return;
                    }
                } catch (Exception ignored) {}
            }
            Thread.sleep(300);
        }
        throw new AssertionError("No leader elected within 30s among " + sidecarContainers.size() + " sidecars");
    }

    private int post(String url) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        ).statusCode();
    }

    private int put(String url, String body) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "text/plain")
                        .timeout(Duration.ofSeconds(10))
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        ).statusCode();
    }

    private String retryGet(String url, int maxAttempts) throws Exception {
        Exception lastException = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                HttpResponse<String> resp = httpClient.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .GET()
                                .timeout(Duration.ofSeconds(5))
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                if (resp.statusCode() == 200) return resp.body();
            } catch (Exception e) {
                lastException = e;
            }
            if (attempt < maxAttempts - 1) Thread.sleep(500);
        }
        throw new AssertionError("GET " + url + " did not return 200 after " + maxAttempts + " attempts",
                lastException);
    }

    private static void stopIfRunning(GenericContainer<?>... containers) {
        for (GenericContainer<?> c : containers) {
            if (c != null && c.isRunning()) c.stop();
        }
    }

    private static Path moduleJar(String module, String artifactId) {
        return resolveRepoRoot().resolve(module).resolve("target").resolve(artifactId + "-0.0.1-SNAPSHOT.jar");
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        return "tests".equals(cwd.getFileName().toString()) ? cwd.getParent() : cwd;
    }
}
