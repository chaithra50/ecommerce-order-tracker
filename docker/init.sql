-- ─── Database Init Script ────────────────────────────────────────────────────
-- Runs automatically when Postgres container starts for the first time

--CREATE DATABASE orderdb;

\c orderdb;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'ROLE_CUSTOMER',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Orders table
CREATE TABLE IF NOT EXISTS orders (
    id               BIGSERIAL PRIMARY KEY,
    order_number     VARCHAR(50)    NOT NULL UNIQUE,
    user_id          BIGINT         NOT NULL REFERENCES users(id),
    status           VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    total_amount     NUMERIC(10,2)  NOT NULL,
    shipping_address VARCHAR(500),
    tracking_number  VARCHAR(255),
    status_note      VARCHAR(500),
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Order items table
CREATE TABLE IF NOT EXISTS order_items (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_name  VARCHAR(255)   NOT NULL,
    product_sku   VARCHAR(100)   NOT NULL,
    quantity      INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(10,2)  NOT NULL,
    subtotal      NUMERIC(10,2)  NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_orders_user_id    ON orders(user_id);
CREATE INDEX idx_orders_status     ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX idx_order_items_order ON order_items(order_id);

-- Seed: admin user (password = Admin@1234 BCrypt encoded)
INSERT INTO users (email, password, full_name, phone, role) VALUES
(
    'admin@ordertracker.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeAhMD.Y9MgOVUICe',
    'System Admin',
    '+919876543210',
    'ROLE_ADMIN'
)
ON CONFLICT (email) DO NOTHING;

-- Seed: test customer (password = Customer@1234 BCrypt encoded)
INSERT INTO users (email, password, full_name, phone, role) VALUES
(
    'customer@test.com',
    '$2a$12$eImiTXuWVxfM37uY4JANjQ==',
    'Test Customer',
    '+919123456789',
    'ROLE_CUSTOMER'
)
ON CONFLICT (email) DO NOTHING;
