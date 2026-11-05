-- V5 — M4 Core Islamic Knowledge Modules schema (quran, hadith, dictionary,
-- duas, calendar, daily content, recitation metadata, personal library,
-- user content, controlled-import records).
-- Forward-only migration (approved testing Option A — no undo).
-- No applied migration is modified. No religious text is created here —
-- canonical content arrives only through the controlled import runner
-- (OD-05/G-3 respected; development fixtures are clearly labelled is_fixture=true).

-- 5.1 Controlled import records ------------------------------------------------
CREATE TABLE import_batches (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_register_id VARCHAR(20)  NOT NULL,               -- 'SR-01', 'SR-08', 'DEV-FIXTURES'
    licence_id         BIGINT       REFERENCES licences (id),
    content_kind       VARCHAR(30)  NOT NULL,               -- quran_text | hadith_collection | fixture_set
    edition_code       VARCHAR(60),
    source_url         VARCHAR(500) NOT NULL,
    file_sha256        CHAR(64),
    manifest           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    records_imported   INT          NOT NULL DEFAULT 0,
    status             VARCHAR(20)  NOT NULL DEFAULT 'running'
                       CHECK (status IN ('running','completed','failed')),
    integrity_status   VARCHAR(20)  NOT NULL DEFAULT 'pending'
                       CHECK (integrity_status IN ('pending','passed','failed')),
    imported_by        BIGINT       REFERENCES users (id),
    imported_at        timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_import_batches_kind ON import_batches (content_kind, imported_at DESC);

-- 5.2 Quran — canonical structure (SR-01 verbatim text) ------------------------
CREATE TABLE quran_surahs (
    id                  INT PRIMARY KEY CHECK (id BETWEEN 1 AND 114),
    name_ar             VARCHAR(100) NOT NULL,
    name_transliterated VARCHAR(100) NOT NULL,
    name_en             VARCHAR(150) NOT NULL,
    revelation_place    VARCHAR(10)  NOT NULL CHECK (revelation_place IN ('makki','madani')),
    revelation_order    INT CHECK (revelation_order BETWEEN 1 AND 114),
    ayah_count          INT NOT NULL CHECK (ayah_count BETWEEN 3 AND 286),
    bismillah_prefix    BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE quran_ayahs (
    surah_id     INT  NOT NULL REFERENCES quran_surahs (id),
    ayah_number  INT  NOT NULL CHECK (ayah_number >= 1),
    text_uthmani TEXT NOT NULL,
    juz          INT CHECK (juz BETWEEN 1 AND 30),
    sajdah       BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (surah_id, ayah_number)
);
CREATE INDEX idx_quran_ayahs_juz ON quran_ayahs (juz, surah_id, ayah_number);
CREATE INDEX idx_quran_ayahs_fts ON quran_ayahs
    USING gin (to_tsvector('simple', text_uthmani));

-- 5.3 Quran — translations (editions publish-gated; unlicensed editions stay
--     is_fixture=true + publish_state='disabled' → never served publicly)
CREATE TABLE quran_translation_editions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code               VARCHAR(60)  NOT NULL UNIQUE,
    language           VARCHAR(8)   NOT NULL,
    display_name       VARCHAR(200) NOT NULL,
    translator         VARCHAR(200),
    source_register_id VARCHAR(20),
    licence_id         BIGINT REFERENCES licences (id),
    is_fixture         BOOLEAN      NOT NULL DEFAULT false,
    publish_state      VARCHAR(20)  NOT NULL DEFAULT 'draft'
                       CHECK (publish_state IN ('draft','approved','published','disabled')),
    created_at         timestamptz  NOT NULL DEFAULT now()
);
CREATE TABLE quran_ayah_translations (
    edition_id  BIGINT NOT NULL REFERENCES quran_translation_editions (id) ON DELETE CASCADE,
    surah_id    INT    NOT NULL,
    ayah_number INT    NOT NULL,
    text        TEXT   NOT NULL,
    PRIMARY KEY (edition_id, surah_id, ayah_number),
    FOREIGN KEY (surah_id, ayah_number) REFERENCES quran_ayahs (surah_id, ayah_number)
);
CREATE INDEX idx_quran_trans_fts ON quran_ayah_translations
    USING gin (to_tsvector('simple', text));

-- 5.4 Quran — word-by-word (no verified dataset: is_fixture=true, gated)
CREATE TABLE quran_word_entries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    surah_id        INT  NOT NULL,
    ayah_number     INT  NOT NULL,
    word_position   INT  NOT NULL CHECK (word_position >= 1),
    text_ar         TEXT NOT NULL,
    transliteration VARCHAR(200),
    meaning_en      TEXT,
    meaning_ur      TEXT,
    meaning_hi      TEXT,
    source_register_id VARCHAR(20),
    is_fixture      BOOLEAN NOT NULL DEFAULT true,
    publish_state   VARCHAR(20) NOT NULL DEFAULT 'draft'
                    CHECK (publish_state IN ('draft','approved','published','disabled')),
    UNIQUE (surah_id, ayah_number, word_position),
    FOREIGN KEY (surah_id, ayah_number) REFERENCES quran_ayahs (surah_id, ayah_number)
);

