package com.wildme.wildbook_lite.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * One place to switch on three things at once:
 *
 *  - @EnableConfigurationProperties(AppProperties.class)
 *      Activates the AppProperties record. Without this, Spring
 *      ignores it and you'd hit NoSuchBeanDefinitionException on inject.
 *
 *  - @EnableScheduling
 *      Lets @Scheduled methods on beans actually run on a TaskScheduler.
 *      No-op for any class that doesn't have @Scheduled methods.
 *
 *  - @EnableRetry
 *      Activates the proxy that powers @Retryable / @Recover.
 *      Same proxy-based mechanism as @Transactional / @Async.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
@EnableRetry
public class SchedulingConfig {
}
