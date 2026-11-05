package app.ikp.backend.config;

import app.ikp.backend.auth.TokenAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless security chain (approved auth design): public endpoints explicitly
 * whitelisted; everything else requires a valid access token; authorisation is
 * enforced per-endpoint via method security (@PreAuthorize permission codes).
 * Server-side authority only — no client-supplied role is ever trusted.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenFilter;

    public SecurityConfig(TokenAuthenticationFilter tokenFilter) {
        this.tokenFilter = tokenFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id per docs/07-security-plan.md §2 (salted, memory-hard).
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                          @Value("${app.cors.allowed-origins:http://localhost:5173}") String origins)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource(origins)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .xssProtection(x -> x.disable())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        // Public surface (approved spec §3.1 / §3.4.1 / §3.5.1 / ops health)
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/password/**").permitAll()
                        .requestMatchers("/api/v1/health", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/subjects", "/api/v1/subjects/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/library/books", "/api/v1/library/books/*").permitAll()
                        // M4 public knowledge content (guest browsing per approved IA)
                        .requestMatchers(HttpMethod.GET, "/api/v1/quran/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/hadith/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/dictionary/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/duas", "/api/v1/duas/categories",
                                "/api/v1/duas/search", "/api/v1/duas/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/calendar/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/prayer/methods").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home", "/api/v1/home/daily").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/search").permitAll()
                        // M5 public learning & research content (guest browsing per approved IA)
                        .requestMatchers(HttpMethod.GET, "/api/v1/academy/courses", "/api/v1/academy/courses/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/assessments/quizzes", "/api/v1/assessments/quizzes/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/research/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/scholars", "/api/v1/scholars/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/fatwas", "/api/v1/fatwas/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/flashcards/decks", "/api/v1/flashcards/decks/*").permitAll()
                        // Signed media access (token in query, validated in controller)
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/*").permitAll()
                        // OpenAPI docs (non-production)
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("application/problem+json");
                            res.getWriter().write("""
                                    {"type":"https://docs.ikp.local/errors/AUTH-002","title":"Unauthorized",\
                                    "status":401,"code":"AUTH-002","detail":"authentication required",\
                                    "instance":"%s"}""".formatted(req.getRequestURI()));
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType("application/problem+json");
                            res.getWriter().write("""
                                    {"type":"https://docs.ikp.local/errors/AUTHZ-001","title":"Forbidden",\
                                    "status":403,"code":"AUTHZ-001","detail":"insufficient role or permission",\
                                    "instance":"%s"}""".formatted(req.getRequestURI()));
                        }));
        return http.build();
    }

    private CorsConfigurationSource corsSource(String origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(origins.split(",")));
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
