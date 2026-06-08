package com.wildme.wildbook_lite.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on @CreatedDate / @LastModifiedDate on entities that extend BaseEntity.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
