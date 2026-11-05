-- R — Library category seed (repeatable; docs/04-database-design.md §3.2 seed rule)
-- The 15 client-reference positions with stable semantic slugs. sort_order is
-- the client-reference position for DISPLAY ORDER ONLY — never a join key.
-- Position 06 is unidentified in the client reference (OD-03): seeded as
-- hidden_unconfirmed and excluded from every public query/API/screen until
-- the client confirms it. Renaming it later is a data update, not a schema change.

INSERT INTO subjects (slug, name, name_ar, sort_order, status) VALUES
    ('cat_quran_tafsir',        'Al-Quran & Tafsir',                  'القرآن والتفسير',        1,  'active'),
    ('cat_hadith',              'Hadith Library',                     'مكتبة الحديث',           2,  'active'),
    ('cat_fiqh',                'Fiqh & Usul al-Fiqh',                'الفقه وأصوله',           3,  'active'),
    ('cat_aqeedah',             'Aqeedah',                            'العقيدة',                4,  'active'),
    ('cat_classical_books',     'Classical Islamic Books',            'الكتب التراثية',         5,  'active'),
    ('cat_unconfirmed_06',      '(Unidentified in client reference)', NULL,                     6,  'hidden_unconfirmed'),
    ('cat_arabic_academy',      'Arabic Language Academy',            'أكاديمية اللغة العربية', 7,  'active'),
    ('cat_learning_academy',    'Learning Academy',                   'أكاديمية التعلم',        8,  'active'),
    ('cat_history_seerah',      'Islamic History & Seerah',           'التاريخ والسيرة',        9,  'active'),
    ('cat_ai_research',         'AI Islamic Research Assistant',      'مساعد البحث الذكي',      10, 'active'),
    ('cat_knowledge_assessment','Knowledge Assessment',               'تقييم المعرفة',          11, 'active'),
    ('cat_duas',                'Duas & Daily Worship',               'الأدعية والأذكار',       12, 'active'),
    ('cat_prayer_utilities',    'Prayer & Islamic Utilities',         'أدوات الصلاة',           13, 'active'),
    ('cat_personal_library',    'Personal Knowledge Library',         'المكتبة الشخصية',        14, 'active'),
    ('cat_scholar_fatwa',       'Scholar Review & Fatwa Directory',   'دليل العلماء والفتاوى',  15, 'active')
ON CONFLICT (slug) DO UPDATE
    SET name = EXCLUDED.name,
        name_ar = EXCLUDED.name_ar,
        sort_order = EXCLUDED.sort_order,
        status = EXCLUDED.status;

-- Guard: exactly 15 positions; position 06 stays reserved & hidden.
DO $$
DECLARE n INT; hidden TEXT;
BEGIN
    SELECT COUNT(*), MIN(slug) INTO n, hidden FROM subjects WHERE status = 'hidden_unconfirmed';
    IF (SELECT COUNT(*) FROM subjects) <> 15 THEN
        RAISE EXCEPTION 'subjects seed must contain exactly 15 client-reference positions';
    END IF;
    IF hidden IS DISTINCT FROM 'cat_unconfirmed_06' THEN
        RAISE EXCEPTION 'only cat_unconfirmed_06 may be hidden_unconfirmed (found: %)', hidden;
    END IF;
END $$;
