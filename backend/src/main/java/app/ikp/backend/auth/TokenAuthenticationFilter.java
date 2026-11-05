package app.ikp.backend.auth;

import app.ikp.backend.user.User;
import app.ikp.backend.user.UserRepository;
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
import java.util.Optional;

/**
 * Bearer-token filter: verifies the JWT signature/expiry, then loads the user
 * from PostgreSQL to attach FRESH role/permission authorities and to enforce
 * account status immediately (suspended users are rejected even inside the
 * 15-minute access-token window — AUTH-004).
 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public TokenAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long uid = jwtService.verify(header.substring(7));
            if (uid != null) {
                // Auth-specific fetch join: roles are resolved inside the query
                // because this filter runs outside any persistence session.
                Optional<User> user = userRepository.findByIdWithRoles(uid);
                if (user.isPresent()) {
                    User u = user.get();
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
        }
        chain.doFilter(request, response);
    }
}
