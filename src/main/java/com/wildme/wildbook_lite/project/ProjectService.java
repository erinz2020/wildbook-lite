package com.wildme.wildbook_lite.project;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.organization.OrgGuard;
import com.wildme.wildbook_lite.project.dto.AddMemberRequest;
import com.wildme.wildbook_lite.project.dto.CreateProjectRequest;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final OrgGuard orgGuard;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository memberRepository,
                          OrgGuard orgGuard) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.orgGuard = orgGuard;
    }

    @Transactional
    public Project create(CreateProjectRequest req) {
        Long currentUserId = SecurityUtils.currentUserId();

        // If an org is supplied, the caller must be a member of it. Any
        // org member can spin up a project — we don't require canManage.
        // (Tighten to canManage if a future use case demands it.)
        if (req.organizationId() != null
            && !orgGuard.isMember(req.organizationId(), currentUserId)) {
            throw new ForbiddenException(
                "Not a member of organization: " + req.organizationId());
        }

        Project p = new Project(req.name(), req.description(), currentUserId, req.organizationId());
        Project saved = projectRepository.save(p);

        // Bootstrap: creator is OWNER
        memberRepository.save(new ProjectMember(saved.getId(), currentUserId, ProjectRole.OWNER));
        return saved;
    }

    @Transactional(readOnly = true)
    public Project findById(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Project> listMyProjects() {
        Long userId = SecurityUtils.currentUserId();
        List<Long> projectIds = memberRepository.findByUserId(userId).stream()
            .map(ProjectMember::getProjectId)
            .toList();
        if (projectIds.isEmpty()) return List.of();
        return projectRepository.findAllById(projectIds);
    }

    @Transactional
    public ProjectMember addMember(Long projectId, AddMemberRequest req) {
        findById(projectId); // ensure exists
        // Upsert: if already a member, update role; otherwise insert
        ProjectMember member = memberRepository
            .findByProjectIdAndUserId(projectId, req.userId())
            .orElseGet(() -> new ProjectMember(projectId, req.userId(), req.role()));
        member.setRole(req.role());
        return memberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        ProjectMember m = memberRepository.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(() -> new NotFoundException("Not a member of project: " + projectId));
        memberRepository.delete(m);
    }

    @Transactional(readOnly = true)
    public List<ProjectMember> listMembers(Long projectId) {
        findById(projectId); // ensure exists
        return memberRepository.findByProjectId(projectId);
    }
}
