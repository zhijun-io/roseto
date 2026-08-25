package io.zhijun.roseto.authorization;

import io.zhijun.roseto.domain.application.Application;
import io.zhijun.roseto.domain.spi.ApplicationRepository;
import io.zhijun.roseto.domain.spi.ExperienceInteractionRepository;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.UserSessionRepository;
import io.zhijun.roseto.domain.tenant.Tenant;
import io.zhijun.roseto.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationControllerTest extends io.zhijun.roseto.RosetoContainerTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired ApplicationRepository applications;
    @Autowired ExperienceInteractionRepository interactions;
    @Autowired UserSessionRepository sessions;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    private String clientId;

    @BeforeEach
    void setUp() {
        var tenant = tenants.save(new Tenant(UUID.randomUUID(), "pkce-" + UUID.randomUUID(), "Acme",
            URI.create("https://auth.example/t/" + UUID.randomUUID()), Instant.now()));
        clientId = "pkce-client-" + UUID.randomUUID();
        var app = applications.save(new Application(UUID.randomUUID(), tenant.id(), clientId, null, "PKCE", Instant.now()));
        applications.addRedirectUri(app.id(), "https://app.example/callback");
        users.save(new User(UUID.randomUUID(), tenant.id(), "alice", passwords.encode("password"), true, Instant.now()));
    }

    @Test
    void authorizeRequiresPkceAndRendersLoginPage() throws Exception {
        mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("scope", "openid").param("state", "state-1")
                .param("code_challenge", "challenge").param("code_challenge_method", "S256"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/oauth2/authorize/login")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("interaction_id")));
    }

    @Test
    void authorizeLoginCompletesInteractionAndCreatesSession() throws Exception {
        var page = mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("scope", "openid").param("state", "state-1")
                .param("code_challenge", "challenge").param("code_challenge_method", "S256")).andReturn();
        var html = page.getResponse().getContentAsString();
        var interactionId = html.replaceAll("(?s).*name='interaction_id' value='([^']+)'.*", "$1");
        var app = applications.findByClientId(clientId).orElseThrow();
        var interaction = interactions.findByIdAndTenant(app.tenantId(), UUID.fromString(interactionId)).orElseThrow();
        mvc.perform(post("/oauth2/authorize/login").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("scope", "openid").param("state", "state-1")
                .param("code_challenge", "challenge").param("code_challenge_method", "S256")
                .param("username", "alice").param("password", "password").param("interaction_id", interaction.id().toString()))
            .andExpect(status().is3xxRedirection());
        var completed = interactions.findByIdAndTenant(app.tenantId(), interaction.id()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", completed.status().name());
        var user = users.findByTenantAndUsername(app.tenantId(), "alice").orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(sessions.findByUser(app.tenantId(), user.id()).isEmpty());
    }

    @Test
    void authorizeLoginRejectsTamperedInteractionContext() throws Exception {
        var page = mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("scope", "openid").param("state", "state-1")
                .param("code_challenge", "challenge").param("code_challenge_method", "S256")
                .param("nonce", "nonce-1")).andReturn();
        var html = page.getResponse().getContentAsString();
        var interactionId = html.replaceAll("(?s).*name='interaction_id' value='([^']+)'.*", "$1");
        mvc.perform(post("/oauth2/authorize/login").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("scope", "openid").param("state", "state-1")
                .param("code_challenge", "challenge").param("code_challenge_method", "S256")
                .param("nonce", "nonce-2").param("username", "alice").param("password", "password")
                .param("interaction_id", interactionId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void authorizeRejectsMissingPkce() throws Exception {
        mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback")).andExpect(status().isBadRequest());
    }

    @Test
    void authorizeRejectsMissingState() throws Exception {
        mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("scope", "openid")
                .param("code_challenge", "challenge").param("code_challenge_method", "S256"))
            .andExpect(status().isBadRequest());
    }


    @Test
    void authorizeRejectsMissingScope() throws Exception {
        mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("state", "state-1")
                .param("code_challenge", "challenge").param("code_challenge_method", "S256"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void authorizeRejectsScopeWithoutOpenid() throws Exception {
        mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("scope", "api:read").param("state", "state-1")
                .param("code_challenge", "challenge").param("code_challenge_method", "S256"))
            .andExpect(status().isBadRequest());
    }
    @Test
    void authorizeRejectsPlainPkce() throws Exception {
        mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("scope", "openid")
                .param("code_challenge", "challenge").param("code_challenge_method", "plain"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void authorizeRejectsApiScopeWithoutResource() throws Exception {
        mvc.perform(get("/oauth2/authorize").param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", "https://app.example/callback").param("scope", "openid api:read")
                .param("code_challenge", "challenge").param("code_challenge_method", "S256"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsCodeOnlyToExactRedirectUri() throws Exception {
        mvc.perform(post("/oauth2/authorize/login").param("client_id", clientId).param("redirect_uri", "https://app.example/callback")
                .param("scope", "openid").param("state", "state-2").param("code_challenge", "challenge")
                .param("code_challenge_method", "S256").param("username", "alice").param("password", "password"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("https://app.example/callback?code=*&state=state-2"));
    }
}
