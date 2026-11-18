package com.taskflow.task.dto;

import com.taskflow.task.TaskPriority;
import com.taskflow.task.TaskStatus;
import com.taskflow.user.dto.UserSummary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID projectId,
        String projectName,
        UUID teamId,
        String teamName,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        UserSummary assignee,
        UserSummary createdBy,
        Instant completedAt,
        Set<String> labels,
        long commentCount,
        long attachmentCount,
        boolean overdue,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
