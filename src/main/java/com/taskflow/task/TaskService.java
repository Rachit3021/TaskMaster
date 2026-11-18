package com.taskflow.task;

import com.taskflow.attachment.Attachment;
import com.taskflow.attachment.AttachmentRepository;
import com.taskflow.attachment.AttachmentStorage;
import com.taskflow.comment.CommentRepository;
import com.taskflow.common.dto.PageResponse;
import com.taskflow.common.error.BadRequestException;
import com.taskflow.common.error.ForbiddenException;
import com.taskflow.common.error.NotFoundException;
import com.taskflow.common.util.AfterCommit;
import com.taskflow.notification.NotificationPublisher;
import com.taskflow.notification.NotificationType;
import com.taskflow.project.Project;
import com.taskflow.project.ProjectService;
import com.taskflow.project.ProjectStatus;
import com.taskflow.task.dto.AssignTaskRequest;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.TaskFilter;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.team.TeamMember;
import com.taskflow.team.TeamMembershipGuard;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import com.taskflow.user.dto.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "updatedAt", "dueDate", "priority", "title", "status");

    private final TaskRepository taskRepository;
    private final ProjectService projectService;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorage attachmentStorage;
    private final TeamMembershipGuard guard;
    private final NotificationPublisher notifications;

    public TaskService(TaskRepository taskRepository,
                       ProjectService projectService,
                       UserRepository userRepository,
                       CommentRepository commentRepository,
                       AttachmentRepository attachmentRepository,
                       AttachmentStorage attachmentStorage,
                       TeamMembershipGuard guard,
                       NotificationPublisher notifications) {
        this.taskRepository = taskRepository;
        this.projectService = projectService;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorage = attachmentStorage;
        this.guard = guard;
        this.notifications = notifications;
    }

    @Transactional
    public TaskResponse create(UUID actorId, UUID projectId, CreateTaskRequest request) {
        Project project = projectService.requireAccess(actorId, projectId);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BadRequestException("Tasks cannot be added to an archived project");
        }
        User actor = requireUser(actorId);
        Task task = new Task(project, request.title().trim(), actor);
        task.setDescription(trimToNull(request.description()));
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setDueDate(request.dueDate());
        task.setLabels(normalizeLabels(request.labels()));

        User assignee = resolveAssignee(project, request.assigneeId());
        task.setAssignee(assignee);
        taskRepository.save(task);

        if (assignee != null && !assignee.getId().equals(actorId)) {
            notifications.publish(assignee.getId(), NotificationType.TASK_ASSIGNED,
                    "Task assigned to you",
                    actor.getName() + " assigned you \"" + task.getTitle() + "\" in "
                            + project.getName(),
                    project.getTeam().getId(), project.getId(), task.getId());
        }
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID actorId, UUID taskId) {
        return toResponse(requireAccess(actorId, taskId));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(UUID actorId, UUID projectId, TaskFilter filter,
                                           int page, int size, String sort) {
        projectService.requireAccess(actorId, projectId);
        Specification<Task> specification = TaskSpecifications.inProject(projectId)
                .and(TaskSpecifications.matching(filter));
        return page(actorId, specification, page, size, sort);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listAssignedToMe(UUID actorId, TaskFilter filter,
                                                       int page, int size, String sort) {
        Specification<Task> specification = TaskSpecifications.accessibleBy(actorId)
                .and(TaskSpecifications.assignedTo(actorId))
                .and(TaskSpecifications.matching(filter));
        return page(actorId, specification, page, size, sort);
    }

    @Transactional
    public TaskResponse update(UUID actorId, UUID taskId, UpdateTaskRequest request) {
        Task task = requireAccess(actorId, taskId);
        Project project = task.getProject();
        TaskStatus previousStatus = task.getStatus();

        if (request.title() != null) {
            task.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            task.setDescription(trimToNull(request.description()));
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        if (request.labels() != null) {
            task.setLabels(normalizeLabels(request.labels()));
        }
        if (request.status() != null) {
            task.setStatus(request.status());
        }

        if (previousStatus != TaskStatus.DONE && task.getStatus() == TaskStatus.DONE) {
            task.setCompletedAt(Instant.now());
            notifyCreator(actorId, task, NotificationType.TASK_COMPLETED, "Task completed",
                    "completed \"" + task.getTitle() + "\"");
        } else if (previousStatus == TaskStatus.DONE && task.getStatus() != TaskStatus.DONE) {
            task.setCompletedAt(null);
        } else if (task.getAssignee() != null && !task.getAssignee().getId().equals(actorId)) {
            notifications.publish(task.getAssignee().getId(), NotificationType.TASK_UPDATED,
                    "Task updated",
                    "Task \"" + task.getTitle() + "\" was updated",
                    project.getTeam().getId(), project.getId(), task.getId());
        }
        return toResponse(task);
    }

    @Transactional
    public TaskResponse assign(UUID actorId, UUID taskId, AssignTaskRequest request) {
        Task task = requireAccess(actorId, taskId);
        Project project = task.getProject();
        User assignee = resolveAssignee(project, request.assigneeId());
        task.setAssignee(assignee);
        if (assignee != null && !assignee.getId().equals(actorId)) {
            notifications.publish(assignee.getId(), NotificationType.TASK_ASSIGNED,
                    "Task assigned to you",
                    "You were assigned \"" + task.getTitle() + "\" in " + project.getName(),
                    project.getTeam().getId(), project.getId(), task.getId());
        }
        return toResponse(task);
    }

    @Transactional
    public TaskResponse complete(UUID actorId, UUID taskId) {
        Task task = requireAccess(actorId, taskId);
        if (task.getStatus() != TaskStatus.DONE) {
            task.setStatus(TaskStatus.DONE);
            task.setCompletedAt(Instant.now());
            notifyCreator(actorId, task, NotificationType.TASK_COMPLETED, "Task completed",
                    "completed \"" + task.getTitle() + "\"");
        }
        return toResponse(task);
    }

    @Transactional
    public void delete(UUID actorId, UUID taskId) {
        Task task = requireAccess(actorId, taskId);
        Project project = task.getProject();
        TeamMember member = guard.requireMember(project.getTeam().getId(), actorId);
        boolean allowed = member.getRole().canManage()
                || task.getCreatedBy().getId().equals(actorId)
                || project.getCreatedBy().getId().equals(actorId);
        if (!allowed) {
            throw new ForbiddenException(
                    "Only the task creator, project creator, team owner or admin can delete this task");
        }
        List<String> storedFiles = attachmentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(Attachment::getStoredName)
                .toList();
        taskRepository.delete(task);
        AfterCommit.run(() -> storedFiles.forEach(attachmentStorage::deleteQuietly));
    }

    /** Loads a task and verifies the caller is a member of the owning team. */
    @Transactional(readOnly = true)
    public Task requireAccess(UUID actorId, UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> NotFoundException.of("Task", taskId));
        guard.requireMember(task.getProject().getTeam().getId(), actorId);
        return task;
    }

    private PageResponse<TaskResponse> page(UUID actorId, Specification<Task> specification,
                                            int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<Task> tasks = taskRepository.findAll(specification, pageable);
        return PageResponse.from(tasks, this::toResponse);
    }

    private TaskResponse toResponse(Task task) {
        Project project = task.getProject();
        return new TaskResponse(
                task.getId(),
                project.getId(),
                project.getName(),
                project.getTeam().getId(),
                project.getTeam().getName(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                UserSummary.from(task.getAssignee()),
                UserSummary.from(task.getCreatedBy()),
                task.getCompletedAt(),
                Set.copyOf(task.getLabels()),
                commentRepository.countByTaskId(task.getId()),
                attachmentRepository.countByTaskId(task.getId()),
                isOverdue(task),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private static boolean isOverdue(Task task) {
        return task.getDueDate() != null
                && task.getStatus() != TaskStatus.DONE
                && task.getDueDate().isBefore(LocalDate.now());
    }

    private User resolveAssignee(Project project, UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        User assignee = requireUser(assigneeId);
        guard.requireAssigneeIsMember(project.getTeam().getId(), assignee);
        return assignee;
    }

    private void notifyCreator(UUID actorId, Task task, NotificationType type,
                               String title, String action) {
        UUID creatorId = task.getCreatedBy().getId();
        if (creatorId.equals(actorId)) {
            return;
        }
        notifications.publish(creatorId, type, title, action,
                task.getProject().getTeam().getId(), task.getProject().getId(), task.getId());
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
    }

    private static Set<String> normalizeLabels(Set<String> labels) {
        if (labels == null) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        labels.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(label -> !label.isEmpty())
                .forEach(label -> normalized.add(label.toLowerCase(Locale.ROOT)));
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static Pageable buildPageable(int page, int size, String sort) {
        String rawSort = (sort == null || sort.isBlank()) ? "createdAt,desc" : sort.trim();
        String[] parts = rawSort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new BadRequestException("Unsupported sort field '" + field
                    + "'. Allowed: " + String.join(", ", SORTABLE_FIELDS));
        }
        Sort.Direction direction = parts.length > 1
                ? Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new BadRequestException("Sort direction must be 'asc' or 'desc'"))
                : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, field));
    }
}