-- 5.5 Tafsir editions + entries (SR-11/SR-12 unresolved: fixtures only, gated)
CREATE TABLE tafsir_editions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code               VARCHAR(60)  NOT NULL UNIQUE,
    name_ar            VARCHAR(200),
    name_en            VARCHAR(200) NOT NULL,
    author             VARCHAR(200),
    language           VARCHAR(8)   NOT NULL DEFAULT 'ar',
    source_register_id VARCHAR(20),
    licence_id         BIGINT REFERENCES licences (id),
    is_fixture         BOOLEAN      NOT NULL DEFAULT true,
    publish_state      VARCHAR(20)  NOT NULL DEFAULT 'draft'
                       CHECK (publish_state IN ('draft','approved','published','disabled')),
    created_at         timestamptz  NOT NULL DEFAULT now()
);
CREATE TABLE tafsir_entries (
    edition_id  BIGINT NOT NULL REFERENCES tafsir_editions (id) ON DELETE CASCADE,
    surah_id    INT    NOT NULL,
    ayah_number INT    NOT NULL,
    text        TEXT   NOT NULL,
    PRIMARY KEY (edition_id, surah_id, ayah_number),
    FOREIGN KEY (surah_id, ayah_number) REFERENCES quran_ayahs (surah_id, ayah_number)
);

-- 5.6 Ayah study notes (asbab al-nuzul / cross-references / explanations; gated)
CREATE TABLE ayah_study_notes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind        VARCHAR(30) NOT NULL
                CHECK (kind IN ('asbab_al_nuzul','cross_reference','explanation')),
    surah_id    INT NOT NULL,
    ayah_number INT NOT NULL,
    text        TEXT NOT NULL,
    source_ref  VARCHAR(500),
    is_fixture  BOOLEAN NOT NULL DEFAULT true,
    publish_state VARCHAR(20) NOT NULL DEFAULT 'draft'
                CHECK (publish_state IN ('draft','approved','published','disabled')),
    FOREIGN KEY (surah_id, ayah_number) REFERENCES quran_ayahs (surah_id, ayah_number)
);
CREATE INDEX idx_ayah_notes ON ayah_study_notes (surah_id, ayah_number, kind);

