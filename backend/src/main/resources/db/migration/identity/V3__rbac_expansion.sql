-- V3 — RBAC expansion to the approved Permission Matrix (tracker sheet "Permission Matrix", 11 roles)
-- Forward-only migration (approved testing Option A — no undo).
--
-- Rule honoured (owner instruction): the M1 permission codes and their
-- established grants are preserved verbatim. This migration only ADDS the
-- permission codes and role grants the approved matrix requires for the
-- admin CMS and that M1 had not yet implemented. Every grant below cites
-- its matrix row.

-- 3.1 New permission codes -------------------------------------------------
INSERT INTO permissions (code, description) VALUES
    ('users.view',    'View user list and profiles (Support sees masked PII)'),
    ('users.suspend', 'Suspend and unsuspend user accounts (audited)'),
    ('users.delete',  'Start the account deletion workflow (Admin = review task, Super Admin = direct)'),
    ('audit.view',    'View the administrative audit trail'),
    ('files.upload',  'Upload and manage platform content files (books, covers, media)'),
    ('fatwa.create',  'Create and submit draft Fatwas for scholarly review')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- 3.2 Role grants: applied by the repeatable seed R__user_admin_role_grants.sql
--     (identity folder). RATIONALE (verified by test + debug): Flyway applies
--     versioned migrations BEFORE repeatable seeds, so at V3 time the roles
--     table is still empty (roles are seeded by R__seed_roles_permissions).
--     A versioned INSERT ... SELECT against empty roles would silently match
--     zero rows. The grants therefore live in a repeatable that runs after the
--     roles seed (repeatable order = description order: "user admin role
--     grants" > "seed roles permissions"), keeping every grant idempotent and
--     re-asserted on every checksum change. Every grant cites its approved
--     matrix row in that file.

-- 3.3 Users.last_login_at — supports "active users" metrics honestly
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at timestamptz;

-- 3.4 Guard: Fatwa separation of duties must survive every migration
--     (same invariant as the M1 repeatable seed, re-asserted after new grants).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN permissions p ON p.id = rp.permission_id
        WHERE p.code IN ('fatwa.publish','fatwa.review.approve')
        GROUP BY rp.role_id
        HAVING COUNT(DISTINCT p.code) = 2
    ) THEN
        RAISE EXCEPTION 'V3 violated Fatwa separation of duties';
    END IF;
END $$;
