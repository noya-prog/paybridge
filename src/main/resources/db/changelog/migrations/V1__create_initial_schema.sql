-- ============================================================
-- PayBridge Initial Schema
-- ============================================================

-- ENUM types
CREATE TYPE payment_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'SUCCESS',
    'FAILED',
    'CANCELLED',
    'REFUNDED'
);

CREATE TYPE payment_provider AS ENUM (
    'STRIPE',
    'CHAPA'
);

CREATE TYPE transaction_type AS ENUM (
    'PAYMENT',
    'REFUND'
);

CREATE TYPE webhook_status AS ENUM (
    'RECEIVED',
    'PROCESSING',
    'PROCESSED',
    'FAILED'
);

-- ============================================================
-- MERCHANTS
-- Who initiates payments through PayBridge
-- ============================================================
CREATE TABLE merchants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255)        NOT NULL,
    email               VARCHAR(255)        NOT NULL UNIQUE,
    api_key             VARCHAR(255)        NOT NULL UNIQUE,
    is_active           BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- ============================================================
-- PAYMENTS
-- A payment request initiated by a merchant
-- ============================================================
CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         UUID                NOT NULL REFERENCES merchants(id),
    idempotency_key     VARCHAR(255)        NOT NULL UNIQUE,  -- prevents duplicate payments
    amount              NUMERIC(19, 4)      NOT NULL,
    currency            VARCHAR(10)         NOT NULL,         -- e.g. ETB, USD
    provider            payment_provider    NOT NULL,
    status              payment_status      NOT NULL DEFAULT 'PENDING',
    customer_email      VARCHAR(255),
    customer_name       VARCHAR(255),
    description         TEXT,
    callback_url        VARCHAR(500),                         -- merchant's callback URL
    return_url          VARCHAR(500),                         -- redirect after payment
    metadata            JSONB,                                -- any extra merchant data
    created_at          TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- ============================================================
-- PAYMENT PROVIDER DETAILS
-- Provider-specific IDs and data, linked to a payment
-- ============================================================
CREATE TABLE payment_provider_details (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id              UUID            NOT NULL REFERENCES payments(id),
    provider                payment_provider NOT NULL,
    provider_payment_id     VARCHAR(255),   -- Stripe: payment_intent_id, Chapa: tx_ref
    provider_status         VARCHAR(100),   -- raw status string from provider
    provider_response       JSONB,          -- full raw response from provider
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE (payment_id, provider)
);

-- ============================================================
-- TRANSACTIONS
-- Every attempt to process a payment (including retries)
-- ============================================================
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID                NOT NULL REFERENCES payments(id),
    type                transaction_type    NOT NULL DEFAULT 'PAYMENT',
    amount              NUMERIC(19, 4)      NOT NULL,
    currency            VARCHAR(10)         NOT NULL,
    status              payment_status      NOT NULL,
    provider            payment_provider    NOT NULL,
    provider_tx_id      VARCHAR(255),       -- provider transaction reference
    failure_reason      TEXT,               -- populated if status = FAILED
    created_at          TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- ============================================================
-- WEBHOOK EVENTS
-- Raw webhook payloads received from Stripe and Chapa
-- Processed asynchronously via Kafka
-- ============================================================
CREATE TABLE webhook_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider            payment_provider    NOT NULL,
    event_type          VARCHAR(100)        NOT NULL,   -- e.g. payment.success
    provider_event_id   VARCHAR(255)        NOT NULL,   -- provider's event ID
    payload             JSONB               NOT NULL,   -- raw webhook body
    status              webhook_status      NOT NULL DEFAULT 'RECEIVED',
    failure_reason      TEXT,
    received_at         TIMESTAMP           NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP,
    UNIQUE (provider, provider_event_id)               -- prevent duplicate processing
);

-- ============================================================
-- AUDIT LOGS
-- Immutable record of every status change on a payment
-- ============================================================
CREATE TABLE audit_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID                NOT NULL REFERENCES payments(id),
    previous_status     payment_status,
    new_status          payment_status      NOT NULL,
    changed_by          VARCHAR(100),       -- system, webhook, merchant
    reason              TEXT,
    created_at          TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- ============================================================
-- INDEXES
-- ============================================================
CREATE INDEX idx_payments_merchant_id     ON payments(merchant_id);
CREATE INDEX idx_payments_status          ON payments(status);
CREATE INDEX idx_payments_created_at      ON payments(created_at);
CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);
CREATE INDEX idx_transactions_payment_id  ON transactions(payment_id);
CREATE INDEX idx_webhook_events_provider  ON webhook_events(provider, status);
CREATE INDEX idx_audit_logs_payment_id    ON audit_logs(payment_id);
CREATE INDEX idx_payments_merchant_created ON payments(merchant_id, created_at DESC);