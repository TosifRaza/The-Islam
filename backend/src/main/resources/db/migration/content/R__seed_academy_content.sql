-- R — Seed curated Academy courses, chapters, lessons, flashcards, and quizzes.
-- Repeatable Flyway migration: automatically applied by Spring Boot backend on boot.
-- Uses ON CONFLICT and deterministic UUIDs so it is safely idempotent.

-- 1. COURSES -----------------------------------------------------------------

-- Course 1: Foundations of Islamic Aqeedah
INSERT INTO academy_courses (
    id, public_id, subject_id, title, description, language, level,
    instructor_display_name, instructor_credentials_note, estimated_hours, status, published_at
) OVERRIDING SYSTEM VALUE VALUES (
    101, 'c0000001-0000-0000-0000-000000000001',
    (SELECT id FROM subjects WHERE slug = 'cat_aqeedah' LIMIT 1),
    'Foundations of Islamic Aqeedah',
    'A structured introduction to the six pillars of Iman, Tawheed, and core Islamic beliefs according to Ahlus Sunnah wal-Jama''ah.',
    'en', 'beginner',
    'Shaykh Abdullah Mansoor', 'Graduate of Islamic University of Madinah',
    8, 'published', now()
) ON CONFLICT (public_id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description,
    instructor_display_name = EXCLUDED.instructor_display_name, status = EXCLUDED.status;

-- Course 2: Quranic Arabic for Beginners
INSERT INTO academy_courses (
    id, public_id, subject_id, title, description, language, level,
    instructor_display_name, instructor_credentials_note, estimated_hours, status, published_at
) OVERRIDING SYSTEM VALUE VALUES (
    102, 'c0000002-0000-0000-0000-000000000002',
    (SELECT id FROM subjects WHERE slug = 'cat_arabic_academy' LIMIT 1),
    'Quranic Arabic: Level 1 Basics',
    'Learn essential Quranic vocabulary, basic grammar (Nahw), and morphological patterns to understand the Quran directly.',
    'en', 'beginner',
    'Ustadh Tariq Al-Hashimi', 'Arabic Linguist & Quran Educator',
    12, 'published', now()
) ON CONFLICT (public_id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description,
    instructor_display_name = EXCLUDED.instructor_display_name, status = EXCLUDED.status;

-- Course 3: Fiqh of Purification and Prayer (Taharah & Salah)
INSERT INTO academy_courses (
    id, public_id, subject_id, title, description, language, level,
    instructor_display_name, instructor_credentials_note, estimated_hours, status, published_at
) OVERRIDING SYSTEM VALUE VALUES (
    103, 'c0000003-0000-0000-0000-000000000003',
    (SELECT id FROM subjects WHERE slug = 'cat_fiqh' LIMIT 1),
    'Fiqh of Taharah & Salah',
    'Step-by-step practical guide to purification (Wudu, Ghusl, Tayammum) and the rulings, conditions, and pillars of daily Salah.',
    'en', 'beginner',
    'Mufti Bilal Khan', 'Senior Lecturer in Islamic Jurisprudence',
    10, 'published', now()
) ON CONFLICT (public_id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description,
    instructor_display_name = EXCLUDED.instructor_display_name, status = EXCLUDED.status;

-- Course 4: Seerah of Prophet Muhammad ﷺ
INSERT INTO academy_courses (
    id, public_id, subject_id, title, description, language, level,
    instructor_display_name, instructor_credentials_note, estimated_hours, status, published_at
) OVERRIDING SYSTEM VALUE VALUES (
    104, 'c0000004-0000-0000-0000-000000000004',
    (SELECT id FROM subjects WHERE slug = 'cat_history_seerah' LIMIT 1),
    'Seerah: Life of Prophet Muhammad ﷺ',
    'A comprehensive journey through the Makkan and Madinan eras of the Messenger of Allah ﷺ and lessons for contemporary life.',
    'en', 'intermediate',
    'Dr. Umar Farooq', 'Faculty of Islamic History',
    15, 'published', now()
) ON CONFLICT (public_id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description,
    instructor_display_name = EXCLUDED.instructor_display_name, status = EXCLUDED.status;

-- Course 5: Tajweed Rules & Recitation
INSERT INTO academy_courses (
    id, public_id, subject_id, title, description, language, level,
    instructor_display_name, instructor_credentials_note, estimated_hours, status, published_at
) OVERRIDING SYSTEM VALUE VALUES (
    105, 'c0000005-0000-0000-0000-000000000005',
    (SELECT id FROM subjects WHERE slug = 'cat_quran_tafsir' LIMIT 1),
    'Tajweed Rules & Recitation Excellence',
    'Master the rules of Noon Sakinah, Meem Sakinah, Madd, and articulation points (Makharij) with practical audio examples.',
    'en', 'beginner',
    'Qari Ibrahim As-Sudais', 'Ijazah holder in Hafs & Shu''bah',
    6, 'published', now()
) ON CONFLICT (public_id) DO UPDATE SET
    title = EXCLUDED.title, description = EXCLUDED.description,
    instructor_display_name = EXCLUDED.instructor_display_name, status = EXCLUDED.status;


-- 2. CHAPTERS ---------------------------------------------------------------

INSERT INTO academy_chapters (id, public_id, course_id, title, description, sort_order)
OVERRIDING SYSTEM VALUE VALUES
    (201, 'd0000001-0000-0000-0000-000000000001', 101, 'Chapter 1: The Three Categories of Tawheed', 'Understanding Rububiyyah, Uluhiyyah, and Asma wa Sifat', 1),
    (202, 'd0000002-0000-0000-0000-000000000002', 101, 'Chapter 2: The Six Pillars of Iman', 'Belief in Allah, Angels, Books, Messengers, Last Day, and Qadar', 2),
    (203, 'd0000003-0000-0000-0000-000000000003', 102, 'Chapter 1: Alphabet & Vowels', 'Arabic phonetics, Harakat, Tanween and Sukoon', 1),
    (204, 'd0000004-0000-0000-0000-000000000004', 103, 'Chapter 1: The Conditions of Wudu', 'Obligations, Sunnan, and things that invalidate purification', 1),
    (205, 'd0000005-0000-0000-0000-000000000005', 104, 'Chapter 1: Arabia Before Islam', 'The religious, moral and social state of the Arabian Peninsula', 1),
    (206, 'd0000006-0000-0000-0000-000000000006', 105, 'Chapter 1: Makharij (Points of Articulation)', 'The five major throat, tongue, and lip articulation regions', 1)
ON CONFLICT (public_id) DO UPDATE SET title = EXCLUDED.title, sort_order = EXCLUDED.sort_order;


-- 3. LESSONS -----------------------------------------------------------------

INSERT INTO academy_lessons (
    id, public_id, chapter_id, title, lesson_type, body_text, duration_minutes, sort_order
) OVERRIDING SYSTEM VALUE VALUES
    (301, 'e0000001-0000-0000-0000-000000000001', 201,
     '1.1 Meaning and Proofs of Tawheed ar-Rububiyyah',
     'text',
     'Tawheed ar-Rububiyyah is the affirmation of Allah’s absolute Oneness in His actions: Creation (Khalq), Sovereignty (Mulk), and Direction of all affairs (Tadbeer). Allah says: "Unquestionably, His is the creation and the command; blessed is Allah, Lord of the worlds." (Surah Al-A''raf 7:54). Even the pre-Islamic polytheists acknowledged this category, yet it alone does not make a person Muslim without affirming Tawheed al-Uluhiyyah.',
     15, 1),
    (302, 'e0000002-0000-0000-0000-000000000002', 201,
     '1.2 Tawheed al-Uluhiyyah: The Purpose of Creation',
     'text',
     'Tawheed al-Uluhiyyah is the singling out of Allah alone in all acts of worship — such as prayer, fasting, supplication (Dua), sacrifice, hope, and reliance. Allah says: "And I did not create the jinn and mankind except to worship Me." (Surah Adh-Dhariyat 51:56). This was the message that every Prophet and Messenger was sent to proclaim.',
     20, 2),
    (303, 'e0000003-0000-0000-0000-000000000003', 202,
     '2.1 Belief in the Angels and Revealed Books',
     'text',
     'Angels are noble creations of light who obey Allah without disobedience. The books include the Torah given to Musa, the Zabur to Dawud, the Injeel to Isa, and the Quran revealed to Muhammad ﷺ as the final, protected testament for all humanity.',
     18, 1),
    (304, 'e0000004-0000-0000-0000-000000000004', 203,
     '1.1 Short & Long Vowels (Harakat & Madd)',
     'text',
     'In Arabic, the three short vowels are Fathah (a), Kasrah (i), and Dammah (u). When lengthened by Alif, Yaa, or Waw, they form the long vowels. Recognizing them is the foundation of Quranic recitation.',
     25, 1),
    (305, 'e0000005-0000-0000-0000-000000000005', 204,
     '1.1 The Fard (Obligatory) Acts of Wudu',
     'text',
     'Based on Surah Al-Ma''idah (5:6), the four obligatory acts of Wudu are: 1. Washing the face from hairline to chin and ear to ear. 2. Washing the hands and forearms including elbows. 3. Wiping over the head. 4. Washing the feet including the ankles. Intention (Niyyah) and sequence (Tarteeb) are conditions in majority schools.',
     15, 1)
ON CONFLICT (public_id) DO UPDATE SET title = EXCLUDED.title, body_text = EXCLUDED.body_text;


-- 4. FLASHCARD DECKS & CARDS -------------------------------------------------

INSERT INTO flashcard_decks (id, public_id, subject_id, title, description, owner_type, publish_state)
OVERRIDING SYSTEM VALUE VALUES
    (401, 'f0000001-0000-0000-0000-000000000001',
     (SELECT id FROM subjects WHERE slug = 'cat_aqeedah' LIMIT 1),
     '99 Names of Allah (Asma ul-Husna)',
     'Memorize the beautiful names of Allah with their Arabic script and English meanings.',
     'admin', 'published'),
    (402, 'f0000002-0000-0000-0000-000000000002',
     (SELECT id FROM subjects WHERE slug = 'cat_duas' LIMIT 1),
     'Essential Daily Duas',
     'Authentic supplications from Hisnul Muslim for morning, evening, sleep, and meals.',
     'admin', 'published'),
    (403, 'f0000003-0000-0000-0000-000000000003',
     (SELECT id FROM subjects WHERE slug = 'cat_hadith' LIMIT 1),
     'Foundational 40 Hadith of An-Nawawi',
     'Core Hadiths embodying the fundamental principles of Islamic faith and character.',
     'admin', 'published')
ON CONFLICT (public_id) DO UPDATE SET title = EXCLUDED.title, description = EXCLUDED.description;

-- Cards for 99 Names of Allah deck
INSERT INTO flashcards (id, deck_id, front_text, back_text, sort_order)
OVERRIDING SYSTEM VALUE VALUES
    (501, 401, 'Ar-Rahman (الرَّحْمَٰنُ)', 'The Entirely Merciful — possessing all-encompassing mercy for all creation in this world.', 1),
    (502, 401, 'Ar-Raheem (الرَّحِيمُ)', 'The Especially Merciful — bestowing specific mercy upon the believers on the Day of Judgment.', 2),
    (503, 401, 'Al-Malik (الْمَلِكُ)', 'The Sovereign Lord — the supreme ruler and absolute owner of the universe.', 3),
    (504, 401, 'Al-Quddus (الْقُدُّوسُ)', 'The Most Holy — pure from any imperfection, defect, or partner.', 4),
    (505, 401, 'As-Salam (السَّلَامُ)', 'The Source of Peace — free from every flaw, and the giver of safety and tranquility.', 5),
    (506, 401, 'Al-Mu''min (الْمُؤْمِنُ)', 'The Granter of Security — who confirms the truth of His promises and protects believers.', 6),
    (507, 401, 'Al-Muhaymin (الْمُهَيْمِنُ)', 'The Ever-Watching Protector — who oversees, protects, and witnesses all actions.', 7),
    (508, 401, 'Al-Aziz (الْعَزِيزُ)', 'The All-Mighty — the invincible whom nothing can overcome or overpower.', 8),
    (509, 401, 'Al-Jabbar (الْجَبَّارُ)', 'The Compeller — the Restorer of broken hearts and the supreme authority.', 9),
    (510, 401, 'Al-Mutakabbir (الْمُتَكَبِّرُ)', 'The Supreme — transcendent in greatness beyond all created things.', 10)
ON CONFLICT (id) DO UPDATE SET front_text = EXCLUDED.front_text, back_text = EXCLUDED.back_text;

-- Cards for Essential Daily Duas deck
INSERT INTO flashcards (id, deck_id, front_text, back_text, sort_order)
OVERRIDING SYSTEM VALUE VALUES
    (511, 402,
     'Dua upon waking up' || E'\n\n' || 'الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ',
     'All praise is for Allah, who gave us life after giving us death, and unto Him is the resurrection. (Sahih al-Bukhari)', 1),
    (512, 402,
     'Dua before sleeping' || E'\n\n' || 'بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا',
     'In Your name, O Allah, I die and I live. (Sahih al-Bukhari)', 2),
    (513, 402,
     'Sayyid al-Istighfar' || E'\n\n' || 'اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ خَلَقْتَنِي وَأَنَا عَبْدُكَ...',
     'The master supplication for seeking forgiveness from Allah for sins. (Sahih al-Bukhari)', 3)
ON CONFLICT (id) DO UPDATE SET front_text = EXCLUDED.front_text, back_text = EXCLUDED.back_text;


-- 5. QUIZZES & QUESTIONS -----------------------------------------------------

INSERT INTO assessment_quizzes (id, public_id, course_id, subject_id, title, description, pass_score_pct, status, published_at)
OVERRIDING SYSTEM VALUE VALUES
    (601, 'a0000001-0000-0000-0000-000000000001', 101,
     (SELECT id FROM subjects WHERE slug = 'cat_aqeedah' LIMIT 1),
     'Foundations of Aqeedah Quiz',
     'Test your understanding of the categories of Tawheed and the six pillars of Iman.',
     70, 'published', now()),
    (602, 'a0000002-0000-0000-0000-000000000002', 103,
     (SELECT id FROM subjects WHERE slug = 'cat_fiqh' LIMIT 1),
     'Fiqh of Taharah & Wudu Quiz',
     'Test your knowledge on the obligatory acts, sunnan, and invalidators of purification.',
     60, 'published', now())
ON CONFLICT (public_id) DO UPDATE SET title = EXCLUDED.title, status = EXCLUDED.status;

-- Quiz questions for Aqeedah Quiz
INSERT INTO assessment_questions (id, public_id, quiz_id, question_type, prompt, prompt_ar, explanation, points, review_state, sort_order)
OVERRIDING SYSTEM VALUE VALUES
    (701, 'b0000001-0000-0000-0000-000000000001', 601, 'mcq',
     'Which category of Tawheed signifies the belief that Allah alone is the Creator, Sustainer, and Sovereign of the universe?',
     'ما هو القسم الذي يقر بأن الله وحده هو الخالق الرازق المدبر؟',
     'Tawheed ar-Rububiyyah is the affirmation of Allah’s Oneness in His actions (Creation, Sustenance, Governance).',
     1, 'approved', 1),
    (702, 'b0000002-0000-0000-0000-000000000002', 601, 'mcq',
     'What was the primary call of all the Prophets sent by Allah?',
     'ما هي الدعوة الأساسية لجميع الأنبياء عليهم السلام؟',
     'Every Prophet proclaimed Tawheed al-Uluhiyyah: to single out Allah alone in all acts of worship.',
     1, 'approved', 2)
ON CONFLICT (public_id) DO UPDATE SET prompt = EXCLUDED.prompt, explanation = EXCLUDED.explanation;

-- Options for Question 1
INSERT INTO assessment_question_options (id, question_id, option_text, is_correct, sort_order)
OVERRIDING SYSTEM VALUE VALUES
    (801, 701, 'Tawheed ar-Rububiyyah', true, 1),
    (802, 701, 'Tawheed al-Uluhiyyah', false, 2),
    (803, 701, 'Tawheed al-Asma was-Sifat', false, 3),
    (804, 701, 'Tawheed al-Ittiba', false, 4)
ON CONFLICT (id) DO NOTHING;

-- Options for Question 2
INSERT INTO assessment_question_options (id, question_id, option_text, is_correct, sort_order)
OVERRIDING SYSTEM VALUE VALUES
    (805, 702, 'Tawheed al-Uluhiyyah (Worship Allah alone)', true, 1),
    (806, 702, 'Political leadership', false, 2),
    (807, 702, 'Building monuments', false, 3),
    (808, 702, 'Trade treaties', false, 4)
ON CONFLICT (id) DO NOTHING;
