package com.evalvis.server

import com.evalvis.database.FileDb
import com.evalvis.database.TableNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.util.Optional

@Service
class FileDbService(
    @Value("\${filedb.root-dir:#{null}}")
    rootDir: String?
) {
    private val fileDb = if (rootDir.isNullOrBlank()) {
        FileDb()
    } else {
        FileDb(Path.of(rootDir))
    }

    fun createTable(tableName: String) {
        fileDb.createTable(tableName)
    }

    fun put(tableName: String, key: String, value: String) {
        fileDb.put(tableName, key, value)
    }

    fun get(tableName: String, key: String): Optional<String> {
        return try {
            fileDb.get(tableName, key)
        } catch (_: TableNotFoundException) {
            Optional.empty()
        }
    }
}
