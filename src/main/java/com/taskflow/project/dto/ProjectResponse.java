package com.taskflow.project.dto;

import com.taskflow.project.ProjectStatus;
import com.taskflow.user.dto.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID teamId,
        String teamName,
        String name,
        String description,
        ProjectStatus status,
        UserSummary createdBy,
        long taskCount,
        long openTaskCount,
        Instant createdAt,
        Instant updatedAt
) {
}
