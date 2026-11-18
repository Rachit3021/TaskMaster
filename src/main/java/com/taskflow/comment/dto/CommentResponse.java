package com.taskflow.comment.dto;

import com.taskflow.comment.Comment;
import com.taskflow.user.dto.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID taskId,
        UserSummary author,
        String body,
        boolean edited,
        Instant createdAt,
        Instant updatedAt
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTask().getId(),
                UserSummary.from(comment.getAuthor()),
                comment.getBody(),
                comment.isEdited(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
