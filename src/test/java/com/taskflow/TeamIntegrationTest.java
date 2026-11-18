package com.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeamIntegrationTest extends IntegrationTestSupport {

    @Test
    void ownerCreatesTeamAndInvitesMemberByEmail() throws Exception {
        TestUser owner = registerUser("Team Owner");
        TestUser invitee = registerUser("Invited Member");
        JsonNode team = createTeam(owner.accessToken(), "Platform Team");

        assertThat(team.path("myRole").asText()).isEqualTo("OWNER");
        assertThat(team.path("memberCount").asInt()).isEqualTo(1);
        assertThat(team.path("inviteCode").asText()).hasSize(10);

        JsonNode invitation = postInvitation(owner.accessToken(), team.path("id").asText(), invitee.email(), "ADMIN");
        assertThat(invitation.path("status").asText()).isEqualTo("PENDING");
        assertThat(invitation.path("token").asText()).isNotBlank();

        JsonNode mine = getJson("/api/v1/teams/invitations/mine", invitee.accessToken(), 200);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).path("teamName").asText()).isEqualTo("Platform Team");

        JsonNode accepted = postJson("/api/v1/teams/invitations/"
                        + invitation.path("token").asText() + "/accept", null, invitee.accessToken(), 200);
        assertThat(accepted.path("myRole").asText()).isEqualTo("ADMIN");
        assertThat(accepted.path("memberCount").asInt()).isEqualTo(2);

        postJson("/api/v1/teams/invitations/" + invitation.path("token").asText() + "/accept",
                null, invitee.accessToken(), 409);
    }

    @Test
    void duplicatePendingInvitationIsRejected() throws Exception {
        TestUser owner = registerUser("Invite Owner");
        JsonNode team = createTeam(owner.accessToken(), "Invite Team");
        String teamId = team.path("id").asText();
        String email = uniqueEmail();

        postInvitation(owner.accessToken(), teamId, email, "MEMBER");
        JsonNode conflict = postJson("/api/v1/teams/" + teamId + "/invitations",
                Map.of("email", email, "role", "MEMBER"), owner.accessToken(), 409);
        assertThat(conflict.path("code").asText()).isEqualTo("CONFLICT");
    }

    @Test
    void joiningByInviteCodeHidesCodeFromRegularMembers() throws Exception {
        TestUser owner = registerUser("Code Owner");
        TestUser joiner = registerUser("Code Joiner");
        JsonNode team = createTeam(owner.accessToken(), "Open Team");
        String teamId = team.path("id").asText();

        JsonNode joined = postJson("/api/v1/teams/join",
                Map.of("inviteCode", team.path("inviteCode").asText()), joiner.accessToken(), 200);
        assertThat(joined.path("myRole").asText()).isEqualTo("MEMBER");
        assertThat(joined.path("memberCount").asInt()).isEqualTo(2);
        assertThat(joined.path("inviteCode").isMissingNode()).isTrue();

        postJson("/api/v1/teams/join",
                Map.of("inviteCode", team.path("inviteCode").asText()), joiner.accessToken(), 409);

        JsonNode members = getJson("/api/v1/teams/" + teamId + "/members", joiner.accessToken(), 200);
        assertThat(members).hasSize(2);

        getJson("/api/v1/teams/" + teamId + "/invitations", joiner.accessToken(), 403);
        patchJson("/api/v1/teams/" + teamId, Map.of("name", "Renamed"), joiner.accessToken(), 403);
    }

    @Test
    void nonMembersCannotReadTeamsOrProjects() throws Exception {
        TestUser owner = registerUser("Private Owner");
        TestUser outsider = registerUser("Outsider");
        JsonNode team = createTeam(owner.accessToken(), "Private Team");
        String teamId = team.path("id").asText();
        JsonNode project = createProject(owner.accessToken(), teamId, "Secret Project");

        getJson("/api/v1/teams/" + teamId, outsider.accessToken(), 403);
        getJson("/api/v1/projects/" + project.path("id").asText(), outsider.accessToken(), 403);
        getJson("/api/v1/teams/" + teamId + "/projects", outsider.accessToken(), 403);
    }

    @Test
    void roleChangesAndRemovalsRespectHierarchy() throws Exception {
        TestUser owner = registerUser("Hierarchy Owner");
        TestUser admin = registerUser("Team Admin");
        TestUser member = registerUser("Team Member");
        JsonNode team = createTeam(owner.accessToken(), "Hierarchy Team");
        String teamId = team.path("id").asText();
        joinTeam(admin, team.path("inviteCode").asText());
        joinTeam(member, team.path("inviteCode").asText());

        // Only the owner can promote/demote.
        patchJson("/api/v1/teams/" + teamId + "/members/" + member.id(),
                Map.of("role", "ADMIN"), admin.accessToken(), 403);

        JsonNode promoted = patchJson("/api/v1/teams/" + teamId + "/members/" + member.id(),
                Map.of("role", "ADMIN"), owner.accessToken(), 200);
        assertThat(promoted.path("role").asText()).isEqualTo("ADMIN");

        // Admins can't remove other admins, but the owner can.
        delete("/api/v1/teams/" + teamId + "/members/" + member.id(), admin.accessToken(), 403);
        delete("/api/v1/teams/" + teamId + "/members/" + member.id(), owner.accessToken(), 204);
        getJson("/api/v1/teams/" + teamId, member.accessToken(), 403);

        // Owner cannot be removed and ownership cannot be re-assigned.
        delete("/api/v1/teams/" + teamId + "/members/" + owner.id(), owner.accessToken(), 400);
        patchJson("/api/v1/teams/" + teamId + "/members/" + admin.id(),
                Map.of("role", "OWNER"), owner.accessToken(), 400);
    }

    @Test
    void memberCanLeaveAndRotateCodeRequiresManager() throws Exception {
        TestUser owner = registerUser("Leave Owner");
        TestUser member = registerUser("Leaver");
        JsonNode team = createTeam(owner.accessToken(), "Leaving Team");
        String teamId = team.path("id").asText();
        joinTeam(member, team.path("inviteCode").asText());

        postJson("/api/v1/teams/" + teamId + "/invite-code/rotate", null, member.accessToken(), 403);
        JsonNode rotated = postJson("/api/v1/teams/" + teamId + "/invite-code/rotate",
                null, owner.accessToken(), 200);
        assertThat(rotated.path("inviteCode").asText())
                .isNotEqualTo(team.path("inviteCode").asText());

        delete("/api/v1/teams/" + teamId + "/members/" + member.id(), member.accessToken(), 204);
        getJson("/api/v1/teams/" + teamId, member.accessToken(), 403);
    }

    @Test
    void deletingTeamCascadesToProjectsAndTasks() throws Exception {
        TestUser owner = registerUser("Delete Owner");
        JsonNode team = createTeam(owner.accessToken(), "Disposable Team");
        String teamId = team.path("id").asText();
        JsonNode project = createProject(owner.accessToken(), teamId, "Disposable Project");
        JsonNode task = createTask(owner.accessToken(), project.path("id").asText(), "Disposable Task");

        delete("/api/v1/teams/" + teamId, owner.accessToken(), 204);

        getJson("/api/v1/teams/" + teamId, owner.accessToken(), 404);
        getJson("/api/v1/projects/" + project.path("id").asText(), owner.accessToken(), 404);
        getJson("/api/v1/tasks/" + task.path("id").asText(), owner.accessToken(), 404);
    }

    private JsonNode postInvitation(String token, String teamId, String email, String role) throws Exception {
        return postJson("/api/v1/teams/" + teamId + "/invitations",
                Map.of("email", email, "role", role), token, 201);
    }

    private void joinTeam(TestUser user, String inviteCode) throws Exception {
        postJson("/api/v1/teams/join", Map.of("inviteCode", inviteCode), user.accessToken(), 200);
    }
}
