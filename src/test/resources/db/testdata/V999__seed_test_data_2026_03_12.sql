-- V99__seed_test_data_2026_03_12.sql

-- test users
INSERT INTO users (
    firstname, lastname, email, phone,
    password_hash, user_status, role_id,
    created_at, updated_at
) VALUES
      -- admin user
      ( 'Test', 'Admin', 'testadmin@verveguard.com', '11111111111',
       '$2a$10$mRxdXj7YESYMm/DIxn9X9OVXykdCww6ZEUX9a1jrOY2GildUkm4Ji', 'ACTIVE', 2, now(), now()),
      -- merchant user
       ('Test', 'Merchant', 'testmerchant@verveguard.com', '22222222222',
       '$2a$10$mRxdXj7YESYMm/DIxn9X9OVXykdCww6ZEUX9a1jrOY2GildUkm4Ji', 'ACTIVE', 3, now(), now()),
      -- suspended user
      ('Suspended', 'User', 'suspended@verveguard.com', '33333333333',
       '$2a$10$mRxdXj7YESYMm/DIxn9X9OVXykdCww6ZEUX9a1jrOY2GildUkm4Ji', 'SUSPENDED', 3, now(), now()),
      -- deleted user
      ('Deleted', 'User', 'deleted@verveguard.com', '44444444444',
       '$2a$10$mRxdXj7YESYMm/DIxn9X9OVXykdCww6ZEUX9a1jrOY2GildUkm4Ji', 'INACTIVE', 3, now(), now()),
      -- second merchant user
      ('Test', 'Merchant2', 'testmerchant2@verveguard.com', '444114444444',
       '$2a$10$mRxdXj7YESYMm/DIxn9X9OVXykdCww6ZEUX9a1jrOY2GildUkm4Ji', 'ACTIVE', 3, now(), now()),
      ('Test', 'Merchant3', 'testmerchant3@verveguard.com', '444114444445',
       '$2a$10$mRxdXj7YESYMm/DIxn9X9OVXykdCww6ZEUX9a1jrOY2GildUkm4Ji', 'ACTIVE', 3, now(), now());



INSERT INTO merchants (
    user_id,
    address,
    kyc_status,
    merchant_status,
    tier,
    created_at,
    updated_at
)
SELECT
    id,
    '2 Test Street, Lagos',
    'APPROVED',
    'ACTIVE',
    'TIER_1',
    NOW(),
    NOW()
FROM users
WHERE email = 'testadmin@verveguard.com';

INSERT INTO merchants (
    user_id,
    address,
    kyc_status,
    merchant_status,
    tier,
    created_at,
    updated_at
)
SELECT
    id,
    '5 Test Street, Lagos',
    'APPROVED',
    'ACTIVE',
    'TIER_1',
    NOW(),
    NOW()
FROM users
WHERE email = 'testmerchant@verveguard.com';


INSERT INTO merchants (
    user_id,
    address,
    kyc_status,
    merchant_status,
    tier,
    created_at,
    updated_at
)
SELECT
    id,
    '3 Test Street, Lagos',
    'PENDING',
    'ACTIVE',
    'TIER_1',
    NOW(),
    NOW()
FROM users
WHERE email = 'testmerchant2@verveguard.com';

INSERT INTO accounts (
     merchant_id, account_number, account_type,
    currency, balance, ledger_balance, account_status,
    created_at, updated_at
) VALUES
      ( 1, '1100000001', 'SETTLEMENT', 'NGN', 10000000.0000, 10000000.0000, 'ACTIVE', now(), now()),
      ( 1, '1100000002', 'SETTLEMENT', 'NGN', 0.0000, 0.0000, 'ACTIVE', now(), now());