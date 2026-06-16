-- ─────────────────────────────────────────────────────────────────────────────
-- V2__seed_data.sql
-- Default admin user and sample customer for development/testing
-- Passwords are BCrypt-encoded with strength 12
-- ─────────────────────────────────────────────────────────────────────────────

-- Admin user  (password: Admin@1234)
INSERT INTO users (email, password, full_name, phone, role)
VALUES (
    'admin@ordertracker.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeAhMD.Y9MgOVUICe',
    'System Admin',
    '+919876543210',
    'ROLE_ADMIN'
)
ON CONFLICT (email) DO NOTHING;

-- Sample customer  (password: Customer@1234)
INSERT INTO users (email, password, full_name, phone, role)
VALUES (
    'customer@test.com',
    '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Test Customer',
    '+919123456789',
    'ROLE_CUSTOMER'
)
ON CONFLICT (email) DO NOTHING;
