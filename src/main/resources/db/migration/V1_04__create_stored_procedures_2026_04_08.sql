-- =====================================================
-- ACCOUNT STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_account_insert(
    p_merchant_id BIGINT,
    p_account_number VARCHAR(20),
    p_account_type VARCHAR(50),
    p_currency VARCHAR(3),
    p_account_status VARCHAR(50),
    p_created_by BIGINT
) RETURNS BIGINT AS $$
DECLARE
v_id BIGINT;
BEGIN
INSERT INTO accounts (merchant_id, account_number, account_type, currency,
                      balance, ledger_balance, account_status, created_at, updated_at, created_by)
VALUES (p_merchant_id, p_account_number, p_account_type, p_currency,
        0, 0, p_account_status, now(), now(), p_created_by)
    RETURNING id INTO v_id;
RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_account_find_by_id(p_id BIGINT)
RETURNS TABLE(
    id BIGINT, merchant_id BIGINT, account_number VARCHAR, account_type VARCHAR,
    currency VARCHAR, balance NUMERIC, ledger_balance NUMERIC, account_status VARCHAR,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
) AS $$
BEGIN
RETURN QUERY
SELECT a.id, a.merchant_id, a.account_number, a.account_type,
       a.currency, a.balance, a.ledger_balance, a.account_status,
       a.created_at, a.updated_at
FROM accounts a
WHERE a.id = p_id AND a.deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_account_find_by_merchant(
    p_merchant_id BIGINT,
    p_limit INT,
    p_offset INT,
    p_sort_field VARCHAR,
    p_sort_direction VARCHAR
) RETURNS TABLE(
    id BIGINT, merchant_id BIGINT, account_number VARCHAR, account_type VARCHAR,
    currency VARCHAR, balance NUMERIC, ledger_balance NUMERIC, account_status VARCHAR,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, total_count BIGINT
) AS $$
BEGIN
RETURN QUERY EXECUTE format(
        'SELECT a.id, a.merchant_id, a.account_number, a.account_type,
                a.currency, a.balance, a.ledger_balance, a.account_status,
                a.created_at, a.updated_at, COUNT(*) OVER() AS total_count
         FROM accounts a
         WHERE a.merchant_id = $1 AND a.deleted_at IS NULL
         ORDER BY %I %s
         LIMIT $2 OFFSET $3',
        p_sort_field, p_sort_direction
    ) USING p_merchant_id, p_limit, p_offset;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_account_update_status(
    p_id BIGINT,
    p_status VARCHAR(50),
    p_updated_by BIGINT
) RETURNS VOID AS $$
BEGIN
UPDATE accounts SET account_status = p_status, updated_at = now(), updated_by = p_updated_by
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_account_soft_delete(
    p_id BIGINT,
    p_deleted_by BIGINT
) RETURNS VOID AS $$
BEGIN
UPDATE accounts SET deleted_at = now(), deleted_by = p_deleted_by, updated_at = now()
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_account_exists(p_id BIGINT)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(SELECT 1 FROM accounts WHERE id = p_id AND deleted_at IS NULL);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_account_get_merchant_validation(p_merchant_id BIGINT)
RETURNS TABLE(
    id BIGINT, kyc_status VARCHAR, tier VARCHAR, max_accounts INT, current_account_count BIGINT
) AS $$
BEGIN
RETURN QUERY
SELECT m.id, m.kyc_status, m.tier, tc.max_accounts, COUNT(a.id)::BIGINT AS current_account_count
FROM merchants m
         LEFT JOIN tier_configs tc ON tc.tier = m.tier
         LEFT JOIN accounts a ON a.merchant_id = m.id AND a.deleted_at IS NULL
