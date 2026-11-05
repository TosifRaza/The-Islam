# M2 API Change Summary

**Milestone:** M2 — Admin CMS Foundation · **Baseline:** M1 `m1-platform-foundation-v0.1.1`
All endpoints live under `/api/v1`. Envelope: `{"data": …, "meta": …}`. Errors: `application/problem+json` with the approved stable codes (`AUTH-001/002/004/005`, `AUTHZ-001`, `VAL-001`, `NF-001`, `UPLOAD-001/002/003`, `QUOTA-001`, `LIC-001`, `WORKFLOW-001`, `RATE-001`, `SERVER-001`). OpenAPI groups mirror the families below (`/swagger-ui`).

## 1. Authentication (new family — approved baseline; OD-04 remains open)

| Method | URL | Auth | Notes |
|---|---|---|---|
| POST | `/auth/login` | public | Argon2id verify; 15-min HS256 access token; rotating refresh (30 d, hashed at rest); rate-limited (RATE-001) |
| POST | `/auth/refresh` | public | Rotation + reuse detection revokes the whole family (AUTH-005) |
| POST | `/auth/logout` | authenticated | Revokes presented session (or all when no body) |
| GET | `/auth/me` | authenticated | Principal + roles + flattened permissions (fresh from DB) |
| GET | `/auth/sessions` | authenticated | Active sessions/devices (§3.1.9) |
| DELETE | `/auth/sessions/{id}` | authenticated | Revoke own session; ownership enforced (AUTHZ-002) |

`/auth/password/**` stays whitelisted for the approved password-reset phase — **not implemented in M2** (documented remainder).

## 2. Admin dashboard (new)

| GET | `/admin/dashboard` | any admin role per matrix | Users/content/review metrics from live SQL + approval queue + recent audit activity; honest zeros; Quran/Hadith stats report `imported:false` (OD-05/G-3) |

## 3. Admin users (new)

| Method | URL | Permission | Notes |
|---|---|---|---|
| GET | `/admin/users` | `users.view`/`users.manage` | Search/status filter/pagination; Support Manager sees masked emails |
| GET | `/admin/users/{id}` | `users.view`/`users.manage` | Full profile + deletion-request history |
| PATCH | `/admin/users/{id}` | `roles.manage` | Role add/remove; last-active-Super-Admin guard; self-demote refused |
| POST | `/admin/users/{id}/suspend` · `/unsuspend` | `users.suspend` | Reason required; revokes sessions; audited; self-suspend refused |
| POST | `/admin/users/{id}/delete` | `users.delete` | Super Admin schedules (+7 d purge), Admin files for review; audited |
| GET | `/admin/users/{id}/activity` | `users.view`/`users.manage` | The user's own audit events |

## 4. Subjects (public extended + new admin family)

| Method | URL | Auth | Notes |
|---|---|---|---|
| GET | `/subjects`, `/subjects/{slug}` | public | Published categories in display order; **reserved position 06 never returned (404 indistinguishable from absent)** |
| GET | `/admin/subjects` | `academy.manage` | All 15 incl. hidden/reserved |
| PATCH | `/admin/subjects/{slug}` | `academy.manage` | Name/description/image/status; category 06 rename/visibility refused (WORKFLOW-001) |
| POST | `/admin/subjects/reorder` | `academy.manage` | Complete set 1..15 required; `sort_order` UNIQUE is DEFERRABLE (V4) to permit permutations |
| POST | `/admin/subjects/{slug}/publish` · `/unpublish` | `academy.manage` | Category 06 always refused |
| POST/DELETE | `/admin/subjects` | `academy.manage` | **Refused by design** — the fixed 15-position set only changes via owner decision + migration |

## 5. Authors & books (new)

| Method | URL | Permission | Notes |
|---|---|---|---|
| POST/GET/PATCH | `/admin/authors` | `content.edit` | Author registry |
| POST | `/admin/books` | `content.edit` | Creates **draft**; never public on save |
| GET | `/admin/books` | `content.edit` | Filters: q/subject/language/status/format + pagination |
| GET/PATCH | `/admin/books/{id}` | `content.edit` | Detail (metadata + files); edit draft-only (WORKFLOW-001 otherwise) |
| POST | `/admin/books/{id}/files` | `files.upload` | Attaches **scan-clean** media only (UPLOAD-002); purpose↔format matched |
| GET | `/library/books` · `/library/books/{id}` | public | **Published** books only |
| POST | `/library/books/{id}/download-token` | authenticated | LIC-001 when downloads not permitted; short-lived signed URL |

## 6. Files & media (new)

| Method | URL | Permission | Notes |
|---|---|---|---|
| POST | `/admin/files` | `files.upload` | Multipart; extension+magic+size+quota gates; quarantine → scan → clean promotes; verdict + scanner recorded |
| GET | `/admin/files` · `/admin/files/{id}` | `files.upload` | Inventory + filters; scan-state detail |
| POST | `/admin/files/{id}/download-token` | `files.upload` | Refused for non-clean (UPLOAD-002); ≤15-min signed URL |
| GET | `/files/{mediaId}?token&exp` | signed URL | HMAC-validated; quarantined never served (AUTH-002/UPLOAD-002) |

## 7. Review workflow + Fatwas (new)

| Method | URL | Auth | Notes |
|---|---|---|---|
| POST | `/admin/books/{id}/{action}` | service-enforced | `submit-review · approve-editorial · approve · reject · publish · unpublish · archive`; WORKFLOW-001 on invalid state/actor; denials audited (own transaction) |
| GET | `/admin/review-queue?state=` | admin/content manager/scholar | editorial · scholarly · ready_to_publish · rejected · archived · fatwas · all |
| GET | `/admin/books/{id}/reviews` | `content.edit` | Review records (reviewer identity, decision, notes) |
| POST/GET/PATCH | `/admin/fatwas` | `fatwa.create` / view per matrix | Draft creation; state queue; edit resets approval→draft |
| POST | `/admin/fatwas/{id}/submit-review` | `fatwa.create` | draft → in_review |
| POST | `/admin/fatwas/{id}/assign-reviewer` | Super Admin/Admin | Assignee must hold Scholar Reviewer (refused + audited otherwise) |
| POST | `/admin/fatwas/{id}/decision` | **Scholar Reviewer only** | approve/reject + notes; 403 AUTHZ for anyone else |
| POST | `/admin/fatwas/{id}/publish` | `fatwa.publish` | **Triple gate:** state=approved AND scholar approval record by a role holder AND publisher ≠ reviewer; otherwise WORKFLOW-001 + audited |
| POST | `/admin/fatwas/{id}/unpublish` | `fatwa.publish` | published → archived |
| GET | `/admin/fatwas/{id}/audit` | Super Admin/Admin | Per-fatwa review + publication trail |

The generic family **refuses `entity=fatwas`** (NF-001) so no weaker publication path exists.

## 8. Audit log (new)

| GET | `/admin/audit-logs` | `audit.view` | Filters: actorId/entity/action/from/to + pagination. Read-only — no create/update/delete path exists at any layer |

## Behavioural notes for API consumers

1. **401 masking guard:** the token filter resolves roles inside its query (no session) — a malformed token yields the standard `AUTH-002` problem+json; suspended users get `AUTH-004` immediately even inside the token window.
2. **Permission ≠ authorisation to publish:** file scan state and workflow state are re-validated server-side at publish time.
3. No database entity is ever serialised directly; only the approved view DTOs are exposed. Internal BIGINT ids are never exposed — public UUIDs (or stable CAT-* slugs for subjects) only.
