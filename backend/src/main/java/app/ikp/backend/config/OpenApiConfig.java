package app.ikp.backend.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI generation per the approved plan (docs/05-api-specification.md §5):
 * one group per implemented API family; bearer security documented. The
 * machine spec is published at /v3/api-docs and swagger-ui at /swagger-ui
 * (non-production).
 */
@Configuration
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    public OpenAPI base() {
        return new OpenAPI().info(new Info()
                .title("Islamic Knowledge Platform — Admin API")
                .version("0.2.0-M2")
                .description("M2 Admin CMS Foundation. Envelope {data,meta}; errors RFC 7807 problem+json "
                        + "with stable codes (docs/05-api-specification.md §2). Fatwa publication is "
                        + "state-gated and non-bypassable."));
    }

    @Bean
    public GroupedOpenApi authGroup() {
        return GroupedOpenApi.builder().group("auth").pathsToMatch("/api/v1/auth/**").build();
    }

    @Bean
    public GroupedOpenApi usersGroup() {
        return GroupedOpenApi.builder().group("admin-users").pathsToMatch("/api/v1/admin/users/**").build();
    }

    @Bean
    public GroupedOpenApi subjectsGroup() {
        return GroupedOpenApi.builder().group("subjects")
                .pathsToMatch("/api/v1/subjects/**", "/api/v1/admin/subjects/**").build();
    }

    @Bean
    public GroupedOpenApi libraryGroup() {
        return GroupedOpenApi.builder().group("library")
                .pathsToMatch("/api/v1/library/**", "/api/v1/admin/books/**", "/api/v1/admin/authors/**").build();
    }

    @Bean
    public GroupedOpenApi filesGroup() {
        return GroupedOpenApi.builder().group("files")
                .pathsToMatch("/api/v1/admin/files/**", "/api/v1/files/**").build();
    }

    @Bean
    public GroupedOpenApi workflowGroup() {
        return GroupedOpenApi.builder().group("workflow")
                .pathsToMatch("/api/v1/admin/books/*/reviews", "/api/v1/admin/review-queue",
                        "/api/v1/admin/{entity}/{entityId}/{action}").build();
    }

    @Bean
    public GroupedOpenApi fatwaGroup() {
        return GroupedOpenApi.builder().group("fatwas").pathsToMatch("/api/v1/admin/fatwas/**").build();
    }

    @Bean
    public GroupedOpenApi auditGroup() {
        return GroupedOpenApi.builder().group("audit").pathsToMatch("/api/v1/admin/audit-logs/**").build();
    }

    @Bean
    public GroupedOpenApi dashboardGroup() {
        return GroupedOpenApi.builder().group("dashboard").pathsToMatch("/api/v1/admin/dashboard").build();
    }
}
