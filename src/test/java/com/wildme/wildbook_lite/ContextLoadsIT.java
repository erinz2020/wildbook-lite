package com.wildme.wildbook_lite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.wildme.wildbook_lite.support.AbstractPostgresIT;

/**
 * The "context loads" sanity check. Catches misconfigured beans,
 * circular dependencies, and missing @Components at build time.
 *
 * Uses the same Testcontainers-backed Postgres as the rest of the IT
 * suite — without this base class, this test would fall over trying
 * to connect to the dev database.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContextLoadsIT extends AbstractPostgresIT {

    @Test
    void contextLoads() {
    }
}
