# Database migrations — policy (approved)

**Edition:** Flyway **Community** — testing **Option A** (approved in the blueprint,
`docs/08-testing-strategy.md`, `docs/13-decisions-and-assumptions.md`).

- **Forward-only** versioned migrations `V<n>__<desc>.sql`. Flyway Undo is a paid
  Teams feature and is **not assumed available**.
- **Never edit an applied migration.** Mistakes are corrected by a new forward
  migration (expand → migrate → contract pattern for zero-downtime changes).
  Rollback = restore from backup + replay, or a compensating forward migration.
- One folder per module (`db/migration/identity/`, `db/migration/content/`, …);
  versions are **global** across folders.
- Reference/seed data ships as **repeatable** migrations (`R__*.sql`) with
  idempotent `INSERT ... ON CONFLICT` and data-quality guards.
- No religious text content is ever seeded here — content enters exclusively
  through the controlled import pipeline after licensing verification
  (source register, gate G-3 / OD-05).
- Backup/restore rehearsal is part of release testing (Option A) — documented
  in the testing strategy; `docker-compose.yml` mounts a local volume for dev.
