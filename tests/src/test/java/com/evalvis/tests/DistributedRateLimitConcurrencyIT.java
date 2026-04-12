package com.evalvis.tests;

import static org.assertj.core.api.Assertions.assertThat;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.testcontainers.utility.MountableFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class DistributedRateLimitConcurrencyIT {

	static {
		ProcessPathSanitizer.sanitizePath();
	}

	private static final int REDIS_PROXY_LISTEN_PORT = 8666;

	private static final int LATENCY_MS = 5000;

	private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(2);

	private static Network network;

	private static GenericContainer<?> redis;

	private static ToxiproxyContainer toxiproxy;

	private static Proxy redisProxy;

	private static GenericContainer<?> server;

	private static GenericContainer<?> ratelimiter1;

	private static GenericContainer<?> ratelimiter2;

	private static HttpClient httpClient;

	@BeforeAll
	static void start() throws Exception {
		Path ratelimiterJar = moduleJar("ratelimiter", "ratelimiter");
		Path serverJar = moduleJar("server", "server");
		Assumptions.assumeTrue(Files.isRegularFile(ratelimiterJar),
				"missing " + ratelimiterJar + "; run mvn -f ../ratelimiter/pom.xml package");
		Assumptions.assumeTrue(Files.isRegularFile(serverJar),
				"missing " + serverJar + "; run mvn -f ../server/pom.xml package");

		httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

		network = Network.newNetwork();
		redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withNetwork(network)
			.withNetworkAliases("redis")
			.withExposedPorts(6379);
		toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.11.0")
			.withNetwork(network)
			.withNetworkAliases("toxiproxy")
			.dependsOn(redis);
		redis.start();
		toxiproxy.start();
		ToxiproxyClient toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
		redisProxy = toxiproxyClient.createProxy("redis", "0.0.0.0:" + REDIS_PROXY_LISTEN_PORT, "redis:6379");
		redisProxy.toxics().latency("down", ToxicDirection.DOWNSTREAM, LATENCY_MS);

		server = new GenericContainer<>(DockerImageName.parse("eclipse-temurin:24-jre"))
			.withNetwork(network)
			.withNetworkAliases("server")
			.withEnv("SERVER_PORT", "8082")
			.withExposedPorts(8082)
			.withCopyFileToContainer(MountableFile.forHostPath(serverJar), "/app.jar")
			.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
			.waitingFor(Wait.forLogMessage(".*Started ServerApplication.*", 1).withStartupTimeout(STARTUP_TIMEOUT))
			.dependsOn(redis, toxiproxy);
		server.start();

		ratelimiter1 = ratelimiterContainer(ratelimiterJar, "8080");
		ratelimiter2 = ratelimiterContainer(ratelimiterJar, "8081");
		ratelimiter1.start();
		ratelimiter2.start();
	}

	private static GenericContainer<?> ratelimiterContainer(Path jar, String serverPort) {
		return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))
			.withNetwork(network)
			.withEnv("SERVER_PORT", serverPort)
			.withEnv("RATELIMITER_REDIS_ENABLED", "true")
			.withEnv("SPRING_DATA_REDIS_HOST", "toxiproxy")
			.withEnv("SPRING_DATA_REDIS_PORT", String.valueOf(REDIS_PROXY_LISTEN_PORT))
			.withEnv("RATELIMITER_FORWARD_HOST", "server")
			.withEnv("RATELIMITER_FORWARD_PORT", "8082")
			.withEnv("RATELIMITER_FORWARD_SCHEME", "http")
			.withEnv("RATELIMITER_RATE_LIMIT_CAPACITY", "1")
			.withEnv("RATELIMITER_RATE_LIMIT_REFILL_PER_SECOND", "0")
			.withExposedPorts(Integer.parseInt(serverPort))
			.withCopyFileToContainer(MountableFile.forHostPath(jar), "/app.jar")
			.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java", "-jar", "/app.jar"))
			.waitingFor(Wait.forLogMessage(".*Started RatelimiterApplication.*", 1).withStartupTimeout(STARTUP_TIMEOUT))
			.dependsOn(server, toxiproxy, redis);
	}

	private static Path moduleJar(String module, String artifactId) {
		return resolveRepoRoot().resolve(module).resolve("target").resolve(artifactId + "-0.0.1-SNAPSHOT.jar");
	}

	private static Path resolveRepoRoot() {
		Path cwd = Path.of("").toAbsolutePath();
		if ("tests".equals(cwd.getFileName().toString())) {
			return cwd.getParent();
		}
		return cwd;
	}

	@AfterAll
	static void stop() {
		if (ratelimiter2 != null && ratelimiter2.isRunning()) {
			ratelimiter2.stop();
		}
		if (ratelimiter1 != null && ratelimiter1.isRunning()) {
			ratelimiter1.stop();
		}
		if (server != null && server.isRunning()) {
			server.stop();
		}
		if (redisProxy != null) {
			try {
				redisProxy.delete();
			}
			catch (IOException ignored) {
			}
		}
		if (toxiproxy != null && toxiproxy.isRunning()) {
			toxiproxy.stop();
		}
		if (redis != null && redis.isRunning()) {
			redis.stop();
		}
		if (network != null) {
			network.close();
		}
	}

	@Test
	void firstRequestOkSecondTooManyWhileFirstBlockedOnRedis() throws Exception {
		String rl1Base = "http://127.0.0.1:" + ratelimiter1.getMappedPort(8080);
		String rl2Base = "http://127.0.0.1:" + ratelimiter2.getMappedPort(8081);
		HttpRequest req1 = HttpRequest.newBuilder(URI.create(rl1Base + "/hello")).GET().timeout(Duration.ofSeconds(60)).build();
		HttpRequest req2 = HttpRequest.newBuilder(URI.create(rl2Base + "/hello")).GET().timeout(Duration.ofSeconds(60)).build();

		CompletableFuture<HttpResponse<String>> first = httpClient.sendAsync(req1, HttpResponse.BodyHandlers.ofString());
		Thread.sleep(200);
		HttpResponse<String> second = httpClient.send(req2, HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> firstDone = first.join();

		assertThat(second.statusCode())
			.as("expected 429 when shared Redis bucket is empty; if both are 200, suspect distributed race or misconfiguration")
			.isEqualTo(429);
		assertThat(firstDone.statusCode()).isEqualTo(200);
	}

}
