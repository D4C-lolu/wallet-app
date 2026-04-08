# VerveGuard API Documentation

**VerveGuard** is a high-performance financial middleware and merchant management platform. It provides a secure, tiered infrastructure for handling merchant lifecycles, card issuance, account management, fund transfers, and granular role-based access control (RBAC).

---

## 🏗 Project Architecture

The project follows a **Layered Architecture**, ensuring high performance and strict data integrity.

- **Controller Layer:** Handles HTTP requests, input validation (`@Valid`), and method-level security (`@PreAuthorize`).
- **Service Layer:** Contains core business logic, coordinates transactions (`@Transactional`), and performs multi-step validations (e.g., checking KYC status before a tier upgrade or account creation).
- **DAO (Data Access Object) Layer:** Direct database interaction using `NamedParameterJdbcTemplate`. This avoids ORM overhead while providing precise control over SQL execution and auditing.
- **Security:** Uses a granular Permission-based RBAC system. Authorities are mapped to Roles, and Roles are assigned to Users. JWT Bearer tokens are required on all private endpoints.

---

## 💳 The Merchant Tier System

VerveGuard operates on a progressive trust model with three distinct levels. Movement between tiers is governed by `TierConfig` and KYC verification status.

1. **TIER_1:** Initial entry level with restricted transaction and account limits.
2. **TIER_2:** Intermediate level. Requires `KYC_APPROVED` status.
3. **TIER_3:** Premium level with maximum transaction limits.

**Logic Flow for Upgrades:**
- Validate current KYC status via `MerchantService`.
- Check for the existence of the next tier configuration in `tier_configs`.
- Update the merchant record and log the `updated_by` auditor ID from `SecurityUtil`.

---

## 📡 API Reference

> All endpoints are prefixed with `/api/v1/`

### 1. Authentication (`/api/v1/auth`)
*Token issuance and session management.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/auth/login` | Authenticate and receive a JWT Bearer token. | *PermitAll* |
| **POST** | `/api/v1/auth/refresh` | Refresh an expired access token. | *PermitAll* |

---

### 2. User Management (`/api/v1/users`)
*System identity management, role assignments, and credential updates.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/users` | Create a new system user. | `USER_CREATE` |
| **GET** | `/api/v1/users` | Paginated list of all users. | `USER_READ` |
| **GET** | `/api/v1/users/{userId}` | Fetch a user's full profile by ID. | `USER_READ` |
| **PUT** | `/api/v1/users/{userId}` | Update user profile details. | `USER_UPDATE` |
| **PATCH** | `/api/v1/users/{userId}/status` | Activate or suspend a user account. | `USER_UPDATE` |
| **PATCH** | `/api/v1/users/{userId}/role` | Reassign a user's system role. | `USER_UPDATE` |
| **PATCH** | `/api/v1/users/{userId}/password` | Change account credentials. | *Owner* |
| **DELETE** | `/api/v1/users/{userId}` | Soft delete a user account. | `USER_DELETE` |

---

