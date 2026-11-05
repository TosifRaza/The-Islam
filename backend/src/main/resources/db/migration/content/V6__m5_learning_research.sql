-- V6 — M5 Learning & Research modules schema (academy, assessments,
-- flashcards, research, scholars).
-- Forward-only migration (approved testing Option A — no undo). V1..V5 are
-- never modified. No religious text is created here — courses, questions,
-- articles and scholar profiles arrive only through the admin CMS with the
-- approved editorial workflow; the platform never generates religious
-- content (no fabricated qualifications, citations or rulings).

-- 6.1 Academy — courses --------------------------------------------------------
-- State machine mirrors books (§3.19.5): draft → in_editorial_review →
-- approved → published → archived. Enforced in AcademyService, audited.
CREATE TABLE academy_courses (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID         NOT NULL UNIQUE,
    subject_id        BIGINT       NOT NULL REFERENCES subjects (id),
    title             VARCHAR(300) NOT NULL,
    description       TEXT,
    language          VARCHAR(8)   NOT NULL DEFAULT 'en',
    level             VARCHAR(20)  NOT NULL DEFAULT 'beginner'
                      CHECK (level IN ('beginner','intermediate','advanced')),
    -- Display-only instructor fields: the platform NEVER generates
    -- qualifications; admins record only verified text and are accountable
    -- via the audit log. Certificates are out of scope (OD-11: not approved).
    instructor_display_name      VARCHAR(200),
    instructor_credentials_note  VARCHAR(500),
    estimated_hours   INT CHECK (estimated_hours BETWEEN 0 AND 1000),
    status            VARCHAR(30)  NOT NULL DEFAULT 'draft'
                      CHECK (status IN ('draft','in_editorial_review','approved','published','archived')),
    created_by        BIGINT REFERENCES users (id),
    published_at      timestamptz,
    archived_at       timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_courses_subject ON academy_courses (subject_id, status);
CREATE INDEX idx_courses_fts ON academy_courses
    USING gin (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,'')));

