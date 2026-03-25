# VervePay API Documentation

**VervePay** is a high-performance financial middleware and merchant management platform. It provides a secure, tiered infrastructure for handling merchant lifecycles, card issuance, account management, fund transfers, and granular role-based access control (RBAC).

---

## 🏗 Project Architecture

The project follows a **Layered Architecture**, ensuring high performance and strict data integrity.

- **Controller Layer:** Handles HTTP requests, input validation (`@Valid`), and method-level security (`@PreAuthorize`).
- **Service Layer:** Contains core business logic, coordinates transactions (`@Transactional`), and performs multi-step validations (e.g., checking KYC status before a tier upgrade or account creation).
- **DAO (Data Access Object) Layer:** Direct database interaction using `NamedParameterJdbcTemplate`. This avoids ORM overhead while providing precise control over SQL execution and auditing.
- **Security:** Uses a granular Permission-based RBAC system. Authorities are mapped to Roles, and Roles are assigned to Users. JWT Bearer tokens are required on all private endpoints.

---

## 💳 The Merchant Tier System

VervePay operates on a progressive trust model with three distinct levels. Movement between tiers is governed by `TierConfig` and KYC verification status.

1. **TIER_1:** Initial entry level with restricted transaction and account limits.
2. **TIER_2:** Intermediate level. Requires `KYC_APPROVED` status.
3. **TIER_3:** Premium level with maximum transaction limits.

**Logic Flow for Upgrades:**
- Validate current KYC status via `MerchantService`.
- Check for the existence of the next tier configuration in `tier_configs`.
- Update the merchant record and log the `updated_by` auditor ID from `SecurityUtil`.

---

## 📡 API Reference

### 1. Authentication (`/auth`)
*Token issuance and session management.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/auth/login` | Authenticate and receive a JWT Bearer token. | *PermitAll* |
| **POST** | `/auth/refresh` | Refresh an expired access token. | *PermitAll* |

---

### 2. User Management (`/users`)
*System identity management, role assignments, and credential updates.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/users` | Create a new system user. | `USER_CREATE` |
| **GET** | `/users` | Paginated list of all users. | `USER_READ` |
| **GET** | `/users/{userId}` | Fetch a user's full profile by ID. | `USER_READ` |
| **PUT** | `/users/{userId}` | Update user profile details. | `USER_UPDATE` |
| **PATCH** | `/users/{userId}/status` | Activate or suspend a user account. | `USER_UPDATE` |
| **PATCH** | `/users/{userId}/role` | Reassign a user's system role. | `USER_UPDATE` |
| **PATCH** | `/users/{userId}/password` | Change account credentials. | *Owner* |
| **DELETE** | `/users/{userId}` | Soft delete a user account. | `USER_DELETE` |

---

### 3. Merchant Management (`/merchants`)
*Full lifecycle management for merchant entities, from registration through KYC and tier progression.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/merchants` | Admin-initiated merchant profile creation. | `MERCHANT_CREATE` |
| **POST** | `/merchants/register` | **Public** signup for new users registering as merchants. | *PermitAll* |
| **POST** | `/merchants/self-register` | Existing authenticated user promotes account to merchant. | *Authenticated* |
| **GET** | `/merchants` | Paginated list of all merchants. | `MERCHANT_READ` |
| **GET** | `/merchants/status` | Filter merchants by account status. | `MERCHANT_READ` |
| **GET** | `/merchants/kyc-status` | Filter merchants by KYC verification level. | `MERCHANT_READ` |
| **GET** | `/merchants/{merchantId}` | Fetch full merchant profile by ID. | `MERCHANT_READ` |
| **PUT** | `/merchants/{merchantId}` | Update core merchant profile information. | `MERCHANT_UPDATE` |
| **PATCH** | `/merchants/{merchantId}/status` | Activate or suspend a merchant account. | `MERCHANT_UPDATE` |
| **PATCH** | `/merchants/{merchantId}/administrative-status` | Override both merchant and KYC status simultaneously. | `ADMIN` / `SUPER_ADMIN` |
| **PATCH** | `/merchants/{merchantId}/kyc` | Approve or reject KYC documents. | `MERCHANT_KYC` |
| **PATCH** | `/merchants/{merchantId}/tier/upgrade` | Advance merchant to the next tier (KYC-gated). | `MERCHANT_UPDATE` |
| **PATCH** | `/merchants/{merchantId}/tier/downgrade` | Reduce merchant to the previous tier. | `MERCHANT_UPDATE` |
| **DELETE** | `/merchants/{merchantId}` | Soft delete a merchant record. | `MERCHANT_DELETE` |

---

### 4. Account Management (`/accounts`)
*Merchant wallet and escrow account lifecycle, balance tracking, and status control.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/accounts` | Admin creates an account for a specific merchant. | `ADMIN` / `SUPER_ADMIN` |
| **POST** | `/accounts/me` | Merchant opens their own account (tier limit enforced). | `MERCHANT` |
| **GET** | `/accounts/{accountId}` | Fetch account balance and status by ID. | `ACCOUNT_READ` |
| **GET** | `/accounts/merchant/{merchantId}` | Paginated list of all accounts for a merchant. | `ACCOUNT_READ` |
| **PATCH** | `/accounts/{accountId}/status` | Freeze, suspend, or activate an account. | `ACCOUNT_UPDATE` |
| **DELETE** | `/accounts/{accountId}` | Soft delete an account. | `ACCOUNT_DELETE` |

