package com.pspswitch.orchestrator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // Allow requests from browser (http/https), from file:// (null origin),
                // and from localhost dev servers
                .allowedOriginPatterns("*", "null", "file://*")
                .allowedOrigins("null") // needed for file:// opened HTML
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false); // must be false with wildcard origins
    }
}
