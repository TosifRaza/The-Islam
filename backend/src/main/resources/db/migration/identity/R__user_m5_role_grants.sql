-- R — M5 role grants (repeatable; docs/07-security-plan.md matrix extended).
-- Adds the two M5 admin capabilities that have no existing code:
--   assessment.manage — quizzes, versioned questions, flashcard decks (admin)
--   scholars.manage   — scholar directory CRUD and credential verification
-- academy.manage (courses/lessons) already exists since M2's RBAC expansion;
-- research articles reuse content.edit (research_editor holds it).
-- The Fatwa separation-of-duties guard is re-asserted unchanged below:
-- fatwa.publish and fatwa.review.approve must never share a role (OD-08).

INSERT INTO permissions (code, description) VALUES
    ('assessment.manage', 'Manage quizzes, versioned question bank and admin flashcard decks'),
    ('scholars.manage',   'Manage the scholar directory and record credential verification')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE (r.code IN ('super_admin','admin','academy_manager') AND p.code = 'assessment.manage')
   OR (r.code IN ('super_admin','admin','scholar_reviewer') AND p.code = 'scholars.manage')
ON CONFLICT DO NOTHING;

-- Guard (re-asserted from R__seed_roles_permissions.sql — unchanged semantics):
-- the two Fatwa permissions must never share a role (OD-08).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN permissions p ON p.id = rp.permission_id
        JOIN roles r ON r.id = rp.role_id
        WHERE p.code IN ('fatwa.publish','fatwa.review.approve')
        GROUP BY r.id
        HAVING COUNT(DISTINCT p.code) = 2
    ) THEN
        RAISE EXCEPTION 'Fatwa separation-of-duties violated: one role holds both publish and approve';
    END IF;
END $$;
