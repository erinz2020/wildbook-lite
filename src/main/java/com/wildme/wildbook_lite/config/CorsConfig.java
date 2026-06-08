package com.wildme.wildbook_lite.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Cross-Origin Resource Sharing.
 *
 * Why a dedicated CorsFilter and not just `.cors(Customizer.withDefaults())`
 * in SecurityConfig:
 *  - With JWT in the Authorization header, the browser issues a preflight
 *    OPTIONS request. The CorsFilter must run BEFORE Spring Security,
 *    otherwise the preflight gets rejected with 401 (preflights are
 *    unauthenticated by design).
 *  - Registering the CorsFilter as a bean makes Spring Security pick it up
 *    automatically via .cors() on the chain.
 *
 * Allowed origins are configured per environment. Hard-coded for dev here.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        cfg.setExposedHeaders(List.of("X-Request-Id"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L); // browser caches preflight for 1h

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(source);
    }
}
