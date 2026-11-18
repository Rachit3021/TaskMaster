package com.taskflow.project;

import com.taskflow.attachment.AttachmentRepository;
import com.taskflow.attachment.AttachmentStorage;
import com.taskflow.common.dto.PageResponse;
import com.taskflow.common.error.ForbiddenException;
import com.taskflow.common.error.NotFoundException;
import com.taskflow.common.util.AfterCommit;
import com.taskflow.project.dto.CreateProjectRequest;
import com.taskflow.project.dto.ProjectResponse;
import com.taskflow.project.dto.UpdateProjectRequest;
import com.taskflow.task.TaskStatus;
import com.taskflow.task.TaskRepository;
import com.taskflow.team.Team;
import com.taskflow.team.TeamMember;
import com.taskflow.team.TeamMembershipGuard;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import com.taskflow.user.dto.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TeamMembershipGuard guard;
    private final UserRepository userRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorage attachmentStorage;

    public ProjectService(ProjectRepository projectRepository,
                          TaskRepository taskRepository,
                          TeamMembershipGuard guard,
                          UserRepository userRepository,
                          AttachmentRepository attachmentRepository,
                          AttachmentStorage attachmentStorage) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.guard = guard;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorage = attachmentStorage;
    }

    @Transactional
    public ProjectResponse create(UUID actorId, UUID teamId, CreateProjectRequest request) {
        TeamMember member = guard.requireMember(teamId, actorId);
        Team team = member.getTeam();
        User actor = requireUser(actorId);
        Project project = new Project(team, request.name().trim(), trimToNull(request.description()), actor);
        projectRepository.save(project);
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(UUID actorId, UUID teamId, ProjectStatus status,
                                              int page, int size) {
        guard.requireMember(teamId, actorId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> projects = status == null
                ? projectRepository.findByTeamId(teamId, pageable)
                : projectRepository.findByTeamIdAndStatus(teamId, status, pageable);
        return PageResponse.from(projects, this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID actorId, UUID projectId) {
        return toResponse(requireAccess(actorId, projectId));
    }

    @Transactional
    public ProjectResponse update(UUID actorId, UUID projectId, UpdateProjectRequest request) {
        Project project = requireAccess(actorId, projectId);
        requireManagePermission(actorId, project);
        if (request.name() != null) {
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            project.setDescription(trimToNull(request.description()));
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        return toResponse(project);
    }

    @Transactional
    public void delete(UUID actorId, UUID projectId) {
        Project project = requireAccess(actorId, projectId);
        requireManagePermission(actorId, project);
        List<String> storedFiles = attachmentRepository.findStoredNamesByProjectId(projectId);
        projectRepository.delete(project);
        AfterCommit.run(() -> storedFiles.forEach(attachmentStorage::deleteQuietly));
    }

    /** Loads a project and verifies the caller belongs to its team. */
    @Transactional(readOnly = true)
    public Project requireAccess(UUID actorId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> NotFoundException.of("Project", projectId));
        guard.requireMember(project.getTeam().getId(), actorId);
        return project;
    }

    public void requireManagePermission(UUID actorId, Project project) {
        TeamMember member = guard.requireMember(project.getTeam().getId(), actorId);
        boolean isCreator = project.getCreatedBy().getId().equals(actorId);
        if (!member.getRole().canManage() && !isCreator) {
            throw new ForbiddenException(
                    "Only the project creator, a team owner or an admin can manage this project");
        }
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getTeam().getId(),
                project.getTeam().getName(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                UserSummary.from(project.getCreatedBy()),
                taskRepository.countByProjectId(project.getId()),
                taskRepository.countByProjectIdAndStatusNot(project.getId(), TaskStatus.DONE),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
