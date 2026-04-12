package com.evalvis.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HelloController::class)
class HelloControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `when GET hello then responds with hello`() {
        mockMvc.perform(get("/hello"))
            .andExpect(status().isOk)
            .andExpect(content().string("hello"))
    }
}
