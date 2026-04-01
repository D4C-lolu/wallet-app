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