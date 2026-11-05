package app.ikp.backend.user.dto;

import java.util.List;

/** Role assignment payload per approved spec §3.2.16 / §3.19.11 (Super Admin only). */
public record RoleChangeRequest(List<String> add, List<String> remove) {
}
