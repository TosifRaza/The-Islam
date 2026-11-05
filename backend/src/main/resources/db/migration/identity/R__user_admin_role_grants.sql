-- R — M2 admin role grants (repeatable; approved Permission Matrix, tracker
-- "Permission Matrix" sheet). Runs AFTER R__seed_roles_permissions (repeatable
-- order = description order: "user admin role grants" > "seed roles
-- permissions"), so roles/permissions rows exist when these grants apply.
-- Idempotent upserts; re-asserted on every checksum change. NO established M1
-- grant is altered — this seed only ADDS the grants the approved matrix
-- requires for the admin CMS.

-- Users — view (matrix "Users / View user list / profiles":
--   Super Admin Full, Admin View, Support Manager View)
-- Users — suspend (matrix "Users / Suspend / unsuspend":
--   Super Admin Full, Admin Full, Support Manager Full)
-- Users — deletion workflow (matrix "Users / Deletion workflow":
--   Super Admin Full, Admin Review)
-- Audit logs (matrix "Audit logs / View admin/content audit trail":
--   Super Admin Full, Admin View)
-- Books — upload files (matrix "Books / Upload files (PDF/EPUB/covers)":
--   Super Admin Full, Admin Upload, Content Manager Upload)
-- Fatwas — submit draft (matrix "Fatwas / Submit draft Fatwa":
--   Super Admin Full, Admin Create, Scholar Reviewer Create.
--   NOTE: API spec §3.17 also lists Content Manager; the approved matrix does
--   not. The stricter matrix wins — flagged to the owner in the M2 API change
--   summary as a documented discrepancy, not silently widened.)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE (r.code IN ('super_admin','admin','support_manager') AND p.code = 'users.view')
   OR (r.code IN ('super_admin','admin','support_manager') AND p.code = 'users.suspend')
   OR (r.code IN ('super_admin','admin')                   AND p.code = 'users.delete')
   OR (r.code IN ('super_admin','admin')                   AND p.code = 'audit.view')
   OR (r.code IN ('super_admin','admin','content_manager') AND p.code = 'files.upload')
   OR (r.code IN ('super_admin','admin','scholar_reviewer') AND p.code = 'fatwa.create')
ON CONFLICT DO NOTHING;

-- Subjects — matrix "Subjects / Create/edit/reorder/publish subjects":
--   Super Admin Full, Admin Edit, Content Manager Full, Academy Manager Full.
-- academy.manage already means "Manage subjects, courses, lessons and media"
-- (M1 seed); M1 granted it only to academy_manager. The matrix extends the
-- SAME capability to Super Admin, Admin and Content Manager — grants added,
-- M1 grant untouched.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('super_admin','admin','content_manager') AND p.code = 'academy.manage'
ON CONFLICT DO NOTHING;

-- Guard: Fatwa separation of duties must hold after every grant re-application.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN permissions p ON p.id = rp.permission_id
        WHERE p.code IN ('fatwa.publish','fatwa.review.approve')
        GROUP BY rp.role_id
        HAVING COUNT(DISTINCT p.code) = 2
    ) THEN
        RAISE EXCEPTION 'M2 admin role grants violated Fatwa separation of duties';
    END IF;
END $$;
