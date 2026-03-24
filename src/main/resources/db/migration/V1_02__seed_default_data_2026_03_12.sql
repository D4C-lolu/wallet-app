CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- roles
INSERT INTO public.roles (name) VALUES
    ('SUPER_ADMIN'),
    ('ADMIN'),
    ('MERCHANT');

-- permissions
INSERT INTO public.permissions (name, description) VALUES
    -- user management
    ( 'user:read',           'View users'),
    ( 'user:create',         'Create users'),
    ( 'user:update',         'Update users'),
    ( 'user:delete',         'Delete users'),

    -- merchant management
    ( 'merchant:read',       'View merchants'),
    ( 'merchant:create',     'Create merchants'),
    ( 'merchant:update',     'Update merchants'),
    ( 'merchant:delete',     'Delete merchants'),
    ( 'merchant:kyc',        'Manage merchant KYC'),
    ( 'merchant:blacklist',  'Blacklist merchants'),

    -- account management
    ( 'account:read',        'View accounts'),
    ( 'account:create',      'Create accounts'),
    ( 'account:update',      'Update accounts'),
    ( 'account:delete',      'Delete accounts'),

    -- card management
    ( 'card:read',           'View cards'),
    ( 'card:create',         'Create cards'),
    ( 'card:update',         'Update cards'),
    ( 'card:delete',         'Delete cards'),
    ( 'card:block',          'Block cards'),

    -- transaction management
    ( 'transaction:read',    'View transactions'),
    ( 'transaction:create',  'Create transactions'),
    ( 'transaction:reverse', 'Reverse transactions'),

    -- transfer management
    ( 'transfer:read',       'View transfers'),
    ( 'transfer:create',     'Create transfers'),
    ( 'transfer:reverse',    'Reverse transfers'),

    -- tier management
    ( 'tier:read',           'View tier config'),
    ( 'tier:update',         'Update tier config'),

    -- role & permission management
    ( 'role:read',           'View roles'),
    ( 'role:create',         'Create roles'),
    ( 'role:update',         'Update roles'),
    ( 'role:delete',         'Delete roles'),
    ( 'permission:read',     'View permissions'),
    ( 'permission:assign',   'Assign permissions to roles'),

    -- Actuator endpoints
    ( 'system:monitor', 'Access actuator endpoints');

-- default super admin user
-- SUPER_ADMIN gets everything
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT (SELECT id FROM public.roles WHERE name = 'SUPER_ADMIN'), id FROM public.permissions;

-- ADMIN gets everything except role/permission management and blacklisting
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT (SELECT id FROM public.roles WHERE name = 'ADMIN'), id FROM public.permissions
WHERE name NOT IN (
                   'role:create', 'role:delete',
                   'permission:assign',
                   'merchant:blacklist'
    );

-- MERCHANT gets read/transact on their own resources only
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT (SELECT id FROM public.roles WHERE name = 'MERCHANT'), id FROM public.permissions
WHERE name IN (
               'account:read', 'account:create',
               'card:read', 'card:create', 'card:block',
               'transaction:read', 'transaction:create',
               'transfer:read', 'transfer:create'
    );

-- super admin user
INSERT INTO public.users (
    firstname, lastname, email, phone,
    password_hash, user_status, role_id,
    created_at, updated_at
) VALUES (
             'Super', 'Admin',
             'superadmin@verveguard.com',
             '00000000000',
             crypt('Admin123!', gen_salt('bf', 10)),
             'ACTIVE',
             (SELECT id FROM public.roles WHERE name = 'SUPER_ADMIN'),
             now(), now()
         );

INSERT INTO public.tier_config (
    tier,
    daily_transaction_limit,
    single_transaction_limit,
    monthly_transaction_limit,
    max_cards, max_accounts,
    created_at, updated_at
) VALUES
      (
          'TIER_1',
          100000.0000, 10000.0000, 1000000.0000,
          2, 1,
          now(), now()
      ),
      (
          'TIER_2',
          500000.0000, 50000.0000, 5000000.0000,
          5, 3,
          now(), now()
      ),
      (
          'TIER_3',
          2000000.0000, 200000.0000, 20000000.0000,
          10, 5,
          now(), now()
      );

-- test merchant user
INSERT INTO public.users (
    firstname, lastname, email, phone,
    password_hash, user_status, role_id,
    created_at, updated_at
) VALUES (
             'Demo', 'Merchant',
             'demo.merchant@verveguard.com',
             '11111111112',
             crypt('Admin123!', gen_salt('bf', 10)),
             'ACTIVE',
             (SELECT id FROM public.roles WHERE name = 'MERCHANT'),
             now(), now()
         );

-- demo merchant
INSERT INTO public.merchants (
    user_id, address, kyc_status, merchant_status, tier,
    created_at, updated_at
) VALUES (
             (SELECT id FROM public.users WHERE email = 'demo.merchant@verveguard.com'),
             '1 Demo Street, Lagos',
             'APPROVED',
             'ACTIVE',
             'TIER_2',
             now(), now()
         );

-- demo account with funds
INSERT INTO public.accounts (
    merchant_id, account_number, account_type,
    currency, balance, ledger_balance, account_status,
    created_at, updated_at
) VALUES (
             (SELECT id FROM public.merchants WHERE user_id = (
                 SELECT id FROM public.users WHERE email = 'demo.merchant@verveguard.com'
             )),
             '0000000000',
             'SETTLEMENT',
             'NGN',
             500000000.0000,
             500000000.0000,
             'ACTIVE',
             now(), now()
         );

-- demo card linked to account
INSERT INTO public.cards (
    card_number, card_hash, account_id, card_type, scheme,
    expiry_month, expiry_year, card_status,
    created_at, updated_at
) VALUES (
             '4011********1111',
             encode(digest('4011111111111111', 'sha256'), 'hex'),
             (SELECT id FROM public.accounts WHERE account_number = '0000000000'),
             'VIRTUAL',
             'VISA',
             12, 2028,
             'ACTIVE',
             now(), now()
         );