package com.evalvis.server

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class FileDbController(
    private val fileDbService: FileDbService
) {

    @PostMapping("/tables/{tableName}")
    fun createTable(@PathVariable tableName: String): ResponseEntity<Unit> {
        fileDbService.createTable(tableName)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @PutMapping("/tables/{tableName}/keys/{key}")
    fun put(
        @PathVariable tableName: String,
        @PathVariable key: String,
        @RequestBody value: String
    ): ResponseEntity<Unit> {
        fileDbService.put(tableName, key, value)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/tables/{tableName}/keys/{key}")
    fun get(@PathVariable tableName: String, @PathVariable key: String): ResponseEntity<String> {
        val value = fileDbService.get(tableName, key)
        return value.map { ResponseEntity.ok(it) }.orElseGet { ResponseEntity.notFound().build() }
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
