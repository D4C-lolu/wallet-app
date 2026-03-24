# Wallet APP API Documentation

**Wallet APP** is a high-performance financial middleware and merchant management system. It provides a secure, tiered infrastructure for handling merchant lifecycles, account transfers, and granular role-based access control (RBAC).

---

## 🏗 Project Architecture

The project follows a **Zero-Hallucination Layered Architecture**, ensuring high performance and strict data integrity.

* **Controller Layer:** Handles HTTP requests, input validation (`@Valid`), and method-level security (`@PreAuthorize`).
* **Service Layer:** Contains core business logic, coordinates transactions (`@Transactional`), and performs multi-step validations (e.g., checking KYC status before a tier upgrade).
* **DAO (Data Access Object) Layer:** Direct database interaction using `NamedParameterJdbcTemplate`. This avoids ORM overhead while providing precise control over SQL execution and auditing.
* **Security:** Uses a granular Permission-based system. Authorities are mapped to Roles, and Roles are assigned to Users.

---

## 💳 The Merchant Tier System

Wallet APP operates on a progressive trust model with three distinct levels. Movement between these tiers is governed by `TierConfig` and verification status.

1.  **TIER_1:** Initial entry level with restricted transaction limits.
2.  **TIER_2:** Intermediate level. Requires `KYC_APPROVED` status.
3.  **TIER_3:** Premium level with maximum transaction limits.

**Logic Flow for Upgrades:**
* Validate current KYC status via `MerchantService`.
* Check for the existence of the next tier configuration in `tier_configs`.
* Update the merchant record and log the `updated_by` auditor ID from `SecurityUtil`.

---

## 📡 API Reference

### 1. Merchant Management (`/merchants`)
*Manages the lifecycle and verification of business entities.*

| Method | Endpoint | Description | Permission/Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/merchants` | Manual merchant creation (Admin). | `MERCHANT_CREATE` |
| **POST** | `/merchants/register` | **Public** signup for new users. | *PermitAll* |
| **POST** | `/merchants/self-register` | User promotes own account to Merchant. | *Authenticated* |
| **GET** | `/merchants` | Paginated list of all merchants. | `MERCHANT_READ` |
| **GET** | `/merchants/status` | Filter merchants by account status. | `MERCHANT_READ` |
| **PATCH** | `/{id}/tier/upgrade` | Move to next tier (Logic-gated). | `MERCHANT_UPDATE` |
| **PATCH** | `/{id}/kyc` | Approve/Reject KYC documents. | `MERCHANT_KYC` |

### 2. Transfers & Transactions (`/transfers`, `/transactions`)
*The financial engine handling fund movements and auditing.*

| Method | Endpoint | Description | Role Required |
| :--- | :--- | :--- | :--- |
| **POST** | `/transfers/me` | Execute transfer from own account. | `MERCHANT` |
| **GET** | `/transfers/me/account/{id}` | Personal transfer history. | `MERCHANT` |
| **GET** | `/transactions/{id}` | Administrative transaction lookup. | `ADMIN` / `SUPER_ADMIN` |
| **GET** | `/transactions/account/{id}` | Full audit of account movements. | `ADMIN` / `SUPER_ADMIN` |

### 3. RBAC & Access Control (`/roles`, `/permissions`)
*Granular management of system access levels.*

| Method | Endpoint | Description | Permission/Authority |
| :--- | :--- | :--- | :--- |
| **POST** | `/roles` | Create a new system role. | `ROLE_CREATE` |
| **POST** | `/roles/{id}/permissions` | Bulk assign permissions to a role. | `PERMISSION_ASSIGN` |
| **GET** | `/permissions` | List all available granular permissions. | `PERMISSION_READ` |
| **DELETE** | `/roles/{id}/permissions` | Bulk revoke permissions from a role. | `PERMISSION_ASSIGN` |

### 4. System Configuration & Users (`/tier-configs`, `/users`)
*Foundational settings and identity management.*

| Method | Endpoint | Description | Authority |
| :--- | :--- | :--- | :--- |
| **PUT** | `/tier-configs/{tier}` | Update limits and fee structures. | `TIER_UPDATE` |
| **PATCH** | `/users/{id}/role` | Change a user's role assignment. | `USER_UPDATE` |
| **PATCH** | `/users/{id}/password` | Update account credentials. | *Owner* |

---

## 🔒 Security & Auditing

* **JWT Authentication:** All private endpoints require a Bearer token.
* **Audit Fields:** Every `UPDATE` and `PATCH` operation in the DAO captures the `updated_by` user ID using `SecurityUtil` and timestamps the action.
* **Transactional Integrity:** Critical operations (Transfers, Tier Changes) are wrapped in `@Transactional` to ensure atomicity and prevent race conditions.