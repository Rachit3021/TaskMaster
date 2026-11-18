package com.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskIntegrationTest extends IntegrationTestSupport {

    @Test
    void taskCrudAssignmentFilteringAndCompletion() throws Exception {
        TestUser owner = registerUser("Task Owner");
        TestUser assignee = registerUser("Task Assignee");
        JsonNode team = createTeam(owner.accessToken(), "Task Team");
        String teamId = team.path("id").asText();
        postJson("/api/v1/teams/join", Map.of("inviteCode", team.path("inviteCode").asText()),
                assignee.accessToken(), 200);
        JsonNode project = createProject(owner.accessToken(), teamId, "Task Project");
        String projectId = project.path("id").asText();

        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("title", "Ship the reporting endpoint");
        createBody.put("description", "Aggregate weekly task completion metrics.");
        createBody.put("status", "TODO");
        createBody.put("priority", "HIGH");
        createBody.put("dueDate", LocalDate.now().plusDays(5).toString());
        createBody.put("assigneeId", assignee.id().toString());
        createBody.put("labels", List.of("backend", "Reporting"));
        JsonNode task = postJson("/api/v1/projects/" + projectId + "/tasks", createBody,
                owner.accessToken(), 201);
        String taskId = task.path("id").asText();

        assertThat(task.path("status").asText()).isEqualTo("TODO");
        assertThat(task.path("priority").asText()).isEqualTo("HIGH");
        assertThat(task.path("assignee").path("email").asText()).isEqualTo(assignee.email());
        assertThat(task.path("labels")).hasSize(2);
        assertThat(task.path("overdue").asBoolean()).isFalse();

        JsonNode filtered = getJson("/api/v1/projects/" + projectId
                + "/tasks?status=TODO&priority=HIGH&q=reporting", owner.accessToken(), 200);
        assertThat(filtered.path("totalElements").asInt()).isEqualTo(1);

        // Query parameters are supplied through the request builder so that a
        // multi-word search term is URL-encoded exactly once.
        MvcResult searchedResult = mockMvc.perform(authenticatedGet(
                        "/api/v1/projects/" + projectId + "/tasks", owner.accessToken())
                        .param("q", "weekly task"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode searched = objectMapper.readTree(searchedResult.getResponse().getContentAsString());
        assertThat(searched.path("totalElements").asInt()).isEqualTo(1);

        JsonNode assignedToMe = getJson("/api/v1/tasks/assigned-to-me", assignee.accessToken(), 200);
        assertThat(assignedToMe.path("totalElements").asInt()).isEqualTo(1);
        assertThat(assignedToMe.path("content").get(0).path("teamName").asText()).isEqualTo("Task Team");

        JsonNode updated = patchJson("/api/v1/tasks/" + taskId,
                Map.of("title", "Ship the weekly report endpoint", "priority", "URGENT"),
                owner.accessToken(), 200);
        assertThat(updated.path("title").asText()).isEqualTo("Ship the weekly report endpoint");
        assertThat(updated.path("priority").asText()).isEqualTo("URGENT");

        JsonNode completed = postJson("/api/v1/tasks/" + taskId + "/complete", null,
                assignee.accessToken(), 200);
        assertThat(completed.path("status").asText()).isEqualTo("DONE");
        assertThat(completed.path("completedAt").isMissingNode()).isFalse();

        JsonNode reopened = patchJson("/api/v1/tasks/" + taskId, Map.of("status", "TODO"),
                owner.accessToken(), 200);
        assertThat(reopened.path("completedAt").isMissingNode()).isTrue();

        JsonNode invalidAssignee = patchJson("/api/v1/tasks/" + taskId + "/assignee",
                Map.of("assigneeId", "not-a-uuid"), owner.accessToken(), 400);
        assertThat(invalidAssignee.path("code").asText()).isEqualTo("BAD_REQUEST");

        JsonNode cleared = patchJson("/api/v1/tasks/" + taskId + "/assignee",
                java.util.Collections.singletonMap("assigneeId", null), owner.accessToken(), 200);
        assertThat(cleared.path("assignee").isMissingNode()).isTrue();
    }

    @Test
    void listSupportsStatusPrioritySearchAndWhitelistedSorting() throws Exception {
        TestUser owner = registerUser("Filter Owner");
        JsonNode team = createTeam(owner.accessToken(), "Filter Team");
        JsonNode project = createProject(owner.accessToken(), team.path("id").asText(), "Filter Project");
        String projectId = project.path("id").asText();

        createTaskWith(owner, projectId, "Low backlog item", "BACKLOG", "LOW", null);
        createTaskWith(owner, projectId, "Urgent payment bug", "IN_PROGRESS", "URGENT",
                LocalDate.now().minusDays(2).toString());
        createTaskWith(owner, projectId, "Medium review item", "IN_REVIEW", "MEDIUM",
                LocalDate.now().plusDays(10).toString());

        JsonNode inProgress = getJson("/api/v1/projects/" + projectId
                + "/tasks?status=IN_PROGRESS,IN_REVIEW", owner.accessToken(), 200);
        assertThat(inProgress.path("totalElements").asInt()).isEqualTo(2);

        JsonNode urgent = getJson("/api/v1/projects/" + projectId
                + "/tasks?priority=URGENT", owner.accessToken(), 200);
        assertThat(urgent.path("totalElements").asInt()).isEqualTo(1);

        JsonNode search = getJson("/api/v1/projects/" + projectId
                + "/tasks?q=payment", owner.accessToken(), 200);
        assertThat(search.path("content").get(0).path("title").asText()).isEqualTo("Urgent payment bug");

        JsonNode overdue = getJson("/api/v1/projects/" + projectId
                + "/tasks?overdue=true", owner.accessToken(), 200);
        assertThat(overdue.path("totalElements").asInt()).isEqualTo(1);
        assertThat(overdue.path("content").get(0).path("overdue").asBoolean()).isTrue();

        JsonNode sorted = getJson("/api/v1/projects/" + projectId
                + "/tasks?sort=title,asc", owner.accessToken(), 200);
        assertThat(sorted.path("content").get(0).path("title").asText()).isEqualTo("Low backlog item");

        JsonNode badSort = getJson("/api/v1/projects/" + projectId
                + "/tasks?sort=password,desc", owner.accessToken(), 400);
        assertThat(badSort.path("code").asText()).isEqualTo("BAD_REQUEST");

        JsonNode badDirection = getJson("/api/v1/projects/" + projectId
                + "/tasks?sort=title,sideways", owner.accessToken(), 400);
        assertThat(badDirection.path("code").asText()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void taskEndpointsEnforceTeamMembershipAndAssigneeRules() throws Exception {
        TestUser owner = registerUser("Guard Owner");
        TestUser outsider = registerUser("Guard Outsider");
        JsonNode team = createTeam(owner.accessToken(), "Guard Team");
        JsonNode project = createProject(owner.accessToken(), team.path("id").asText(), "Guard Project");
        String projectId = project.path("id").asText();
        JsonNode task = createTask(owner.accessToken(), projectId, "Guarded task");
        String taskId = task.path("id").asText();

        getJson("/api/v1/tasks/" + taskId, outsider.accessToken(), 403);
        postJson("/api/v1/projects/" + projectId + "/tasks", Map.of("title", "Intruder task"),
                outsider.accessToken(), 403);
        postJson("/api/v1/tasks/" + taskId + "/comments", Map.of("body", "Intruder comment"),
                outsider.accessToken(), 403);

        patchJson("/api/v1/tasks/" + taskId + "/assignee",
                Map.of("assigneeId", outsider.id().toString()), owner.accessToken(), 403);

        delete("/api/v1/tasks/" + taskId, outsider.accessToken(), 403);
    }

    @Test
    void validationRejectsInvalidTasksAndArchivedProjects() throws Exception {
        TestUser owner = registerUser("Validation Owner");
        JsonNode team = createTeam(owner.accessToken(), "Validation Team");
        JsonNode project = createProject(owner.accessToken(), team.path("id").asText(), "Validation Project");
        String projectId = project.path("id").asText();

        JsonNode missingTitle = postJson("/api/v1/projects/" + projectId + "/tasks",
                Map.of("description", "No title here"), owner.accessToken(), 400);
        assertThat(missingTitle.path("fieldErrors").has("title")).isTrue();

        JsonNode invalidStatus = postJson("/api/v1/projects/" + projectId + "/tasks",
                Map.of("title", "Invalid status task", "status", "NOT_A_STATUS"), owner.accessToken(), 400);
        assertThat(invalidStatus.path("code").asText()).isEqualTo("BAD_REQUEST");

        patchJson("/api/v1/projects/" + projectId, Map.of("status", "ARCHIVED"),
                owner.accessToken(), 200);
        JsonNode archived = postJson("/api/v1/projects/" + projectId + "/tasks",
                Map.of("title", "Task in archived project"), owner.accessToken(), 400);
        assertThat(archived.path("message").asText()).contains("archived");
    }

    private JsonNode createTaskWith(TestUser user, String projectId, String title,
                                    String status, String priority, String dueDate) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("status", status);
        body.put("priority", priority);
        if (dueDate != null) {
            body.put("dueDate", dueDate);
        }
        return postJson("/api/v1/projects/" + projectId + "/tasks", body, user.accessToken(), 201);
    }
}
