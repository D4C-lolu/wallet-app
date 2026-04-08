-- =====================================================
-- FRAUD CONSEQUENCE STORED PROCEDURES
-- =====================================================

-- Block a card by its hash
CREATE OR REPLACE FUNCTION sp_card_block_by_hash(p_card_hash VARCHAR)
RETURNS TABLE(card_id BIGINT, was_blocked BOOLEAN) AS $$
DECLARE
    v_card_id BIGINT;
    v_was_blocked BOOLEAN := FALSE;
BEGIN
    SELECT c.id INTO v_card_id
    FROM cards c
    WHERE c.card_hash = p_card_hash
    AND c.deleted_at IS NULL
    AND c.card_status != 'BLOCKED';

    IF v_card_id IS NOT NULL THEN
        UPDATE cards
        SET card_status = 'BLOCKED', updated_at = NOW()
        WHERE id = v_card_id;
        v_was_blocked := TRUE;
    END IF;

    RETURN QUERY SELECT v_card_id, v_was_blocked;
END;
$$ LANGUAGE plpgsql;

-- Get card ID by hash
CREATE OR REPLACE FUNCTION sp_card_find_id_by_hash(p_card_hash VARCHAR)
RETURNS BIGINT AS $$
DECLARE
    v_id BIGINT;
BEGIN
    SELECT c.id INTO v_id
    FROM cards c
    WHERE c.card_hash = p_card_hash
    AND c.deleted_at IS NULL;
    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

-- Get merchant email by merchant ID
CREATE OR REPLACE FUNCTION sp_merchant_get_email(p_merchant_id BIGINT)
RETURNS VARCHAR AS $$
DECLARE
    v_email VARCHAR;
BEGIN
    SELECT u.email INTO v_email
    FROM merchants m
    JOIN users u ON u.id = m.user_id
    WHERE m.id = p_merchant_id
    AND m.deleted_at IS NULL
    AND u.deleted_at IS NULL;
    RETURN v_email;
END;
$$ LANGUAGE plpgsql;

-- Get merchant email by account number
CREATE OR REPLACE FUNCTION sp_merchant_get_email_by_account(p_account_number VARCHAR)
RETURNS VARCHAR AS $$
DECLARE
    v_email VARCHAR;
BEGIN
    SELECT u.email INTO v_email
    FROM accounts a
    JOIN merchants m ON m.id = a.merchant_id
    JOIN users u ON u.id = m.user_id
    WHERE a.account_number = p_account_number
    AND a.deleted_at IS NULL
    AND m.deleted_at IS NULL
    AND u.deleted_at IS NULL;
    RETURN v_email;
END;
$$ LANGUAGE plpgsql;

-- Get merchant name by account number (for email personalization)
CREATE OR REPLACE FUNCTION sp_merchant_get_name_by_account(p_account_number VARCHAR)
RETURNS VARCHAR AS $$
DECLARE
    v_name VARCHAR;
BEGIN
    SELECT CONCAT(u.firstname, ' ', u.lastname) INTO v_name
    FROM accounts a
    JOIN merchants m ON m.id = a.merchant_id
    JOIN users u ON u.id = m.user_id
    WHERE a.account_number = p_account_number
    AND a.deleted_at IS NULL
    AND m.deleted_at IS NULL
    AND u.deleted_at IS NULL;
    RETURN v_name;
END;
$$ LANGUAGE plpgsql;

-- Check if a card exists and is active by hash
CREATE OR REPLACE FUNCTION sp_card_is_active_by_hash(p_card_hash VARCHAR)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS(
        SELECT 1 FROM cards
        WHERE card_hash = p_card_hash
        AND deleted_at IS NULL
        AND card_status = 'ACTIVE'
    );
END;
$$ LANGUAGE plpgsql;
