package com.taskflow.task;

import com.taskflow.common.dto.PageResponse;
import com.taskflow.security.SecurityUtils;
import com.taskflow.task.dto.AssignTaskRequest;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.TaskFilter;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.dto.UpdateTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tasks", description = "Task CRUD, filtering, assignment and completion")
@Validated
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task in a project")
    public TaskResponse create(@PathVariable UUID projectId,
                               @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(SecurityUtils.currentUserId(), projectId, request);
    }

    @GetMapping("/projects/{projectId}/tasks")
    @Operation(summary = "List, filter, sort and search tasks in a project")
    public PageResponse<TaskResponse> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) List<TaskPriority> priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) @Size(max = 160) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueAfter,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        TaskFilter filter = new TaskFilter(status, priority, assigneeId, q, dueBefore, dueAfter, overdue);
        return taskService.list(SecurityUtils.currentUserId(), projectId, filter, page, size, sort);
    }

    @GetMapping("/tasks/assigned-to-me")
    @Operation(summary = "List tasks assigned to the current user across all teams")
    public PageResponse<TaskResponse> assignedToMe(
            @RequestParam(required = false) List<TaskStatus> status,
            @RequestParam(required = false) List<TaskPriority> priority,
            @RequestParam(required = false) @Size(max = 160) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueAfter,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "dueDate,asc") String sort) {
        TaskFilter filter = new TaskFilter(status, priority, null, q, dueBefore, dueAfter, overdue);
        return taskService.listAssignedToMe(SecurityUtils.currentUserId(), filter, page, size, sort);
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Get a task by id")
    public TaskResponse get(@PathVariable UUID taskId) {
        return taskService.get(SecurityUtils.currentUserId(), taskId);
    }

    @PatchMapping("/tasks/{taskId}")
    @Operation(summary = "Update task fields")
    public TaskResponse update(@PathVariable UUID taskId,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(SecurityUtils.currentUserId(), taskId, request);
    }

    @PatchMapping("/tasks/{taskId}/assignee")
    @Operation(summary = "Assign the task to a team member, or unassign it")
    public TaskResponse assign(@PathVariable UUID taskId,
                               @Valid @RequestBody AssignTaskRequest request) {
        return taskService.assign(SecurityUtils.currentUserId(), taskId, request);
    }

    @PostMapping("/tasks/{taskId}/complete")
    @Operation(summary = "Mark a task as completed")
    public TaskResponse complete(@PathVariable UUID taskId) {
        return taskService.complete(SecurityUtils.currentUserId(), taskId);
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task (creator, project creator, owner or admin)")
    public ResponseEntity<Void> delete(@PathVariable UUID taskId) {
        taskService.delete(SecurityUtils.currentUserId(), taskId);
        return ResponseEntity.noContent().build();
    }
}
