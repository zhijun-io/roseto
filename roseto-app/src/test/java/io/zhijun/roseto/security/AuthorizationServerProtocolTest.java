package io.zhijun.roseto.security;

import io.zhijun.roseto.domain.application.Application;
import io.zhijun.roseto.domain.spi.ApplicationRepository;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.tenant.Tenant;
import io.zhijun.roseto.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "roseto.authorization-server.enabled=true"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class AuthorizationServerProtocolTest extends io.zhijun.roseto.RosetoContainerTest {
    @Autowired ApplicationRepository applications;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc mvc;

    private String clientId;
    private String username;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired JwtDecoder jwtDecoder;

    private String issuer;

    @BeforeEach
    void registerClient() {
        var tenant = tenants.findFirst().orElseGet(() -> {
            issuer = "http://localhost";
            return tenants.save(new Tenant(java.util.UUID.randomUUID(), "sas-" + java.util.UUID.randomUUID(),
                "SAS", java.net.URI.create(issuer), java.time.Instant.now()));
        });
        issuer = tenant.issuer().toString();
        var application = applications.save(new Application(java.util.UUID.randomUUID(), tenant.id(),
            "client-" + java.util.UUID.randomUUID(), null, "SAS client", java.time.Instant.now()));
        username = "user-" + java.util.UUID.randomUUID();
        users.save(new User(java.util.UUID.randomUUID(), tenant.id(), username,
            passwords.encode("password"), true, java.time.Instant.now()));
        applications.addRedirectUri(application.id(), "https://client.example/callback");
        clientId = application.clientId();
    }

    @Test
    void discoveryPublishesSpringAuthorizationServerEndpoints() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost/oauth2/authorize"))
            .andExpect(jsonPath("$.token_endpoint").value("http://localhost/oauth2/token"))
            .andExpect(jsonPath("$.jwks_uri").value("http://localhost/oauth2/jwks"))
            .andExpect(jsonPath("$.userinfo_endpoint").value("http://localhost/userinfo"));
    }

    @Test
    void loginCompletesAuthorizationAndRedirectsWithCode() throws Exception {
        var challenge = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        var request = mvc.perform(get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", "https://client.example/callback")
                .queryParam("scope", "openid")
                .queryParam("state", "state-123")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        var session = request.getRequest().getSession(false);

        mvc.perform(post("/login")
                .session((org.springframework.mock.web.MockHttpSession) session)
                .param("username", username)
                .param("password", "password"))
            .andExpect(status().is3xxRedirection());

        mvc.perform(get("/oauth2/authorize")
                .session((org.springframework.mock.web.MockHttpSession) session)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", "https://client.example/callback")
                .queryParam("scope", "openid")
                .queryParam("state", "state-123")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256"))
            .andExpect(status().is3xxRedirection())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern(
                "https://client.example/callback?code=*&state=state-123"));
    }

    @Test
    void authorizationCodeCanBeExchangedForOidcTokens() throws Exception {
        var verifier = "verifier-value-123456789012345678901234567890";
        var challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        var authorization = mvc.perform(get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", "https://client.example/callback")
                .queryParam("scope", "openid")
                .queryParam("nonce", "nonce-token")
                .queryParam("state", "state-token")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256"))
            .andReturn();
        var session = authorization.getRequest().getSession(false);
        mvc.perform(post("/login")
                .session((org.springframework.mock.web.MockHttpSession) session)
                .param("username", username)
                .param("password", "password"))
            .andExpect(status().is3xxRedirection());
        var callback = mvc.perform(get("/oauth2/authorize")
                .session((org.springframework.mock.web.MockHttpSession) session)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", "https://client.example/callback")
                .queryParam("scope", "openid")
                .queryParam("nonce", "nonce-token")
                .queryParam("state", "state-token")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        var code = org.springframework.web.util.UriComponentsBuilder
            .fromUriString(callback.getResponse().getRedirectedUrl())
            .build().getQueryParams().getFirst("code");
        mvc.perform(post("/oauth2/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("client_id", clientId)
                .param("redirect_uri", "https://client.example/callback")
                .param("code", code)
                .param("code_verifier", verifier + "-wrong"))
            .andExpect(status().is4xxClientError());

        var tokenResponse = mvc.perform(post("/oauth2/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("client_id", clientId)
                .param("redirect_uri", "https://client.example/callback")
                .param("code", code)
                .param("code_verifier", verifier))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().string("Pragma", "no-cache"))
            .andExpect(jsonPath("$.expires_in").value(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.greaterThanOrEqualTo(299),
                org.hamcrest.Matchers.lessThanOrEqualTo(300))))
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.id_token").isNotEmpty())
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn();
        var tokenJson = objectMapper.readTree(tokenResponse.getResponse().getContentAsString());
        var accessToken = tokenJson.get("access_token").asText();
        var idToken = jwtDecoder.decode(tokenJson.get("id_token").asText());
        var claims = jwtDecoder.decode(accessToken);
        org.junit.jupiter.api.Assertions.assertEquals("nonce-token", idToken.getClaimAsString("nonce"));
        org.junit.jupiter.api.Assertions.assertEquals(issuer, claims.getIssuer().toString());
        org.junit.jupiter.api.Assertions.assertEquals(username, claims.getSubject());
        org.junit.jupiter.api.Assertions.assertNull(claims.getClaim("preferred_username"));
        org.junit.jupiter.api.Assertions.assertNull(idToken.getClaim("preferred_username"));
        org.junit.jupiter.api.Assertions.assertEquals(clientId, claims.getClaimAsString("client_id"));
        org.junit.jupiter.api.Assertions.assertNotNull(claims.getId());
        mvc.perform(get("/userinfo")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(jsonPath("$.sub").value(username))
            .andExpect(jsonPath("$.preferred_username").doesNotExist());
        mvc.perform(get("/userinfo").header("Accept", "application/json"))
            .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(issuer + "/userinfo"), claims.getAudience());
        mvc.perform(post("/oauth2/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("client_id", clientId)
                .param("redirect_uri", "https://client.example/callback")
                .param("code", code)
                .param("code_verifier", verifier))
            .andExpect(status().is4xxClientError());
        org.junit.jupiter.api.Assertions.assertTrue(claims.getClaimAsString("scope").contains("openid"));
    }
    @Test
    void authorizationRejectsUnregisteredRedirectUri() throws Exception {
        mvc.perform(get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", "https://attacker.example/callback")
                .queryParam("scope", "openid")
                .queryParam("state", "state-token")
                .queryParam("code_challenge", "challenge")
                .queryParam("code_challenge_method", "S256"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void loginPageIsAvailableForAuthorizationRequests() throws Exception {
        mvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=username")));
    }

    @Test
    void authorizationRequestRedirectsUnauthenticatedUserToLogin() throws Exception {
        mvc.perform(get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", "https://client.example/callback")
                .queryParam("scope", "openid")
                .queryParam("state", "state-123")

                .queryParam("code_challenge", "challenge")
                .queryParam("code_challenge_method", "S256"))
            .andExpect(status().is3xxRedirection())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("http://localhost/login"));
    }
}
