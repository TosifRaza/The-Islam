-- R — Role & permission seed (repeatable; docs/07-security-plan.md — 11 roles)
-- Fatwa separation of duties: fatwa.publish (state-gated; Super Admin/Admin)
-- and fatwa.review.approve (Scholar Reviewer, sole authority) are granted to
-- disjoint roles. Publish without a recorded Scholar Reviewer approval is
-- rejected server-side (WORKFLOW-001) — see docs/07-security-plan.md.

INSERT INTO roles (code, name) VALUES
    ('guest',            'Guest'),
    ('registered_user',  'Registered User'),
    ('super_admin',      'Super Admin'),
    ('admin',            'Admin'),
    ('content_manager',  'Content Manager'),
    ('quran_editor',     'Quran Editor'),
    ('hadith_editor',    'Hadith Editor'),
    ('research_editor',  'Research Editor'),
    ('scholar_reviewer', 'Scholar Reviewer'),
    ('academy_manager',  'Academy Manager'),
    ('support_manager',  'Support Manager')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO permissions (code, description) VALUES
    ('content.edit',           'Edit content in own editorial scope'),
    ('content.publish',        'Publish reviewed content (state-gated)'),
    ('fatwa.publish',          'Publish Fatwas from approved state only (state-gated)'),
    ('fatwa.review.approve',   'Approve/reject Fatwas in scholarly review (sole authority)'),
    ('users.manage',           'Manage user accounts and statuses'),
    ('roles.manage',           'Grant and revoke admin roles'),
    ('academy.manage',         'Manage subjects, courses, lessons and media'),
    ('support.view_users',     'View users for support purposes (masked PII)')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

-- Disjoint fatwa duties: publisher roles vs sole review authority
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE (r.code IN ('super_admin','admin')     AND p.code = 'fatwa.publish')
   OR (r.code = 'scholar_reviewer'           AND p.code = 'fatwa.review.approve')
   OR (r.code = 'super_admin'                AND p.code IN ('roles.manage','users.manage'))
   OR (r.code IN ('super_admin','admin','content_manager','quran_editor','hadith_editor','research_editor')
                                             AND p.code = 'content.edit')
   OR (r.code IN ('super_admin','admin','content_manager')
                                             AND p.code = 'content.publish')
   OR (r.code = 'academy_manager'            AND p.code = 'academy.manage')
   OR (r.code = 'support_manager'            AND p.code = 'support.view_users')
ON CONFLICT DO NOTHING;

-- Guard: the two Fatwa permissions must never share a role.
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
