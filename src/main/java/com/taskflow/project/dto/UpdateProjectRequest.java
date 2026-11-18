package com.taskflow.project.dto;

import com.taskflow.project.ProjectStatus;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(min = 2, max = 160, message = "name must be between 2 and 160 characters")
        String name,

        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description,

        ProjectStatus status
) {
}
