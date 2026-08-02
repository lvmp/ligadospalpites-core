package com.ligadospalpites.web

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.shared.config.TraceIdFilter
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class LogbookTraceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var wac: WebApplicationContext

    @Autowired
    private lateinit var traceIdFilter: TraceIdFilter

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
            .addFilter<DefaultMockMvcBuilder>(traceIdFilter)
            .build()
    }

    @Test
    fun `should include X-Trace-Id header in HTTP response and propagate custom traceId`() {
        val customTraceId = UUID.randomUUID().toString()

        mockMvc.perform(
            get("/api/v1/sports/leagues")
                .header("X-Trace-Id", customTraceId)
        )
            .andExpect(status().isOk)
            .andExpect(header().string("X-Trace-Id", customTraceId))
    }

    @Test
    fun `should generate new X-Trace-Id header when incoming request does not provide one`() {
        mockMvc.perform(get("/api/v1/sports/leagues"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Trace-Id", notNullValue()))
    }
}
