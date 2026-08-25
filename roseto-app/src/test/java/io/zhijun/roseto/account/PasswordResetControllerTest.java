package io.zhijun.roseto.account;

import io.zhijun.roseto.RosetoContainerTest;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.VerificationRepository;
import io.zhijun.roseto.domain.tenant.Tenant;
import io.zhijun.roseto.domain.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetControllerTest extends RosetoContainerTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired VerificationRepository verifications;

    @Test
    void unknownUserDoesNotResetAcrossTenant() throws Exception {
        var tenant = new Tenant(UUID.randomUUID(), "reset-" + UUID.randomUUID(), "Reset", URI.create("https://auth.example/reset"), Instant.now());
        tenants.save(tenant);
        var body = "{\"tenantId\":\"" + tenant.id() + "\",\"username\":\"missing\"}";
        mvc.perform(post("/api/account/password/reset-request").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    void resetRequiresVerificationFields() throws Exception {
        var body = "{\"tenantId\":\"" + UUID.randomUUID() + "\",\"verificationId\":\"" + UUID.randomUUID() + "\",\"code\":\"\",\"newPassword\":\"\"}";
        mvc.perform(post("/api/account/password/reset").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }
}
