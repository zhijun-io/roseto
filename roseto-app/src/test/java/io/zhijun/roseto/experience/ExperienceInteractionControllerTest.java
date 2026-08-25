package io.zhijun.roseto.experience;

import io.zhijun.roseto.RosetoContainerTest;
import io.zhijun.roseto.domain.interaction.ExperienceInteraction;
import io.zhijun.roseto.domain.spi.ExperienceInteractionRepository;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.UserSessionRepository;
import io.zhijun.roseto.domain.spi.VerificationRepository;
import io.zhijun.roseto.domain.verification.Verification;
import io.zhijun.roseto.domain.tenant.Tenant;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExperienceInteractionControllerTest extends RosetoContainerTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired ExperienceInteractionRepository interactions;
    @Autowired UserRepository users;
    @Autowired VerificationRepository verifications;
    @Autowired UserSessionRepository sessions;

    @Test
    void createsReadsAndCompletesInteractionWithinTenant() throws Exception {
        var tenant = tenant();
        var request = "{\"tenantId\":\"" + tenant.id() + "\",\"identifier\":\"alice@example.com\",\"mode\":\"sign-in\"}";
        var created = mvc.perform(post("/api/experience/interaction").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CREATED")).andReturn();
        var interactionId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.interactionId"));
        mvc.perform(get("/api/experience/interaction").param("tenantId", tenant.id().toString()).param("interactionId", interactionId.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.identifier").value("alice@example.com"));
        mvc.perform(post("/api/experience/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenant.id() + "\",\"interactionId\":\"" + interactionId + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void signUpSubmitCreatesUserAndSession() throws Exception {
        var tenant = tenant();
        var create = "{\"tenantId\":\"" + tenant.id() + "\",\"identifier\":\"new@example.com\",\"mode\":\"sign-up\"}";
        var created = mvc.perform(post("/api/experience/interaction").contentType(MediaType.APPLICATION_JSON).content(create))
            .andExpect(status().isCreated()).andReturn();
        var interactionId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.interactionId"));
        var verification = verifications.save(Verification.issue(UUID.randomUUID(), tenant.id(), null, interactionId,
            "email_verification", "new@example.com", "123456", Instant.now().plusSeconds(300), 3));
        verifications.save(verification.verify("123456", Instant.now()).verification());
        mvc.perform(post("/api/experience/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenant.id() + "\",\"interactionId\":\"" + interactionId
                    + "\",\"password\":\"correct-horse\",\"verificationId\":\"" + verification.id() + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").exists()).andExpect(jsonPath("$.sessionId").exists());
        var user = users.findByTenantAndUsername(tenant.id(), "new@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(sessions.findByUser(tenant.id(), user.id()).isEmpty());
    }

    @Test
    void signUpSubmitRequiresConsumedVerificationBoundToInteraction() throws Exception {
        var tenant = tenant();
        var created = mvc.perform(post("/api/experience/interaction").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenant.id() + "\",\"identifier\":\"unverified@example.com\",\"mode\":\"sign-up\"}"))
            .andExpect(status().isCreated()).andReturn();
        var interactionId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.interactionId");
        mvc.perform(post("/api/experience/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"" + tenant.id() + "\",\"interactionId\":\"" + interactionId
                    + "\",\"password\":\"correct-horse\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void doesNotExposeInteractionAcrossTenants() throws Exception {
        var owner = tenant();
        var other = tenant();
        var interaction = interactions.save(new ExperienceInteraction(UUID.randomUUID(), owner.id(), "a@example.com", "sign-in",
            ExperienceInteraction.Status.CREATED, Instant.now(), Instant.now()));
        mvc.perform(get("/api/experience/interaction").param("tenantId", other.id().toString()).param("interactionId", interaction.id().toString()))
            .andExpect(status().isNotFound());
    }

    private Tenant tenant() {
        var id = UUID.randomUUID();
        return tenants.save(new Tenant(id, "interaction-" + id, "Interaction", URI.create("https://auth.example/" + id), Instant.now()));
    }
}
