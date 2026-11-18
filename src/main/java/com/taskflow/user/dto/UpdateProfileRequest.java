package com.taskflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "name is required")
        @Size(min = 2, max = 80, message = "name must be between 2 and 80 characters")
        String name,

        @Size(max = 500, message = "bio must be at most 500 characters")
        String bio,

        @Size(max = 500, message = "avatarUrl must be at most 500 characters")
        String avatarUrl
) {
}