### 3. Merchant Management (`/api/v1/merchants`)
*Full lifecycle management for merchant entities, from registration through KYC and tier progression.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/merchants` | Admin-initiated merchant profile creation. | `MERCHANT_CREATE` |
| **POST** | `/api/v1/merchants/register` | **Public** signup for new users registering as merchants. | *PermitAll* |
| **POST** | `/api/v1/merchants/self-register` | Existing authenticated user promotes account to merchant. | *Authenticated* |
| **GET** | `/api/v1/merchants` | Paginated list of all merchants. | `MERCHANT_READ` |
| **GET** | `/api/v1/merchants/status` | Filter merchants by account status. | `MERCHANT_READ` |
| **GET** | `/api/v1/merchants/kyc-status` | Filter merchants by KYC verification level. | `MERCHANT_READ` |
| **GET** | `/api/v1/merchants/{merchantId}` | Fetch full merchant profile by ID. | `MERCHANT_READ` |
| **PUT** | `/api/v1/merchants/{merchantId}` | Update core merchant profile information. | `MERCHANT_UPDATE` |
| **PATCH** | `/api/v1/merchants/{merchantId}/status` | Activate or suspend a merchant account. | `MERCHANT_UPDATE` |
| **PATCH** | `/api/v1/merchants/{merchantId}/administrative-status` | Override both merchant and KYC status simultaneously. | `ADMIN` / `SUPER_ADMIN` |
| **PATCH** | `/api/v1/merchants/{merchantId}/kyc` | Approve or reject KYC documents. | `MERCHANT_KYC` |
| **PATCH** | `/api/v1/merchants/{merchantId}/tier/upgrade` | Advance merchant to the next tier (KYC-gated). | `MERCHANT_UPDATE` |
| **PATCH** | `/api/v1/merchants/{merchantId}/tier/downgrade` | Reduce merchant to the previous tier. | `MERCHANT_UPDATE` |
| **DELETE** | `/api/v1/merchants/{merchantId}` | Soft delete a merchant record. | `MERCHANT_DELETE` |

---

### 4. Account Management (`/api/v1/accounts`)
*Merchant wallet and escrow account lifecycle, balance tracking, and status control.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/accounts` | Admin creates an account for a specific merchant. | `ADMIN` / `SUPER_ADMIN` |
| **POST** | `/api/v1/accounts/me` | User opens their own account (tier limit enforced). | `USER` |
| **GET** | `/api/v1/accounts/{accountId}` | Fetch account balance and status by ID. | `ACCOUNT_READ` |
| **GET** | `/api/v1/accounts/merchant/{merchantId}` | Paginated list of all accounts for a merchant. | `ACCOUNT_READ` |
| **PATCH** | `/api/v1/accounts/{accountId}/status` | Freeze, suspend, or activate an account. | `ACCOUNT_UPDATE` |
| **DELETE** | `/api/v1/accounts/{accountId}` | Soft delete an account. | `ACCOUNT_DELETE` |

---

### 5. Card Management (`/api/v1/cards`)
*Issuance and lifecycle management for virtual and physical payment cards.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/cards` | Admin issues a card to a merchant account. | `ADMIN` / `SUPER_ADMIN` |
| **POST** | `/api/v1/cards/me` | User issues a card for their own account. | `USER` |
| **GET** | `/api/v1/cards/{cardId}` | Fetch card metadata and status by ID. | `CARD_READ` |
| **GET** | `/api/v1/cards/account/{accountId}` | Paginated list of all cards linked to an account. | `CARD_READ` |
| **PATCH** | `/api/v1/cards/{cardId}/status` | Update card lifecycle state (e.g., ACTIVE, EXPIRED). | `CARD_UPDATE` |
| **PATCH** | `/api/v1/cards/{cardId}/block` | Admin hard-block on any card. | `ADMIN` / `SUPER_ADMIN` |
| **PATCH** | `/api/v1/cards/{cardId}/block/me` | User immediately blocks their own card. | `USER` |
| **DELETE** | `/api/v1/cards/{cardId}` | Soft delete a card record. | `CARD_DELETE` |

---

### 6. Transfers (`/api/v1/transfers`)
*Fund movement execution and transfer history.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/transfers/me` | User initiates a transfer from their own account (balance and tier-limit validated). | `USER` |
| **GET** | `/api/v1/transfers/{transferId}` | Admin lookup for a specific transfer's metadata and status. | `ADMIN` / `SUPER_ADMIN` |
| **GET** | `/api/v1/transfers/me/account/{accountId}` | User views transfer history for their own account. | `USER` |
| **GET** | `/api/v1/transfers/account/{accountId}` | Admin paginated view of all transfers for any account. | `ADMIN` / `SUPER_ADMIN` |

---