CREATE TABLE academy_chapters (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL UNIQUE,
    course_id   BIGINT       NOT NULL REFERENCES academy_courses (id) ON DELETE CASCADE,
    title       VARCHAR(300) NOT NULL,
    description TEXT,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_chapters_course ON academy_chapters (course_id, sort_order);

-- 6.2 Assessment — quizzes, versioned questions, options -----------------------
-- Question content must be reviewed for accuracy before publication (spec
-- Module 13): questions carry review_state and an append-only version history;
-- publishing a quiz is refused while any question is not approved
-- (WORKFLOW-001, enforced in AssessmentService and audited).
CREATE TABLE assessment_quizzes (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id          UUID         NOT NULL UNIQUE,
    course_id          BIGINT       REFERENCES academy_courses (id),
    subject_id         BIGINT       REFERENCES subjects (id),
    title              VARCHAR(300) NOT NULL,
    description        TEXT,
    language           VARCHAR(8)   NOT NULL DEFAULT 'en',
    time_limit_seconds INT CHECK (time_limit_seconds IS NULL OR time_limit_seconds BETWEEN 10 AND 7200),
    pass_score_pct     INT          NOT NULL DEFAULT 60 CHECK (pass_score_pct BETWEEN 0 AND 100),
    status             VARCHAR(30)  NOT NULL DEFAULT 'draft'
                       CHECK (status IN ('draft','in_editorial_review','approved','published','archived')),
    created_by         BIGINT REFERENCES users (id),
    published_at       timestamptz,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_quizzes_subject ON assessment_quizzes (subject_id, status);
CREATE INDEX idx_quizzes_course ON assessment_quizzes (course_id, status);

CREATE TABLE assessment_questions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID         NOT NULL UNIQUE,
    quiz_id      BIGINT       NOT NULL REFERENCES assessment_quizzes (id) ON DELETE CASCADE,
    question_type VARCHAR(20) NOT NULL DEFAULT 'mcq' CHECK (question_type IN ('mcq')),
    prompt       TEXT         NOT NULL,
    prompt_ar    TEXT,
    -- Answer explanation: served ONLY inside a submitted attempt result —
    -- never through the pre-submission quiz payload.
    explanation  TEXT         NOT NULL,
    points       INT          NOT NULL DEFAULT 1 CHECK (points BETWEEN 1 AND 100),
    version      INT          NOT NULL DEFAULT 1,
    review_state VARCHAR(20)  NOT NULL DEFAULT 'draft'
                 CHECK (review_state IN ('draft','pending_review','approved')),
    sort_order   INT          NOT NULL DEFAULT 0,
    created_by   BIGINT REFERENCES users (id),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_questions_quiz ON assessment_questions (quiz_id, sort_order);

-- Append-only version history for every question edit (versioned question
-- management + editorial review trail, spec Module 13).
CREATE TABLE assessment_question_versions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id BIGINT      NOT NULL REFERENCES assessment_questions (id) ON DELETE CASCADE,
    version     INT         NOT NULL,
    snapshot    JSONB       NOT NULL,
    change_note VARCHAR(500),
    edited_by   BIGINT      REFERENCES users (id),
    edited_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (question_id, version)
);

CREATE TABLE assessment_question_options (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES assessment_questions (id) ON DELETE CASCADE,
    option_text TEXT   NOT NULL,
    is_correct  BOOLEAN NOT NULL DEFAULT false,
    sort_order  INT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_options_question ON assessment_question_options (question_id, sort_order);

-- Attempts: the presented set is snapshotted at start so later question edits
-- cannot alter a running or graded attempt; grading happens server-side only.
CREATE TABLE assessment_attempts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID        NOT NULL UNIQUE,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    quiz_id      BIGINT      NOT NULL REFERENCES assessment_quizzes (id),
    presented    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    status       VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                 CHECK (status IN ('in_progress','submitted','expired')),
    started_at   timestamptz NOT NULL DEFAULT now(),
    submitted_at timestamptz,
    score_pct    INT CHECK (score_pct BETWEEN 0 AND 100),
    passed       BOOLEAN
);
CREATE INDEX idx_attempts_user ON assessment_attempts (user_id, started_at DESC);
CREATE INDEX idx_attempts_quiz ON assessment_attempts (quiz_id);

CREATE TABLE assessment_attempt_answers (
    attempt_id         BIGINT NOT NULL REFERENCES assessment_attempts (id) ON DELETE CASCADE,
    question_id        BIGINT NOT NULL REFERENCES assessment_questions (id),
    selected_option_id BIGINT REFERENCES assessment_question_options (id),
    correct            BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (attempt_id, question_id)
);

-- 6.3 Flashcards — admin decks and personal user decks are one schema, two
-- owners. Admin decks go through publish states; user decks are private by
-- construction (owner_user_id enforced on every read path).
CREATE TABLE flashcard_decks (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID         NOT NULL UNIQUE,
    owner_type    VARCHAR(10)  NOT NULL CHECK (owner_type IN ('admin','user')),
    owner_user_id BIGINT       REFERENCES users (id),
    subject_id    BIGINT       REFERENCES subjects (id),
    title         VARCHAR(300) NOT NULL,
    description   TEXT,
    language      VARCHAR(8)   NOT NULL DEFAULT 'en',
    publish_state VARCHAR(20)  NOT NULL DEFAULT 'draft'
                  CHECK (publish_state IN ('draft','approved','published','disabled')),
    created_by    BIGINT REFERENCES users (id),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CHECK ((owner_type = 'admin' AND owner_user_id IS NULL)
        OR (owner_type = 'user' AND owner_user_id IS NOT NULL))
);
CREATE INDEX idx_decks_owner ON flashcard_decks (owner_type, owner_user_id);
CREATE INDEX idx_decks_public ON flashcard_decks (owner_type, publish_state);

CREATE TABLE flashcards (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    deck_id    BIGINT      NOT NULL REFERENCES flashcard_decks (id) ON DELETE CASCADE,
    front_text TEXT        NOT NULL,
    back_text  TEXT        NOT NULL,
    hint       VARCHAR(500),
    sort_order INT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_cards_deck ON flashcards (deck_id, sort_order);

-- 6.4 Academy — lessons (after quizzes/decks so the FKs resolve) ---------------
CREATE TABLE academy_lessons (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID         NOT NULL UNIQUE,
    chapter_id        BIGINT       NOT NULL REFERENCES academy_chapters (id) ON DELETE CASCADE,
    title             VARCHAR(300) NOT NULL,
    lesson_type       VARCHAR(20)  NOT NULL CHECK (lesson_type IN ('text','video','pdf','quiz','flashcards')),
    body_text         TEXT,                       -- text lessons
    video_url         VARCHAR(500),               -- external reference only (OD-07: no paid hosting)
    media_asset_id    BIGINT REFERENCES media_assets (id),   -- pdf material via scan-gated storage
    quiz_id           BIGINT REFERENCES assessment_quizzes (id),
    flashcard_deck_id BIGINT REFERENCES flashcard_decks (id),
    duration_minutes  INT CHECK (duration_minutes BETWEEN 0 AND 600),
    sort_order        INT          NOT NULL DEFAULT 0,
    created_at        timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_lessons_chapter ON academy_lessons (chapter_id, sort_order);

-- 6.5 Academy — enrolments & progress -----------------------------------------
CREATE TABLE academy_enrolments (
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    course_id    BIGINT      NOT NULL REFERENCES academy_courses (id),
    status       VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active','completed')),
    enrolled_at  timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    PRIMARY KEY (user_id, course_id)
);
CREATE INDEX idx_enrolments_course ON academy_enrolments (course_id);

CREATE TABLE academy_lesson_progress (
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    lesson_id    BIGINT      NOT NULL REFERENCES academy_lessons (id),
    completed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, lesson_id)
);
CREATE INDEX idx_progress_lesson ON academy_lesson_progress (lesson_id);

-- 6.6 Research — topics, articles, citations -----------------------------------
-- Topic taxonomy fixed by the approved spec (Module 11); seeded as reference
-- LABELS only (no religious content).
CREATE TABLE research_topics (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug        VARCHAR(60)  NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    name_ar     VARCHAR(150),
    description VARCHAR(500),
    sort_order  INT          NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active' CHECK (status IN ('active','hidden'))
);

-- source_tier is the mandatory three-way classification (primary sources vs
-- secondary research vs editorial commentary) — public surfaces must render
-- it; scholarly review is required for primary/secondary articles.
CREATE TABLE research_articles (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID         NOT NULL UNIQUE,
    topic_id            BIGINT       NOT NULL REFERENCES research_topics (id),
    title               VARCHAR(400) NOT NULL,
    title_ar            VARCHAR(400),
    summary             VARCHAR(1000),
    body                TEXT         NOT NULL,
    language            VARCHAR(8)   NOT NULL DEFAULT 'en',
    author_display_name VARCHAR(200),
    source_tier         VARCHAR(20)  NOT NULL
                        CHECK (source_tier IN ('primary','secondary','editorial')),
    status              VARCHAR(30)  NOT NULL DEFAULT 'draft'
                        CHECK (status IN ('draft','in_editorial_review','in_scholarly_review','approved','published','archived')),
    created_by          BIGINT REFERENCES users (id),
    published_at        timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_articles_topic ON research_articles (topic_id, status);
CREATE INDEX idx_articles_fts ON research_articles
    USING gin (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(summary,'') || ' ' || coalesce(body,'')));

-- Citations: each citation is structurally validated against the record it
-- points to (quran_ayah → quran_ayahs row; hadith → hadiths row; book → books
-- row; research_article → research_articles row). The FK to the referenced
-- content is checked in ResearchService at save time — a citation that points
-- to nothing is rejected (VAL-001), never silently stored.
CREATE TABLE research_article_citations (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_id        BIGINT      NOT NULL REFERENCES research_articles (id) ON DELETE CASCADE,
    citation_kind     VARCHAR(20) NOT NULL
                      CHECK (citation_kind IN ('quran_ayah','hadith','book','research_article','external')),
    surah_id          INT,
    ayah_number       INT,
    hadith_id         BIGINT,
    book_public_id    UUID,
    article_public_id UUID,
    external_ref      VARCHAR(500),
    quote             TEXT,
    note              VARCHAR(500),
    sort_order        INT         NOT NULL DEFAULT 0,
    CHECK (
        (citation_kind = 'quran_ayah'      AND surah_id IS NOT NULL AND ayah_number IS NOT NULL)
     OR (citation_kind = 'hadith'          AND hadith_id IS NOT NULL)
     OR (citation_kind = 'book'            AND book_public_id IS NOT NULL)
     OR (citation_kind = 'research_article' AND article_public_id IS NOT NULL)
     OR (citation_kind = 'external'        AND external_ref IS NOT NULL)
    )
);
CREATE INDEX idx_citations_article ON research_article_citations (article_id, sort_order);

-- 6.7 Scholars — directory + verification (spec Module 14) ----------------------
-- Qualifications/verification are admin-recorded facts, never generated.
-- verification_status changes are audited; user_id optionally links a scholar
-- profile to a platform account so review history (fatwas, articles) resolves.
CREATE TABLE scholars (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id            UUID         NOT NULL UNIQUE,
    name                 VARCHAR(200) NOT NULL,
    name_ar              VARCHAR(200),
    biography            TEXT,
    qualifications       VARCHAR(1000),
    areas_of_expertise   VARCHAR(500),
    photo_media_id       BIGINT REFERENCES media_assets (id),
    user_id              BIGINT UNIQUE REFERENCES users (id),
    verification_status  VARCHAR(20)  NOT NULL DEFAULT 'unverified'
                         CHECK (verification_status IN ('unverified','verified','revoked')),
    verified_by          BIGINT REFERENCES users (id),
    verified_at          timestamptz,
    created_by           BIGINT REFERENCES users (id),
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_scholars_status ON scholars (verification_status);

-- 6.8 M5 reference seed — research topic taxonomy (spec Module 11, labels only)
INSERT INTO research_topics (slug, name, name_ar, description, sort_order, status) VALUES
    ('quran',               'Quran',                  'القرآن',          'Quranic studies and exegesis themes',              1,  'active'),
    ('hadith',              'Hadith',                 'الحديث',          'Hadith sciences, chains and collection studies',    2,  'active'),
    ('fiqh',                'Fiqh',                   'الفقه',           'Jurisprudence across recognised schools',           3,  'active'),
    ('usul_al_fiqh',        'Usul al-Fiqh',           'أصول الفقه',      'Principles of Islamic jurisprudence',               4,  'active'),
    ('aqeedah',             'Aqeedah',                'العقيدة',         'Creed and theology studies',                        5,  'active'),
    ('islamic_history',     'Islamic History',        'التاريخ الإسلامي','Historical studies of the Muslim world',            6,  'active'),
    ('seerah',              'Seerah',                 'السيرة',          'Prophetic biography studies',                       7,  'active'),
    ('arabic_language',     'Arabic Language',        'اللغة العربية',   'Arabic philology and linguistics',                  8,  'active'),
    ('classical_literature','Classical Literature',   'الأدب الكلاسيكي', 'Classical Islamic literary heritage',               9,  'active'),
    ('comparative_studies', 'Comparative Studies',    'الدراسات المقارنة','Comparative religion and madhhab studies',         10, 'active')
ON CONFLICT (slug) DO NOTHING;

-- 6.9 M5 bugfix (found by M5 tests): the M4 notes PUT upserts with
-- ON CONFLICT (user_id, ref_key), but V5 created user_notes WITHOUT that
-- unique constraint — so every note write failed with a planner error
-- (latent M4 defect; M4 tests never covered notes). Safe to add here: the
-- broken insert path can never have produced duplicate rows in any database.
CREATE UNIQUE INDEX IF NOT EXISTS ux_notes_user_ref ON user_notes (user_id, ref_key);
