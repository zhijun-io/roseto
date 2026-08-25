package io.zhijun.roseto.management;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import io.zhijun.roseto.domain.spi.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagementControllerTest extends io.zhijun.roseto.RosetoContainerTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Test
    @Order(1)
    void bootstrapCreatesTenantAndManagementToken() throws Exception {
        mvc.perform(post("/api/management/bootstrap")
                .header("X-Roseto-Bootstrap-Secret", "test-bootstrap-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantName":"Acme","publicKey":"acme","issuer":"https://auth.example/t/acme","username":"admin","password":"correct horse battery staple"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.publicKey").value("acme"));

        mvc.perform(post("/api/management/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"correct horse battery staple\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @Order(2)
    void bootstrapCannotBeUsedTwice() throws Exception {
        mvc.perform(post("/api/management/bootstrap")
                .header("X-Roseto-Bootstrap-Secret", "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantName\":\"Acme\",\"publicKey\":\"acme\",\"issuer\":\"https://auth.example/t/acme\",\"username\":\"admin\",\"password\":\"password\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void managementCanRegisterApplicationAndUser() throws Exception {
        MvcResult tokenResult = mvc.perform(post("/api/management/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"correct horse battery staple\"}"))
            .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(post("/api/management/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Web","clientId":"web-client","clientSecret":"secret","redirectUris":["https://app.example/callback"]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId").value("web-client"));

        mvc.perform(post("/api/management/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"correct horse battery staple\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("alice"));
        mvc.perform(post("/api/management/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"another-password\"}"))
            .andExpect(status().isConflict());
        var tenantId = tenants.findFirst().orElseThrow().id();
        mvc.perform(post("/api/tenants/" + tenantId + "/resources")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"indicator\":\"https://api.example.com\",\"name\":\"Example API\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.indicator").value("https://api.example.com"));
        mvc.perform(post("/api/tenants/" + tenantId + "/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceIndicator\":\"https://api.example.com\",\"name\":\"api:read\",\"description\":\"Read API\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("api:read"));
        mvc.perform(post("/api/tenants/" + tenantId + "/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo Web\",\"type\":\"web\",\"redirectUris\":[\"https://new.example/callback\"],\"resourceIndicator\":\"https://api.example.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId").isNotEmpty());
        mvc.perform(post("/api/tenants/" + tenantId + "/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"carol@example.com\",\"password\":\"correct horse battery staple\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("carol@example.com"));
        mvc.perform(post("/api/management/tenants/" + tenantId + "/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bob@example.com\",\"password\":\"correct horse battery staple\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("bob@example.com"));
    }
    @Test
    @Order(4)
    void managementCanRegisterTenantRole() throws Exception {
        MvcResult tokenResult = mvc.perform(post("/api/management/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"correct horse battery staple\"}"))
            .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/management/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"billing-admin\",\"description\":\"Billing administrator\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("billing-admin"));
        mvc.perform(post("/api/management/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"billing-admin\"}"))
            .andExpect(status().isConflict());
    }
    @Test
    @Order(5)
    void managementCanAssignPermissionToRole() throws Exception {
        MvcResult tokenResult = mvc.perform(post("/api/management/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"correct horse battery staple\"}"))
            .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.accessToken");
        var body = """
            {"roleName":"billing-admin","resourceIndicator":"https://api.example.com","permissionName":"api:read"}
            """;
        mvc.perform(post("/api/management/roles/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roleName").value("billing-admin"))
            .andExpect(jsonPath("$.permissionName").value("api:read"));
        mvc.perform(post("/api/management/roles/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }
    @Test
    @Order(6)
    void managementCanAssignRoleToUser() throws Exception {
        MvcResult tokenResult = mvc.perform(post("/api/management/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"correct horse battery staple\"}"))
            .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.accessToken");
        var body = "{\"username\":\"alice\",\"roleName\":\"billing-admin\"}";
        mvc.perform(post("/api/management/users/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.roleName").value("billing-admin"));
        mvc.perform(post("/api/management/users/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }
    @Test
    @Order(7)
    void managementCanRemoveRoleFromUser() throws Exception {
        MvcResult tokenResult = mvc.perform(post("/api/management/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"correct horse battery staple\"}"))
            .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.accessToken");
        var body = "{\"username\":\"alice\",\"roleName\":\"billing-admin\"}";
        mvc.perform(delete("/api/management/users/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNoContent());
        mvc.perform(delete("/api/management/users/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound());
    }
}
