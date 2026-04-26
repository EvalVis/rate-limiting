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
class StrongConsistencyIT {

    static {
        ProcessPathSanitizer.sanitizePath();
    }

    private static final Logger LOG = Logger.getLogger(StrongConsistencyIT.class.getName());
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final String PEERS = "1:db1:8090,2:db2:8090,3:db3:8090";

    private static Network network;
    private static GenericContainer<?> db1Db, db2Db, db3Db;
    private static GenericContainer<?> db1Sidecar, db2Sidecar, db3Sidecar;
    private static GenericContainer<?> server1;
    private static HttpClient httpClient;

    @BeforeAll
    static void start() throws Exception {
        Path databaseJar = moduleJar("database", "database");
        Path sidecarJar = moduleJar("election-sidecar", "election-sidecar");
        Path serverJar = moduleJar("server", "server");

        Assumptions.assumeTrue(Files.isRegularFile(databaseJar), "missing " + databaseJar);
        Assumptions.assumeTrue(Files.isRegularFile(sidecarJar), "missing " + sidecarJar);
        Assumptions.assumeTrue(Files.isRegularFile(serverJar), "missing " + serverJar);

        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        network = Network.newNetwork();

        db1Db = dbContainer(databaseJar, 1);
        db2Db = dbContainer(databaseJar, 2);
        db3Db = dbContainer(databaseJar, 3);
        db1Db.start();
        db2Db.start();
        db3Db.start();

        db1Sidecar = sidecarContainer(sidecarJar, 1, "db1", "db1-db", "STRICT", 2);
        db2Sidecar = sidecarContainer(sidecarJar, 2, "db2", "db2-db", "STRICT", 2);
        db3Sidecar = sidecarContainer(sidecarJar, 3, "db3", "db3-db", "STRICT", 2);
        db1Sidecar.start();
        db2Sidecar.start();
        db3Sidecar.start();

        awaitLeader(List.of(db1Sidecar, db2Sidecar, db3Sidecar));

        server1 = strictServerContainer(serverJar, "server1", "db1", "STRICT", 2);
        server1.start();
    }

    @AfterAll
    static void stop() {
        stopIfRunning(server1, db3Sidecar, db2Sidecar, db1Sidecar, db3Db, db2Db, db1Db);
        if (network != null) network.close();
    }

    @Test
    @Order(1)
    void whenAllNodesUp_writeAndReadSucceed() throws Exception {
        int s1Port = server1.getMappedPort(8080);
        String urlBase = "http://127.0.0.1:" + s1Port;

        assertThat(post(urlBase + "/tables/consistent")).isEqualTo(201);
        assertThat(put(urlBase + "/tables/consistent/keys/k1", "v1")).isEqualTo(204);
        assertThat(get(urlBase + "/tables/consistent/keys/k1")).isEqualTo("v1");
        
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(urlBase + "/tables/consistent/keys/missing")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(resp.statusCode()).isEqualTo(404);
    }

    @Test
    @Order(2)
    void whenOneNodeDown_quorumStillMet_writeAndReadSucceed() throws Exception {
        int s1Port = server1.getMappedPort(8080);
        String urlBase = "http://127.0.0.1:" + s1Port;
        
        db1Sidecar.stop();
        db1Db.stop();
        LOG.info("Killed node 1");

        // Allow system to detect failure and elect new leader if needed
        Thread.sleep(5000);

        // N=3, W=2, R=2. With 2 nodes alive, it should still work.
        assertThat(putWithRetry(urlBase + "/tables/consistent/keys/k2", "v2", 10)).isEqualTo(204);
        assertThat(getWithRetry(urlBase + "/tables/consistent/keys/k2", 10)).isEqualTo("v2");
    }

    @Test
    @Order(3)
    void whenTwoNodesDown_quorumFails_writeAndReadFail() throws Exception {
        int s1Port = server1.getMappedPort(8080);
        String urlBase = "http://127.0.0.1:" + s1Port;

        db2Sidecar.stop();
        db2Db.stop();
        LOG.info("Killed node 2. Only node 3 remains.");

        Thread.sleep(5000);

        // Write should fail (503) because W=2 but only 1 node available
        int putStatus = put(urlBase + "/tables/consistent/keys/k3", "v3");
        assertThat(putStatus).isEqualTo(503);

        // Read should fail (503) because R=2 but only 1 node available
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(urlBase + "/tables/consistent/keys/k1")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(resp.statusCode()).isEqualTo(503);
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

    private static GenericContainer<?> sidecarContainer(Path jar, int nodeId, String alias, String localDbAlias, String mode, int w) {
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
                .withEnv("CONSISTENCY_MODE", mode)
                .withEnv("QUORUM_W", String.valueOf(w))
                .withExposedPorts(8090)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forHttp("/status").forPort(8090).withStartupTimeout(STARTUP_TIMEOUT));
    }

    private static GenericContainer<?> strictServerContainer(Path jar, String alias, String readDbAlias, String mode, int r) {
        String electionEndpoints = "db1:8090,db2:8090,db3:8090";
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
                .withNetwork(network)
                .withNetworkAliases(alias)
                .withEnv("SERVER_PORT", "8080")
                .withEnv("DATABASE_URL", readDbAlias + ":7379")
                .withEnv("DATABASE_LEADER_DISCOVERY_ELECTION_ENDPOINTS", electionEndpoints)
                .withEnv("DATABASE_LEADER_DISCOVERY_CACHE_TTL_MS", "1000")
                .withEnv("DATABASE_CONSISTENCY_MODE", mode)
                .withEnv("DATABASE_CONSISTENCY_QUORUM_R", String.valueOf(r))
                .withExposedPorts(8080)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forLogMessage(".*Started ServerApplication.*", 1).withStartupTimeout(STARTUP_TIMEOUT));
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
                        return;
                    }
                } catch (Exception ignored) {}
            }
            Thread.sleep(500);
        }
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
                        .timeout(Duration.ofSeconds(15))
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        ).statusCode();
    }

    private int putWithRetry(String url, String body, int retries) throws Exception {
        for (int i = 0; i < retries; i++) {
            int status = put(url, body);
            if (status == 204) return status;
            Thread.sleep(1000);
        }
        return put(url, body);
    }

    private String get(String url) throws Exception {
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (resp.statusCode() != 200) throw new RuntimeException("Status " + resp.statusCode() + ": " + resp.body());
        return resp.body();
    }

    private String getWithRetry(String url, int retries) throws Exception {
        for (int i = 0; i < retries; i++) {
            try {
                return get(url);
            } catch (Exception e) {
                Thread.sleep(1000);
            }
        }
        return get(url);
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