---

### 5. Card Management (`/cards`)
*Issuance and lifecycle management for virtual and physical payment cards.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/cards` | Admin issues a card to a merchant account. | `ADMIN` / `SUPER_ADMIN` |
| **POST** | `/cards/me` | Merchant issues a card for their own account. | `MERCHANT` |
| **GET** | `/cards/{cardId}` | Fetch card metadata and status by ID. | `CARD_READ` |
| **GET** | `/cards/account/{accountId}` | Paginated list of all cards linked to an account. | `CARD_READ` |
| **PATCH** | `/cards/{cardId}/status` | Update card lifecycle state (e.g., ACTIVE, EXPIRED). | `CARD_UPDATE` |
| **PATCH** | `/cards/{cardId}/block` | Admin hard-block on any card. | `ADMIN` / `SUPER_ADMIN` |
| **PATCH** | `/cards/{cardId}/block/me` | Merchant immediately blocks their own card. | `MERCHANT` |
| **DELETE** | `/cards/{cardId}` | Soft delete a card record. | `CARD_DELETE` |

---

### 6. Transfers (`/transfers`)
*Fund movement execution and transfer history.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/transfers/me` | Merchant initiates a transfer from their own account (balance and tier-limit validated). | `MERCHANT` |
| **GET** | `/transfers/{transferId}` | Admin lookup for a specific transfer's metadata and status. | `ADMIN` / `SUPER_ADMIN` |
| **GET** | `/transfers/me/account/{accountId}` | Merchant views transfer history for their own account. | `MERCHANT` |
| **GET** | `/transfers/account/{accountId}` | Admin paginated view of all transfers for any account. | `ADMIN` / `SUPER_ADMIN` |

---

### 7. Transactions (`/transactions`)
*Immutable financial ledger entries generated automatically on every transfer.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **GET** | `/transactions/{transactionId}` | Admin lookup for a single transaction by ID. | `ADMIN` / `SUPER_ADMIN` |
| **GET** | `/transactions/me/account/{accountId}` | Merchant views their own account's transaction history. | `MERCHANT` |
| **GET** | `/transactions/account/{accountId}` | Admin full audit view of all transactions for any account. | `ADMIN` / `SUPER_ADMIN` |

---

### 8. Tier Configuration (`/tier-configs`)
*Define and manage the transaction limits and fee structures governing each merchant tier.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/tier-configs` | Create a new tier configuration. | `TIER_UPDATE` |
| **GET** | `/tier-configs` | List all active tier configurations. | `TIER_READ` |
| **GET** | `/tier-configs/{tierConfigId}` | Fetch configuration by internal ID. | `TIER_READ` |
| **GET** | `/tier-configs/tier/{tier}` | Lookup limits and fees by tier name (e.g., `TIER_1`). | `TIER_READ` |
| **PUT** | `/tier-configs/{tier}` | Update limits or fee percentages for an existing tier. | `TIER_UPDATE` |

---

### 9. Roles & Permissions (`/roles`, `/permissions`)
*Granular RBAC management for controlling system access levels.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/roles` | Create a new system role. | `ROLE_CREATE` |
| **POST** | `/roles/{roleId}/permissions` | Bulk assign permissions to a role. | `PERMISSION_ASSIGN` |
| **GET** | `/permissions` | List all available system permissions. | `PERMISSION_READ` |
| **DELETE** | `/roles/{roleId}/permissions` | Bulk revoke permissions from a role. | `PERMISSION_ASSIGN` |

---

## 🔒 Security & Auditing

- **JWT Authentication:** All private endpoints require a `Authorization: Bearer <token>` header.
- **Audit Fields:** Every mutating operation captures `created_by` and `updated_by` user IDs via `SecurityUtil`, with full timestamps.
- **Transactional Integrity:** Critical operations (transfers, tier changes, KYC updates) are wrapped in `@Transactional` to ensure atomicity and prevent race conditions.
- **Idempotency:** Transfers enforce unique reference validation to prevent duplicate fund movements.
- **Soft Deletes:** Users, merchants, accounts, and cards are never hard-deleted — all deletions set a `deleted_at` timestamp and are excluded from active queries.