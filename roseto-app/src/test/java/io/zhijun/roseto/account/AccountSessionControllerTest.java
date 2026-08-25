package io.zhijun.roseto.account;

import io.zhijun.roseto.RosetoContainerTest;
import io.zhijun.roseto.domain.session.UserSession;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.UserSessionRepository;
import io.zhijun.roseto.domain.tenant.Tenant;
import io.zhijun.roseto.domain.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSessionControllerTest extends RosetoContainerTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;

    @Test
    void listsAndRevokesOnlyCurrentUsersSessions() throws Exception {
        var tenantId = UUID.randomUUID();
        tenants.save(new Tenant(tenantId, "sessions-" + tenantId, "Sessions", URI.create("https://auth.example/sessions/" + tenantId), Instant.now()));
        var userId = UUID.randomUUID();
        users.save(new User(userId, tenantId, "session-user-" + userId, "hash", true, Instant.now()));
        var sessionId = UUID.randomUUID();
        sessions.save(new UserSession(sessionId, tenantId, userId, "Safari", Instant.now(), Instant.now(), null));
        var auth = jwt().jwt(token -> { token.subject(userId.toString()); token.claim("tenant_id", tenantId.toString()); });

        mvc.perform(get("/api/account/sessions").with(auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(sessionId.toString()))
            .andExpect(jsonPath("$[0].active").value(true));
        mvc.perform(delete("/api/account/sessions/" + sessionId).with(auth))
            .andExpect(status().isOk());
        mvc.perform(get("/api/account/sessions").with(auth))
            .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void oidcLogoutRevokesAuthenticatedUserSessions() throws Exception {
        var tenantId = UUID.randomUUID();
        tenants.save(new Tenant(tenantId, "logout-" + tenantId, "Logout", URI.create("https://auth.example/logout/" + tenantId), Instant.now()));
        var userId = UUID.randomUUID();
        users.save(new User(userId, tenantId, "logout-user-" + userId, "hash", true, Instant.now()));
        var sessionId = UUID.randomUUID();
        sessions.save(new UserSession(sessionId, tenantId, userId, "Chrome", Instant.now(), Instant.now(), null));
        var auth = jwt().jwt(token -> { token.subject(userId.toString()); token.claim("tenant_id", tenantId.toString()); });

        mvc.perform(get("/oauth2/logout").with(auth))
            .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertFalse(
            sessions.findByIdAndUser(tenantId, userId, sessionId).orElseThrow().active());
    }
}
