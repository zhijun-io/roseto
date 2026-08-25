package io.zhijun.roseto.e2e;

import io.zhijun.roseto.domain.api.ApiResource;
import io.zhijun.roseto.domain.application.Application;
import io.zhijun.roseto.domain.spi.ApiResourceRepository;
import io.zhijun.roseto.domain.spi.ApplicationRepository;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.tenant.Tenant;
import io.zhijun.roseto.domain.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase0EndToEndTest extends io.zhijun.roseto.RosetoContainerTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired ApplicationRepository applications;
    @Autowired ApiResourceRepository apiResources;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;

    @Test
    void authorizationCodePkceIssuesResourceTokenAndProtectsApi() throws Exception {
        var tenant = tenants.save(new Tenant(UUID.randomUUID(), "e2e-" + UUID.randomUUID(), "E2E",
            URI.create("https://auth.example/e2e/" + UUID.randomUUID()), Instant.now()));
        var application = applications.save(new Application(UUID.randomUUID(), tenant.id(), "e2e-client-" + UUID.randomUUID(), null, "PKCE", Instant.now()));
        var otherApplication = applications.save(new Application(UUID.randomUUID(), tenant.id(), "other-client-" + UUID.randomUUID(), null, "PKCE", Instant.now()));
        applications.addRedirectUri(application.id(), "https://app.example/callback");
        var resource = apiResources.save(new ApiResource(UUID.randomUUID(), tenant.id(), "https://api.example.com", "Example API", Instant.now()));
        apiResources.addScope(resource.id(), "api:read");
        applications.addApiResource(application.id(), resource.id());
        var user = users.save(new User(UUID.randomUUID(), tenant.id(), "e2e-user", passwords.encode("correct-password"), true, Instant.now()));

        String verifier = "e2e-code-verifier-123456789";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        MvcResult login = mvc.perform(post("/oauth2/authorize/login")
                .param("client_id", application.clientId()).param("redirect_uri", "https://app.example/callback")
                .param("scope", "openid api:read").param("resource", resource.indicator())
                .param("state", "e2e-state").param("code_challenge", challenge).param("code_challenge_method", "S256")
                .param("username", "e2e-user").param("password", "correct-password"))
            .andExpect(status().is3xxRedirection()).andReturn();
        String code = UriComponentsBuilder.fromUri(URI.create(login.getResponse().getRedirectedUrl())).build().getQueryParams().getFirst("code");

        String token = mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code).param("client_id", application.clientId())
                .param("redirect_uri", "https://app.example/callback").param("code_verifier", verifier)
                .param("scope", "openid api:read").param("resource", resource.indicator()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.refresh_token").isNotEmpty()).andReturn()
            .getResponse().getContentAsString();
        String accessToken = com.jayway.jsonpath.JsonPath.read(token, "$.access_token");
        String refreshToken = com.jayway.jsonpath.JsonPath.read(token, "$.refresh_token");

        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token").param("refresh_token", refreshToken)
                .param("client_id", otherApplication.clientId()))
            .andExpect(status().isBadRequest());

        String rotated = mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token").param("refresh_token", refreshToken)
                .param("client_id", application.clientId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.refresh_token").isNotEmpty()).andReturn()
            .getResponse().getContentAsString();
        String rotatedRefreshToken = com.jayway.jsonpath.JsonPath.read(rotated, "$.refresh_token");
        users.updateEnabled(user.id(), false);
        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token").param("refresh_token", rotatedRefreshToken)
                .param("client_id", application.clientId()))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/oauth2/revoke").param("token", rotatedRefreshToken))
            .andExpect(status().isNoContent());

        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token").param("refresh_token", rotatedRefreshToken)
                .param("client_id", application.clientId()))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/oauth2/logout")
                .param("client_id", application.clientId())
                .param("post_logout_redirect_uri", "https://app.example/callback"))
            .andExpect(status().isFound())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("https://app.example/callback"));
        mvc.perform(get("/oauth2/logout")
                .param("client_id", application.clientId())
                .param("post_logout_redirect_uri", "https://evil.example/callback"))
            .andExpect(status().isBadRequest());

        mvc.perform(get("/api/sample/read").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access").value("read"));

        mvc.perform(post("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code).param("client_id", application.clientId())
                .param("redirect_uri", "https://app.example/callback").param("code_verifier", verifier))
            .andExpect(status().isBadRequest());
    }
}
