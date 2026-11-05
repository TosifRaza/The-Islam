import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Minimal SQL runner used by scripts/stack-smoke-native.sh when the local
 * PostgreSQL binaries do not include a psql client (e.g. the stripped Zonky
 * embedded-postgres binaries used by the backend tests).
 *
 * Usage: java -cp <postgresql-jdbc.jar> scripts/JdbcRun.java <jdbc-url> <sql>
 * Prints the first column of each row, or "OK(n)" for updates.
 */
public class JdbcRun {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: JdbcRun <jdbc-url> <sql>");
            System.exit(2);
        }
        try (Connection c = DriverManager.getConnection(args[0], "ikp", "ikp");
             Statement s = c.createStatement()) {
            boolean hasResultSet = s.execute(args[1]);
            if (hasResultSet) {
                try (ResultSet r = s.getResultSet()) {
                    while (r.next()) {
                        System.out.println(r.getString(1));
                    }
                }
            } else {
                System.out.println("OK(" + s.getUpdateCount() + ")");
            }
        }
    }
}
