package com.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared harness for HTTP-level integration tests. There is deliberately no
 * test-wide transaction: every HTTP call must commit like it does in
 * production, otherwise bulk updates and deletes stay invisible to the next
 * request because the persistence context is never refreshed. Tests stay
 * independent by registering unique users and creating their own teams.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    public record TestUser(UUID id, String name, String email, String password,
                           String accessToken, String refreshToken) {
    }

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected TestUser registerUser(String name) throws Exception {
        return registerUser(name, uniqueEmail());
    }

    protected TestUser registerUser(String name, String email) throws Exception {
        String password = "Password123";
        JsonNode body = postJson("/api/v1/auth/register",
                Map.of("name", name, "email", email, "password", password), null, 201);
        return new TestUser(
                UUID.fromString(body.path("user").path("id").asText()),
                name, email, password,
                body.path("accessToken").asText(),
                body.path("refreshToken").asText());
    }

    protected JsonNode postJson(String url, Object body, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andExpect(status().is(expectedStatus)).andReturn();
        return readBody(result);
    }

    protected JsonNode getJson(String url, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(url);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andExpect(status().is(expectedStatus)).andReturn();
        return readBody(result);
    }

    protected JsonNode patchJson(String url, Object body, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.patch(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andExpect(status().is(expectedStatus)).andReturn();
        return readBody(result);
    }

    protected JsonNode putJson(String url, Object body, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andExpect(status().is(expectedStatus)).andReturn();
        return readBody(result);
    }

    protected void delete(String url, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(url);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        mockMvc.perform(builder).andExpect(status().is(expectedStatus));
    }

    protected MvcResult upload(String url, String token, String fileName, String contentType,
                               byte[] content, int expectedStatus) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, contentType, content);
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart(url)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return result;
    }

    protected JsonNode createTeam(String token, String name) throws Exception {
        return postJson("/api/v1/teams",
                Map.of("name", name, "description", "Test team for " + name), token, 201);
    }

    protected JsonNode createProject(String token, String teamId, String name) throws Exception {
        return postJson("/api/v1/teams/" + teamId + "/projects",
                Map.of("name", name, "description", "Project " + name), token, 201);
    }

    protected JsonNode createTask(String token, String projectId, String title) throws Exception {
        return postJson("/api/v1/projects/" + projectId + "/tasks",
                Map.of("title", title, "description", "Description for " + title,
                        "priority", "MEDIUM"), token, 201);
    }

    protected MockHttpServletRequestBuilder authenticatedGet(String url, String token) {
        return MockMvcRequestBuilders.get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        return content == null || content.isBlank() ? null : objectMapper.readTree(content);
    }
}
