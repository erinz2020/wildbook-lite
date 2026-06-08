package com.wildme.wildbook_lite.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Production-profile placeholder. Not wired to a real AWS SDK yet — the
 * point is to show the @Profile pattern: same interface, totally
 * different implementation behind it, switched by spring.profiles.active.
 *
 * The Strategy pattern + @Profile is one of Spring Boot's cleanest
 * answers to "how do I differ behaviour between dev and prod". No
 * if-prod-then-X branching inside MediaAssetService — that file doesn't
 * even know which store it's using.
 *
 * To turn it on:
 *   SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
 * (then Spring picks this bean and LocalAssetStore is skipped.)
 */
@Component
@Profile("prod")
public class S3AssetStore implements AssetStore {

    @Override
    public String store(MultipartFile file, String safeName) {
        // TODO: wire AWS SDK v2 S3Client here.
        //   - PutObjectRequest builder with bucket/key/contentType
        //   - return canonical "s3://bucket/key" string
        //   - cap memory: stream from MultipartFile.getInputStream() directly
        throw new UnsupportedOperationException("S3AssetStore not implemented yet");
    }

    @Override
    public byte[] read(String path) {
        throw new UnsupportedOperationException("S3AssetStore not implemented yet");
    }
}
