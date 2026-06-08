package com.wildme.wildbook_lite.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.wildme.wildbook_lite.config.AppProperties;

/**
 * Dev / default-profile implementation: writes to the local filesystem
 * under app.storage.media-dir.
 *
 * Spring Boot bits demonstrated here:
 *
 *  - @Profile({"dev","default"})
 *      Bean is registered only when one of these profiles is active.
 *      Spring activates "default" when no explicit profile is set, and
 *      "dev" is our active profile in application.yml. So in tests with
 *      @ActiveProfiles("test") this bean would NOT be loaded — letting
 *      a test substitute its own in-memory implementation.
 *
 *  - @Retryable(...) — Spring Retry
 *      Wraps the method in a proxy that, on the matching exception,
 *      sleeps + invokes again. Useful for transient infra blips (NFS
 *      hiccup, brief disk pressure). Three attempts, exponential
 *      backoff capped at 2 seconds.
 *
 *      Same proxy gotcha as @Transactional / @Async: a same-class
 *      internal call bypasses the proxy → no retry. Annotated method
 *      must be called from outside the bean.
 */
@Component
@Profile({"dev", "default"})
public class LocalAssetStore implements AssetStore {

    private final Path mediaDir;

    public LocalAssetStore(AppProperties props) {
        this.mediaDir = Paths.get(props.storage().mediaDir()).toAbsolutePath();
    }

    @Override
    @Retryable(
        retryFor = { java.io.UncheckedIOException.class, RuntimeException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2000)
    )
    public String store(MultipartFile file, String safeName) {
        try {
            if (!Files.exists(mediaDir)) {
                Files.createDirectories(mediaDir);
            }
            Path filePath = mediaDir.resolve(safeName);
            file.transferTo(filePath.toFile());
            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("File upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] read(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException("File read failed: " + e.getMessage(), e);
        }
    }
}