WHERE m.id = p_merchant_id AND m.deleted_at IS NULL
GROUP BY m.id, m.kyc_status, m.tier, tc.max_accounts;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_account_find_merchant_id_by_user_id(p_user_id BIGINT)
RETURNS BIGINT AS $$
DECLARE
v_id BIGINT;
BEGIN
SELECT id INTO v_id FROM merchants WHERE user_id = p_user_id AND deleted_at IS NULL;
RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_account_next_number()
RETURNS VARCHAR AS $$
BEGIN
RETURN nextval('account_number_seq')::VARCHAR;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- USER STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_user_validate_for_create(
    p_email VARCHAR,
    p_phone VARCHAR,
    p_role_id BIGINT
) RETURNS TABLE(email_exists BIGINT, phone_exists BIGINT, role_name VARCHAR) AS $$
BEGIN
RETURN QUERY
SELECT
    (SELECT COUNT(*) FROM users WHERE email = p_email AND deleted_at IS NULL),
    (SELECT COUNT(*) FROM users WHERE phone = p_phone AND deleted_at IS NULL),
    (SELECT r.name FROM roles r WHERE r.id = p_role_id);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_validate_for_update(
    p_id BIGINT,
    p_email VARCHAR,
    p_phone VARCHAR
) RETURNS TABLE(email_exists BIGINT, phone_exists BIGINT) AS $$
BEGIN
RETURN QUERY
SELECT
    (SELECT COUNT(*) FROM users WHERE email = p_email AND deleted_at IS NULL AND id != p_id),
    (SELECT COUNT(*) FROM users WHERE phone = p_phone AND deleted_at IS NULL AND id != p_id);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_insert(
    p_firstname VARCHAR,
    p_lastname VARCHAR,
    p_othername VARCHAR,
    p_email VARCHAR,
    p_phone VARCHAR,
    p_password_hash VARCHAR,
    p_user_status VARCHAR,
    p_role_id BIGINT,
    p_created_by BIGINT,
    p_updated_by BIGINT
) RETURNS BIGINT AS $$
DECLARE
v_id BIGINT;
BEGIN
INSERT INTO users (firstname, lastname, othername, email, phone, password_hash,
                   user_status, role_id, created_by, updated_by, created_at, updated_at)
VALUES (p_firstname, p_lastname, p_othername, p_email, p_phone, p_password_hash,
        p_user_status, p_role_id, p_created_by, p_updated_by, now(), now())
    RETURNING id INTO v_id;
RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_find_by_id(p_id BIGINT)
RETURNS TABLE(
    id BIGINT, firstname VARCHAR, lastname VARCHAR, othername VARCHAR,
    email VARCHAR, phone VARCHAR, role_name VARCHAR, user_status VARCHAR,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
) AS $$
BEGIN
RETURN QUERY
SELECT u.id, u.firstname, u.lastname, u.othername, u.email, u.phone,
       r.name AS role_name, u.user_status, u.created_at, u.updated_at
FROM users u
         JOIN roles r ON r.id = u.role_id
