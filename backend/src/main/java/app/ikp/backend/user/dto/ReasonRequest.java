package app.ikp.backend.user.dto;

import jakarta.validation.constraints.Size;

/** Suspension/deletion payload per approved spec §3.2.17 (reason 10–500, audited). */
public record ReasonRequest(@Size(min = 10, max = 500) String reason) {
}
