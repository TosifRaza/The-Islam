package app.ikp.backend.auth;

import app.ikp.backend.user.RoleRepository;
import app.ikp.backend.user.User;
import app.ikp.backend.user.UserRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Bearer-token filter: verifies the JWT signature/expiry, then loads the user
 * from PostgreSQL to attach FRESH role/permission authorities and to enforce
 * account status immediately (suspended users are rejected even inside the
 * 15-minute access-token window — AUTH-004). Supports both internal backend
 * JWTs and Supabase federated identity tokens.
 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public TokenAuthenticationFilter(JwtService jwtService, UserRepository userRepository, RoleRepository roleRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Long uid = jwtService.verify(token);
            User u = null;
            if (uid != null) {
                // Auth-specific fetch join: roles are resolved inside the query
                // because this filter runs outside any persistence session.
                Optional<User> user = userRepository.findByIdWithRoles(uid);
                if (user.isPresent()) {
                    u = user.get();
                }
            } else {
                // Fallback: verify and auto-resolve federated Supabase JWT
                u = verifyAndResolveSupabaseUser(token);
            }

            if (u != null) {
                if (!u.isActive()) {
                    response.setStatus(403);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("""
                            {"type":"https://docs.ikp.local/errors/AUTH-004","title":"Forbidden",\
                            "status":403,"code":"AUTH-004","detail":"account suspended or deleted",\
                            "instance":"%s"}""".formatted(request.getRequestURI()));
                    return;
                }
                SecurityPrincipal principal = SecurityPrincipal.from(u);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private User verifyAndResolveSupabaseUser(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String issuer = claims.getIssuer();
            if (issuer == null || !issuer.contains("supabase.co")) {
                return null;
            }
            if (claims.getExpirationTime() == null || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
                return null;
            }
            String email = claims.getStringClaim("email");
            String sub = claims.getSubject();
            if (email == null || email.isBlank()) {
                return null;
            }
            // 1. Try find existing user by email
            Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
            if (existing.isPresent()) {
                return userRepository.findByIdWithRoles(existing.get().getId()).orElse(existing.get());
            }
            // 2. Auto-provision new user for Supabase identity
            User newUser = new User();
            newUser.setEmail(email.trim().toLowerCase());
            if (sub != null) {
                try {
                    newUser.setPublicId(UUID.fromString(sub));
                } catch (IllegalArgumentException ignored) {
                }
            }
            newUser.setPasswordHash("{noop}supabase-federated");
            roleRepository.findByCode("registered_user").ifPresent(newUser.getRoles()::add);
            User saved = userRepository.save(newUser);
            return userRepository.findByIdWithRoles(saved.getId()).orElse(saved);
        } catch (Exception e) {
            return null;
        }
    }
}
