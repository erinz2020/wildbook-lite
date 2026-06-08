package com.wildme.wildbook_lite.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.wildme.wildbook_lite.auth.CurrentUserArgumentResolver;

/**
 * Customise Spring MVC without giving up its auto-configuration.
 *
 * The pattern (rather than @EnableWebMvc, which disables Spring Boot's
 * sensible defaults): implement WebMvcConfigurer and override only what
 * you need.
 *
 *  - addInterceptors:  per-handler middleware (logging, timing, throttling).
 *  - addArgumentResolvers:  custom @CurrentUser parameter binding.
 *  - Other hooks include addCorsMappings (we use CorsFilter instead),
 *    addFormatters (e.g., custom enum parsing), addResourceHandlers
 *    (serve static files), configureMessageConverters (JSON tweaks).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestLoggingInterceptor loggingInterceptor;
    private final CurrentUserArgumentResolver currentUserResolver;

    public WebMvcConfig(RequestLoggingInterceptor loggingInterceptor,
                        CurrentUserArgumentResolver currentUserResolver) {
        this.loggingInterceptor = loggingInterceptor;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor)
            // Exclude the noisy/automatic health probes
            .excludePathPatterns("/actuator/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
