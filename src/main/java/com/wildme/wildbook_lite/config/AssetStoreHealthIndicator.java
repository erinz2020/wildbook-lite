package com.wildme.wildbook_lite.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom Actuator HealthIndicator. Exposed at /actuator/health/assetStore.
 *
 * Returns DOWN when the local media directory is missing or unwritable —
 * if Postgres is up but the disk is full, /actuator/health would otherwise
 * say UP and we'd ship broken uploads to clients.
 */
@Component("assetStore")
public class AssetStoreHealthIndicator implements HealthIndicator {

    private final Path mediaDir;

    public AssetStoreHealthIndicator(@Value("${app.storage.media-dir}") String mediaDir) {
        this.mediaDir = Paths.get(mediaDir).toAbsolutePath();
    }

    @Override
    public Health health() {
        if (!Files.exists(mediaDir)) {
            return Health.down()
                .withDetail("path", mediaDir.toString())
                .withDetail("reason", "directory does not exist")
                .build();
        }
        if (!Files.isWritable(mediaDir)) {
            return Health.down()
                .withDetail("path", mediaDir.toString())
                .withDetail("reason", "directory is not writable")
                .build();
        }
        return Health.up().withDetail("path", mediaDir.toString()).build();
    }
}
