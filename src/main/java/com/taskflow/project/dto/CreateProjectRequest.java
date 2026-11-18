package com.taskflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "name is required")
        @Size(min = 2, max = 160, message = "name must be between 2 and 160 characters")
        String name,

        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description
) {
}