WHERE u.id = p_id AND u.deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_find_all(
    p_limit INT,
    p_offset INT,
    p_sort_field VARCHAR,
    p_sort_direction VARCHAR
) RETURNS TABLE(
    id BIGINT, firstname VARCHAR, lastname VARCHAR, othername VARCHAR,
    email VARCHAR, phone VARCHAR, role_name VARCHAR, user_status VARCHAR,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, total_count BIGINT
) AS $$
BEGIN
RETURN QUERY EXECUTE format(
        'SELECT u.id, u.firstname, u.lastname, u.othername, u.email, u.phone,
                r.name AS role_name, u.user_status, u.created_at, u.updated_at,
                COUNT(*) OVER() AS total_count
         FROM users u
         JOIN roles r ON r.id = u.role_id
         WHERE u.deleted_at IS NULL
         ORDER BY %I %s
         LIMIT $1 OFFSET $2',
        p_sort_field, p_sort_direction
    ) USING p_limit, p_offset;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_update(
    p_id BIGINT,
    p_firstname VARCHAR,
    p_lastname VARCHAR,
    p_othername VARCHAR,
    p_email VARCHAR,
    p_phone VARCHAR
) RETURNS VOID AS $$
BEGIN
UPDATE users SET firstname = p_firstname, lastname = p_lastname, othername = p_othername,
                 email = p_email, phone = p_phone, updated_at = now()
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_update_status(p_id BIGINT, p_status VARCHAR)
RETURNS VOID AS $$
BEGIN
UPDATE users SET user_status = p_status, updated_at = now()
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_update_role(p_id BIGINT, p_role_id BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE users SET role_id = p_role_id, updated_at = now()
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_update_password(p_id BIGINT, p_password_hash VARCHAR)
RETURNS VOID AS $$
BEGIN
UPDATE users SET password_hash = p_password_hash, updated_at = now()
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_soft_delete(p_id BIGINT, p_deleted_by BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE users SET deleted_at = now(), deleted_by = p_deleted_by, updated_at = now()
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_find_role_name_by_id(p_role_id BIGINT)
RETURNS VARCHAR AS $$
DECLARE
v_name VARCHAR;
BEGIN
SELECT name INTO v_name FROM roles WHERE id = p_role_id;
RETURN v_name;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_find_password_hash_by_id(p_id BIGINT)
RETURNS VARCHAR AS $$
DECLARE
v_hash VARCHAR;
BEGIN
SELECT password_hash INTO v_hash FROM users WHERE id = p_id AND deleted_at IS NULL;
RETURN v_hash;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_user_exists_by_id(p_id BIGINT)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(SELECT 1 FROM users WHERE id = p_id AND deleted_at IS NULL);
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- TRANSACTION STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_transaction_insert(
    p_account_id BIGINT,
    p_card_id BIGINT,
    p_transfer_id BIGINT,
    p_type VARCHAR,
    p_amount NUMERIC,
    p_currency VARCHAR,
    p_created_by BIGINT
) RETURNS BIGINT AS $$
DECLARE
v_id BIGINT;
BEGIN
INSERT INTO transactions (account_id, card_id, transfer_id, transaction_type,
                          channel, amount, fee, currency, transaction_status, created_at, updated_at, created_by)
VALUES (p_account_id, p_card_id, p_transfer_id, p_type,
        'TRANSFER', p_amount, 0, p_currency, 'SUCCESS', now(), now(), p_created_by)
    RETURNING id INTO v_id;
RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transaction_find_by_id(p_id BIGINT)
RETURNS TABLE(
    id BIGINT, account_id BIGINT, card_id BIGINT, transfer_id BIGINT,
    transaction_type VARCHAR, channel VARCHAR, amount NUMERIC, fee NUMERIC,
    currency VARCHAR, transaction_status VARCHAR, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
) AS $$
BEGIN
RETURN QUERY
SELECT t.id, t.account_id, t.card_id, t.transfer_id, t.transaction_type, t.channel,
       t.amount, t.fee, t.currency, t.transaction_status, t.created_at, t.updated_at
FROM transactions t WHERE t.id = p_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transaction_find_by_account(
    p_account_id BIGINT,
    p_limit INT,
    p_offset INT
) RETURNS TABLE(
    id BIGINT, account_id BIGINT, card_id BIGINT, transfer_id BIGINT,
    transaction_type VARCHAR, channel VARCHAR, amount NUMERIC, fee NUMERIC,
    currency VARCHAR, transaction_status VARCHAR, created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ, total_count BIGINT
) AS $$
BEGIN
RETURN QUERY
SELECT t.id, t.account_id, t.card_id, t.transfer_id, t.transaction_type, t.channel,
       t.amount, t.fee, t.currency, t.transaction_status, t.created_at, t.updated_at,
       COUNT(*) OVER() AS total_count
FROM transactions t
WHERE t.account_id = p_account_id
ORDER BY t.created_at DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transaction_is_merchant_owner(p_account_id BIGINT, p_user_id BIGINT)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(
    SELECT 1 FROM accounts a
                      JOIN merchants m ON a.merchant_id = m.id
    WHERE a.id = p_account_id AND m.user_id = p_user_id AND a.deleted_at IS NULL
);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transaction_sum_by_criteria(
    p_account_id BIGINT,
    p_type VARCHAR,
    p_status VARCHAR,
    p_from TIMESTAMPTZ,
    p_to TIMESTAMPTZ
) RETURNS NUMERIC AS $$
DECLARE
v_sum NUMERIC;
BEGIN
SELECT COALESCE(SUM(amount), 0) INTO v_sum
FROM transactions
WHERE account_id = p_account_id
  AND transaction_type = p_type
  AND transaction_status = p_status
  AND created_at >= p_from
  AND created_at <= p_to;
RETURN v_sum;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transaction_count_by_transfer_id(p_transfer_id BIGINT)
RETURNS INT AS $$
DECLARE
v_count INT;
BEGIN
SELECT COUNT(*)::INT INTO v_count FROM transactions WHERE transfer_id = p_transfer_id;
RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- TRANSFER STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_transfer_get_validation(
    p_from_account_number VARCHAR,
    p_to_account_number VARCHAR,
    p_reference VARCHAR
) RETURNS TABLE(
    reference_exists INT, from_account_id BIGINT, from_balance NUMERIC,
    from_currency VARCHAR, from_status VARCHAR, to_account_id BIGINT,
    to_currency VARCHAR, to_status VARCHAR, single_transaction_limit NUMERIC,
    daily_transaction_limit NUMERIC, monthly_transaction_limit NUMERIC
) AS $$
BEGIN
RETURN QUERY
SELECT
    (SELECT COUNT(*)::INT FROM transfers WHERE reference = p_reference),
    fa.id, fa.balance, fa.currency, fa.account_status,
    ta.id, ta.currency, ta.account_status,
    tc.single_transaction_limit, tc.daily_transaction_limit, tc.monthly_transaction_limit
FROM accounts fa
         JOIN accounts ta ON ta.account_number = p_to_account_number AND ta.deleted_at IS NULL
         JOIN merchants m ON m.id = fa.merchant_id AND m.deleted_at IS NULL
         JOIN tier_configs tc ON tc.tier = m.tier
WHERE fa.account_number = p_from_account_number AND fa.deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transfer_insert(
    p_reference VARCHAR,
    p_from_account_id BIGINT,
    p_to_account_id BIGINT,
    p_amount NUMERIC,
    p_currency VARCHAR,
    p_description TEXT,
    p_created_by BIGINT
) RETURNS BIGINT AS $$
DECLARE
v_id BIGINT;
BEGIN
INSERT INTO transfers (reference, from_account_id, to_account_id, amount,
                       currency, transfer_status, description, created_at, updated_at, created_by)
VALUES (p_reference, p_from_account_id, p_to_account_id, p_amount,
        p_currency, 'PENDING', p_description, now(), now(), p_created_by)
    RETURNING id INTO v_id;
RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transfer_debit_account(p_id BIGINT, p_amount NUMERIC)
RETURNS VOID AS $$
BEGIN
UPDATE accounts SET balance = balance - p_amount, ledger_balance = ledger_balance - p_amount,
                    updated_at = now() WHERE id = p_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transfer_credit_account(p_id BIGINT, p_amount NUMERIC)
RETURNS VOID AS $$
BEGIN
UPDATE accounts SET balance = balance + p_amount, ledger_balance = ledger_balance + p_amount,
                    updated_at = now() WHERE id = p_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transfer_update_status(p_id BIGINT, p_status VARCHAR)
RETURNS VOID AS $$
BEGIN
UPDATE transfers SET transfer_status = p_status, updated_at = now() WHERE id = p_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transfer_is_merchant_owner(p_account_id BIGINT, p_user_id BIGINT)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(SELECT 1 FROM accounts a JOIN merchants m ON a.merchant_id = m.id WHERE a.id = p_account_id AND m.user_id = p_user_id);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transfer_is_merchant_owner_by_number(p_account_number VARCHAR, p_user_id BIGINT)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(SELECT 1 FROM accounts a JOIN merchants m ON a.merchant_id = m.id WHERE a.account_number = p_account_number AND m.user_id = p_user_id);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transfer_find_by_id(p_id BIGINT)
RETURNS TABLE(
    id BIGINT, reference VARCHAR, from_account_id BIGINT, to_account_id BIGINT,
    amount NUMERIC, currency VARCHAR, transfer_status VARCHAR, description TEXT,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
) AS $$
BEGIN
RETURN QUERY
SELECT t.id, t.reference, t.from_account_id, t.to_account_id, t.amount, t.currency,
       t.transfer_status, t.description, t.created_at, t.updated_at
FROM transfers t WHERE t.id = p_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transfer_find_by_account_with_page(
    p_account_id BIGINT,
    p_limit INT,
    p_offset INT
) RETURNS TABLE(
    id BIGINT, reference VARCHAR, from_account_id BIGINT, to_account_id BIGINT,
    amount NUMERIC, currency VARCHAR, transfer_status VARCHAR, description TEXT,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, total_count BIGINT
) AS $$
BEGIN
RETURN QUERY
SELECT t.id, t.reference, t.from_account_id, t.to_account_id, t.amount, t.currency,
       t.transfer_status, t.description, t.created_at, t.updated_at,
       COUNT(*) OVER() AS total_count
FROM transfers t
WHERE t.from_account_id = p_account_id OR t.to_account_id = p_account_id
ORDER BY t.created_at DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_transfer_find_stuck()
RETURNS TABLE(id BIGINT, from_account_id BIGINT, to_account_id BIGINT, amount NUMERIC) AS $$
BEGIN
RETURN QUERY
SELECT t.id, t.from_account_id, t.to_account_id, t.amount
FROM transfers t
WHERE t.transfer_status = 'PENDING'
  AND t.created_at < NOW() - INTERVAL '300 seconds';
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- CARD STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_card_insert(
    p_card_number VARCHAR,
    p_card_hash VARCHAR,
    p_account_id BIGINT,
    p_card_type VARCHAR,
    p_scheme VARCHAR,
    p_expiry_month SMALLINT,
    p_expiry_year SMALLINT,
    p_card_status VARCHAR,
    p_created_by BIGINT
) RETURNS BIGINT AS $$
DECLARE
v_id BIGINT;
BEGIN
INSERT INTO cards (card_number, card_hash, account_id, card_type, scheme,
                   expiry_month, expiry_year, card_status, created_at, updated_at, created_by)
VALUES (p_card_number, p_card_hash, p_account_id, p_card_type, p_scheme,
        p_expiry_month, p_expiry_year, p_card_status, now(), now(), p_created_by)
    RETURNING id INTO v_id;
RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_card_find_by_id(p_id BIGINT)
RETURNS TABLE(
    id BIGINT, account_id BIGINT, card_number VARCHAR, card_type VARCHAR,
    scheme VARCHAR, expiry_month SMALLINT, expiry_year SMALLINT, card_status VARCHAR,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
) AS $$
BEGIN
RETURN QUERY
SELECT c.id, c.account_id, c.card_number, c.card_type, c.scheme,
       c.expiry_month, c.expiry_year, c.card_status, c.created_at, c.updated_at
FROM cards c WHERE c.id = p_id AND c.deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_card_find_by_account(
    p_account_id BIGINT,
    p_limit INT,
    p_offset INT,
    p_sort_field VARCHAR,
    p_sort_direction VARCHAR
) RETURNS TABLE(
    id BIGINT, account_id BIGINT, card_number VARCHAR, card_type VARCHAR,
    scheme VARCHAR, expiry_month SMALLINT, expiry_year SMALLINT, card_status VARCHAR,
    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, total_count BIGINT
) AS $$
BEGIN
RETURN QUERY EXECUTE format(
        'SELECT c.id, c.account_id, c.card_number, c.card_type, c.scheme,
                c.expiry_month, c.expiry_year, c.card_status, c.created_at, c.updated_at,
                COUNT(*) OVER() AS total_count
         FROM cards c
         WHERE c.account_id = $1 AND c.deleted_at IS NULL
         ORDER BY %I %s
         LIMIT $2 OFFSET $3',
        p_sort_field, p_sort_direction
    ) USING p_account_id, p_limit, p_offset;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_card_exists(p_id BIGINT)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(SELECT 1 FROM cards WHERE id = p_id AND deleted_at IS NULL);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_card_block(p_id BIGINT, p_updated_by BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE cards SET card_status = 'BLOCKED', updated_at = now(), updated_by = p_updated_by
WHERE id = p_id AND deleted_at IS NULL AND card_status != 'BLOCKED';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_card_is_merchant_owner(p_card_id BIGINT, p_user_id BIGINT)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(
    SELECT 1 FROM cards c
                      JOIN accounts a ON c.account_id = a.id
                      JOIN merchants m ON a.merchant_id = m.id
    WHERE c.id = p_card_id AND m.user_id = p_user_id AND c.deleted_at IS NULL
);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_card_validate_merchant_account(p_user_id BIGINT, p_account_id BIGINT)
RETURNS TABLE(merchant_id BIGINT) AS $$
BEGIN
RETURN QUERY
SELECT m.id AS merchant_id
FROM merchants m
         JOIN accounts a ON a.merchant_id = m.id
WHERE m.user_id = p_user_id AND a.id = p_account_id AND m.deleted_at IS NULL AND a.deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_card_get_creation_validation(p_account_id BIGINT, p_card_hash VARCHAR)
RETURNS TABLE(
    account_id BIGINT, merchant_id BIGINT, tier VARCHAR, max_cards INT,
    current_card_count BIGINT, card_hash_exists BOOLEAN
) AS $$
BEGIN
RETURN QUERY
SELECT a.id, m.id, m.tier, tc.max_cards, COUNT(c.id)::BIGINT AS current_card_count,
    EXISTS(SELECT 1 FROM cards WHERE card_hash = p_card_hash AND deleted_at IS NULL) AS card_hash_exists
FROM accounts a
         JOIN merchants m ON a.merchant_id = m.id
         JOIN tier_configs tc ON tc.tier = m.tier
         LEFT JOIN cards c ON c.account_id = a.id AND c.deleted_at IS NULL
WHERE a.id = p_account_id AND a.deleted_at IS NULL
GROUP BY a.id, m.id, m.tier, tc.max_cards;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_card_update_status(p_id BIGINT, p_status VARCHAR, p_updated_by BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE cards SET card_status = p_status, updated_at = now(), updated_by = p_updated_by
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_card_soft_delete(p_id BIGINT, p_deleted_by BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE cards SET deleted_at = now(), deleted_by = p_deleted_by, updated_at = now()
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- MERCHANT STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_merchant_insert(
    p_user_id BIGINT,
    p_address TEXT,
    p_kyc_status VARCHAR,
    p_merchant_status VARCHAR,
    p_tier VARCHAR,
    p_created_by BIGINT
) RETURNS BIGINT AS $$
DECLARE
v_id BIGINT;
BEGIN
INSERT INTO merchants (user_id, address, kyc_status, merchant_status, tier, created_at, updated_at, created_by)
VALUES (p_user_id, p_address, p_kyc_status, p_merchant_status, p_tier, now(), now(), p_created_by)
    RETURNING id INTO v_id;
RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_find_by_id(p_id BIGINT)
RETURNS TABLE(
    id BIGINT, address TEXT, kyc_status VARCHAR, merchant_status VARCHAR, tier VARCHAR,
    user_id BIGINT, firstname VARCHAR, lastname VARCHAR, email VARCHAR, phone VARCHAR,
    user_status VARCHAR, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
) AS $$
BEGIN
RETURN QUERY
SELECT m.id, m.address, m.kyc_status, m.merchant_status, m.tier,
       m.user_id, u.firstname, u.lastname, u.email, u.phone, u.user_status,
       m.created_at, m.updated_at
FROM merchants m
         JOIN users u ON u.id = m.user_id
WHERE m.id = p_id AND m.deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_validate_creation(p_user_id BIGINT, p_tier VARCHAR)
RETURNS TABLE(merchant_exists BOOLEAN, user_exists BOOLEAN, tier_exists BOOLEAN) AS $$
BEGIN
RETURN QUERY
SELECT
    EXISTS(SELECT 1 FROM merchants WHERE user_id = p_user_id AND deleted_at IS NULL),
    EXISTS(SELECT 1 FROM users WHERE id = p_user_id AND deleted_at IS NULL),
    EXISTS(SELECT 1 FROM tier_configs WHERE tier = p_tier);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_find_role_id_by_name(p_name VARCHAR)
RETURNS BIGINT AS $$
DECLARE
v_id BIGINT;
BEGIN
SELECT id INTO v_id FROM roles WHERE name = p_name;
RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_tier_exists(p_tier VARCHAR)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(SELECT 1 FROM tier_configs WHERE tier = p_tier);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_exists(p_id BIGINT)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(SELECT 1 FROM merchants WHERE id = p_id AND deleted_at IS NULL);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_update_address(p_id BIGINT, p_address TEXT, p_updated_by BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE merchants SET address = p_address, updated_at = now(), updated_by = p_updated_by WHERE id = p_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_update_kyc_status(p_id BIGINT, p_kyc_status VARCHAR, p_updated_by BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE merchants SET kyc_status = p_kyc_status, updated_at = now(), updated_by = p_updated_by
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_update_status(p_id BIGINT, p_merchant_status VARCHAR, p_updated_by BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE merchants SET merchant_status = p_merchant_status, updated_at = now(), updated_by = p_updated_by
WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_update_status_and_kyc(
    p_id BIGINT,
    p_merchant_status VARCHAR,
    p_kyc_status VARCHAR,
    p_updated_by BIGINT
) RETURNS VOID AS $$
BEGIN
UPDATE merchants SET merchant_status = p_merchant_status, kyc_status = p_kyc_status,
                     updated_at = now(), updated_by = p_updated_by WHERE id = p_id AND deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_update_tier(p_id BIGINT, p_tier VARCHAR, p_updated_by BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE merchants SET tier = p_tier, updated_at = now(), updated_by = p_updated_by WHERE id = p_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_merchant_soft_delete(p_id BIGINT, p_deleted_by BIGINT)
RETURNS VOID AS $$
BEGIN
UPDATE merchants SET deleted_at = now(), deleted_by = p_deleted_by WHERE id = p_id;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- BLACKLIST STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_blacklist_is_actively_blacklisted(p_merchant_id BIGINT)
RETURNS BOOLEAN AS $$
BEGIN
RETURN EXISTS(SELECT 1 FROM merchant_blacklist WHERE merchant_id = p_merchant_id AND lifted_at IS NULL);
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- FRAUD STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_fraud_insert_attempt(
    p_card_hash VARCHAR,
    p_merchant_id BIGINT,
    p_ip_address VARCHAR,
    p_amount NUMERIC,
    p_currency VARCHAR,
    p_status VARCHAR,
    p_flags TEXT[]
) RETURNS VOID AS $$
BEGIN
INSERT INTO fraud_attempts (card_hash, merchant_id, ip_address, amount, currency, status, flags, created_at)
VALUES (p_card_hash, p_merchant_id, p_ip_address, p_amount, p_currency, p_status, p_flags, now());
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_fraud_get_card_velocity_count(p_card_hash VARCHAR, p_since TIMESTAMPTZ)
RETURNS INT AS $$
DECLARE
v_count INT;
BEGIN
SELECT COUNT(*)::INT INTO v_count FROM fraud_attempts WHERE card_hash = p_card_hash AND created_at >= p_since;
RETURN v_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_fraud_get_merchant_single_limit(p_merchant_id BIGINT)
RETURNS NUMERIC AS $$
DECLARE
v_limit NUMERIC;
BEGIN
SELECT tc.single_transaction_limit INTO v_limit
FROM tier_configs tc
         JOIN merchants m ON m.tier = tc.tier
WHERE m.id = p_merchant_id AND m.deleted_at IS NULL;
RETURN v_limit;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_fraud_get_merchant_single_limit_by_account(p_account_number VARCHAR)
RETURNS NUMERIC AS $$
DECLARE
v_limit NUMERIC;
BEGIN
SELECT tc.single_transaction_limit INTO v_limit
FROM tier_configs tc
         JOIN merchants m ON m.tier = tc.tier
         JOIN accounts a ON a.merchant_id = m.id
WHERE a.account_number = p_account_number AND m.deleted_at IS NULL AND a.deleted_at IS NULL;
RETURN v_limit;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_fraud_get_merchant_id_by_account(p_account_number VARCHAR)
RETURNS BIGINT AS $$
DECLARE
v_id BIGINT;
BEGIN
SELECT m.id INTO v_id
FROM merchants m
         JOIN accounts a ON a.merchant_id = m.id
WHERE a.account_number = p_account_number AND m.deleted_at IS NULL AND a.deleted_at IS NULL;
RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_fraud_get_attempts(p_limit INT, p_offset INT)
RETURNS TABLE(
    id BIGINT, card_hash VARCHAR, merchant_id BIGINT, ip_address VARCHAR,
    amount NUMERIC, currency VARCHAR, status VARCHAR, flags TEXT[], created_at TIMESTAMPTZ
) AS $$
BEGIN
RETURN QUERY
SELECT f.id, f.card_hash, f.merchant_id, f.ip_address, f.amount, f.currency,
       f.status, f.flags, f.created_at
FROM fraud_attempts f
ORDER BY f.created_at DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_fraud_count_attempts()
RETURNS BIGINT AS $$
DECLARE
v_count BIGINT;
BEGIN
SELECT COUNT(*) INTO v_count FROM fraud_attempts;
RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- MAINTENANCE STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_maintenance_find_stuck_transfers()
RETURNS TABLE(id BIGINT, from_account_id BIGINT, to_account_id BIGINT, amount NUMERIC) AS $$
BEGIN
RETURN QUERY
SELECT t.id, t.from_account_id, t.to_account_id, t.amount
FROM transfers t
WHERE t.transfer_status = 'PENDING'
  AND t.created_at < NOW() - INTERVAL '300 seconds';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_maintenance_find_balance_mismatches()
RETURNS TABLE(id BIGINT, stored_balance NUMERIC, calculated_balance NUMERIC) AS $$
BEGIN
RETURN QUERY
SELECT a.id, a.balance AS stored_balance,
       (SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0)
        FROM transactions t WHERE t.account_id = a.id AND t.transaction_status = 'SUCCESS') AS calculated_balance
FROM accounts a
WHERE a.deleted_at IS NULL
GROUP BY a.id, a.balance
HAVING a.balance <> (
    SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0)
    FROM transactions t WHERE t.account_id = a.id AND t.transaction_status = 'SUCCESS'
);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_maintenance_recalculate_all_balances()
RETURNS VOID AS $$
BEGIN
UPDATE accounts a
SET balance = (
    SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0)
    FROM transactions t WHERE t.account_id = a.id AND t.transaction_status = 'SUCCESS'
),
    ledger_balance = (
        SELECT COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0)
        FROM transactions t WHERE t.account_id = a.id AND t.transaction_status IN ('SUCCESS', 'PENDING')
    ),
    updated_at = NOW()
WHERE a.deleted_at IS NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_maintenance_bulk_resolve_successful_transfers()
RETURNS INT AS $$
DECLARE
v_count INT;
BEGIN
WITH updated AS (
UPDATE transfers
SET transfer_status = 'SUCCESS', updated_at = NOW()
WHERE transfer_status = 'PENDING'
  AND created_at < NOW() - INTERVAL '5 minutes'
  AND (SELECT COUNT(*) FROM transactions WHERE transfer_id = transfers.id) >= 2
    RETURNING 1
    )
SELECT COUNT(*)::INT INTO v_count FROM updated;
RETURN v_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sp_maintenance_bulk_resolve_failed_transfers()
RETURNS INT AS $$
DECLARE
v_count INT;
BEGIN
WITH updated AS (
UPDATE transfers
SET transfer_status = 'FAILED', updated_at = NOW()
WHERE transfer_status = 'PENDING'
  AND created_at < NOW() - INTERVAL '5 minutes'
  AND (SELECT COUNT(*) FROM transactions WHERE transfer_id = transfers.id) < 2
    RETURNING 1
    )
SELECT COUNT(*)::INT INTO v_count FROM updated;
RETURN v_count;
END;
$$ LANGUAGE plpgsql;
