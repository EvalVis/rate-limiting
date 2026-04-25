package com.evalvis.server

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/tables")
class FileDbController(
    private val fileDbService: FileDbService
) {

    @PostMapping("/{tableName}")
    fun createTable(@PathVariable tableName: String): ResponseEntity<Unit> {
        fileDbService.createTable(tableName)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PutMapping("/{tableName}/keys/{key}")
    fun put(
        @PathVariable tableName: String,
        @PathVariable key: String,
        @RequestBody value: String
    ): ResponseEntity<Unit> {
        fileDbService.put(tableName, key, value)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{tableName}/keys/{key}")
    fun get(@PathVariable tableName: String, @PathVariable key: String): ResponseEntity<String> {
        return fileDbService.get(tableName, key)
            .map { ResponseEntity.ok(it) }
            .orElse(ResponseEntity.notFound().build())
    }

    @PostMapping("/{tableName}/migrate-from")
    fun migrateFrom(
        @PathVariable tableName: String,
        @RequestParam(name = "sourceUrl") sourceUrl: String
    ): ResponseEntity<Unit> {
        fileDbService.migrateFrom(tableName, sourceUrl)
        return ResponseEntity.ok().build()
    }

    @ExceptionHandler(TableNotFoundException::class)
    fun onTableNotFound(): ResponseEntity<Unit> {
        return ResponseEntity.notFound().build()
    }

    @ExceptionHandler(DatabaseClientException::class)
    fun onDatabaseUnavailable(): ResponseEntity<Unit> {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
    }
}
