-- Combined fraud evaluation data query
-- Returns all fraud-related data in a single call for efficiency

CREATE OR REPLACE FUNCTION sp_fraud_get_evaluation_data(
    p_account_number VARCHAR,
    p_card_hash VARCHAR,
    p_since TIMESTAMPTZ
)
RETURNS TABLE (
    is_blacklisted BOOLEAN,
    velocity_count INTEGER,
    transaction_limit NUMERIC,
    merchant_id BIGINT
) AS $$
BEGIN
RETURN QUERY
    WITH account_merchant AS (
        SELECT a.merchant_id as mid
        FROM accounts a
        WHERE a.account_number = p_account_number
        LIMIT 1
    )
SELECT
    -- Blacklist check: is merchant actively blacklisted?
    COALESCE((
                 SELECT EXISTS (
                     SELECT 1
                     FROM merchant_blacklist mb
                     WHERE mb.merchant_id = am.mid
                       AND mb.lifted_at IS NULL
                 )
             ), false) AS is_blacklisted,

    -- Velocity count: transactions from this card hash since window start
    COALESCE((
                 SELECT COUNT(*)::INTEGER
                 FROM fraud_attempts fa
                 WHERE fa.card_hash = p_card_hash
                   AND fa.created_at >= p_since
             ), 0) AS velocity_count,

    -- Transaction limit for this merchant (via tier_configs)
    (
        SELECT tc.single_transaction_limit
        FROM merchants m
        JOIN tier_configs tc ON tc.tier = m.tier
        WHERE m.id = am.mid
    ) AS transaction_limit,

    -- Merchant ID for logging
    am.mid AS merchant_id
FROM account_merchant am;
END;
$$ LANGUAGE plpgsql;
