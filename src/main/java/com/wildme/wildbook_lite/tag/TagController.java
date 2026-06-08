package com.wildme.wildbook_lite.tag;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.tag.dto.CreateTagRequest;
import com.wildme.wildbook_lite.tag.dto.TagResponse;

import jakarta.validation.Valid;

@RestController
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    // ---- Tag CRUD scoped to a project ----

    @PostMapping("/api/projects/{projectId}/tags")
    public TagResponse create(@PathVariable Long projectId,
                              @Valid @RequestBody CreateTagRequest req) {
        return TagResponse.from(tagService.create(projectId, req));
    }

    @GetMapping("/api/projects/{projectId}/tags")
    public List<TagResponse> listByProject(@PathVariable Long projectId) {
        return tagService.listByProject(projectId).stream().map(TagResponse::from).toList();
    }

    @DeleteMapping("/api/projects/{projectId}/tags/{tagId}")
    public void delete(@PathVariable Long projectId, @PathVariable Long tagId) {
        tagService.delete(projectId, tagId);
    }

    // ---- Attaching tags to encounters ----

    @PostMapping("/api/encounters/{encounterId}/tags/{tagId}")
    public EncounterTag attach(@PathVariable Long encounterId, @PathVariable Long tagId) {
        return tagService.attach(encounterId, tagId);
    }

    @DeleteMapping("/api/encounters/{encounterId}/tags/{tagId}")
    public void detach(@PathVariable Long encounterId, @PathVariable Long tagId) {
        tagService.detach(encounterId, tagId);
    }

    @GetMapping("/api/encounters/{encounterId}/tags")
    public List<EncounterTag> listForEncounter(@PathVariable Long encounterId) {
        return tagService.listForEncounter(encounterId);
    }
}
