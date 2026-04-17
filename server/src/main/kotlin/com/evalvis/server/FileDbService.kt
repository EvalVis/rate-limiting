package com.evalvis.server

import com.evalvis.database.FileDbClient
import com.evalvis.database.TableNotFoundException
import com.evalvis.database.TcpFileDbClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Optional

@Service
class FileDbService(
    @Value("\${database.url:127.0.0.1:7379}")
    databaseUrl: String
) {
    private val client: FileDbClient = createClient(databaseUrl)

    fun createTable(tableName: String) {
        execute { client.createTable(tableName) }
    }

    fun put(tableName: String, key: String, value: String) {
        execute { client.put(tableName, key, value) }
    }

    fun get(tableName: String, key: String): Optional<String> {
        return execute { client.get(tableName, key) }
    }

    private fun createClient(databaseUrl: String): FileDbClient {
        val tokens = databaseUrl.split(":", limit = 2)
        if (tokens.size != 2) {
            throw IllegalArgumentException("database.url must be in host:port format")
        }
        return TcpFileDbClient(tokens[0], tokens[1].toInt())
    }

    private fun <T> execute(action: () -> T): T {
        try {
            return action()
        } catch (_: TableNotFoundException) {
            throw com.evalvis.server.TableNotFoundException()
        } catch (exception: Exception) {
            throw DatabaseClientException("Database unavailable")
        }
    }
}
