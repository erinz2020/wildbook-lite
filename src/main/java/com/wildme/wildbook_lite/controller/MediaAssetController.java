package com.wildme.wildbook_lite.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;


import com.wildme.wildbook_lite.service.MediaAssetService;
import com.wildme.wildbook_lite.entity.MediaAsset;

@RestController
@RequestMapping("/api/media")
public class MediaAssetController {

    private final MediaAssetService service;

    public MediaAssetController(MediaAssetService service) {
        this.service = service;
    }

    @PostMapping("/{encounterId}")
    public MediaAsset create(
        @PathVariable Long encounterId,
        @RequestParam("file") MultipartFile file
     ) {
        return service.upload(encounterId, file);
    }
}