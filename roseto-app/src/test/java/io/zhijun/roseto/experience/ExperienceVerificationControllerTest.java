package io.zhijun.roseto.experience;

import io.zhijun.roseto.RosetoContainerTest;
import io.zhijun.roseto.domain.interaction.ExperienceInteraction;
import io.zhijun.roseto.domain.spi.ExperienceInteractionRepository;
import io.zhijun.roseto.domain.spi.VerificationRepository;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.tenant.Tenant;
import io.zhijun.roseto.domain.verification.Verification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ExperienceVerificationControllerTest extends RosetoContainerTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @Autowired TenantRepository tenants;
    @org.junit.jupiter.api.BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).build(); }
    @Autowired ExperienceInteractionRepository interactions;
    @Autowired VerificationRepository verifications;

    @Test
    void verifiesCodeAndConsumesRecord() throws Exception {
        var tenant = tenants.save(new Tenant(UUID.randomUUID(), "verification-" + UUID.randomUUID(), "Verification",
            URI.create("https://auth.example/verification/" + UUID.randomUUID()), Instant.now()));
        var verificationId = UUID.randomUUID();
        verifications.save(Verification.issue(verificationId, tenant.id(), null, "email_verification",
            "a@example.com", "123456", Instant.now().plusSeconds(300), 3));
        var body = "{\"tenantId\":\"" + tenant.id() + "\",\"verificationId\":\"" + verificationId + "\",\"code\":\"123456\"}";

        mvc.perform(post("/api/experience/verification/email")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("verified"));

        mvc.perform(post("/api/experience/verification/email")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }
    @Test
    void rejectsVerificationBoundToAnotherInteraction() throws Exception {
        var tenant = tenants.save(new Tenant(UUID.randomUUID(), "verification-" + UUID.randomUUID(), "Verification",
            URI.create("https://auth.example/verification/" + UUID.randomUUID()), Instant.now()));
        var interactionId = UUID.randomUUID();
        interactions.save(new ExperienceInteraction(interactionId, tenant.id(), "a@example.com", "sign-up",
            ExperienceInteraction.Status.CREATED, Instant.now(), Instant.now()));
        var verificationId = UUID.randomUUID();
        verifications.save(Verification.issue(verificationId, tenant.id(), null, interactionId, "email_verification",
            "a@example.com", "123456", Instant.now().plusSeconds(300), 3));
        var body = "{\"tenantId\":\"" + tenant.id() + "\",\"verificationId\":\"" + verificationId
            + "\",\"interactionId\":\"" + UUID.randomUUID() + "\",\"code\":\"123456\"}";

        mvc.perform(post("/api/experience/verification/email")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }
}
