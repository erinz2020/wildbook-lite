package com.wildme.wildbook_lite.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalAssetStore implements AssetStore {

    private static final String UPLOAD_DIR = "uploads/";

    @Override
    public String store(MultipartFile file, String safeName) {
        Path dirPath = Paths.get(UPLOAD_DIR);
        try {
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            Path filePath = dirPath.resolve(safeName);
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("File upload failed", e);
        }
        return UPLOAD_DIR + safeName;
    }

    @Override
    public byte[] read(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException("File read failed", e);
        }
    }
}
