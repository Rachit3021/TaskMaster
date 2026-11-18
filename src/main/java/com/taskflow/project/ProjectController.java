package com.taskflow.project;

import com.taskflow.common.dto.PageResponse;
import com.taskflow.project.dto.CreateProjectRequest;
import com.taskflow.project.dto.ProjectResponse;
import com.taskflow.project.dto.UpdateProjectRequest;
import com.taskflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Projects", description = "Projects inside a team")
@Validated
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/teams/{teamId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a project inside a team")
    public ProjectResponse create(@PathVariable UUID teamId,
                                  @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(SecurityUtils.currentUserId(), teamId, request);
    }

    @GetMapping("/teams/{teamId}/projects")
    @Operation(summary = "List projects of a team")
    public PageResponse<ProjectResponse> list(
            @PathVariable UUID teamId,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return projectService.list(SecurityUtils.currentUserId(), teamId, status, page, size);
    }

    @GetMapping("/projects/{projectId}")
    @Operation(summary = "Get a project by id")
    public ProjectResponse get(@PathVariable UUID projectId) {
        return projectService.get(SecurityUtils.currentUserId(), projectId);
    }

    @PatchMapping("/projects/{projectId}")
    @Operation(summary = "Update a project (creator, owner or admin)")
    public ProjectResponse update(@PathVariable UUID projectId,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(SecurityUtils.currentUserId(), projectId, request);
    }

    @DeleteMapping("/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a project and its tasks (creator, owner or admin)")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId) {
        projectService.delete(SecurityUtils.currentUserId(), projectId);
        return ResponseEntity.noContent().build();
    }
}
