package com.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationIntegrationTest extends IntegrationTestSupport {

    @Test
    void taskActivityCreatesNotificationsThatCanBeReadAndMarkedRead() throws Exception {
        TestUser owner = registerUser("Notify Owner");
        TestUser member = registerUser("Notify Member");
        JsonNode team = createTeam(owner.accessToken(), "Notify Team");
        String teamId = team.path("id").asText();
        joinTeam(member, team);
        JsonNode project = createProject(owner.accessToken(), teamId, "Notify Project");
        String projectId = project.path("id").asText();

        JsonNode task = postJson("/api/v1/projects/" + projectId + "/tasks",
                Map.of("title", "Alert the team", "priority", "HIGH",
                        "assigneeId", member.id().toString()),
                owner.accessToken(), 201);
        String taskId = task.path("id").asText();

        // The owner performed the assignment, so only the assignee is notified about it.
        // Joining the team by code already produced a single TEAM_MEMBER_JOINED notification.
        JsonNode ownerInitial = getJson("/api/v1/notifications", owner.accessToken(), 200);
        assertThat(ownerInitial.path("totalElements").asInt()).isEqualTo(1);
        assertThat(ownerInitial.path("content").get(0).path("type").asText())
                .isEqualTo("TEAM_MEMBER_JOINED");

        JsonNode assigned = getJson("/api/v1/notifications", member.accessToken(), 200);
        assertThat(assigned.path("totalElements").asInt()).isEqualTo(1);
        assertThat(assigned.path("content").get(0).path("type").asText()).isEqualTo("TASK_ASSIGNED");
        assertThat(assigned.path("content").get(0).path("read").asBoolean()).isFalse();
        assertThat(assigned.path("content").get(0).path("taskId").asText()).isEqualTo(taskId);
        assertThat(assigned.path("content").get(0).path("projectId").asText()).isEqualTo(projectId);
        assertThat(assigned.path("content").get(0).path("teamId").asText()).isEqualTo(teamId);

        // A comment from the assignee notifies the task creator.
        postJson("/api/v1/tasks/" + taskId + "/comments",
                Map.of("body", "On it."), member.accessToken(), 201);
        // Completing the task notifies the creator as well.
        postJson("/api/v1/tasks/" + taskId + "/complete", null, member.accessToken(), 200);

        JsonNode ownerNotifications = getJson("/api/v1/notifications", owner.accessToken(), 200);
        assertThat(ownerNotifications.path("totalElements").asInt()).isEqualTo(3);
        List<String> types = new ArrayList<>();
        ownerNotifications.path("content").forEach(node -> types.add(node.path("type").asText()));
        assertThat(types).containsExactlyInAnyOrder(
                "TEAM_MEMBER_JOINED", "TASK_COMMENTED", "TASK_COMPLETED");

        assertThat(getJson("/api/v1/notifications/unread-count", owner.accessToken(), 200)
                .path("unreadCount").asLong()).isEqualTo(3);
        assertThat(getJson("/api/v1/notifications?unreadOnly=true", owner.accessToken(), 200)
                .path("totalElements").asInt()).isEqualTo(3);

        String notificationId = ownerNotifications.path("content").get(0).path("id").asText();
        JsonNode read = postJson("/api/v1/notifications/" + notificationId + "/read",
                null, owner.accessToken(), 200);
        assertThat(read.path("read").asBoolean()).isTrue();
        assertThat(read.path("readAt").isMissingNode()).isFalse();

        // Notifications are scoped to their recipient.
        JsonNode foreign = postJson("/api/v1/notifications/" + notificationId + "/read",
                null, member.accessToken(), 404);
        assertThat(foreign.path("code").asText()).isEqualTo("NOT_FOUND");

        assertThat(getJson("/api/v1/notifications/unread-count", owner.accessToken(), 200)
                .path("unreadCount").asLong()).isEqualTo(2);

        JsonNode markedAll = postJson("/api/v1/notifications/read-all", null, owner.accessToken(), 200);
        assertThat(markedAll.path("updated").asInt()).isEqualTo(2);
        assertThat(getJson("/api/v1/notifications/unread-count", owner.accessToken(), 200)
                .path("unreadCount").asLong()).isZero();
    }

    @Test
    void invitationAndJoinEventsNotifyTheRelevantUsers() throws Exception {
        TestUser owner = registerUser("Invite Notify Owner");
        TestUser invitee = registerUser("Invite Notify Invitee");
        TestUser joiner = registerUser("Invite Notify Joiner");
        JsonNode team = createTeam(owner.accessToken(), "Invite Notify Team");
        String teamId = team.path("id").asText();

        JsonNode invitation = postJson("/api/v1/teams/" + teamId + "/invitations",
                Map.of("email", invitee.email(), "role", "MEMBER"), owner.accessToken(), 201);
        String token = invitation.path("token").asText();

        JsonNode inviteeNotifications = getJson("/api/v1/notifications", invitee.accessToken(), 200);
        assertThat(inviteeNotifications.path("totalElements").asInt()).isEqualTo(1);
        JsonNode invitationNotification = inviteeNotifications.path("content").get(0);
        assertThat(invitationNotification.path("type").asText()).isEqualTo("TEAM_INVITATION");
        assertThat(invitationNotification.path("teamId").asText()).isEqualTo(teamId);
        assertThat(invitationNotification.path("message").asText()).contains(token);

        postJson("/api/v1/teams/invitations/" + token + "/accept", null, invitee.accessToken(), 200);
        joinTeam(joiner, team);

        JsonNode ownerNotifications = getJson("/api/v1/notifications", owner.accessToken(), 200);
        assertThat(ownerNotifications.path("totalElements").asInt()).isEqualTo(2);
        ownerNotifications.path("content").forEach(node ->
                assertThat(node.path("type").asText()).isEqualTo("TEAM_MEMBER_JOINED"));
        assertThat(ownerNotifications.path("content").get(0).path("teamId").asText()).isEqualTo(teamId);
    }

    @Test
    void notificationEndpointsRequireAuthenticationAndValidatePaging() throws Exception {
        TestUser user = registerUser("Notify Auth User");

        getJson("/api/v1/notifications", null, 401);
        getJson("/api/v1/notifications/unread-count", null, 401);
        getJson("/api/v1/notifications/stream", null, 401);

        JsonNode invalidPage = getJson("/api/v1/notifications?page=-1", user.accessToken(), 400);
        assertThat(invalidPage.path("code").asText()).isEqualTo("VALIDATION_FAILED");

        JsonNode invalidSize = getJson("/api/v1/notifications?size=500", user.accessToken(), 400);
        assertThat(invalidSize.path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    private void joinTeam(TestUser user, JsonNode team) throws Exception {
        postJson("/api/v1/teams/join",
                Map.of("inviteCode", team.path("inviteCode").asText()), user.accessToken(), 200);
    }
}
