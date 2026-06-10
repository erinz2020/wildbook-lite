package com.wildme.wildbook_lite.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base class for integration tests that need a real Postgres.
 *
 * Spring Boot testing bits demonstrated here:
 *
 *  - @Testcontainers (JUnit 5 extension)
 *      Manages the lifecycle of @Container fields — start before tests,
 *      stop after. We mark the container `static` so it starts ONCE per
 *      JVM and is reused across every IT subclass; Postgres takes ~3s
 *      to boot, so this matters.
 *
 *  - @DynamicPropertySource (Spring 5.2.5+)
 *      Hook for setting Spring properties AFTER the container starts
 *      (because we don't know the host port until then). Spring evaluates
 *      this BEFORE the context starts, so spring.datasource.* binds
 *      cleanly. This is the cleanest way to wire Testcontainers into
 *      Spring tests — no @TestPropertySource shenanigans.
 *
 *  - `withReuse(true)` would let the same container survive across
 *    Maven runs (needs ~/.testcontainers.properties opt-in). We don't
 *    enable it here to keep tests hermetic.
 */
@Testcontainers
public abstract class AbstractPostgresIT {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static {
        // Eager start, once per JVM. JUnit's @Container annotation also
        // works, but this lets us bind properties before the FIRST
        // Spring context starts (which would be too late for instance
        // containers).
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
