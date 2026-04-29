# VerveguardAPI - Payment Wallet with Real-Time Fraud Detection

## Executive Summary

VerveguardAPI is a secure payment wallet system designed for merchants, featuring real-time fraud detection, tiered account management, and comprehensive transaction monitoring. The system processes transfers between merchant accounts while actively protecting against fraudulent activity.

---

## User Story

> *"As a merchant, I want to transfer funds between accounts quickly and securely, knowing that suspicious transactions will be automatically flagged and blocked before they cause financial harm."*

### Acceptance Criteria

- Merchants can transfer funds from their accounts to any valid account
- Each transfer is evaluated against fraud detection rules in real-time
- Suspicious transactions trigger alerts; blocked transactions prevent fund movement
- All transaction attempts are logged for audit purposes

##### Additional Features

- Merchants receive email notifications for flagged activity (Partially implemented- need an api key for this)

---

## System Architecture

### Core Entities

| Entity | Description |
|--------|-------------|
| **User** | Authentication identity with role-based permissions |
| **Merchant** | Business entity linked to a user, with KYC status and tier |
| **Account** | Holds funds in a specific currency (NGN) |
| **Card** | Payment card linked to an account |
| **Transfer** | Movement of funds between accounts |
| **Transaction** | Debit/credit leg of a transfer |

### Entity Relationships

```
User (1) ──── (1) Merchant (1) ──── (N) Account (1) ──── (N) Card
                                          │
                                          └──── (N) Transfer ──── (2) Transaction
```

### Tier System

Merchants are assigned tiers that control their transaction limits:

| Tier | Single Limit | Daily Limit | Monthly Limit |
|------|-------------|-------------|---------------|
| TIER_1 | 10,000 | 100,000 | 1,000,000 |
| TIER_2 | 50,000 | 500,000 | 5,000,000 |
| TIER_3 | 200,000 | 2,000,000 | 20,000,000 |

---

## Transfer Flow

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│   Client    │────▶│  Controller  │────▶│ Fraud Detection │
└─────────────┘     └──────────────┘     └────────┬────────┘
                                                  │
                           ┌──────────────────────┴──────────────────────┐
                           │                                             │
                           ▼                                             ▼
                    ┌─────────────┐                              ┌──────────────┐
                    │   BLOCKED   │                              │    CLEAN     │
                    │  (403 Error)│                              │  (Continue)  │
                    └─────────────┘                              └──────┬───────┘
                                                                       │
                                                                       ▼
                                                               ┌───────────────┐
                                                               │ Limit Checks  │
                                                               └───────┬───────┘
                                                                       │
                                                                       ▼
                                                               ┌───────────────┐
                                                               │Execute Transfer│
                                                               │ Debit + Credit │
                                                               └───────────────┘
```

---

## Fraud Detection System

The fraud detection engine evaluates every transfer request against multiple rules before allowing the transaction to proceed.

### Detection Rules

| Rule | Type | Trigger Condition |
|------|------|-------------------|
| **Merchant Blacklisted** | Hard Block | Merchant is on the blacklist |
| **Rate Limited** | Hard Block | >5 requests per minute from same IP |
| **Card Velocity** | Soft Flag | >3 transactions with same card in 60 seconds |
| **Exceeds Single Limit** | Soft Flag | Amount exceeds tier's single transaction limit |
| **After Hours** | Soft Flag | Transaction outside 6:00 - 22:00 |

### Fraud Statuses

- **CLEAN** - Transaction proceeds normally
- **SUSPICIOUS** - Transaction proceeds but merchant is alerted via email
- **BLOCKED** - Transaction is rejected, card may be blocked, merchant is alerted

### Consequences

When fraud is detected:

1. **BLOCKED transactions**:
   - Card is automatically blocked
   - Urgent email sent to merchant
   - Transaction logged with flags

2. **SUSPICIOUS transactions**:
   - Warning email sent to merchant
   - Transaction logged with flags
   - Transaction is allowed to proceed

---

## Observability Stack

| Component | Purpose | Port |
|-----------|---------|------|
| **Prometheus** | Metrics collection | 9090 |
| **Loki** | Log aggregation | 3100 |
| **Grafana** | Dashboards & visualization | 3000 |

### Key Metrics

- Request throughput (req/s)
- Response time percentiles (P50, P95, P99)
- Error rates by endpoint
- Transfer success/failure rates

---

## Stress Testing

### Overview

The stress test simulates real-world load by executing transfers across multiple merchants concurrently. Each iteration logs in as a different merchant and transfers from their account.

### Test Data

The system includes 200 pre-seeded stress test merchants:

| Data | Format |
|------|--------|
| Email | `merchant{1-200}@stresstest.com` |
| Password | `Admin123!` |
| Source Account | `33000000{01-200}` |
| Destination Account | `330000{0201-0400}` |
| Card Number | `4{000000000000001-200}` |
| Initial Balance | 100,000,000 NGN each |

### Usage

```bash
node stress-test.js [options]
```

### Options

| Flag | Short | Description | Default |
|------|-------|-------------|---------|
| `--iterations` | `-n` | Number of transfers to execute | 50 |
| `--concurrency` | `-c` | Number of concurrent requests | 10 |
| `--base-url` | `-u` | API base URL | `http://localhost:8080/api/v1` |
| `--verbose` | `-v` | Enable detailed logging | false |
| `--help` | `-h` | Show help message | - |

