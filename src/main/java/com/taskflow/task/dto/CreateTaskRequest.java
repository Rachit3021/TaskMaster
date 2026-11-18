package com.taskflow.task.dto;

import com.taskflow.task.TaskPriority;
import com.taskflow.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank(message = "title is required")
        @Size(min = 3, max = 160, message = "title must be between 3 and 160 characters")
        String title,

        @Size(max = 5000, message = "description must be at most 5000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        LocalDate dueDate,

        UUID assigneeId,

        @Size(max = 10, message = "a task can have at most 10 labels")
        Set<@Size(max = 40, message = "each label must be at most 40 characters") String> labels
) {
}
