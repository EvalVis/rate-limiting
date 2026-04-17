package com.evalvis.server

import com.evalvis.database.FileDbTcpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files

@SpringBootTest
@AutoConfigureMockMvc
class FileDbControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `when creating table then request succeeds`() {
        mockMvc.perform(post("/tables/users"))
            .andExpect(status().isCreated)
    }

    @Test
    fun `when putting and then getting key then returns latest value`() {
        mockMvc.perform(post("/tables/users"))
            .andExpect(status().isCreated)

        mockMvc.perform(put("/tables/users/keys/alice").contentType("text/plain").content("v1"))
            .andExpect(status().isNoContent)
        mockMvc.perform(put("/tables/users/keys/alice").contentType("text/plain").content("v2"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/tables/users/keys/alice"))
            .andExpect(status().isOk)
            .andExpect(content().string("v2"))
    }

    @Test
    fun `when key does not exist then returns not found`() {
        mockMvc.perform(post("/tables/users"))
            .andExpect(status().isCreated)

        mockMvc.perform(get("/tables/users/keys/missing"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `when table does not exist on put then returns not found`() {
        mockMvc.perform(put("/tables/missing/keys/alice").contentType("text/plain").content("v1"))
            .andExpect(status().isNotFound)
    }

    companion object {
        @JvmStatic
        private lateinit var fileDbTcpServer: FileDbTcpServer

        @JvmStatic
        @BeforeAll
        fun startServer() {
            val tempDir = Files.createTempDirectory("database-integration-test")
            fileDbTcpServer = FileDbTcpServer(0, tempDir)
            fileDbTcpServer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            fileDbTcpServer.close()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("database.url") { "127.0.0.1:${fileDbTcpServer.port()}" }
        }
    }
}
