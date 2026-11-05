package app.ikp.backend.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * ONE real PostgreSQL (Zonky embedded binaries — no Docker needed) shared by
 * every M2 integration test class, so the Spring context boots once and all
 * suites run against a single migrated database.
 */
public final class SharedPostgres {

    private static EmbeddedPostgres instance;

    private SharedPostgres() {
    }

    public static synchronized DataSource get() {
        if (instance == null) {
            try {
                instance = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new IllegalStateException("embedded postgres failed to start", e);
            }
        }
        return instance.getPostgresDatabase();
    }
}
