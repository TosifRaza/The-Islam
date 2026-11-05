-- R — Reference licence seed (repeatable; approved reference data only — NOT
-- religious content and not an import). Development reference entries the
-- admin CMS needs for the licence selector; replaceable via admin/settings
-- later phases. Content imports remain gated at OD-05/G-3 and would carry
-- their own licence rows at import time.
INSERT INTO licences (code, title, spdx_id, attribution_required, commercial_use_allowed,
                      modification_allowed, source_url, notes) VALUES
    ('dev_public_domain',   'Public Domain (development reference)',              'LicenseRef-Public-Domain', false, true,  true,  NULL,
     'Reference row for works already in the public domain. Verify per-edition provenance before any real import (SR-05/SR-12).'),
    ('dev_cc_by_40',        'CC Attribution 4.0 (development reference)',         'CC-BY-4.0',                true,  true,  true,  'https://creativecommons.org/licenses/by/4.0/',
     'Reference row for CC BY 4.0 licensed material; attribution required.'),
    ('dev_all_rights',      'All Rights Reserved — Platform Licensed (reference)', NULL,                      true,  false, false, NULL,
     'Reference row for platform-licensed material used under written permission; download flags stay licence-driven.')
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title, spdx_id = EXCLUDED.spdx_id,
    attribution_required = EXCLUDED.attribution_required,
    commercial_use_allowed = EXCLUDED.commercial_use_allowed,
    modification_allowed = EXCLUDED.modification_allowed,
    source_url = EXCLUDED.source_url, notes = EXCLUDED.notes;