-- 5.7 Hadith (SR-08 Unlicense; ara+eng+urd editions merged per hadith) ---------
CREATE TABLE hadith_collections (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                   VARCHAR(30) NOT NULL UNIQUE,
    name_ar                VARCHAR(200),
    name_en                VARCHAR(200) NOT NULL,
    edition_note           VARCHAR(500),
    numbering_note         VARCHAR(500),
    source_register_id     VARCHAR(20) NOT NULL DEFAULT 'SR-08',
    licence_id             BIGINT REFERENCES licences (id),
    scholarly_qa_status    VARCHAR(20) NOT NULL DEFAULT 'pending'
                           CHECK (scholarly_qa_status IN ('pending','passed')),
    publish_state          VARCHAR(20) NOT NULL DEFAULT 'draft'
                           CHECK (publish_state IN ('draft','approved','published','disabled')),
    created_at             timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE hadith_books (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    collection_id  BIGINT NOT NULL REFERENCES hadith_collections (id) ON DELETE CASCADE,
    book_number    INT    NOT NULL CHECK (book_number >= 0),
    name_en        TEXT,
    name_ar        TEXT,
    hadith_first   INT,
    hadith_last    INT,
    UNIQUE (collection_id, book_number)
);
CREATE INDEX idx_hadith_books_coll ON hadith_books (collection_id, book_number);
CREATE TABLE hadiths (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    collection_id   BIGINT NOT NULL REFERENCES hadith_collections (id),
    book_id         BIGINT NOT NULL REFERENCES hadith_books (id),
    number_in_book  INT    NOT NULL CHECK (number_in_book >= 0),
    arabic_number   INT,
    text_ar         TEXT,
    text_en         TEXT,
    text_ur         TEXT,
    narrator_chain  TEXT,          -- NULL when the dataset does not provide it
    gradings        JSONB  NOT NULL DEFAULT '[]'::jsonb,  -- [{gradedBy, grade, source}]
    takhrij         TEXT,
    reference_note  VARCHAR(500),
    publish_state   VARCHAR(20) NOT NULL DEFAULT 'published'
                    CHECK (publish_state IN ('draft','approved','published','disabled')),
    UNIQUE (collection_id, book_id, number_in_book)
);
CREATE INDEX idx_hadiths_book ON hadiths (book_id, number_in_book);
CREATE INDEX idx_hadiths_fts_ar ON hadiths
    USING gin (to_tsvector('simple', coalesce(text_ar, '')));
CREATE INDEX idx_hadiths_fts_en ON hadiths
    USING gin (to_tsvector('simple', coalesce(text_en, '')));

-- 5.8 Arabic dictionary (SR-18 unresolved: fixtures only, gated) ----------------
CREATE TABLE dictionary_entries (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    headword_ar      VARCHAR(200) NOT NULL,
    root             VARCHAR(100),
    part_of_speech   VARCHAR(30),
    transliteration  VARCHAR(200),
    pronunciation    VARCHAR(200),
    meaning_en       TEXT,
    meaning_ur       TEXT,
    meaning_hi       TEXT,
    forms            JSONB NOT NULL DEFAULT '[]'::jsonb,
    related          JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_register_id VARCHAR(20),
    licence_id       BIGINT REFERENCES licences (id),
    is_fixture       BOOLEAN NOT NULL DEFAULT true,
    publish_state    VARCHAR(20) NOT NULL DEFAULT 'draft'
                     CHECK (publish_state IN ('draft','approved','published','disabled')),
    created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_dictionary_fts ON dictionary_entries
    USING gin (to_tsvector('simple', coalesce(headword_ar,'') || ' ' || coalesce(meaning_en,'')));

-- 5.9 Duas & Adhkar (SR-16 unresolved: fixtures only, gated) --------------------
CREATE TABLE dua_categories (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(40) NOT NULL UNIQUE,
    name_en       VARCHAR(150) NOT NULL,
    name_ar       VARCHAR(150),
    name_ur       VARCHAR(150),
    name_hi       VARCHAR(150),
    sort_order    INT NOT NULL DEFAULT 100,
    publish_state VARCHAR(20) NOT NULL DEFAULT 'draft'
                  CHECK (publish_state IN ('draft','approved','published','disabled'))
);
CREATE TABLE duas (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id        BIGINT NOT NULL REFERENCES dua_categories (id),
    title              VARCHAR(200),
    text_ar            TEXT NOT NULL,
    transliteration    TEXT,
    meaning_en         TEXT,
    meaning_ur         TEXT,
    meaning_hi         TEXT,
    reference          VARCHAR(500),
    source_attribution VARCHAR(300),
    authenticity_note  VARCHAR(300),
    audio_media_id     BIGINT REFERENCES media_assets (id),
    is_fixture         BOOLEAN NOT NULL DEFAULT true,
    publish_state      VARCHAR(20) NOT NULL DEFAULT 'draft'
                       CHECK (publish_state IN ('draft','approved','published','disabled')),
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_duas_category ON duas (category_id, publish_state);
CREATE INDEX idx_duas_fts_en ON duas
    USING gin (to_tsvector('simple', coalesce(meaning_en,'') || ' ' || coalesce(title,'')));

-- 5.10 Hijri calendar (SR-19 verified reference data) ---------------------------
CREATE TABLE hijri_conventions (
    code               VARCHAR(30) PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    default_offset_days INT NOT NULL DEFAULT 0 CHECK (default_offset_days BETWEEN -3 AND 3),
    is_default         BOOLEAN NOT NULL DEFAULT false
);
CREATE TABLE islamic_events (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(40) NOT NULL UNIQUE,
    name_en       VARCHAR(150) NOT NULL,
    name_ar       VARCHAR(150),
    hijri_month   INT NOT NULL CHECK (hijri_month BETWEEN 1 AND 12),
    hijri_day     INT NOT NULL CHECK (hijri_day BETWEEN 1 AND 30),
    basis         VARCHAR(25) NOT NULL DEFAULT 'calculated'
                  CHECK (basis IN ('calculated','observed_announced')),
    description   TEXT,
    is_major      BOOLEAN NOT NULL DEFAULT false,
    publish_state VARCHAR(20) NOT NULL DEFAULT 'draft'
                  CHECK (publish_state IN ('draft','approved','published','disabled'))
);

-- 5.11 Daily content (admin-managed; slots served only from published records) --
CREATE TABLE daily_content_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slot_code      VARCHAR(30) NOT NULL
                   CHECK (slot_code IN ('ayah_of_day','hadith_of_day','daily_reminder','islamic_event')),
    publish_date   DATE,
    rotation_weight INT NOT NULL DEFAULT 1 CHECK (rotation_weight BETWEEN 1 AND 100),
    content_type   VARCHAR(20) NOT NULL
                   CHECK (content_type IN ('ayah','hadith','dua','event','text')),
    ayah_surah_id  INT,
    ayah_number    INT,
    hadith_id      BIGINT REFERENCES hadiths (id),
    dua_id         BIGINT REFERENCES duas (id),
    event_code     VARCHAR(40) REFERENCES islamic_events (code),
    custom_text    TEXT,
    attribution    VARCHAR(300),
    status         VARCHAR(30) NOT NULL DEFAULT 'draft'
                   CHECK (status IN ('draft','in_editorial_review','in_scholarly_review',
                                     'approved','published','archived')),
    created_by     BIGINT REFERENCES users (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    published_at   timestamptz
);
CREATE INDEX idx_daily_items_slot ON daily_content_items (slot_code, status, publish_date);

-- 5.12 Recitation audio metadata (no audio imported — rights unverified; the
--      publish_state stays 'disabled' so playback surfaces an honest state) ----
CREATE TABLE recitation_editions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code               VARCHAR(60) NOT NULL UNIQUE,
    reciter_name       VARCHAR(200) NOT NULL,
    style              VARCHAR(60),
    source_register_id VARCHAR(20),
    licence_id         BIGINT REFERENCES licences (id),
    publish_state      VARCHAR(20) NOT NULL DEFAULT 'disabled'
                       CHECK (publish_state IN ('draft','approved','published','disabled')),
    notes              VARCHAR(500)
);
CREATE TABLE ayah_audio (
    edition_id  BIGINT NOT NULL REFERENCES recitation_editions (id) ON DELETE CASCADE,
    surah_id    INT    NOT NULL,
    ayah_number INT    NOT NULL,
    media_id    BIGINT NOT NULL REFERENCES media_assets (id),
    PRIMARY KEY (edition_id, surah_id, ayah_number),
    FOREIGN KEY (surah_id, ayah_number) REFERENCES quran_ayahs (surah_id, ayah_number)
);

-- 5.13 Personal Knowledge Library (strictly private; NEVER exposed through the
--      platform books domain — no admin endpoint exists for these tables) ------
CREATE TABLE personal_books (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id           BIGINT      NOT NULL REFERENCES users (id),
    title             VARCHAR(300) NOT NULL,
    author            VARCHAR(200),
    description       TEXT,
    language          VARCHAR(8)  NOT NULL DEFAULT 'en',
    format            VARCHAR(10) NOT NULL CHECK (format IN ('pdf','epub')),
    media_id          BIGINT      NOT NULL REFERENCES media_assets (id),
    cover_media_id    BIGINT      REFERENCES media_assets (id),
    file_size_bytes   BIGINT,
    text_searchable   BOOLEAN     NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    deleted_at        timestamptz
);
CREATE INDEX idx_personal_books_user ON personal_books (user_id) WHERE deleted_at IS NULL;
CREATE TABLE personal_collections (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id  UUID   NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id    BIGINT NOT NULL REFERENCES users (id),
    name       VARCHAR(150) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);
CREATE TABLE personal_book_items (
    collection_id BIGINT NOT NULL REFERENCES personal_collections (id) ON DELETE CASCADE,
    book_id       BIGINT NOT NULL REFERENCES personal_books (id) ON DELETE CASCADE,
    added_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (collection_id, book_id)
);

-- 5.14 User content: bookmarks / notes / favourites / progress / history --------
CREATE TABLE user_bookmarks (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id),
    ref_key    VARCHAR(120) NOT NULL,   -- 'ayah:2:255' | 'hadith:<id>' | 'book:<uuid>' | 'dua:<id>'
    note       VARCHAR(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, ref_key)
);
CREATE INDEX idx_bookmarks_user ON user_bookmarks (user_id, created_at DESC);
CREATE TABLE user_notes (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id),
    ref_key    VARCHAR(120) NOT NULL,
    body       TEXT   NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notes_user ON user_notes (user_id, updated_at DESC);
CREATE TABLE user_favourites (
    user_id    BIGINT NOT NULL REFERENCES users (id),
    ref_key    VARCHAR(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, ref_key)
);
CREATE TABLE reading_progress (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    ref_key     VARCHAR(120) NOT NULL,
    position_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    percent     INT CHECK (percent BETWEEN 0 AND 100),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, ref_key)
);
CREATE INDEX idx_progress_user ON reading_progress (user_id, updated_at DESC);
CREATE TABLE reading_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    ref_key     VARCHAR(120) NOT NULL,
    title       VARCHAR(300),
    accessed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_history_user ON reading_history (user_id, accessed_at DESC);

-- 5.15 Search support for existing platform books (full-text over title+desc) ---
CREATE INDEX idx_books_fts ON books
    USING gin (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,'')));
