package io.zhijun.roseto;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class RosetoContainerTest {
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("roseto")
            .withUsername("roseto")
            .withPassword("roseto");

    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
        POSTGRES.start();
    }
    @BeforeAll
    static void resetDatabase() throws java.sql.SQLException {
        try (var connection = java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var tables = connection.createStatement().executeQuery(
                 "select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'")) {
            var names = new java.util.ArrayList<String>();
            while (tables.next()) {
                names.add("\"public\".\"" + tables.getString(1).replace("\"", "\"\"") + "\"");
            }
            if (!names.isEmpty()) {
                try (var truncate = connection.createStatement()) {
                    truncate.execute("truncate table " + String.join(", ", names) + " cascade");
                }
            }
        }
    }
    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
