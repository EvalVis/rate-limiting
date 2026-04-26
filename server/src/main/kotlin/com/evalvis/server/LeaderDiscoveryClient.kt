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

    @Volatile private var cachedLeader: Pair<String, Long>? = null
    @Volatile private var cachedNodes: Pair<List<String>, Long>? = null

    fun findLeaderDbAddress(): String {
        val now = System.currentTimeMillis()
        cachedLeader?.let { (addr, exp) -> if (now < exp) return addr }

        val status = fetchAnyStatus()
        val host = status["leaderHost"]?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("No leader host found")
        val port = status["leaderDbPort"]?.takeIf { it.isNotBlank() && it != "0" } ?: throw IllegalStateException("No leader port found")
        val addr = "$host:$port"
        cachedLeader = addr to (now + cacheTtlMs)
        return addr
    }

    fun findAllProxyAddresses(): List<String> {
        val now = System.currentTimeMillis()
        cachedNodes?.let { (nodes, exp) -> if (now < exp) return nodes }

        val nodes = mutableListOf<String>()
        for (endpoint in electionEndpoints) {
            runCatching { fetchStatus(endpoint) }
                .getOrNull()
                ?.let { s ->
                    val host = s["selfHost"] ?: return@let
                    val port = s["selfDbPort"] ?: return@let
                    nodes.add("$host:$port")
                }
        }
        
        if (nodes.isEmpty()) throw IllegalStateException("No proxy nodes found")
        
        cachedNodes = nodes to (now + cacheTtlMs)
        return nodes
    }

    fun invalidate() {
        cachedLeader = null
        cachedNodes = null
    }

    private fun fetchAnyStatus(): Map<String, String> {
        for (endpoint in electionEndpoints) {
            runCatching { fetchStatus(endpoint) }
                .getOrNull()
                ?.let { return it }
        }
        throw IllegalStateException("Could not reach any election endpoint: $electionEndpoints")
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
