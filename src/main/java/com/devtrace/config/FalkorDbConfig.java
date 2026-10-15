package com.devtrace.config;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FalkorDbConfig {

    /**
     * Singleton FalkorDB Driver bean.
     * 
     * Connection Pooling Decision:
     * The JFalkorDB Driver manages an internal thread-safe socket connection pool (backed by Apache Commons Pool2).
     * Establishing a single Spring bean with destroyMethod = "close" avoids per-request connection overhead,
     * prevents socket descriptor exhaustion under load, and ensures orderly shutdown when the application stops.
     */
    @Bean(destroyMethod = "close")
    public Driver falkorDriver(FalkorDbProperties properties) {
        var builder = FalkorDB.builder()
                .host(properties.getHost())
                .port(properties.getPort());

        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            builder.credentials(properties.getUsername(), properties.getPassword());
        }

        return builder.build();
    }
}
