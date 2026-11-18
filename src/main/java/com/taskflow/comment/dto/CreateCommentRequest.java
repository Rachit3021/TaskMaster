package com.taskflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "body is required")
        @Size(max = 4000, message = "body must be at most 4000 characters")
        String body
) {
}
