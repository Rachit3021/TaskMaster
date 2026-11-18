package com.taskflow.comment;

import com.taskflow.comment.dto.CommentResponse;
import com.taskflow.comment.dto.CreateCommentRequest;
import com.taskflow.comment.dto.UpdateCommentRequest;
import com.taskflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Comments", description = "Task discussions")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/tasks/{taskId}/comments")
    @Operation(summary = "List comments for a task")
    public List<CommentResponse> list(@PathVariable UUID taskId) {
        return commentService.list(SecurityUtils.currentUserId(), taskId);
    }

    @PostMapping("/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a comment to a task")
    public CommentResponse add(@PathVariable UUID taskId,
                               @Valid @RequestBody CreateCommentRequest request) {
        return commentService.add(SecurityUtils.currentUserId(), taskId, request);
    }

    @PatchMapping("/comments/{commentId}")
    @Operation(summary = "Edit a comment (author only)")
    public CommentResponse update(@PathVariable UUID commentId,
                                  @Valid @RequestBody UpdateCommentRequest request) {
        return commentService.update(SecurityUtils.currentUserId(), commentId, request);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a comment (author, owner or admin)")
    public ResponseEntity<Void> delete(@PathVariable UUID commentId) {
        commentService.delete(SecurityUtils.currentUserId(), commentId);
        return ResponseEntity.noContent().build();
    }
}
