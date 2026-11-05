-- V1 — Identity & access foundation (docs/04-database-design.md §3.1)
-- Forward-only migration (testing Option A — Flyway Community, no undo).
-- oauth_accounts is intentionally NOT created: it is gated on OD-04
-- (authentication flow decision, gate G-2). It will be added by a later
-- forward migration if OD-04 selects social login.

CREATE TABLE roles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(80)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE permissions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(80)  NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    email             VARCHAR(255) UNIQUE,
    phone             VARCHAR(32)  UNIQUE,
    -- OD-04 (gate G-2) decides the primary auth factor(s); email XOR phone
    -- presence is enforced at the application layer until then.
    password_hash     VARCHAR(255),
    status            VARCHAR(20)  NOT NULL DEFAULT 'active'
                      CHECK (status IN ('active','suspended')),
    deletion_state    VARCHAR(20)  NOT NULL DEFAULT 'none'
                      CHECK (deletion_state IN ('none','requested','anonymising','purged')),
    language_pref     VARCHAR(8)   NOT NULL DEFAULT 'en',
    privacy_settings  jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_contact_chk CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE TABLE user_roles (
    user_id    BIGINT NOT NULL REFERENCES users (id),
    role_id    BIGINT NOT NULL REFERENCES roles (id),
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles (id),
    permission_id BIGINT NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(128) NOT NULL UNIQUE,  -- only hashes stored, never raw tokens
    device_name VARCHAR(60),
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE email_verifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE password_resets (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id),
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
