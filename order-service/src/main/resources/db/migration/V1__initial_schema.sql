-- ─────────────────────────────────────────────────────────────────────────────
-- V1__initial_schema.sql
-- Initial database schema for E-Commerce Order Tracking System
-- ─────────────────────────────────────────────────────────────────────────────

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL       PRIMARY KEY,
    email       VARCHAR(100)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    full_name   VARCHAR(100)    NOT NULL,
    phone       VARCHAR(20)     NOT NULL,
    role        VARCHAR(20)     NOT NULL DEFAULT 'ROLE_CUSTOMER',
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_role CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_ADMIN'))
);

-- Orders table
CREATE TABLE IF NOT EXISTS orders (
    id               BIGSERIAL       PRIMARY KEY,
    order_number     VARCHAR(50)     NOT NULL UNIQUE,
    user_id          BIGINT          NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status           VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    total_amount     NUMERIC(10, 2)  NOT NULL CHECK (total_amount >= 0),
    shipping_address VARCHAR(500),
    tracking_number  VARCHAR(255),
    status_note      VARCHAR(500),
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_status CHECK (status IN (
        'PENDING', 'CONFIRMED', 'PAYMENT_PROCESSING', 'PAYMENT_FAILED',
        'PREPARING', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED',
        'CANCELLED', 'REFUNDED'
    ))
);

-- Order items table
CREATE TABLE IF NOT EXISTS order_items (
    id            BIGSERIAL       PRIMARY KEY,
    order_id      BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_name  VARCHAR(255)    NOT NULL,
    product_sku   VARCHAR(100)    NOT NULL,
    quantity      INTEGER         NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(10, 2)  NOT NULL CHECK (unit_price > 0),
    subtotal      NUMERIC(10, 2)  NOT NULL CHECK (subtotal > 0)
);

-- ─── Indexes ──────────────────────────────────────────────────────────────────

CREATE INDEX idx_orders_user_id    ON orders(user_id);
CREATE INDEX idx_orders_status     ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_users_email       ON users(email);
