package io.zhijun.roseto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RosetoApplicationTest extends RosetoContainerTest {

    @Test
    void contextLoads() {
    }
}
