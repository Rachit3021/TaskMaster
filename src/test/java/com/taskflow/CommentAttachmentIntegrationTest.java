package com.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentAttachmentIntegrationTest extends IntegrationTestSupport {

    @Test
    void commentsSupportLifecycleAndRoleBasedModeration() throws Exception {
        TestUser owner = registerUser("Comment Owner");
        TestUser author = registerUser("Comment Author");
        TestUser peer = registerUser("Comment Peer");
        JsonNode team = createTeam(owner.accessToken(), "Comment Team");
        joinTeam(author, team);
        joinTeam(peer, team);
        JsonNode project = createProject(owner.accessToken(), team.path("id").asText(), "Comment Project");
        String taskId = createTask(owner.accessToken(), project.path("id").asText(), "Discuss API design")
                .path("id").asText();

        JsonNode comment = postJson("/api/v1/tasks/" + taskId + "/comments",
                Map.of("body", "  I will draft the OpenAPI spec.  "), author.accessToken(), 201);
        String commentId = comment.path("id").asText();
        assertThat(comment.path("body").asText()).isEqualTo("I will draft the OpenAPI spec.");
        assertThat(comment.path("edited").asBoolean()).isFalse();
        assertThat(comment.path("author").path("email").asText()).isEqualTo(author.email());

        JsonNode comments = getJson("/api/v1/tasks/" + taskId + "/comments", peer.accessToken(), 200);
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).path("id").asText()).isEqualTo(commentId);

        // Only the author may edit a comment.
        JsonNode forbiddenEdit = patchJson("/api/v1/comments/" + commentId,
                Map.of("body", "Peer rewrite"), peer.accessToken(), 403);
        assertThat(forbiddenEdit.path("code").asText()).isEqualTo("FORBIDDEN");

        JsonNode edited = patchJson("/api/v1/comments/" + commentId,
                Map.of("body", "Draft ready for review."), author.accessToken(), 200);
        assertThat(edited.path("edited").asBoolean()).isTrue();
        assertThat(edited.path("body").asText()).isEqualTo("Draft ready for review.");

        // A regular teammate cannot delete someone else's comment, but the team owner can.
        delete("/api/v1/comments/" + commentId, peer.accessToken(), 403);
        JsonNode taskWithComment = getJson("/api/v1/tasks/" + taskId, owner.accessToken(), 200);
        assertThat(taskWithComment.path("commentCount").asInt()).isEqualTo(1);

        delete("/api/v1/comments/" + commentId, owner.accessToken(), 204);
        assertThat(getJson("/api/v1/tasks/" + taskId + "/comments", owner.accessToken(), 200)).isEmpty();

        // Authors can delete their own comments.
        JsonNode second = postJson("/api/v1/tasks/" + taskId + "/comments",
                Map.of("body", "Scratch note"), author.accessToken(), 201);
        delete("/api/v1/comments/" + second.path("id").asText(), author.accessToken(), 204);

        JsonNode blank = postJson("/api/v1/tasks/" + taskId + "/comments",
                Map.of("body", "   "), author.accessToken(), 400);
        assertThat(blank.path("fieldErrors").has("body")).isTrue();
    }

    @Test
    void attachmentsUploadDownloadAndDeleteWithPermissions() throws Exception {
        TestUser owner = registerUser("Attachment Owner");
        TestUser uploader = registerUser("Attachment Uploader");
        TestUser peer = registerUser("Attachment Peer");
        TestUser outsider = registerUser("Attachment Outsider");
        JsonNode team = createTeam(owner.accessToken(), "Attachment Team");
        joinTeam(uploader, team);
        joinTeam(peer, team);
        JsonNode project = createProject(owner.accessToken(), team.path("id").asText(), "Attachment Project");
        String taskId = createTask(owner.accessToken(), project.path("id").asText(), "Attach the report")
                .path("id").asText();

        byte[] content = "Quarterly numbers".getBytes(StandardCharsets.UTF_8);
        MvcResult uploadResult = upload("/api/v1/tasks/" + taskId + "/attachments",
                uploader.accessToken(), "quarterly-report.txt", MediaType.TEXT_PLAIN_VALUE, content, 201);
        JsonNode attachment = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String attachmentId = attachment.path("id").asText();

        assertThat(attachment.path("fileName").asText()).isEqualTo("quarterly-report.txt");
        assertThat(attachment.path("contentType").asText()).isEqualTo("text/plain");
        assertThat(attachment.path("sizeBytes").asLong()).isEqualTo(content.length);
        assertThat(attachment.path("uploadedBy").path("email").asText()).isEqualTo(uploader.email());
        assertThat(attachment.path("downloadUrl").asText())
                .isEqualTo("/api/v1/attachments/" + attachmentId + "/download");

        JsonNode attachments = getJson("/api/v1/tasks/" + taskId + "/attachments",
                peer.accessToken(), 200);
        assertThat(attachments).hasSize(1);
        JsonNode taskWithAttachment = getJson("/api/v1/tasks/" + taskId, owner.accessToken(), 200);
        assertThat(taskWithAttachment.path("attachmentCount").asInt()).isEqualTo(1);

        MvcResult download = mockMvc.perform(authenticatedGet(
                        attachment.path("downloadUrl").asText(), peer.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("quarterly-report.txt")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(content);

        // Outsiders can neither list nor download.
        getJson("/api/v1/tasks/" + taskId + "/attachments", outsider.accessToken(), 403);
        getJson("/api/v1/attachments/" + attachmentId + "/download", outsider.accessToken(), 403);

        // A regular teammate cannot delete someone else's upload; the uploader can.
        delete("/api/v1/attachments/" + attachmentId, peer.accessToken(), 403);
        delete("/api/v1/attachments/" + attachmentId, uploader.accessToken(), 204);
        getJson("/api/v1/attachments/" + attachmentId + "/download", uploader.accessToken(), 404);
        assertThat(getJson("/api/v1/tasks/" + taskId + "/attachments",
                uploader.accessToken(), 200)).isEmpty();
    }

    @Test
    void uploadsRejectEmptyBlockedOversizedAndUnsupportedFiles() throws Exception {
        TestUser owner = registerUser("Upload Guard Owner");
        JsonNode team = createTeam(owner.accessToken(), "Upload Guard Team");
        JsonNode project = createProject(owner.accessToken(), team.path("id").asText(), "Upload Guard Project");
        String taskId = createTask(owner.accessToken(), project.path("id").asText(), "Guard uploads")
                .path("id").asText();
        String url = "/api/v1/tasks/" + taskId + "/attachments";

        MvcResult empty = upload(url, owner.accessToken(), "empty.txt",
                MediaType.TEXT_PLAIN_VALUE, new byte[0], 400);
        assertThat(empty.getResponse().getContentAsString()).contains("empty");

        MvcResult blocked = upload(url, owner.accessToken(), "payload.exe",
                MediaType.TEXT_PLAIN_VALUE, "not really a text file".getBytes(StandardCharsets.UTF_8), 400);
        assertThat(blocked.getResponse().getContentAsString()).contains("not allowed");

        MvcResult unsupported = upload(url, owner.accessToken(), "notes.rtf",
                "application/rtf", "rtf".getBytes(StandardCharsets.UTF_8), 400);
        assertThat(unsupported.getResponse().getContentAsString()).contains("Unsupported file type");

        MvcResult oversized = upload(url, owner.accessToken(), "large.txt",
                MediaType.TEXT_PLAIN_VALUE, new byte[10 * 1024 * 1024 + 1], 400);
        assertThat(oversized.getResponse().getContentAsString()).contains("maximum allowed size");

        // Path components are stripped from the client-supplied file name.
        MvcResult traversal = upload(url, owner.accessToken(), "../../etc/passwd.txt",
                MediaType.TEXT_PLAIN_VALUE, "safe".getBytes(StandardCharsets.UTF_8), 201);
        JsonNode sanitized = objectMapper.readTree(traversal.getResponse().getContentAsString());
        assertThat(sanitized.path("fileName").asText()).isEqualTo("passwd.txt");
    }

    private void joinTeam(TestUser user, JsonNode team) throws Exception {
        postJson("/api/v1/teams/join",
                Map.of("inviteCode", team.path("inviteCode").asText()), user.accessToken(), 200);
    }
}
