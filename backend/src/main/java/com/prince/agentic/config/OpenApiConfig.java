package com.prince.agentic.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger metadata for the API.
 *
 * <p>Only describes what actually exists. As real endpoints are added in later
 * milestones, they are documented via springdoc annotations on their controllers —
 * this class supplies the top-level document metadata, not endpoint definitions.
 */
@Configuration
public class OpenApiConfig {

    private final String applicationName;
    private final String applicationVersion;

    public OpenApiConfig(
            @Value("${spring.application.name}") String applicationName,
            @Value("${app.version}") String applicationVersion) {
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
    }

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI agenticOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName)
                        .version(applicationVersion)
                        .description("""
                                Backend API for the Agentic AI Task Orchestrator.

                                Public: registration, login, health/info, and the API docs.
                                All other endpoints require a bearer JWT — obtain one via
                                POST /api/v1/auth/login, then click Authorize. Domain and
                                agent APIs are PLANNED — see docs/ROADMAP.md and docs/API.md.""")
                        .contact(new Contact().name("Prince Ramteke"))
                        .license(new License().name("TBD")))
                // Declare the bearer scheme and apply it globally; public endpoints opt out
                // via @SecurityRequirements on their controller.
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the JWT from /api/v1/auth/login (without the 'Bearer ' prefix).")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
