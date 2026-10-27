package com.intics.modelradar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the REST API. The Vaadin dashboard is served by this same app
 * (same-origin, so it needs no CORS); this exists for external/dev consumers of
 * the API — including a browser frontend on a dev port such as
 * {@code http://localhost:3000}. Internal tool on a trusted network, so kept
 * permissive (any localhost port) but scoped to {@code /api/**}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // Covers http://localhost:3000 and any other local dev port.
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
