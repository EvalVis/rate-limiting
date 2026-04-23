package com.evalvis.server

import com.evalvis.database.FileDbClient
import com.evalvis.database.TableNotFoundException
import com.evalvis.database.TcpFileDbClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Optional

@Service
class FileDbService(
    @Value("\${database.url:127.0.0.1:7379}") private val readDbUrl: String,
    @Value("\${database.leader-discovery.election-endpoints:}") electionEndpointsRaw: String,
    @Value("\${database.leader-discovery.cache-ttl-ms:5000}") cacheTtlMs: Long
) {
    private val leaderDiscovery: LeaderDiscoveryClient? =
        electionEndpointsRaw.trim()
            .takeIf { it.isNotBlank() }
            ?.let { raw -> LeaderDiscoveryClient(raw.split(",").map { it.trim() }, cacheTtlMs) }

    fun createTable(tableName: String) {
        executeWrite { it.createTable(tableName) }
    }

    fun put(tableName: String, key: String, value: String) {
        executeWrite { it.put(tableName, key, value) }
    }

    fun get(tableName: String, key: String): Optional<String> {
        return executeRead { it.get(tableName, key) }
    }

    private fun <T> executeWrite(action: (FileDbClient) -> T): T {
        try {
            return execute { action(writeClient()) }
        } catch (e: DatabaseClientException) {
            leaderDiscovery?.invalidate()
            return execute { action(writeClient()) }
        }
    }

    private fun <T> executeRead(action: (FileDbClient) -> T): T {
        return execute { action(createClient(readDbUrl)) }
    }

    private fun writeClient(): FileDbClient =
        createClient(leaderDiscovery?.findLeaderDbAddress() ?: readDbUrl)

    private fun <T> execute(action: () -> T): T {
        try {
            return action()
        } catch (_: TableNotFoundException) {
            throw com.evalvis.server.TableNotFoundException()
        } catch (_: Exception) {
            throw DatabaseClientException("Database unavailable")
        }
    }

    private fun createClient(url: String): FileDbClient {
        val tokens = url.split(":", limit = 2)
        require(tokens.size == 2) { "database url must be host:port, got: $url" }
        return TcpFileDbClient(tokens[0], tokens[1].toInt())
    }
}
