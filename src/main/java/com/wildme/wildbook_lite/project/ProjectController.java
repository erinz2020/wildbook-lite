package com.wildme.wildbook_lite.project;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.project.dto.AddMemberRequest;
import com.wildme.wildbook_lite.project.dto.CreateProjectRequest;
import com.wildme.wildbook_lite.project.dto.ProjectResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest req) {
        return ProjectResponse.from(projectService.create(req));
    }

    @GetMapping
    public List<ProjectResponse> listMine() {
        return projectService.listMyProjects().stream()
            .map(ProjectResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@projectGuard.canRead(#id)")
    public ProjectResponse get(@PathVariable Long id) {
        return ProjectResponse.from(projectService.findById(id));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("@projectGuard.canRead(#id)")
    public List<ProjectMember> listMembers(@PathVariable Long id) {
        return projectService.listMembers(id);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@projectGuard.canManage(#id)")
    public ProjectMember addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest req) {
        return projectService.addMember(id, req);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@projectGuard.canManage(#id)")
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
    }
}
