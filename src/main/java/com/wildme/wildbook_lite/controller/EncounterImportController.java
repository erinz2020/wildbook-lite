package com.wildme.wildbook_lite.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.wildme.wildbook_lite.dto.ImportResult;
import com.wildme.wildbook_lite.service.EncounterImportService;

@RestController
@RequestMapping("/api/encounters")
public class EncounterImportController {

    private final EncounterImportService importService;

    public EncounterImportController(EncounterImportService importService) {
        this.importService = importService;
    }

    /**
     * Upload a CSV with header "species,location,notes" (notes optional).
     * Best-effort: partial failures are reported per row in the response.
     *
     * curl -X POST -H "Authorization: Bearer $TOKEN" \
     *      -F file=@encounters.csv \
     *      "http://localhost:8080/api/encounters/import?projectId=1"
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@RequestParam Long projectId,
                                  @RequestParam("file") MultipartFile file) {
        return importService.importCsv(projectId, file);
    }
}
