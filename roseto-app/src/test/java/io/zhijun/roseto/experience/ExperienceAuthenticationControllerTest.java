package io.zhijun.roseto.experience;

import io.zhijun.roseto.RosetoContainerTest;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.UserSessionRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExperienceAuthenticationControllerTest extends RosetoContainerTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;

    @Test
    void registersLogsInAndPersistsSession() throws Exception {
        var tenantId = UUID.randomUUID();
        tenants.save(new Tenant(tenantId, "auth-" + tenantId, "Auth", URI.create("https://auth.example/" + tenantId), Instant.now()));
        var register = "{\"tenantId\":\"" + tenantId + "\",\"username\":\"alice\",\"password\":\"correct-horse\"}";
        mvc.perform(post("/api/experience/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("alice"));
        var login = "{\"tenantId\":\"" + tenantId + "\",\"username\":\"alice\",\"password\":\"correct-horse\",\"device\":\"browser\"}";
        var result = mvc.perform(post("/api/experience/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").exists()).andReturn();
        org.junit.jupiter.api.Assertions.assertFalse(sessions.findByUser(tenantId,
            UUID.fromString(com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.userId"))).isEmpty());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        var tenantId = UUID.randomUUID();
        tenants.save(new Tenant(tenantId, "auth-" + tenantId, "Auth", URI.create("https://auth.example/" + tenantId), Instant.now()));
        var body = "{\"tenantId\":\"" + tenantId + "\",\"username\":\"missing\",\"password\":\"wrong\"}";
        mvc.perform(post("/api/experience/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
    }
}
