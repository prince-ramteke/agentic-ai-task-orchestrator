package com.prince.agentic.support;

import com.prince.agentic.security.RoleNames;
import com.prince.agentic.user.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies Flyway V1/V2 apply on real PostgreSQL: the seeded roles are present. */
class SchemaIT extends AbstractPostgresIntegrationTest {

    @Autowired private RoleRepository roleRepository;

    @Test
    void flywayMigrations_seedRoles_onRealPostgres() {
        assertThat(roleRepository.findByName(RoleNames.ROLE_USER)).isPresent();
        assertThat(roleRepository.findByName(RoleNames.ROLE_ADMIN)).isPresent();
    }
}
