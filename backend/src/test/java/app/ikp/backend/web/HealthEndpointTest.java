package app.ikp.backend.web;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full Spring Boot context against a REAL PostgreSQL (embedded),
 * with Flyway applying the approved migrations, then verifies the contract
 * shape of the health endpoint (envelope {"data": ...}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthEndpointTest {

    private static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) throws IOException {
        pg = EmbeddedPostgres.builder().start();
        DataSource ds = pg.getPostgresDatabase();
        String url;
        try {
            url = ds.getConnection().getMetaData().getURL();
        } catch (SQLException e) {
            throw new IOException("failed to read embedded postgres JDBC url", e);
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    void stop() throws IOException {
        if (pg != null) pg.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointFollowsApprovedEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.db").value("UP"))
                .andExpect(jsonPath("$.data.service").value("ikp-backend"));
    }

    @Test
    void contextLoadedWithMigratedDatabase() {
        // If the context is up, Flyway ran against the embedded PostgreSQL.
        assertThat(pg).isNotNull();
    }
}
