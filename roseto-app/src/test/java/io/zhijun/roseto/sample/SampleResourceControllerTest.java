package io.zhijun.roseto.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SampleResourceControllerTest extends io.zhijun.roseto.RosetoContainerTest {
    @Autowired MockMvc mvc;

    @Test
    void readRequiresApiReadScope() throws Exception {
        mvc.perform(get("/api/sample/read").with(SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.claim("scope", "openid"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/sample/read").with(SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.claim("scope", "openid api:read").audience(java.util.List.of("https://api.example.com")))))
            .andExpect(status().isOk());
    }
}
