package com.devtrace;

import com.devtrace.config.EnvLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DevTraceApplicationIT {

    @BeforeAll
    static void setup() {
        EnvLoader.loadLocalEnv();
    }

    @Test
    void contextLoads() {
    }
}
