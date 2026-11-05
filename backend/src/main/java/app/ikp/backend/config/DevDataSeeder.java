package app.ikp.backend.config;

import app.ikp.backend.user.Role;
import app.ikp.backend.user.RoleRepository;
import app.ikp.backend.user.User;
import app.ikp.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

/**
 * DEVELOPMENT-ONLY seed of labelled synthetic accounts. Runs exclusively when
 * IKP_SEED_DEV_DATA=true (set in docker-compose and local dev, never in
 * production) and is fully idempotent. These are clearly-labelled development
 * credentials — NOT production credentials (no production credentials are ever
 * created in the repository; production users arrive via the approved auth
 * flow). Every password below is synthetic and documented in README.
 */
@Configuration
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** Synthetic dev accounts: email → (password, roles…). Documented in README §5. */
    private static final List<Map.Entry<String, String[]>> DEV_ACCOUNTS = List.of(
            Map.entry("dev-superadmin@ikp.test", new String[]{"Dev#Super-1234", "super_admin"}),
            Map.entry("dev-admin@ikp.test", new String[]{"Dev#Admin-1234", "admin"}),
            Map.entry("dev-content@ikp.test", new String[]{"Dev#Content-1234", "content_manager"}),
            Map.entry("dev-scholar@ikp.test", new String[]{"Dev#Scholar-1234", "scholar_reviewer"}),
            Map.entry("dev-support@ikp.test", new String[]{"Dev#Support-1234", "support_manager"}),
            Map.entry("dev-academy@ikp.test", new String[]{"Dev#Academy-1234", "academy_manager"}),
            Map.entry("dev-reader@ikp.test", new String[]{"Dev#Reader-1234", "registered_user"}),
            Map.entry("dev-reader2@ikp.test", new String[]{"Dev#Reader-5678", "registered_user"}));

    @Bean
    ApplicationRunner devSeeder(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                                @Value("${IKP_SEED_DEV_DATA:false}") boolean enabled) {
        return args -> {
            if (!enabled) {
                return;
            }
            int created = 0;
            for (Map.Entry<String, String[]> entry : DEV_ACCOUNTS) {
                String email = entry.getKey();
                if (users.findByEmailIgnoreCase(email).isPresent()) {
                    continue;
                }
                User u = new User();
                u.setEmail(email);
                u.setPasswordHash(encoder.encode(entry.getValue()[0]));
                // value[0] is the password — only the remaining entries are role codes
                for (int i = 1; i < entry.getValue().length; i++) {
                    Role role = roles.findByCode(entry.getValue()[i]).orElseThrow();
                    u.getRoles().add(role);
                }
                users.save(u);
                created++;
            }
            if (created > 0) {
                log.info("DEV SEED: created {} labelled development accounts (IKP_SEED_DEV_DATA=true) — synthetic credentials only", created);
            }
        };
    }
}