### 7. Transactions (`/api/v1/transactions`)
*Immutable financial ledger entries generated automatically on every transfer.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **GET** | `/api/v1/transactions/{transactionId}` | Admin lookup for a single transaction by ID. | `ADMIN` / `SUPER_ADMIN` |
| **GET** | `/api/v1/transactions/me/account/{accountId}` | User views their own account's transaction history. | `USER` |
| **GET** | `/api/v1/transactions/account/{accountId}` | Admin full audit view of all transactions for any account. | `ADMIN` / `SUPER_ADMIN` |

---

### 8. Tier Configuration (`/api/v1/tier-configs`)
*Define and manage the transaction limits and fee structures governing each merchant tier.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/tier-configs` | Create a new tier configuration. | `TIER_UPDATE` |
| **GET** | `/api/v1/tier-configs` | List all active tier configurations. | `TIER_READ` |
| **GET** | `/api/v1/tier-configs/{tierConfigId}` | Fetch configuration by internal ID. | `TIER_READ` |
| **GET** | `/api/v1/tier-configs/tier/{tier}` | Lookup limits and fees by tier name (e.g., `TIER_1`). | `TIER_READ` |
| **PUT** | `/api/v1/tier-configs/{tier}` | Update limits or fee percentages for an existing tier. | `TIER_UPDATE` |

---

### 9. Roles & Permissions (`/api/v1/roles`, `/api/v1/permissions`)
*Granular RBAC management for controlling system access levels.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/roles` | Create a new system role. | `ROLE_CREATE` |
| **POST** | `/api/v1/roles/{roleId}/permissions` | Bulk assign permissions to a role. | `PERMISSION_ASSIGN` |
| **GET** | `/api/v1/permissions` | List all available system permissions. | `PERMISSION_READ` |
| **DELETE** | `/api/v1/roles/{roleId}/permissions` | Bulk revoke permissions from a role. | `PERMISSION_ASSIGN` |

---

## 🔒 Security & Auditing

- **JWT Authentication:** All private endpoints require a `Authorization: Bearer <token>` header.
- **Audit Fields:** Every mutating operation captures `created_by` and `updated_by` user IDs via `SecurityUtil`, with full timestamps.
- **Transactional Integrity:** Critical operations (transfers, tier changes, KYC updates) are wrapped in `@Transactional` to ensure atomicity and prevent race conditions.
- **Idempotency:** Transfers enforce unique reference validation to prevent duplicate fund movements.
- **Soft Deletes:** Users, merchants, accounts, and cards are never hard-deleted — all deletions set a `deleted_at` timestamp and are excluded from active queries.

---

## 🛡 Fraud Detection System

VerveGuard includes a comprehensive real-time fraud detection engine that evaluates transactions, applies rule-based analysis, and automatically enforces protective measures when suspicious or fraudulent activity is detected.

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           FRAUD DETECTION FLOW                              │
└─────────────────────────────────────────────────────────────────────────────┘

  ┌──────────────┐      ┌───────────────────┐      ┌─────────────────────┐
  │  Transaction │ ───▶ │ FraudDetection    │ ───▶ │ FraudConsequence    │
  │  Request     │      │ Service           │      │ Service             │
  └──────────────┘      └───────────────────┘      └─────────────────────┘
                               │                          │
                               ▼                          ▼
                        ┌─────────────┐           ┌──────────────┐
                        │ Rule Engine │           │ Card Blocking│
                        │             │           │ Email Alerts │
                        │ • Blacklist │           └──────────────┘
                        │ • Rate Limit│
                        │ • Velocity  │
                        │ • Limits    │
                        │ • Patterns  │
                        └─────────────┘
                               │
                               ▼
                        ┌─────────────┐
                        │ FraudAttempt│
                        │ Logger      │
                        │ (Audit Log) │
                        └─────────────┘
