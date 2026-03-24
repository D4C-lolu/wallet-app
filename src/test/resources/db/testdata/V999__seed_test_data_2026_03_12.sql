-- test users (can bulk insert since role_id is the only FK and they share the same role values)
INSERT INTO public.users (
    firstname, lastname, email, phone,
    password_hash, user_status, role_id,
    created_at, updated_at
) VALUES
      (
          'Test', 'Admin',
          'testadmin@verveguard.com',
          '11111111111',
          crypt('Admin123!', gen_salt('bf', 10)),
          'ACTIVE',
          (SELECT id FROM public.roles WHERE name = 'ADMIN'),
          now(), now()
      ),
      (
          'Test', 'Merchant',
          'testmerchant@verveguard.com',
          '22222222222',
          crypt('Admin123!', gen_salt('bf', 10)),
          'ACTIVE',
          (SELECT id FROM public.roles WHERE name = 'MERCHANT'),
          now(), now()
      ),
      (
          'Suspended', 'User',
          'suspended@verveguard.com',
          '33333333333',
          crypt('Admin123!', gen_salt('bf', 10)),
          'SUSPENDED',
          (SELECT id FROM public.roles WHERE name = 'MERCHANT'),
          now(), now()
      ),
      (
          'Deleted', 'User',
          'deleted@verveguard.com',
          '44444444444',
          crypt('Admin123!', gen_salt('bf', 10)),
          'INACTIVE',
          (SELECT id FROM public.roles WHERE name = 'MERCHANT'),
          now(), now()
      ),
      (
          'Test', 'Merchant2',
          'testmerchant2@verveguard.com',
          '444114444444',
          crypt('Admin123!', gen_salt('bf', 10)),
          'ACTIVE',
          (SELECT id FROM public.roles WHERE name = 'MERCHANT'),
          now(), now()
      );

INSERT INTO public.merchants (
    user_id, address, kyc_status, merchant_status, tier,
    created_at, updated_at
) VALUES (
             (SELECT id FROM public.users WHERE email = 'testmerchant2@verveguard.com'),
             '2 Test Street, Lagos',
             'PENDING',
             'INACTIVE',
             'TIER_1',
             now(), now()
         );

INSERT INTO public.merchant_blacklist (
    merchant_id, reason, blacklisted_at
) VALUES (
             (SELECT id FROM public.merchants WHERE user_id = (
                 SELECT id FROM public.users WHERE email = 'testmerchant2@verveguard.com'
             )),
             'Fraudulent activity detected',
             now()
         );

INSERT INTO public.accounts (
    merchant_id, account_number, account_type,
    currency, balance, ledger_balance, account_status,
    created_at, updated_at
) VALUES (
             (SELECT id FROM public.merchants WHERE user_id = (
                 SELECT id FROM public.users WHERE email = 'demo.merchant@verveguard.com'
             )),
             '1100000002',
             'SETTLEMENT',
             'NGN',
             0.0000,
             0.0000,
             'ACTIVE',
             now(), now()
         );