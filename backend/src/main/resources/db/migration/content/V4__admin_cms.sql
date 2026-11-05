-- V4 — Admin CMS core schema (books, authors, fatwas, deletion workflow, media extension)
-- Forward-only migration (approved testing Option A — no undo).
-- Extends the V2 content spine; no existing table is dropped or reshaped.
-- NO religious content is created here — containers only (import gated at OD-05/G-3).

-- 4.1 Authors ---------------------------------------------------------------
CREATE TABLE authors (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL,
    name_ar    VARCHAR(200),
    bio        TEXT,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

-- 4.2 Books (platform-managed library; user personal uploads are a separate,
--     storage-prefixed domain and can never be published through this table).
CREATE TABLE books (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                  UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    title                      VARCHAR(300) NOT NULL,
    subtitle                   VARCHAR(300),
    description                TEXT,
    language                   VARCHAR(8)   NOT NULL DEFAULT 'en',
    subject_id                 BIGINT       NOT NULL REFERENCES subjects (id),
    publisher                  VARCHAR(200),
    edition                    VARCHAR(100),
    publication_year           INT          CHECK (publication_year IS NULL OR publication_year BETWEEN 1000 AND 2100),
    page_count                 INT          CHECK (page_count IS NULL OR page_count > 0),
    isbn                       VARCHAR(32),
    copyright_info             VARCHAR(500),
    licence_id                 BIGINT       REFERENCES licences (id),
    download_allowed           BOOLEAN      NOT NULL DEFAULT false,
    scholarly_review_required  BOOLEAN      NOT NULL DEFAULT false,
    status                     VARCHAR(30)  NOT NULL DEFAULT 'draft'
                               CHECK (status IN ('draft','in_editorial_review','in_scholarly_review',
                                                 'approved','published','archived')),
    cover_media_id             BIGINT       REFERENCES media_assets (id),
    created_by                 BIGINT       REFERENCES users (id),
    updated_by                 BIGINT       REFERENCES users (id),
    created_at                 timestamptz  NOT NULL DEFAULT now(),
    updated_at                 timestamptz  NOT NULL DEFAULT now(),
    published_at               timestamptz,
    archived_at                timestamptz
);
CREATE INDEX idx_books_status    ON books (status, updated_at DESC);
CREATE INDEX idx_books_subject   ON books (subject_id);
CREATE INDEX idx_books_language  ON books (language);
CREATE INDEX idx_books_title     ON books (lower(title));

-- 4.3 Book ↔ author (spec §3.5.1 exposes authors[]; ordered join, no property
--     of the display position is ever used as a key elsewhere).
CREATE TABLE book_authors (
    book_id    BIGINT NOT NULL REFERENCES books (id)   ON DELETE CASCADE,
    author_id  BIGINT NOT NULL REFERENCES authors (id),
    sort_order INT    NOT NULL DEFAULT 1,
    PRIMARY KEY (book_id, author_id)
);
CREATE INDEX idx_book_authors_author ON book_authors (author_id);

-- 4.4 Book files (platform book PDF/EPUB attachments; media must be scan-clean
--     before the book can be published — enforced in the service layer).
CREATE TABLE book_files (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id          BIGINT      NOT NULL REFERENCES books (id) ON DELETE CASCADE,
    media_id         BIGINT      NOT NULL UNIQUE REFERENCES media_assets (id),
    format           VARCHAR(10) NOT NULL CHECK (format IN ('pdf','epub')),
    download_allowed BOOLEAN     NOT NULL DEFAULT false,
    ocr_available    BOOLEAN     NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_book_files_book ON book_files (book_id);

-- 4.5 Fatwas (dedicated strict workflow — spec §3.17 state machine:
--     draft → in_review → approved → published → archived; reject → draft;
--     edit-after-approval resets to draft. Publish gate is enforced in the
--     service layer: state=approved AND a scholar_reviewer approval record
--     exists AND publisher ≠ approving reviewer; every denied attempt is
--     audited and returns WORKFLOW-001.)
CREATE TABLE fatwas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id            UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    question             TEXT        NOT NULL,
    answer_draft         TEXT        NOT NULL,
    answer               TEXT,
    category             VARCHAR(60),
    language             VARCHAR(8)  NOT NULL DEFAULT 'ar',
    source_refs          JSONB       NOT NULL DEFAULT '[]'::jsonb,
    state                VARCHAR(20) NOT NULL DEFAULT 'draft'
                         CHECK (state IN ('draft','in_review','approved','published','archived')),
    created_by           BIGINT      NOT NULL REFERENCES users (id),
    assigned_reviewer_id BIGINT      REFERENCES users (id),
    decided_by           BIGINT      REFERENCES users (id),
    decided_at           timestamptz,
    decision_notes       TEXT,
    published_by         BIGINT      REFERENCES users (id),
    published_at         timestamptz,
    version              INT         NOT NULL DEFAULT 1,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_fatwas_state     ON fatwas (state, updated_at DESC);
CREATE INDEX idx_fatwas_reviewer  ON fatwas (assigned_reviewer_id);

-- 4.6 Admin-started account deletion workflow (spec §3.2.19; matrix
--     "Users / Deletion workflow": Super Admin Full, Admin Review).
CREATE TABLE account_deletion_requests (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    requested_by BIGINT     NOT NULL REFERENCES users (id),
    state       VARCHAR(20) NOT NULL DEFAULT 'pending_review'
                CHECK (state IN ('pending_review','scheduled','cancelled','purged')),
    reason      VARCHAR(500),
    purge_at    timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    decided_at  timestamptz
);
CREATE INDEX idx_deletion_requests_user ON account_deletion_requests (user_id, created_at DESC);

-- 4.7 subjects.description — the M2 admin CMS lets administrators edit
--     subject descriptions (Module C). V2 (applied in M1) is never modified;
--     the column is added forward-only here.
ALTER TABLE subjects ADD COLUMN IF NOT EXISTS description text;

-- 4.7b subjects.sort_order UNIQUE becomes DEFERRABLE (constraint still enforced
--     at COMMIT — the exactly-15 invariant is preserved). Rationale: reordering
--     display positions swaps values inside 1..15; with an immediately-checked
--     unique index every permutation collides on intermediate statements. The
--     V2 CHECK (BETWEEN 1 AND 15) stays immediate — intermediate values remain
--     in range; only uniqueness needs deferral.
ALTER TABLE subjects DROP CONSTRAINT subjects_sort_order_key;
ALTER TABLE subjects ADD CONSTRAINT subjects_sort_order_key UNIQUE (sort_order) DEFERRABLE INITIALLY DEFERRED;

-- 4.8 media_assets extension for the M2 upload pipeline (columns are additive;
--     existing rows keep NULL purpose and remain valid). public_id gives media
--     the stable public identifier the API contract exposes (internal BIGINT
--     ids are never exposed per docs/05-api-specification.md §1).
ALTER TABLE media_assets
    ADD COLUMN public_id         UUID,
    ADD COLUMN purpose           VARCHAR(40),
    ADD COLUMN original_filename VARCHAR(255),
    ADD COLUMN uploaded_by       BIGINT      REFERENCES users (id),
    ADD COLUMN scanned_at        timestamptz,
    ADD COLUMN scanner           VARCHAR(60);
UPDATE media_assets SET public_id = gen_random_uuid() WHERE public_id IS NULL;
ALTER TABLE media_assets ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX uq_media_public_id ON media_assets (public_id);
CREATE INDEX idx_media_scan  ON media_assets (scan_status, created_at DESC);
CREATE INDEX idx_media_owner ON media_assets (uploaded_by);
