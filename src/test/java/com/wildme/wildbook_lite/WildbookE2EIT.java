package com.wildme.wildbook_lite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wildme.wildbook_lite.support.AbstractPostgresIT;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Full-stack integration test: real Spring context, real Postgres, real
 * Spring Security filter chain, but no actual HTTP server — MockMvc
 * dispatches in-process.
 *
 * Why this test exists alongside the unit + slice tests:
 *
 *  - Unit tests verify each piece in isolation.
 *  - Slice tests verify a layer (JPA / Web) in cooperation with Spring's
 *    auto-config for that layer.
 *  - This E2E test is the catch-net for *integration* problems: are the
 *    JWT, security filter chain, controllers, services, JPA, and event
 *    listeners actually wired together correctly?
 *
 * Trade-off:
 *  - Spring context costs ~5s to boot. Keep these tests few.
 *  - One run can cover a lot of ground — here it walks the whole
 *    register → login → create-project → create-encounter flow.
 *
 * Spring Boot bits demonstrated:
 *
 *  - @SpringBootTest(webEnvironment = MOCK):
 *      Boots the FULL context but does NOT start Tomcat. Faster +
 *      MockMvc gives us synchronous assertions on the response.
 *  - MockMvcBuilders.webAppContextSetup(...).apply(springSecurity()):
 *      Manually building MockMvc so we can plug in the Spring Security
 *      filter chain. With @AutoConfigureMockMvc that's automatic.
 *  - @TestInstance(PER_CLASS): one instance, so test methods can share
 *    state (the JWT) cheaply. The default PER_METHOD requires a
 *    @BeforeAll method to be static — annoying.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class WildbookE2EIT extends AbstractPostgresIT {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String accessToken;

    @org.junit.jupiter.api.BeforeAll
    void setup() {
        this.mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("register → login token round-trip")
    void register() throws Exception {
        // Using a username with a fixed prefix + nanoTime so reruns
        // against a kept-alive container don't fight a UNIQUE constraint.
        String username = "alice" + System.nanoTime();
        String json = """
            {"username":"%s","email":"%s@x.com","password":"password123"}
            """.formatted(username, username);

        MvcResult result = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.refreshToken").isString())
            .andExpect(jsonPath("$.username").value(username))
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        this.accessToken = body.get("accessToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("hitting a protected endpoint without a token returns 401")
    void unauthenticated() throws Exception {
        mvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("with the JWT, /api/users/me returns the current user")
    void authenticated() throws Exception {
        mvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").exists())
            .andExpect(jsonPath("$.passwordHash").doesNotExist()); // safe view, no hash leaked
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("create project → create encounter → list encounters")
    void projectFlow() throws Exception {
        // Create project — creator becomes OWNER
        MvcResult res = mvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"E2E Test\"}"))
            .andExpect(status().isOk())
            .andReturn();
        long projectId = objectMapper.readTree(res.getResponse().getContentAsString())
            .get("id").asLong();

        // Create encounter inside that project
        mvc.perform(post("/api/encounters")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"projectId":%d,"species":"Humpback whale","location":"Maui","notes":"calf"}
                    """.formatted(projectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.species").value("Humpback whale"));

        // List encounters in the project
        mvc.perform(get("/api/encounters")
                .header("Authorization", "Bearer " + accessToken)
                .param("projectId", String.valueOf(projectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].location").value("Maui"));
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("/actuator/health is publicly reachable")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
