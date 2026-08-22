package com.prince.agentic.support;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for real-infrastructure integration tests, using the Testcontainers <em>singleton-container</em>
 * pattern: ONE {@code postgres:16-alpine} and ONE {@code redis:7-alpine} container are started for the
 * whole test run and shared by every {@code *IT} class (Ryuk reaps them at JVM exit). Stable, shared
 * endpoints are essential so Spring's cached test context stays valid across IT classes. A per-class
 * {@code @Container} (started and stopped around each class) leaves the cached context pointing at a
 * dead port on the second IT class → {@code HikariPool ... Connection is not available} timeouts.
 *
 * <p>The {@code it} profile mirrors production, which runs both stores: PostgreSQL for durable domain
 * data and Redis for ephemeral agent conversation memory (M7). With Redis present, Spring Boot's
 * default Redis health indicator reports UP, so {@code /actuator/health} is genuinely healthy here.
 *
 * <p>Docker is detected once. When it is unavailable the containers are not started and every IT is
 * skipped via a JUnit assumption (the same effect as {@code @Testcontainers(disabledWithoutDocker
 * = true)}), so {@code ./mvnw verify} stays green without Docker while running for real in
 * Docker-capable CI (ADR-0008).
 */
@SpringBootTest
@ActiveProfiles("it")
public abstract class AbstractPostgresIntegrationTest {

    private static final boolean DOCKER_AVAILABLE = detectDocker();
    private static final int REDIS_PORT = 6379;

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    static {
        if (DOCKER_AVAILABLE) {
            POSTGRES.start();   // started once; shared by all IT classes; reaped by Ryuk at JVM exit
            REDIS.start();
        }
    }

    private static boolean detectDocker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @BeforeAll
    static void requireDocker() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE,
                "Docker is not available — skipping PostgreSQL integration tests");
    }

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }
}