### Examples

```bash
# Basic test: 50 transfers, 10 concurrent
node stress-test.js

# Higher load: 100 transfers, 20 concurrent
node stress-test.js -n 100 -c 20

# Maximum load: all 200 merchants, 50 concurrent
node stress-test.js -n 200 -c 50

# Debug mode: 5 transfers with verbose output
node stress-test.js -n 5 -v
```

### Sample Output

```
═══════════════════════════════════════════════════════════
           VerveguardAPI Stress Test Runner
═══════════════════════════════════════════════════════════

  Iterations:    50
  Concurrency:   10
  Base URL:      http://localhost:8080/api/v1
  Merchants:     merchant1@stresstest.com to merchant50@stresstest.com

🚀 Starting stress test...

✅ #47 (merchant47@stresstest.com) success (245.12ms) - ID: 1523

═══════════════════════════════════════════════════════════
                    STRESS TEST SUMMARY
═══════════════════════════════════════════════════════════

  Results:
    Total Requests:  50
    Successful:      48 ✅
    Failed:          2 ❌
    Success Rate:    96.00%

  Performance:
    Total Duration:  12.34s
    Throughput:      4.05 req/s
    Avg Response:    287.45ms
    P95:             412.33ms
    P99:             523.17ms
```

---

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/login` | Authenticate and receive tokens |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| POST | `/api/v1/auth/logout` | Invalidate current session |

### Transfers

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/transfers/me` | Create transfer from own account |
| GET | `/api/v1/transfers/{id}` | Get transfer by ID |
| GET | `/api/v1/transfers/me/account/{accountId}` | List transfers for own account |

### Transfer Request Body

```json
{
  "fromAccountNumber": "3300000001",
  "toAccountNumber": "3300000201",
  "amount": 5000.00,
  "currency": "NGN",
  "cardNumber": "4000000000000001",
  "description": "Payment for services"
}
```

### Required Headers

| Header | Description |
|--------|-------------|
| `Authorization` | `Bearer {accessToken}` |
| `X-Idempotency-Key` | Unique key to prevent duplicate transfers |
| `Content-Type` | `application/json` |

---

## Running the Application

### Prerequisites

- Docker & Docker Compose
- Node.js 18+ (for stress testing)
- Java 21 (for development)

### Quick Start

```bash
# Start all services
docker compose -f compose.prod.yaml up -d

# View logs
docker compose -f compose.prod.yaml logs -f wallet-app

# Run stress test
node stress-test.js -n 50 -c 10

# Access Grafana dashboard
open http://localhost:3000
```

### Service URLs

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

---

## Security Considerations

- All passwords are hashed using BCrypt
- JWT tokens for stateless authentication
- Card numbers are hashed (SHA-256) before storage
- Rate limiting protects against brute force attacks
- Localhost IPs bypass rate limiting for testing only

---

*Document generated for VerveguardAPI v1.0*
