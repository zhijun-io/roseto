package io.zhijun.roseto.infrastructure;

import io.zhijun.roseto.RosetoContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedisContainerTest extends RosetoContainerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void redisHealthIsReportedAsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }


    @Test
    void writesAndReadsFromRedisContainer() {
        redis.opsForValue().set("p0:redis:test", "ok");

        assertEquals("ok", redis.opsForValue().get("p0:redis:test"));
    }
}
