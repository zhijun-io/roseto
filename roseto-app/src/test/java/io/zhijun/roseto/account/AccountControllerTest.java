package io.zhijun.roseto.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest extends io.zhijun.roseto.RosetoContainerTest {
    @Autowired
    MockMvc mvc;

    @Test
    void accountRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/account/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void accountReturnsAuthenticatedSubjectAndTenant() throws Exception {
        mvc.perform(get("/api/account/me").with(jwt().jwt(token -> {
            token.subject("user-1");
            token.claim("tenant_id", "tenant-1");
            token.claim("scope", "openid api:read");
            token.audience(java.util.List.of("https://api.example.com"));
        })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sub").value("user-1"))
            .andExpect(jsonPath("$.tenant_id").value("tenant-1"))
            .andExpect(jsonPath("$.scope").value("openid api:read"))
            .andExpect(jsonPath("$.aud[0]").value("https://api.example.com"));
    }
}
