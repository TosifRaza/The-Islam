-- R — M4 licence rows (repeatable seed; reference data only, not content).
-- Real rows for the two Verified sources imported in M4 + clearly-labelled
-- rows for development fixtures and unresolved-rights content. Content
-- imports carry licence_id from these rows so licensing controls can verify
-- every published item (LIC-001 checks).

INSERT INTO licences (code, title, spdx_id, attribution_required, commercial_use_allowed,
                      modification_allowed, source_url, notes) VALUES
    ('cc_by_30_tanzil', 'Creative Commons Attribution 3.0 (Tanzil Quran Text)', 'CC-BY-3.0',
     true, true, false, 'https://tanzil.net/docs/text_license',
     'SR-01 Uthmani v1.1: verbatim copies only — changing the text is not allowed. '
     || 'Credit "Tanzil Project" with link to tanzil.net; keep the copyright notice.'),
    ('unlicense_hadith', 'The Unlicense (fawazahmed0/hadith-api v1)', 'Unlicense',
     false, true, true, 'https://github.com/fawazahmed0/hadith-api/blob/1/LICENSE',
     'SR-08 public-domain dedication. Own scholarly QA of numbering + grading '
     || 'provenance (workflow C-13) remains required before production release.'),
    ('dev_fixture_unresolved', 'Development fixture — source rights unresolved', NULL,
     true, false, false, NULL,
     'Labelled development fixtures standing in for sources whose licence review is '
     || 'incomplete (SR-04/05/06/07/11/12/16/18). is_fixture=true; publish_state stays '
     || 'draft/disabled until rights are cleared; never served as real content.'),
    ('all_rights_unverified', 'All rights reserved — redistribution unverified', NULL,
     true, false, false, NULL,
     'Placeholder row for sources (e.g. recitation audio SR-13/14/15) whose '
     || 'redistribution terms are unverified. No import permitted while unresolved.')
ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title, spdx_id = EXCLUDED.spdx_id,
    attribution_required = EXCLUDED.attribution_required,
    commercial_use_allowed = EXCLUDED.commercial_use_allowed,
    modification_allowed = EXCLUDED.modification_allowed,
    source_url = EXCLUDED.source_url, notes = EXCLUDED.notes;

-- SR-19 verified reference data: hijri conventions
INSERT INTO hijri_conventions (code, name, default_offset_days, is_default) VALUES
    ('umm_al_qura', 'Umm al-Qura (civil, tabular)', 0, true),
    ('tabular_islamic', 'Tabular Islamic calendar (arithmetic)', 0, false)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, default_offset_days = EXCLUDED.default_offset_days,
    is_default = EXCLUDED.is_default;

-- SR-19 verified reference data: widely-observed Islamic events (basis = calculated;
-- never presented as confirmed moon-sighting dates).
INSERT INTO islamic_events (code, name_en, hijri_month, hijri_day, basis, is_major, publish_state) VALUES
    ('islamic_new_year',   'Islamic New Year (1 Muharram)',          1,  1, 'calculated', true,  'published'),
    ('ashura',             'Day of Ashura (10 Muharram)',            1, 10, 'calculated', true,  'published'),
    ('mawlid_al_nabi',     'Mawlid al-Nabi (12 Rabi'' al-Awwal)',    3, 12, 'calculated', true,  'published'),
    ('isra_miraj',         'Isra and Mi''raj (27 Rajab)',            7, 27, 'calculated', false, 'published'),
    ('laylat_al_qadr',     'Laylat al-Qadr (observed in the last 10 nights of Ramadan)', 9, 27, 'calculated', true, 'published'),
    ('eid_al_fitr',        'Eid al-Fitr (1 Shawwal)',               10,  1, 'calculated', true,  'published'),
    ('arafah',             'Day of Arafah (9 Dhul Hijjah)',         12,  9, 'calculated', true,  'published'),
    ('eid_al_adha',        'Eid al-Adha (10 Dhul Hijjah)',          12, 10, 'calculated', true,  'published')
ON CONFLICT (code) DO UPDATE SET name_en = EXCLUDED.name_en, hijri_month = EXCLUDED.hijri_month,
    hijri_day = EXCLUDED.hijri_day, basis = EXCLUDED.basis, is_major = EXCLUDED.is_major;

-- Prayer calculation methods (SR-17 verified reference data; Adhan library params).
CREATE TABLE IF NOT EXISTS prayer_calculation_methods (
    code            VARCHAR(40) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    fajr_angle      NUMERIC(5,2) NOT NULL,
    isha_angle      NUMERIC(5,2),
    isha_interval   INT,
    madhab_default  VARCHAR(10) NOT NULL DEFAULT 'shafi' CHECK (madhab_default IN ('shafi','hanafi'))
);
INSERT INTO prayer_calculation_methods (code, name, fajr_angle, isha_angle, madhab_default) VALUES
    ('muslim_world_league',  'Muslim World League',        18.0, 17.0, 'shafi'),
    ('egyptian',             'Egyptian General Authority', 19.5, 17.5, 'shafi'),
    ('umm_al_qura',          'Umm al-Qura, Makkah',        18.5, NULL, 'shafi'),
    ('karachi',              'University of Islamic Sciences, Karachi', 18.0, 18.0, 'hanafi'),
    ('dubai',                'Dubai (UAE)',                18.2, 18.2, 'shafi'),
    ('north_america',        'ISNA (North America)',       15.0, 15.0, 'shafi'),
    ('kuwait',               'Kuwait',                     18.0, 17.5, 'shafi'),
    ('qatar',                'Qatar',                      18.0, NULL, 'shafi'),
    ('moonsighting',         'Moonsighting Committee',     18.0, 18.0, 'shafi')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, fajr_angle = EXCLUDED.fajr_angle,
    isha_angle = EXCLUDED.isha_angle, isha_interval = EXCLUDED.isha_interval,
    madhab_default = EXCLUDED.madhab_default;
