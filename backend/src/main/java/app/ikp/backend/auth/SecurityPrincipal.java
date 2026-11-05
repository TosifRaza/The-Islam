package app.ikp.backend.auth;

import app.ikp.backend.user.Permission;
import app.ikp.backend.user.Role;
import app.ikp.backend.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request-scoped principal carrying FRESH authorities resolved from the
 * database on every request (roles as ROLE_* + flattened permission codes),
 * so backend RBAC never trusts stale token claims or client input.
 */
public record SecurityPrincipal(Long id, UUID publicId, String email, String status,
                                List<GrantedAuthority> authorities) {

    public static SecurityPrincipal from(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> perms = new ArrayList<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode().toUpperCase()));
            for (Permission p : role.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority(p.getCode()));
                perms.add(p.getCode());
            }
        }
        return new SecurityPrincipal(user.getId(), user.getPublicId(), user.getEmail(),
                user.getStatus(), List.copyOf(authorities));
    }

    public List<String> roleCodes() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5).toLowerCase())
                .toList();
    }

    public List<String> permissionCodes() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .sorted()
                .toList();
    }
}
