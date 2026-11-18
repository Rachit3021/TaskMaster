package com.taskflow.comment;

import com.taskflow.comment.dto.CommentResponse;
import com.taskflow.comment.dto.CreateCommentRequest;
import com.taskflow.comment.dto.UpdateCommentRequest;
import com.taskflow.common.error.ForbiddenException;
import com.taskflow.common.error.NotFoundException;
import com.taskflow.notification.NotificationPublisher;
import com.taskflow.notification.NotificationType;
import com.taskflow.task.Task;
import com.taskflow.task.TaskService;
import com.taskflow.team.TeamMember;
import com.taskflow.team.TeamMembershipGuard;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskService taskService;
    private final TeamMembershipGuard guard;
    private final UserRepository userRepository;
    private final NotificationPublisher notifications;

    public CommentService(CommentRepository commentRepository,
                          TaskService taskService,
                          TeamMembershipGuard guard,
                          UserRepository userRepository,
                          NotificationPublisher notifications) {
        this.commentRepository = commentRepository;
        this.taskService = taskService;
        this.guard = guard;
        this.userRepository = userRepository;
        this.notifications = notifications;
    }

    @Transactional
    public CommentResponse add(UUID actorId, UUID taskId, CreateCommentRequest request) {
        Task task = taskService.requireAccess(actorId, taskId);
        User author = requireUser(actorId);
        Comment comment = commentRepository.save(new Comment(task, author, request.body().trim()));

        Set<UUID> recipients = new LinkedHashSet<>();
        if (task.getAssignee() != null) {
            recipients.add(task.getAssignee().getId());
        }
        recipients.add(task.getCreatedBy().getId());
        recipients.remove(actorId);

        for (UUID recipientId : recipients) {
            notifications.publish(recipientId, NotificationType.TASK_COMMENTED,
                    "New comment on a task",
                    author.getName() + " commented on \"" + task.getTitle() + "\"",
                    task.getProject().getTeam().getId(), task.getProject().getId(), task.getId());
        }
        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID actorId, UUID taskId) {
        taskService.requireAccess(actorId, taskId);
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse update(UUID actorId, UUID commentId, UpdateCommentRequest request) {
        Comment comment = requireComment(commentId);
        taskService.requireAccess(actorId, comment.getTask().getId());
        if (!comment.getAuthor().getId().equals(actorId)) {
            throw new ForbiddenException("Only the comment author can edit this comment");
        }
        comment.setBody(request.body().trim());
        return CommentResponse.from(comment);
    }

    @Transactional
    public void delete(UUID actorId, UUID commentId) {
        Comment comment = requireComment(commentId);
        Task task = taskService.requireAccess(actorId, comment.getTask().getId());
        boolean isAuthor = comment.getAuthor().getId().equals(actorId);
        if (!isAuthor) {
            TeamMember member = guard.requireMember(task.getProject().getTeam().getId(), actorId);
            if (!member.getRole().canManage()) {
                throw new ForbiddenException(
                        "Only the comment author or a team owner/admin can delete this comment");
            }
        }
        commentRepository.delete(comment);
    }

    private Comment requireComment(UUID commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> NotFoundException.of("Comment", commentId));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
    }
}
