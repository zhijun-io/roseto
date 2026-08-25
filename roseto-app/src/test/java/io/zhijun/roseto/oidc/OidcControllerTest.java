package io.zhijun.roseto.oidc;

import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.tenant.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OidcControllerTest extends io.zhijun.roseto.RosetoContainerTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;

    @BeforeEach
    void setUp() {
        if (tenants.count() == 0) {
            tenants.save(new Tenant(UUID.randomUUID(), "acme", "Acme", URI.create("https://auth.example/t/acme"), Instant.now()));
        }
    }

    @Test
    void discoveryPublishesOidcEndpoints() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issuer").value("https://auth.example/t/acme"))
            .andExpect(jsonPath("$.authorization_endpoint").value("https://auth.example/t/acme/authorize"))
            .andExpect(jsonPath("$.token_endpoint").value("https://auth.example/t/acme/token"))
            .andExpect(jsonPath("$.jwks_uri").value("https://auth.example/t/acme/jwks"));
    }

    @Test
    void jwksPublishesAnRsaSigningKey() throws Exception {
        mvc.perform(get("/oauth2/jwks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.keys[0].kid").value("roseto-1"))
            .andExpect(jsonPath("$.keys[0].use").value("sig"));
    }
}
