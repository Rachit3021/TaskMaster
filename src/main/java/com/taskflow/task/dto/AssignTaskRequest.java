package com.taskflow.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Send {@code {"assigneeId": null}} to unassign a task. */
public record AssignTaskRequest(
        @Schema(description = "Team member to assign the task to, or null to unassign", nullable = true)
        UUID assigneeId
) {
}
