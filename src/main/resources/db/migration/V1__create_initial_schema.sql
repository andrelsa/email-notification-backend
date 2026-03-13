-- =============================================
-- V1: Initial schema
-- Tables: users, email_request, email_status, retry_control
-- =============================================

-- Required for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------- USERS ----------
CREATE TABLE users (
    id          BIGSERIAL       PRIMARY KEY,
    public_id   UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name        VARCHAR(255)    NOT NULL,
    email       VARCHAR(255)    NOT NULL UNIQUE,
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_public_id ON users (public_id);
CREATE INDEX idx_users_email     ON users (email);
CREATE INDEX idx_users_status    ON users (status);

-- ---------- EMAIL REQUEST ----------
CREATE TABLE email_request (
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    recipient_email VARCHAR(255)    NOT NULL,
    subject         VARCHAR(500)    NOT NULL,
    body            TEXT            NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_request_public_id  ON email_request (public_id);
CREATE INDEX idx_email_request_user_id    ON email_request (user_id);
CREATE INDEX idx_email_request_status     ON email_request (status);

-- ---------- EMAIL STATUS (audit trail) ----------
CREATE TABLE email_status (
    id                  BIGSERIAL       PRIMARY KEY,
    email_request_id    BIGINT          NOT NULL REFERENCES email_request(id),
    previous_status     VARCHAR(20),
    new_status          VARCHAR(20)     NOT NULL,
    description         TEXT,
    occurred_at         TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_status_request_id  ON email_status (email_request_id);
CREATE INDEX idx_email_status_occurred_at ON email_status (occurred_at);

-- ---------- RETRY CONTROL ----------
CREATE TABLE retry_control (
    id                  BIGSERIAL       PRIMARY KEY,
    email_request_id    BIGINT          NOT NULL UNIQUE REFERENCES email_request(id),
    attempt_count       INT             NOT NULL DEFAULT 0,
    max_attempts        INT             NOT NULL DEFAULT 3,
    last_attempt_at     TIMESTAMP,
    next_attempt_at     TIMESTAMP,
    last_error_message  TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_retry_control_email_request_id ON retry_control (email_request_id);
CREATE INDEX idx_retry_control_next_attempt     ON retry_control (next_attempt_at);

