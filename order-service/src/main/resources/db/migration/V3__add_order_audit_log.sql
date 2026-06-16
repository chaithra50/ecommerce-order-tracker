-- ─────────────────────────────────────────────────────────────────────────────
-- V3__add_order_audit_log.sql
-- Tracks every status change for compliance and debugging
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS order_audit_log (
    id           BIGSERIAL    PRIMARY KEY,
    order_id     BIGINT       NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    old_status   VARCHAR(30),
    new_status   VARCHAR(30)  NOT NULL,
    changed_by   VARCHAR(100) NOT NULL,  -- email of user who made the change
    note         VARCHAR(500),
    changed_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_order_id   ON order_audit_log(order_id);
CREATE INDEX idx_audit_changed_at ON order_audit_log(changed_at DESC);
