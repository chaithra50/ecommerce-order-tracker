-- Admin user
MERGE INTO users (
    email,
    password,
    full_name,
    phone,
    role
)
KEY(email)
VALUES (
    'admin@ordertracker.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeAhMD.Y9MgOVUICe',
    'System Admin',
    '+919876543210',
    'ROLE_ADMIN'
);

-- Sample customer
MERGE INTO users (
    email,
    password,
    full_name,
    phone,
    role
)
KEY(email)
VALUES (
    'customer@ordertracker.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LeAhMD.Y9MgOVUICe',
    'Sample Customer',
    '+919999999999',
    'ROLE_CUSTOMER'
);