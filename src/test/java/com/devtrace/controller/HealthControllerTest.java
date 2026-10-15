package com.devtrace.controller;

import com.devtrace.config.EnvLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class HealthControllerTest {

    @Autowired
    private HealthController healthController;

    private MockMvc mockMvc;

    @BeforeAll
    static void setup() {
        EnvLoader.loadLocalEnv();
    }

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(healthController).build();
    }

    @Test
    void healthEndpointReturnsUpWhenDatabaseIsRunning() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.database.graph").value("devtrace"));
    }
}
