package com.wildme.wildbook_lite.comment;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wildme.wildbook_lite.comment.dto.CreateCommentRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/encounters/{encounterId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public List<Comment> list(@PathVariable Long encounterId) {
        return commentService.list(encounterId);
    }

    @PostMapping
    public Comment create(@PathVariable Long encounterId,
                          @Valid @RequestBody CreateCommentRequest req) {
        return commentService.create(encounterId, req);
    }

    @DeleteMapping("/{commentId}")
    public void delete(@PathVariable Long encounterId, @PathVariable Long commentId) {
        commentService.delete(encounterId, commentId);
    }
}
