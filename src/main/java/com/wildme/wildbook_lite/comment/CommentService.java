package com.wildme.wildbook_lite.comment;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wildme.wildbook_lite.auth.SecurityUtils;
import com.wildme.wildbook_lite.comment.dto.CreateCommentRequest;
import com.wildme.wildbook_lite.common.ForbiddenException;
import com.wildme.wildbook_lite.entity.Encounter;
import com.wildme.wildbook_lite.exception.NotFoundException;
import com.wildme.wildbook_lite.repository.EncounterRepository;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final EncounterRepository encounterRepository;

    public CommentService(CommentRepository commentRepository,
                          EncounterRepository encounterRepository) {
        this.commentRepository = commentRepository;
        this.encounterRepository = encounterRepository;
    }

    @Transactional(readOnly = true)
    public List<Comment> list(Long encounterId) {
        ensureEncounterExists(encounterId);
        return commentRepository.findByEncounterIdOrderByCreatedAtDesc(encounterId);
    }

    @Transactional
    public Comment create(Long encounterId, CreateCommentRequest req) {
        ensureEncounterExists(encounterId);
        Long userId = SecurityUtils.currentUserId();
        return commentRepository.save(new Comment(encounterId, userId, req.body()));
    }

    @Transactional
    public void delete(Long encounterId, Long commentId) {
        Comment c = commentRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
        if (!c.getEncounterId().equals(encounterId)) {
            throw new NotFoundException("Comment not found on encounter: " + commentId);
        }
        Long userId = SecurityUtils.currentUserId();
        if (!c.getAuthorUserId().equals(userId)) {
            throw new ForbiddenException("Only the author can delete this comment");
        }
        commentRepository.delete(c);
    }

    private Encounter ensureEncounterExists(Long encounterId) {
        return encounterRepository.findById(encounterId)
            .orElseThrow(() -> new NotFoundException("Encounter not found: " + encounterId));
    }
}
