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
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShardingIT {

    static {
        ProcessPathSanitizer.sanitizePath();
    }

    private static final Logger LOG = Logger.getLogger(ShardingIT.class.getName());
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

    private static Network network;
    private static GenericContainer<?> shard1Db, shard2Db;
    private static GenericContainer<?> shard1Sidecar, shard2Sidecar;
    private static GenericContainer<?> shard1Server, shard2Server;
    private static GenericContainer<?> shard1SidecarF, shard1DbF, shard1ServerF;
    private static GenericContainer<?> lb;
    private static HttpClient httpClient;

    @BeforeAll
    static void start() throws Exception {
        Path databaseJar = moduleJar("database", "database");
        Path sidecarJar = moduleJar("election-sidecar", "election-sidecar");
        Path serverJar = moduleJar("server", "server");
        Path lbJar = moduleJar("loadbalancer", "loadbalancer");

        Assumptions.assumeTrue(Files.isRegularFile(databaseJar), "missing " + databaseJar);
        Assumptions.assumeTrue(Files.isRegularFile(sidecarJar), "missing " + sidecarJar);
        Assumptions.assumeTrue(Files.isRegularFile(serverJar), "missing " + serverJar);
        Assumptions.assumeTrue(Files.isRegularFile(lbJar), "missing " + lbJar);

        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        network = Network.newNetwork();

        // Shard 1 (Leader + Follower)
        String shard1Peers = "1:shard1-sidecar:8090,0:shard1-sidecar-f:8090";
        String shard1Endpoints = "shard1-sidecar:8090,shard1-sidecar-f:8090";
        shard1Db = dbContainer(databaseJar, 1, "shard1-db");
        shard1Db.start();
        shard1Sidecar = sidecarContainer(sidecarJar, 1, "shard1-sidecar", "shard1-db", shard1Peers);
        shard1Sidecar.start();
        shard1Server = serverContainer(serverJar, "shard1-server", "shard1-sidecar", shard1Endpoints);
        shard1Server.start();

        shard1DbF = dbContainer(databaseJar, 0, "shard1-db-f");
        shard1DbF.start();
        shard1SidecarF = sidecarContainer(sidecarJar, 0, "shard1-sidecar-f", "shard1-db-f", shard1Peers);
        shard1SidecarF.start();
        shard1ServerF = serverContainer(serverJar, "shard1-server-f", "shard1-sidecar-f", shard1Endpoints);
        shard1ServerF.start();

        // Shard 2
        String shard2Peers = "2:shard2-sidecar:8090";
        String shard2Endpoints = "shard2-sidecar:8090";
        shard2Db = dbContainer(databaseJar, 2, "shard2-db");
        shard2Db.start();
        shard2Sidecar = sidecarContainer(sidecarJar, 2, "shard2-sidecar", "shard2-db", shard2Peers);
        shard2Sidecar.start();
        shard2Server = serverContainer(serverJar, "shard2-server", "shard2-sidecar", shard2Endpoints);
        shard2Server.start();

        lb = lbContainer(lbJar, List.of(
            new ShardEntry("shard1", "shard1-server:8080", false),
            new ShardEntry("shard2", "shard2-server:8080", false)
        ));
        lb.start();

        awaitLeader(List.of(shard1Sidecar));
        awaitLeader(List.of(shard2Sidecar));
        LOG.info("Leaders elected in both shards");
        Thread.sleep(5000); // Wait for stabilization
    }

    @AfterAll
    static void stop() {
        stopIfRunning(lb, shard2Server, shard1Server, shard1ServerF, shard2Sidecar, shard1Sidecar, shard1SidecarF, shard2Db, shard1Db, shard1DbF);
        if (network != null) network.close();
    }

    @Test
    @Order(1)
    void requestsAreRoutedToCorrectShardsBasedOnKey() throws Exception {
        int lbPort = lb.getMappedPort(8080);
        String baseUrl = "http://127.0.0.1:" + lbPort;
        String table = "table1";
        assertThat(post(baseUrl + "/tables/" + table)).isEqualTo(201);

        String key1 = "u1"; String key2 = "u2"; String key3 = "u3";
        put(baseUrl + "/tables/" + table + "/keys/" + key1, "v1");
        put(baseUrl + "/tables/" + table + "/keys/" + key2, "v2");
        put(baseUrl + "/tables/" + table + "/keys/" + key3, "v3");

        int s1Port = shard1Server.getMappedPort(8080);
        int s2Port = shard2Server.getMappedPort(8080);

        Map<String, String> s1Keys = new java.util.HashMap<>();
        s1Keys.put(key1, get("http://127.0.0.1:" + s1Port + "/tables/" + table + "/keys/" + key1));
        s1Keys.put(key2, get("http://127.0.0.1:" + s1Port + "/tables/" + table + "/keys/" + key2));
        s1Keys.put(key3, get("http://127.0.0.1:" + s1Port + "/tables/" + table + "/keys/" + key3));

        Map<String, String> s2Keys = new java.util.HashMap<>();
        s2Keys.put(key1, get("http://127.0.0.1:" + s2Port + "/tables/" + table + "/keys/" + key1));
        s2Keys.put(key2, get("http://127.0.0.1:" + s2Port + "/tables/" + table + "/keys/" + key2));
        s2Keys.put(key3, get("http://127.0.0.1:" + s2Port + "/tables/" + table + "/keys/" + key3));

        checkKey(key1, s1Keys, s2Keys);
        checkKey(key2, s1Keys, s2Keys);
        checkKey(key3, s1Keys, s2Keys);

        long totalFound = s1Keys.values().stream().filter(java.util.Objects::nonNull).count() + 
                         s2Keys.values().stream().filter(java.util.Objects::nonNull).count();
        assertThat(totalFound).isEqualTo(3);
    }

    @Test
    @Order(2)
    void replicationWithinShardStillWorks() throws Exception {
        int lbPort = lb.getMappedPort(8080);
        String table = "table2";
        assertThat(post("http://127.0.0.1:" + lbPort + "/tables/" + table)).isEqualTo(201);
        
        String key = "key-shard1-replicate";
        put("http://127.0.0.1:" + lbPort + "/tables/" + table + "/keys/" + key, "val-replicated");

        int fServerPort = shard1ServerF.getMappedPort(8080);
        assertThat(retryGet("http://127.0.0.1:" + fServerPort + "/tables/" + table + "/keys/" + key, 10)).isEqualTo("val-replicated");
    }

    @Test
    @Order(3)
    void shardAdditionAndLazyMigration() throws Exception {
        int lbPort = lb.getMappedPort(8080);
        String baseUrl = "http://127.0.0.1:" + lbPort;
        String table = "table3";
        assertThat(post(baseUrl + "/tables/" + table)).isEqualTo(201);
        String keyBefore = "key-add-1";
        put(baseUrl + "/tables/" + table + "/keys/" + keyBefore, "old-val");

        Path databaseJar = moduleJar("database", "database");
        Path sidecarJar = moduleJar("election-sidecar", "election-sidecar");
        Path serverJar = moduleJar("server", "server");
        Path lbJar = moduleJar("loadbalancer", "loadbalancer");

        GenericContainer<?> shard3Db = dbContainer(databaseJar, 3, "shard3-db");
        shard3Db.start();
        GenericContainer<?> shard3Sidecar = sidecarContainer(sidecarJar, 3, "shard3-sidecar", "shard3-db", "3:shard3-sidecar:8090");
        shard3Sidecar.start();
        GenericContainer<?> shard3Server = serverContainer(serverJar, "shard3-server", "shard3-sidecar", "shard3-sidecar:8090");
        shard3Server.start();

        awaitLeader(List.of(shard3Sidecar));

        lb.stop();
        lb = lbContainer(lbJar, List.of(
            new ShardEntry("shard1", "shard1-server:8080", false),
            new ShardEntry("shard2", "shard2-server:8080", false),
            new ShardEntry("shard3", "shard3-server:8080", false)
        ));
        lb.start();

        assertThat(retryGet("http://127.0.0.1:" + lb.getMappedPort(8080) + "/tables/" + table + "/keys/" + keyBefore, 10)).isEqualTo("old-val");

        shard3Server.stop(); shard3Sidecar.stop(); shard3Db.stop();
    }

    @Test
    @Order(4)
    void shardRemovalAndGracefulDecommissioning() throws Exception {
        Path lbJar = moduleJar("loadbalancer", "loadbalancer");
        lb.stop();
        lb = lbContainer(lbJar, List.of(
            new ShardEntry("shard1", "shard1-server:8080", false),
            new ShardEntry("shard2", "shard2-server:8080", false)
        ));
        lb.start();

        int lbPort = lb.getMappedPort(8080);
        String baseUrl = "http://127.0.0.1:" + lbPort;
        String table = "table4";
        assertThat(post(baseUrl + "/tables/" + table)).isEqualTo(201);

        String keyInShard2 = "key-rem-1"; 
        put(baseUrl + "/tables/" + table + "/keys/" + keyInShard2, "val-rem");

        lb.stop();
        lb = lbContainer(lbJar, List.of(
            new ShardEntry("shard1", "shard1-server:8080", false),
            new ShardEntry("shard2", "shard2-server:8080", true)
        ));
        lb.start();

        lbPort = lb.getMappedPort(8080);
        assertThat(retryGet("http://127.0.0.1:" + lbPort + "/tables/" + table + "/keys/" + keyInShard2, 10)).isEqualTo("val-rem");
        
        int s1Port = shard1Server.getMappedPort(8080);
        assertThat(post("http://127.0.0.1:" + s1Port + "/tables/" + table + "/migrate-from?sourceUrl=shard2-sidecar:7379")).isEqualTo(200);

        lb.stop();
        lb = lbContainer(lbJar, List.of(new ShardEntry("shard1", "shard1-server:8080", false)));
        lb.start();

        assertThat(retryGet("http://127.0.0.1:" + lb.getMappedPort(8080) + "/tables/" + table + "/keys/" + keyInShard2, 10)).isEqualTo("val-rem");
    }

    private void checkKey(String key, Map<String, String> s1, Map<String, String> s2) {
        String v1 = s1.get(key); String v2 = s2.get(key);
        assertThat((v1 != null && v2 == null) || (v1 == null && v2 != null)).isTrue();
    }

    private static class ShardEntry {
        String id; String backend; boolean decomm;
        ShardEntry(String id, String backend, boolean decomm) { this.id = id; this.backend = backend; this.decomm = decomm; }
    }

    private static GenericContainer<?> lbContainer(Path jar, List<ShardEntry> shards) {
        GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
                .withNetwork(network).withNetworkAliases("lb")
                .withEnv("SERVER_PORT", "8080").withEnv("LOADBALANCER_STRATEGY", "SHARDING_CONSISTENT_HASH")
                .withEnv("LOADBALANCER_SHARDING_PATH_PATTERN", "/tables/[^/]+/keys/([^/]+)");
        for (int i = 0; i < shards.size(); i++) {
            ShardEntry s = shards.get(i);
            c.withEnv("LOADBALANCER_SHARDS_" + i + "_ID", s.id);
            c.withEnv("LOADBALANCER_SHARDS_" + i + "_BACKENDS_0", "http://" + s.backend);
            c.withEnv("LOADBALANCER_SHARDS_" + i + "_DECOMMISSIONING", String.valueOf(s.decomm));
        }
        return c.withExposedPorts(8080).withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forLogMessage(".*Started LoadbalancerApplication.*", 1).withStartupTimeout(STARTUP_TIMEOUT));
    }

    private static GenericContainer<?> dbContainer(Path jar, int nodeId, String alias) {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
                .withNetwork(network).withNetworkAliases(alias)
                .withEnv("DB_PORT", "7379").withEnv("DATA_DIR", "/tmp/filedb-" + nodeId)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forLogMessage(".*FileDb server started.*", 1).withStartupTimeout(STARTUP_TIMEOUT));
    }

    private static GenericContainer<?> sidecarContainer(Path jar, int nodeId, String alias, String localDbAlias, String peers) {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
                .withNetwork(network).withNetworkAliases(alias)
                .withEnv("NODE_ID", String.valueOf(nodeId)).withEnv("SIDECAR_HOST", alias)
                .withEnv("LOCAL_DB_HOST", localDbAlias).withEnv("LOCAL_DB_PORT", "7379")
                .withEnv("PROXY_PORT", "7379").withEnv("ELECTION_PORT", "8090").withEnv("PEERS", peers)
                .withExposedPorts(8090).withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forHttp("/status").forPort(8090).withStartupTimeout(STARTUP_TIMEOUT));
    }

    private static GenericContainer<?> serverContainer(Path jar, String alias, String sidecarAlias, String electionEndpoints) {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
                .withNetwork(network).withNetworkAliases(alias)
                .withEnv("SERVER_PORT", "8080").withEnv("DATABASE_URL", sidecarAlias + ":7379")
                .withEnv("DATABASE_LEADER_DISCOVERY_ELECTION_ENDPOINTS", electionEndpoints)
                .withExposedPorts(8080).withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
                .waitingFor(Wait.forLogMessage(".*Started ServerApplication.*", 1).withStartupTimeout(STARTUP_TIMEOUT));
    }

    private int post(String url) throws Exception {
        HttpResponse<String> resp = httpClient.send(HttpRequest.newBuilder().uri(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        return resp.statusCode();
    }

    private void put(String url, String body) throws Exception {
        httpClient.send(HttpRequest.newBuilder().uri(URI.create(url)).PUT(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "text/plain").build(), HttpResponse.BodyHandlers.discarding());
    }

    private String get(String url) throws Exception {
        HttpResponse<String> resp = httpClient.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 200 ? resp.body() : null;
    }

    private String retryGet(String url, int maxAttempts) throws Exception {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String res = get(url); if (res != null) return res;
            Thread.sleep(1500);
        }
        return null;
    }

    private static void awaitLeader(List<GenericContainer<?>> sidecarContainers) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            for (GenericContainer<?> container : sidecarContainers) {
                try {
                    HttpResponse<String> resp = httpClient.send(HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + container.getMappedPort(8090) + "/status")).build(), HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 && resp.body().contains("\"LEADER\"")) return;
                } catch (Exception ignored) {}
            }
            Thread.sleep(1000);
        }
    }

    private static void stopIfRunning(GenericContainer<?>... containers) {
        for (GenericContainer<?> c : containers) { if (c != null && c.isRunning()) c.stop(); }
    }

    private static Path moduleJar(String module, String artifactId) {
        return resolveRepoRoot().resolve(module).resolve("target").resolve(artifactId + "-0.0.1-SNAPSHOT.jar");
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        return "tests".equals(cwd.getFileName().toString()) ? cwd.getParent() : cwd;
    }
}
