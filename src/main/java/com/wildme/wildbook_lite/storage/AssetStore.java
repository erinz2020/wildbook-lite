package com.wildme.wildbook_lite.storage;

import org.springframework.web.multipart.MultipartFile;

public interface AssetStore {
    String store(MultipartFile file, String safeName);
    byte[] read(String path);
}