```

### Fraud Evaluation Process

When a transaction is submitted for fraud evaluation, the system performs the following checks in order:

#### 1. Hard Blocks (Immediate Rejection)

These checks result in an immediate `BLOCKED` status — the transaction is rejected and cannot proceed.

| Check | Flag | Description |
| :--- | :--- | :--- |
| **Merchant Blacklist** | `MERCHANT_BLACKLISTED` | The merchant is on the system blacklist (cached for performance). |
| **Rate Limiting** | `RATE_LIMITED` | The IP address has exceeded the allowed request threshold. |

#### 2. Soft Flags (Suspicious Activity)

These checks result in a `SUSPICIOUS` status — the transaction is allowed to proceed but is flagged for review.

| Check | Flag | Description |
| :--- | :--- | :--- |
| **Card Velocity** | `CARD_VELOCITY_EXCEEDED` | The same card has been used more than 3 times in 60 seconds. |
| **Single Limit** | `EXCEEDS_SINGLE_LIMIT` | The transaction amount exceeds the merchant's tier single-transaction limit. |
| **Round Amount** | `ROUND_AMOUNT` | The amount is a round number divisible by 1000 (common fraud pattern). |
| **After Hours** | `AFTER_HOURS` | The transaction occurs outside business hours (before 6 AM or after 10 PM). |

#### 3. Status Outcomes

| Status | Description | Consequence |
| :--- | :--- | :--- |
| `CLEAN` | No fraud indicators detected. | Transaction proceeds normally. |
| `SUSPICIOUS` | One or more soft flags triggered. | Transaction proceeds; merchant receives warning email. |
| `BLOCKED` | Hard block triggered. | Transaction rejected; card blocked; merchant receives urgent alert. |

---

### Automatic Consequences

When fraud is detected, the `FraudConsequenceService` automatically applies protective measures:

#### Card Blocking

When a transaction is **BLOCKED**, the associated card is automatically blocked:

```
Card Status: ACTIVE → BLOCKED
```

- The card is identified by its SHA-256 hash
- The block is immediate and prevents further transactions
- The card remains blocked until manually unblocked by an administrator

#### Email Alerts

The system sends HTML email alerts to merchants when fraud is detected:

| Status | Email Subject | Email Content |
| :--- | :--- | :--- |
| `BLOCKED` | `[URGENT] Transaction Blocked - Fraud Alert` | Full transaction details, triggered flags, card blocked notification |
| `SUSPICIOUS` | `[WARNING] Suspicious Transaction Detected` | Full transaction details, triggered flags, recommended actions |

**Email Content Includes:**
- Transaction details (masked account number, amount, currency, IP, timestamp)
- List of triggered fraud flags
- Card action taken (if blocked)
- Recommended next steps for the merchant

---

### Fraud Detection API

#### Evaluate Transaction

```http
POST /api/v1/fraud/evaluate
Authorization: Bearer <token>
Content-Type: application/json

{
  "accountNumber": "1000000001",
  "amount": 50000.00,
  "currency": "NGN",
  "cardNumber": "4111111111111111",
  "ipAddress": "192.168.1.100",
  "transactionTime": "2026-04-08T14:30:00Z"
}
```

**Response:**
```json
{
  "status": "success",
  "data": "CLEAN"
}
```

Possible values: `CLEAN`, `SUSPICIOUS`, `BLOCKED`

#### View Fraud Attempts (Audit Log)

```http
GET /api/v1/fraud/attempts?page=1&size=10
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "cardHash": "a3f2b8c9...",
        "merchantId": 5,
        "ipAddress": "192.168.1.100",
        "amount": 50000.00,
        "currency": "NGN",
        "status": "SUSPICIOUS",
        "flags": ["ROUND_AMOUNT", "AFTER_HOURS"],
        "createdAt": "2026-04-08T23:15:00Z"
      }
    ],
    "totalElements": 150,
    "totalPages": 15
  }
}
```

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/fraud/evaluate` | Submit a transaction for fraud evaluation. | `TRANSACTION_CREATE` |
| **GET** | `/api/v1/fraud/attempts` | Paginated audit log of all fraud evaluations. | `SUPER_ADMIN` |

