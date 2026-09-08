package com.medicare.identity;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for every test that needs a real Postgres (Flyway migrations,
 * JPA, the full Spring context). One container is started once per JVM and
 * reused across all subclasses — deliberately not annotated with
 * @Testcontainers/@Container, which would stop and restart it per test
 * class; Ryuk (Testcontainers' own reaper) cleans it up when the JVM exits.
 *
 * Also satisfies the two env vars that have no application.yml default
 * (identity.oauth2.patient-service-client-secret,
 * identity.bootstrap.admin-email) so tests don't depend on a developer's
 * local .env being sourced.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18"))
                    .withDatabaseName("identity_db_test")
                    .withUsername("identity_test")
                    .withPassword("identity_test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("identity.oauth2.patient-service-client-secret", () -> "test-client-secret");
        registry.add("identity.bootstrap.admin-email", () -> "bootstrap-admin@test.local");
    }
}
