package com.evalvis.server

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class LeaderDiscoveryClient(
    val electionEndpoints: List<String>,
    private val cacheTtlMs: Long = 5000
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    @Volatile private var cached: Pair<String, Long>? = null

    fun findLeaderDbAddress(): String {
        val now = System.currentTimeMillis()
        cached?.let { (addr, exp) -> if (now < exp) return addr }

        for (endpoint in electionEndpoints) {
            runCatching { fetchStatus(endpoint) }
                .getOrNull()
                ?.let { status ->
                    val host = status["leaderHost"]?.takeIf { it.isNotBlank() } ?: return@let null
                    val port = status["leaderDbPort"]?.takeIf { it.isNotBlank() && it != "0" } ?: return@let null
                    "$host:$port"
                }
                ?.also { addr ->
                    cached = addr to (now + cacheTtlMs)
                    return addr
                }
        }
        throw IllegalStateException("No leader found from election endpoints: $electionEndpoints")
    }

    fun invalidate() {
        cached = null
    }

    private fun fetchStatus(endpoint: String): Map<String, String> {
        val body = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://$endpoint/status"))
                .timeout(Duration.ofSeconds(2))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).body()
        return parseJson(body)
    }

    private fun parseJson(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val content = json.trim().removeSurrounding("{", "}")
        val stringPattern = Regex(""""(\w+)"\s*:\s*"([^"]*)"""")
        val numberPattern = Regex(""""(\w+)"\s*:\s*(-?\d+)""")
        stringPattern.findAll(content).forEach { result[it.groupValues[1]] = it.groupValues[2] }
        numberPattern.findAll(content).forEach { result.putIfAbsent(it.groupValues[1], it.groupValues[2]) }
        return result
    }
}
