package com.devtrace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final DevTraceTokenInterceptor tokenInterceptor;
    private final String allowedOrigin;

    public WebMvcConfig(
            DevTraceTokenInterceptor tokenInterceptor,
            @Value("${devtrace.cors.allowed-origin:http://localhost:5173}") String allowedOrigin
    ) {
        this.tokenInterceptor = tokenInterceptor;
        this.allowedOrigin = allowedOrigin;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/api/incidents/*/resolution");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", DevTraceTokenInterceptor.TOKEN_HEADER)
                .maxAge(3600);
    }
}
