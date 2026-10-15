package com.devtrace;

import com.devtrace.config.EnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DevTraceApplication {

    public static void main(String[] args) {
        EnvLoader.loadLocalEnv();
        SpringApplication.run(DevTraceApplication.class, args);
    }
}
