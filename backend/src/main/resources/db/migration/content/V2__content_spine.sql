-- V2 — Content spine (docs/04-database-design.md §3.2)
-- Forward-only migration (testing Option A). Audit-log design per §6
-- (append-only; production DB roles will hold no UPDATE/DELETE grants).

CREATE TABLE subjects (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug        VARCHAR(60)  NOT NULL UNIQUE,       -- stable join key (e.g. cat_arabic_academy)
    name        VARCHAR(120) NOT NULL,
    name_ar     VARCHAR(120),
    -- Client-reference position 01–15: DISPLAY ORDER ONLY, never a join key
    -- or identifier (owner audit §1, docs/00-project-overview.md §3.2).
    sort_order  INT          NOT NULL UNIQUE CHECK (sort_order BETWEEN 1 AND 15),
    status      VARCHAR(20)  NOT NULL DEFAULT 'active'
                CHECK (status IN ('active','hidden_unconfirmed','archived')),
    image       VARCHAR(255),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE licences (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                   VARCHAR(60)  NOT NULL UNIQUE,
    title                  VARCHAR(200) NOT NULL,
    spdx_id                VARCHAR(60),
    attribution_required   boolean      NOT NULL DEFAULT true,
    commercial_use_allowed boolean      NOT NULL DEFAULT false,
    modification_allowed   boolean      NOT NULL DEFAULT false,
    source_url             VARCHAR(500),
    notes                  text,
    created_at             timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE media_assets (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    storage_key VARCHAR(500) NOT NULL UNIQUE,
    mime        VARCHAR(100) NOT NULL,
    size_bytes  BIGINT,
    sha256      CHAR(64)     UNIQUE,
    scan_status VARCHAR(20)  NOT NULL DEFAULT 'pending'
                CHECK (scan_status IN ('pending','clean','infected','rejected')),
    licence_id  BIGINT REFERENCES licences (id),
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE content_versions (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type    VARCHAR(40) NOT NULL,
    entity_id      BIGINT      NOT NULL,
    version        INT         NOT NULL,
    snapshot       jsonb       NOT NULL,
    change_summary VARCHAR(500),
    changed_by     BIGINT REFERENCES users (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (entity_type, entity_id, version)
);

CREATE TABLE content_review_records (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   BIGINT      NOT NULL,
    stage       VARCHAR(30) NOT NULL
                CHECK (stage IN ('editorial_review','scholarly_review')),
    reviewer_id BIGINT      NOT NULL REFERENCES users (id),
    decision    VARCHAR(20) NOT NULL
                CHECK (decision IN ('approved','rejected','changes_requested')),
    notes       text,
    decided_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_review_records_entity ON content_review_records (entity_type, entity_id);

-- Append-only audit log (docs/04-database-design.md §6).
CREATE TABLE audit_logs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT REFERENCES users (id),
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   BIGINT,
    before_hash CHAR(64),
    after_hash  CHAR(64),
    ip          inet,
    user_agent  VARCHAR(300),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_actor  ON audit_logs (actor_id, created_at DESC);

-- Fatwa separation-of-duties is enforced at the application/workflow layer with
-- these audit + review tables as the trail (docs/07-security-plan.md; the
-- permission seed keeps fatwa.publish and fatwa.review.approve disjoint).