---

### Blacklist Management

Merchants can be added to a system-wide blacklist. Blacklisted merchants have all transactions immediately blocked.

**Database Table:** `merchant_blacklist`

| Column | Description |
| :--- | :--- |
| `merchant_id` | The blacklisted merchant |
| `reason` | Reason for blacklisting |
| `blacklisted_at` | Timestamp when blacklisted |
| `blacklisted_by` | User who added to blacklist |
| `lifted_at` | Timestamp when removed (NULL if active) |
| `lifted_by` | User who removed from blacklist |

**Caching:** Blacklist status is cached using `BlacklistCache` for high-performance lookups during transaction evaluation.

---

### Email Service Configuration

The fraud alert email system uses Gmail API with a Caffeine-based async queue:

```properties
# application.properties

# Enable/disable email sending (disabled in tests)
email.enabled=true

# Gmail API Configuration
gmail.api.key=${GMAIL_API_KEY}
gmail.sender.email=noreply@verveguard.com

# Queue Configuration
email.queue.max-size=10000
email.queue.expire-after-write-minutes=30
email.max-retries=3
email.batch-size=50
email.process-interval-ms=5000
```

**Test Environment:**
```properties
# application-test.properties
email.enabled=false
```

When `email.enabled=false`:
- No emails are queued
- No emails are sent
- All email operations are silently skipped
- Fraud detection continues to work normally

---

### Data Flow Example

**Scenario:** A transaction triggers the `ROUND_AMOUNT` and `AFTER_HOURS` flags.

```
1. POST /api/v1/fraud/evaluate
   └── FraudDetectionService.evaluate()
       │
       ├── Check: Blacklist? → NO
       ├── Check: Rate Limited? → NO
       ├── Check: Card Velocity? → NO
       ├── Check: Single Limit? → NO
       ├── Check: Round Amount? → YES → Flag: ROUND_AMOUNT
       ├── Check: After Hours? → YES → Flag: AFTER_HOURS
       │
       ├── Status: SUSPICIOUS (soft flags only)
       │
       ├── FraudAttemptLoggerService.logAttempt()
       │   └── INSERT INTO fraud_attempts (...)
       │
       └── FraudConsequenceService.applyConsequences()
           └── EmailQueueService.queueEmail()
               └── Merchant receives warning email

2. Response: { "data": "SUSPICIOUS" }

3. Transaction proceeds (soft flags don't block)
```

**Scenario:** A blacklisted merchant attempts a transaction.

```
1. POST /api/v1/fraud/evaluate
   └── FraudDetectionService.evaluate()
       │
       ├── Check: Blacklist? → YES → Flag: MERCHANT_BLACKLISTED
       │
       ├── Status: BLOCKED (hard block)
       │
       ├── FraudAttemptLoggerService.logAttempt()
       │   └── INSERT INTO fraud_attempts (...)
       │
       └── FraudConsequenceService.applyConsequences()
           ├── CardDao.blockByHash() → Card BLOCKED
           └── EmailQueueService.queueEmail() → Urgent alert sent

2. Response: { "data": "BLOCKED" }

3. Transaction rejected
```

---

### Stored Procedures

The fraud system uses PostgreSQL stored procedures for performance and atomicity:

| Procedure | Description |
| :--- | :--- |
| `sp_fraud_insert_attempt` | Log a fraud evaluation to the audit table |
| `sp_fraud_get_card_velocity_count` | Count card uses within a time window |
| `sp_fraud_get_merchant_single_limit` | Get merchant's tier transaction limit |
| `sp_fraud_get_merchant_id_by_account` | Lookup merchant by account number |
| `sp_fraud_get_attempts` | Paginated fraud attempt history |
| `sp_card_block_by_hash` | Block a card by its hash |
| `sp_merchant_get_email_by_account` | Get merchant email for alerts |
| `sp_blacklist_is_actively_blacklisted` | Check if merchant is blacklisted |