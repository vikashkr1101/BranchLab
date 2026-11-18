package com.branchlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceFlowTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;

    ResultActions submit(String path, Object payload) throws Exception {
        return mvc.perform(post("/api/v1/auth/" + path).contentType(APPLICATION_JSON)
                .content(json.writeValueAsBytes(payload)));
    }

    String access(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }

    @Test
    void schemaAndHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        assertThat(db.queryForList("select tablename from pg_tables where schemaname='public'", String.class))
                .contains("workspace", "app_user", "dataset", "event", "policy", "policy_version", "experiment",
                        "experiment_arm", "run", "run_outcome", "run_diff", "run_summary", "job");
    }

    @Test
    void signupLoginRefreshAndReadOwnWorkspace() throws Exception {
        var owner = Map.of("workspaceName", "Indigo Goods", "email", "owner@example.test", "password", "local-test-password");
        var signup = submit("signup", owner).andExpect(status().isCreated()).andReturn();
        var token = access(signup);
        var cookie = signup.getResponse().getCookie("branchlab_refresh");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(encoder.matches(owner.get("password"), db.queryForObject(
                "select password_hash from app_user where email=?", String.class, owner.get("email")))).isTrue();

        submit("signup", owner).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from workspace where name='Indigo Goods'", Integer.class)).isOne();
        var credentials = Map.of("email", "OWNER@example.test", "password", owner.get("password"));
        var login = submit("login", credentials).andExpect(status().isOk()).andReturn();
        var second = submit("signup", Map.of("workspaceName", "Second shop", "email", "second@example.test",
                "password", "second-password")).andExpect(status().isCreated()).andReturn();
        db.update("""
                insert into dataset (id, workspace_id, name, source, status)
                select gen_random_uuid(), workspace_id, 'Private dataset', 'GENERATED', 'READY'
                from app_user where email='second@example.test'
                """);
        var refresh = mvc.perform(post("/api/v1/auth/refresh").cookie(cookie).contentType(APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        for (var bearer : new String[]{token, access(login), access(refresh)}) {
            mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Indigo Goods"))
                    .andExpect(jsonPath("$.datasets").value(0)).andExpect(jsonPath("$.policies").value(0))
                    .andExpect(jsonPath("$.recentRuns").isEmpty());
        }
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + access(second)))
                .andExpect(jsonPath("$.name").value("Second shop")).andExpect(jsonPath("$.datasets").value(1));
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + cookie.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("branchlab_refresh", token)).contentType(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie).contentType(APPLICATION_JSON))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("branchlab_refresh", 0));
    }

    @Test
    void repeatedLoginAttemptsAreLimited() throws Exception {
        var credentials = Map.of("email", "rate-limit@example.test", "password", "unknown-password");
        for (int attempt = 0; attempt < 20; attempt++) {
            submit("login", credentials).andExpect(status().isUnauthorized());
        }
        submit("login", credentials).andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void failuresUseProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/workspace")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON));
        submit("signup", Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        submit("login", Map.of("email", "missing@example.test", "password", "unknown-password"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").isString());
        mvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }
}
