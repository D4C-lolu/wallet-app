CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    i INT;
    user_role_id BIGINT;
    new_user_id BIGINT;
    new_merchant_id BIGINT;
    source_account_id BIGINT;
    raw_pan TEXT;
    masked_pan TEXT;
    pan_hash TEXT;
BEGIN
    SELECT id INTO user_role_id FROM roles WHERE name = 'USER';

    FOR i IN 1..200 LOOP
        -- e.g. 4000000000000001, 4000000000000002, ...
        raw_pan    := '4' || LPAD(i::TEXT, 15, '0');
        masked_pan := LEFT(raw_pan, 4) || '********' || RIGHT(raw_pan, 4);
        pan_hash   := encode(digest(raw_pan, 'sha256'), 'hex');

        INSERT INTO users (
            firstname, lastname, email, phone,
            password_hash, user_status, role_id,
            created_at, updated_at
        ) VALUES (
            'Stress',
            'Merchant' || i,
            'merchant' || i || '@stresstest.com',
            '5' || LPAD(i::TEXT, 10, '0'),
            '$2a$10$mRxdXj7YESYMm/DIxn9X9OVXykdCww6ZEUX9a1jrOY2GildUkm4Ji',
            'ACTIVE',
            user_role_id,
            NOW(), NOW()
        ) RETURNING id INTO new_user_id;

        INSERT INTO merchants (
            user_id, address, kyc_status, merchant_status, tier,
            created_at, updated_at
        ) VALUES (
            new_user_id,
            i || ' Stress Test Street, Lagos',
            'APPROVED', 'ACTIVE', 'TIER_3',
            NOW(), NOW()
        ) RETURNING id INTO new_merchant_id;

        INSERT INTO accounts (
            merchant_id, account_number, account_type,
            currency, balance, ledger_balance, account_status,
            created_at, updated_at
        ) VALUES (
            new_merchant_id,
            '33' || LPAD(i::TEXT, 8, '0'),
            'SETTLEMENT', 'NGN',
            100000000.0000, 100000000.0000, 'ACTIVE',
            NOW(), NOW()
        ) RETURNING id INTO source_account_id;

        INSERT INTO accounts (
            merchant_id, account_number, account_type,
            currency, balance, ledger_balance, account_status,
            created_at, updated_at
        ) VALUES (
            new_merchant_id,
            '33' || LPAD((i + 200)::TEXT, 8, '0'),
            'SETTLEMENT', 'NGN',
            0.0000, 0.0000, 'ACTIVE',
            NOW(), NOW()
        );

        INSERT INTO cards (
            card_number, card_hash, account_id,
            card_type, scheme,
            expiry_month, expiry_year, card_status,
            created_at, updated_at
        ) VALUES (
            masked_pan,
            pan_hash,
            source_account_id,
            'VIRTUAL', 'VISA',
            12, 2028, 'ACTIVE',
            NOW(), NOW()
        );

    END LOOP;
END $$;