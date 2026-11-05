package app.ikp.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Islamic Knowledge Platform — backend entry point.
 *
 * M1 development foundation: application boots, Flyway migrations (forward-only,
 * per-module folders) apply against PostgreSQL on startup, minimal contract-shaped
 * endpoints are exposed. Full module endpoints arrive with their approved phases.
 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
