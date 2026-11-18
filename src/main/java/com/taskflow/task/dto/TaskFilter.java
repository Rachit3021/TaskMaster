package com.taskflow.task.dto;

import com.taskflow.task.TaskPriority;
import com.taskflow.task.TaskStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Internal filter object backing the task list endpoints. */
public record TaskFilter(
        List<TaskStatus> statuses,
        List<TaskPriority> priorities,
        UUID assigneeId,
        String search,
        LocalDate dueBefore,
        LocalDate dueAfter,
        Boolean overdue
) {
}
