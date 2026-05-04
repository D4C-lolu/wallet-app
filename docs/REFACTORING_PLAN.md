# Wallet App Performance & Fraud Module Refactoring Plan

**Target:** 200 concurrent requests @ <100ms latency

**Created:** 2026-04-30

---

## Table of Contents

1. [Phase 0: Auth Performance](#phase-0-auth-performance)
2. [Phase 1: Fraud Pipeline Architecture](#phase-1-fraud-pipeline-architecture)
3. [Phase 2: Fraud Module Decoupling](#phase-2-fraud-module-decoupling)
4. [Phase 3: Fraud Data Separation](#phase-3-fraud-data-separation)
5. [Phase 4: Async Fraud Evaluation](#phase-4-async-fraud-evaluation)
6. [Phase 5: Standalone Module (Optional)](#phase-5-standalone-module-optional)
7. [Phase 6: General App Performance](#phase-6-general-app-performance)
8. [Appendix: Interface Designs](#appendix-interface-designs)

---

## Phase 0: Auth Performance

**Goal:** Eliminate DB lookups on every authenticated request.

**Current problem:** `JwtAuthFilter` calls `userDetailsService.loadUserById()` on every request, hitting the database to load user + role + permissions.

**Solution:** Embed claims in JWT, build `UserPrincipal` from token claims.

### Tasks

- [x] **0.1 Modify JWT token structure**
  - File: `JwtService.java`
  - Add claims to `generateToken()`:
    ```
    .claim("role", principal.user().getRole().getName())
    .claim("permissions", permissionNames) // List<String>
    ```
  - Keep token compact — store permission names only, not full objects

- [x] **0.2 Create lightweight principal for filter**
  - Create `JwtUserPrincipal` record (or modify `UserPrincipal`)
  - Should be constructable from JWT claims without DB access:
    ```
    JwtUserPrincipal(Long id, String email, String role, List<String> permissions)
    ```
  - Implements `UserDetails`, builds `GrantedAuthority` list from permissions

- [x] **0.3 Update JwtAuthFilter to use claims**
  - File: `JwtAuthFilter.java`
  - Remove `UserDetailsServiceImpl` dependency
  - Extract claims from token via `JwtService.extractUserClaims()`
  - Build `JwtUserPrincipal` from claims
  - Set in SecurityContext

- [x] **0.4 Add claim extraction methods to JwtService**
  - Created `extractUserClaims(String token): JwtUserClaims` (single parse, returns all claims)
  - `JwtUserClaims` record contains: userId, email, role, permissions

- [x] **0.5 Update token refresh to reload permissions**
  - File: `TokenService.java`
  - On refresh, load fresh user data from DB (already does this)
  - Issue new token with current permissions

- [x] **0.6 Remove unnecessary UserDetailsService usage**
  - `AuthService` — keep (needed for login)
  - `TokenService.refresh()` — keep (needed for refresh)
  - `JwtAuthFilter` — removed DB dependency ✓

- [x] **0.7 Invalidate tokens on permission/role changes**
  - `UserService.changeUserRole()` — calls `tokenService.revokeAll(userId)`
  - `UserService.changeUserStatus()` — revokes on suspend/inactive
  - `RoleService.assignPermission/revokePermission/assignPermissions/revokePermissions` — revokes all users with that role
  - Added `UserDao.findUserIdsByRoleId()` for bulk lookup

- [x] **0.8 Update SecurityUtil for dual principal types**
  - `SecurityUtil.findCurrentUserId()` now handles both `JwtUserPrincipal` and `UserPrincipal`

### Verification

- [ ] Login still works
- [ ] Authenticated requests work without DB call (check logs)
- [ ] Permission-based access control still works (`@PreAuthorize`)
- [ ] Token refresh updates permissions correctly
- [ ] Role/permission changes invalidate existing tokens
- [ ] Measure latency improvement (expect ~10-50ms reduction per request)

---

## Phase 1: Fraud Pipeline Architecture

**Goal:** Replace monolithic `FraudDetectionService.evaluate()` with a scored pipeline of independent gates.

### 1.1 Define Core Interfaces

- [ ] **Create `FraudGate` interface**
  - File: `com.interswitch.walletapp.fraud.api.FraudGate`
  ```java
  public interface FraudGate {
      FraudGateResult evaluate(FraudContext ctx);
      String getName();      // e.g., "BLACKLIST", "VELOCITY"
      int getOrder();        // execution order (lower = first)
      boolean isHardBlockCapable(); // can this gate hard-block?
  }
  ```

- [ ] **Create `FraudGateResult` record**
  - File: `com.interswitch.walletapp.fraud.api.FraudGateResult`
  ```java
  public record FraudGateResult(
      int score,              // 0-100 contribution to risk
      String reasonCode,      // e.g., "VELOCITY_EXCEEDED"
      String reasonDetail,    // "3 txns in 60s from card ***1234"
      boolean hardBlock,      // stop pipeline immediately?
      Map<String, Object> metadata  // extra audit data
  ) {
      public static FraudGateResult pass() {
          return new FraudGateResult(0, null, null, false, Map.of());
      }

      public static FraudGateResult flag(int score, String code, String detail) {
          return new FraudGateResult(score, code, detail, false, Map.of());
      }

      public static FraudGateResult block(String code, String detail) {
          return new FraudGateResult(100, code, detail, true, Map.of());
      }
  }
  ```

- [ ] **Create `FraudContext` record**
  - File: `com.interswitch.walletapp.fraud.api.FraudContext`
  - Input data for evaluation (wallet-agnostic):
  ```java
  public record FraudContext(
      String accountIdentifier,
      String cardHash,           // pre-hashed
      String ipAddress,
      BigDecimal amount,
      String currency,
      OffsetDateTime transactionTime,
      Map<String, Object> extra  // extensible
  ) {}
  ```

- [ ] **Create `FraudEvaluationResult` record**
  - File: `com.interswitch.walletapp.fraud.api.FraudEvaluationResult`
  - Final result after all gates:
  ```java
  public record FraudEvaluationResult(
      FraudDecision decision,     // ALLOW, BLOCK, REVIEW
      int totalScore,
      List<FraudGateResult> gateResults,
      String primaryReason,       // first hard-block or highest-score reason
      List<String> allReasonCodes,
      Duration evaluationTime
  ) {}

  public enum FraudDecision { ALLOW, BLOCK, REVIEW }
  ```

### 1.2 Implement Gate Classes

- [ ] **BlacklistGate**
  - Order: 1 (first)
  - Hard-block capable: yes
  - Logic: Check `BlacklistCache.isBlacklisted(merchantId)`
  - Score: 100 (if blacklisted)
  - Reason: `MERCHANT_BLACKLISTED` / "Merchant {id} is on blacklist"

- [ ] **RateLimitGate**
  - Order: 2
  - Hard-block capable: yes
  - Logic: Check `RateLimiterService.isRateLimited(ip)`
  - Score: 100 (if rate limited)
  - Reason: `RATE_LIMITED` / "IP {ip} exceeded rate limit"

- [ ] **VelocityGate**
  - Order: 10
  - Hard-block capable: no (soft flag)
  - Logic: Check card velocity count against threshold
  - Score: 30-50 (configurable)
  - Reason: `VELOCITY_EXCEEDED` / "{count} transactions in {window}s"
  - **Cache velocity counts** (5-10s TTL)

- [ ] **LimitGate**
  - Order: 11
  - Hard-block capable: no (soft flag)
  - Logic: Check amount against merchant's single transaction limit
  - Score: 25-40 (configurable)
  - Reason: `EXCEEDS_SINGLE_LIMIT` / "Amount {amount} exceeds limit {limit}"
  - **Cache merchant limits** (5min TTL)

- [ ] **AfterHoursGate**
  - Order: 20
  - Hard-block capable: no (soft flag)
  - Logic: Check if transaction time is outside business hours
  - Score: 10-15 (configurable)
  - Reason: `AFTER_HOURS` / "Transaction at {time} outside business hours"

- [ ] **Future gates (placeholder)**
  - `GeoVelocityGate` — impossible travel detection
  - `AmountAnomalyGate` — unusual amount for merchant
  - `NewCardGate` — first-time card usage
  - `DeviceFingerprintGate` — device reputation

### 1.3 Implement Pipeline Executor

- [ ] **Create `FraudPipelineExecutor`**
  - File: `com.interswitch.walletapp.fraud.core.FraudPipelineExecutor`
  - Inject all `FraudGate` beans (use `List<FraudGate>`)
  - Sort by `getOrder()`
  - Execute logic:
    ```
    1. Start timer
    2. Resolve merchantId from accountIdentifier (cached)
    3. Run hard-block gates sequentially (fail-fast)
       - If any returns hardBlock=true, stop and return BLOCK
    4. Run remaining gates in parallel (CompletableFuture.allOf)
    5. Collect all results
    6. Sum scores
    7. Determine decision:
       - score >= blockThreshold → BLOCK
       - score >= reviewThreshold → REVIEW
       - else → ALLOW
    8. Return FraudEvaluationResult
    ```

- [ ] **Configure thresholds**
  - File: `application.properties`
  ```properties
  fraud.pipeline.block-threshold=70
  fraud.pipeline.review-threshold=30
  fraud.pipeline.timeout-ms=50
  ```

- [ ] **Create `FraudProperties` config class**
  - File: `com.interswitch.walletapp.fraud.config.FraudProperties`
  - `@ConfigurationProperties(prefix = "fraud")`
  - Gate-specific configs:
    ```properties
    fraud.gates.velocity.threshold=3
    fraud.gates.velocity.window-seconds=60
    fraud.gates.velocity.score=30
    fraud.gates.limit.score=25
    fraud.gates.after-hours.start=6
    fraud.gates.after-hours.end=22
    fraud.gates.after-hours.score=10
    ```

### 1.4 Parallel Execution

- [ ] **Identify independent gates**
  - Hard-block gates: run sequentially first (BlacklistGate, RateLimitGate)
  - Soft gates: can run in parallel (VelocityGate, LimitGate, AfterHoursGate)

- [ ] **Implement parallel execution**
  ```java
  List<CompletableFuture<FraudGateResult>> futures = softGates.stream()
      .map(gate -> CompletableFuture.supplyAsync(
          () -> gate.evaluate(ctx),
          fraudExecutor
      ))
      .toList();

  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
      .join();
  ```

- [ ] **Create dedicated executor**
  - File: `FraudConfig.java`
  ```java
  @Bean
  public Executor fraudExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(10);
      executor.setMaxPoolSize(50);
      executor.setQueueCapacity(100);
      executor.setThreadNamePrefix("fraud-");
      return executor;
  }
  ```

### 1.5 Caching for Gates

- [ ] **Cache merchant limits**
  - Key: `accountNumber`
  - Value: `BigDecimal` (single limit)
  - TTL: 5 minutes
  - Evict on tier change

- [ ] **Cache velocity counts**
  - Key: `cardHash`
  - Value: `int` (count)
  - TTL: 5-10 seconds (short, since it changes frequently)
  - Or: use atomic counter in cache instead of DB query

- [ ] **Cache merchant ID lookups**
  - Key: `accountNumber`
  - Value: `Long` (merchantId)
  - TTL: 30 minutes (rarely changes)

### 1.6 Update Fraud Aspect

- [ ] **Modify `FraudDetectionAspect`**
  - Use new `FraudPipelineExecutor` instead of `FraudDetectionService`
  - Build `FraudContext` from `TransferRequest`
  - Handle `FraudEvaluationResult`:
    - `BLOCK` → throw `FraudDetectedException` with reason
    - `REVIEW` → allow but flag (or configurable behavior)
    - `ALLOW` → proceed

### 1.7 Audit Trail

- [ ] **Store gate results for audit**
  - Modify `fraud_attempts` table or create new `fraud_evaluations` table:
    ```sql
    CREATE TABLE fraud_evaluations (
        id bigint PRIMARY KEY,
        account_identifier varchar(50),
        card_hash varchar(64),
        ip_address varchar(45),
        amount numeric(19,4),
        currency varchar(3),
        decision varchar(20),        -- ALLOW, BLOCK, REVIEW
        total_score int,
        gate_results jsonb,          -- array of gate results
        primary_reason varchar(100),
        evaluation_time_ms int,
        created_at timestamptz
    );
    ```

- [ ] **Async logging**
  - Gate results logged asynchronously (don't block request)
  - Use `@Async` or event publishing

### Verification

- [ ] All existing fraud tests pass
- [ ] Pipeline executes gates in correct order
- [ ] Hard-block gates stop pipeline early
- [ ] Soft gates run in parallel
- [ ] Scores accumulate correctly
- [ ] Audit trail captures all gate results
- [ ] Latency < 50ms for fraud evaluation (target)

---

## Phase 2: Fraud Module Decoupling

**Goal:** Make fraud module reusable across projects with minimal dependencies.

### Package Structure

- [ ] **Reorganize fraud code**
  ```
  com.interswitch.walletapp.fraud/
  ├── api/                 # Public interfaces (FraudGate, FraudContext, etc.)
  ├── config/              # FraudProperties, FraudConfig
  ├── core/                # FraudPipelineExecutor
  ├── gates/               # Gate implementations
  ├── cache/               # FraudDataCache, BlacklistCache
  ├── persistence/         # FraudDao, FraudEvaluationRepository
  └── integration/         # Wallet-specific adapters
  ```

### Tasks

- [ ] **2.1 Define module boundaries**
  - `api/` — no dependencies on wallet code
  - `gates/` — may depend on `cache/` only
  - `integration/` — adapters that connect to wallet services

- [ ] **2.2 Create `FraudDataProvider` interface**
  - Abstraction for data gates need:
  ```java
  public interface FraudDataProvider {
      Optional<Long> getMerchantId(String accountIdentifier);
      Optional<BigDecimal> getSingleLimit(String accountIdentifier);
      boolean isBlacklisted(Long merchantId);
      int getVelocityCount(String cardHash, Duration window);
  }
  ```
  - Wallet app provides implementation that uses DAOs/caches

- [ ] **2.3 Create `FraudConsequenceHandler` interface**
  - Abstraction for consequences:
  ```java
  public interface FraudConsequenceHandler {
      void handle(FraudEvaluationResult result, FraudContext ctx);
  }
  ```
  - Wallet app provides implementation (block cards, send emails, etc.)

- [ ] **2.4 Remove direct DAO dependencies from gates**
  - Gates should use `FraudDataProvider`, not `FraudDao` directly
  - Makes gates testable and portable

- [ ] **2.5 Extract email logic from fraud module**
  - `FraudConsequenceService` should not know about email
  - Instead: emit event, let wallet app handle notification

---

## Phase 3: Fraud Data Separation

**Goal:** Separate fraud data from wallet data for cleaner boundaries.

### Tasks

- [ ] **3.1 Create fraud schema (optional)**
  - Option A: Separate PostgreSQL schema
    ```sql
    CREATE SCHEMA fraud;
    -- Move tables: fraud.fraud_attempts, fraud.merchant_blacklist, fraud.fraud_evaluations
    ```
  - Option B: Keep tables in public schema with `fraud_` prefix (current approach)

- [ ] **3.2 Remove FK to wallet tables**
  - `fraud_attempts.merchant_id` → store as identifier, remove FK constraint
  - Fraud module shouldn't enforce referential integrity with wallet
  - Reason: allows fraud module to work independently

- [ ] **3.3 Add merchant_identifier column**
  - Add `merchant_identifier varchar(100)` alongside `merchant_id`
  - Allows fraud module to work with any identifier scheme

- [ ] **3.4 Create migration script**
  ```sql
  -- Add identifier column
  ALTER TABLE fraud_attempts ADD COLUMN merchant_identifier varchar(100);

  -- Backfill from merchants table
  UPDATE fraud_attempts fa
  SET merchant_identifier = m.id::text
  FROM merchants m
  WHERE fa.merchant_id = m.id;

  -- Drop FK (optional, do after validation)
  -- ALTER TABLE fraud_attempts DROP CONSTRAINT IF EXISTS fraud_attempts_merchant_fkey;
  ```

---

## Phase 4: Async Fraud Evaluation

**Goal:** Option to run fraud evaluation asynchronously for maximum throughput.

### Tasks

- [ ] **4.1 Add evaluation mode config**
  ```properties
  fraud.evaluation.mode=sync    # sync | async | hybrid
  fraud.evaluation.sync-timeout-ms=50
  ```

- [ ] **4.2 Implement hybrid mode**
  - Run with timeout (e.g., 50ms)
  - If timeout: allow transaction, flag for async review
  - Log timeout occurrences for monitoring

- [ ] **4.3 Implement async mode (optional)**
  - Publish `FraudEvaluationRequested` event
  - Transaction proceeds immediately
  - Async consumer evaluates and applies consequences
  - Use case: very high throughput where blocking is unacceptable

- [ ] **4.4 Add fraud decision cache**
  - Cache recent decisions:
    - Key: hash of (cardHash, accountId, amountBucket)
    - Value: `FraudDecision`
    - TTL: 30 seconds
  - If same pattern seen recently, return cached decision
  - Reduces redundant evaluations for retries

---

## Phase 5: Standalone Module (Optional)

**Goal:** Extract fraud module as a reusable library or microservice.

### Option A: Library

- [ ] **5.1 Create `verve-guard-core` module**
  - Pure Java, no Spring dependencies
  - Contains: interfaces, core pipeline logic, gate base classes

- [ ] **5.2 Create `verve-guard-spring-boot-starter`**
  - Spring Boot auto-configuration
  - `@EnableVerveguard` annotation
  - Provides: `FraudPipelineExecutor` bean, metrics, health indicator

- [ ] **5.3 Publish to internal Maven repository**
  - Other projects can add dependency:
    ```xml
    <dependency>
        <groupId>com.interswitch</groupId>
        <artifactId>verve-guard-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
    ```

### Option B: Microservice (True Sidecar)

- [ ] **5.4 Create standalone fraud service**
  - Separate deployable with its own database
  - REST or gRPC API:
    ```
    POST /evaluate
    {
      "accountIdentifier": "1234567890",
      "cardHash": "abc123...",
      "ipAddress": "1.2.3.4",
      "amount": 1000.00,
      "currency": "NGN"
    }
    ```

- [ ] **5.5 Add service client to wallet app**
  - `FraudServiceClient` implements `FraudEvaluator`
  - Circuit breaker for resilience
  - Fallback: allow transaction if fraud service unavailable

---

## Phase 6: General App Performance

**Goal:** Address remaining performance issues across the app.

### 6.1 Database Query Optimization

- [ ] **Audit N+1 queries**
  - Enable Hibernate statistics in dev:
    ```properties
    spring.jpa.properties.hibernate.generate_statistics=true
    logging.level.org.hibernate.stat=DEBUG
    ```
  - Review: `MerchantService`, `AccountService`, `CardService`

- [ ] **Add missing `JOIN FETCH` or `@EntityGraph`**
  - Identify lazy collections accessed in loops
  - Add fetch joins to repository methods

- [ ] **Add slow query logging**
  ```properties
  # Log queries taking > 100ms
  spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS=100
  ```

### 6.2 Transaction Boundary Review

- [ ] **Audit `@Transactional` usage**
  - Ensure read-only operations use `@Transactional(readOnly = true)`
  - Move non-DB code outside transaction boundaries
  - Avoid `@Transactional` on controller methods

- [ ] **Review transaction propagation**
  - `FraudAttemptLoggerService` uses `REQUIRES_NEW` — consider `@Async` instead
  - Ensure nested transactions are intentional

### 6.3 Connection Pool Tuning

- [ ] **Review HikariCP settings**
  ```properties
  spring.datasource.hikari.maximum-pool-size=20      # default 10
  spring.datasource.hikari.minimum-idle=5
  spring.datasource.hikari.connection-timeout=30000
  spring.datasource.hikari.idle-timeout=600000
  spring.datasource.hikari.max-lifetime=1800000
  ```

- [ ] **Add connection pool metrics**
  - Ensure HikariCP metrics exported to Prometheus
  - Monitor: active connections, pending threads, pool size

### 6.4 Caching Review

- [ ] **Verify L2 cache is working**
  - Check Hibernate statistics for cache hit/miss
  - Entities: `User`, `Role`, `Permission` (already done)
  - Consider: `TierConfig`, `Merchant` (if frequently accessed)

- [ ] **Add application-level caching where needed**
  - Tier configs (rarely change)
  - Permission lookups

### 6.5 Async Operations

- [ ] **Make email sending async**
  - `EmailQueueService` should not block request
  - Verify queue processing is on separate thread pool

- [ ] **Make audit logging async**
  - Fraud attempt logging
  - Transaction audit logging (if any)

---

## Appendix: Interface Designs

### FraudGate Interface

```java
package com.interswitch.walletapp.fraud.api;

public interface FraudGate {

    /**
     * Evaluate the transaction against this gate's rules.
     * Must be thread-safe and non-blocking.
     */
    FraudGateResult evaluate(FraudContext ctx);

    /**
     * Unique name for this gate (used in logs, metrics, audit).
     */
    String getName();

    /**
     * Execution order. Lower values execute first.
     * Hard-block gates should have low order (1-10).
     * Soft-flag gates should have higher order (10+).
     */
    int getOrder();

    /**
     * Whether this gate can issue a hard block.
     * Hard-block gates run sequentially before parallel gates.
     */
    default boolean isHardBlockCapable() {
        return false;
    }

    /**
     * Whether this gate is enabled.
     * Allows runtime toggling via configuration.
     */
    default boolean isEnabled() {
        return true;
    }
}
```

### FraudContext Record

```java
package com.interswitch.walletapp.fraud.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record FraudContext(
    String accountIdentifier,      // account number or ID
    String cardHash,               // pre-hashed card number
    String ipAddress,
    BigDecimal amount,
    String currency,
    OffsetDateTime transactionTime,
    String transactionType,        // TRANSFER, PAYMENT, etc.
    Map<String, Object> metadata   // extensible context
) {
    public FraudContext {
        if (accountIdentifier == null || accountIdentifier.isBlank()) {
            throw new IllegalArgumentException("accountIdentifier required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
```

### FraudGateResult Record

```java
package com.interswitch.walletapp.fraud.api;

import java.util.Map;

public record FraudGateResult(
    int score,                     // 0-100
    String reasonCode,             // machine-readable code
    String reasonDetail,           // human-readable explanation
    boolean hardBlock,             // stop pipeline immediately?
    Map<String, Object> metadata   // additional audit data
) {

    public static FraudGateResult pass() {
        return new FraudGateResult(0, null, null, false, Map.of());
    }

    public static FraudGateResult flag(int score, String code, String detail) {
        return new FraudGateResult(score, code, detail, false, Map.of());
    }

    public static FraudGateResult block(String code, String detail) {
        return new FraudGateResult(100, code, detail, true, Map.of());
    }

    public static FraudGateResult block(String code, String detail, Map<String, Object> meta) {
        return new FraudGateResult(100, code, detail, true, meta);
    }

    public boolean isClean() {
        return score == 0 && !hardBlock;
    }
}
```

### FraudEvaluationResult Record

```java
package com.interswitch.walletapp.fraud.api;

import java.time.Duration;
import java.util.List;

public record FraudEvaluationResult(
    FraudDecision decision,
    int totalScore,
    List<FraudGateResult> gateResults,
    String primaryReasonCode,
    String primaryReasonDetail,
    List<String> allReasonCodes,
    Duration evaluationTime
) {

    public boolean isBlocked() {
        return decision == FraudDecision.BLOCK;
    }

    public boolean requiresReview() {
        return decision == FraudDecision.REVIEW;
    }

    public boolean isAllowed() {
        return decision == FraudDecision.ALLOW;
    }
}

public enum FraudDecision {
    ALLOW,      // Transaction can proceed
    REVIEW,     // Transaction proceeds but flagged for review
    BLOCK       // Transaction rejected
}
```

### FraudDataProvider Interface

```java
package com.interswitch.walletapp.fraud.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Abstraction for data required by fraud gates.
 * Implement this interface in your application to provide
 * the necessary data from your domain.
 */
public interface FraudDataProvider {

    /**
     * Get the merchant ID associated with an account.
     */
    Optional<Long> getMerchantId(String accountIdentifier);

    /**
     * Get the single transaction limit for an account's tier.
     */
    Optional<BigDecimal> getSingleTransactionLimit(String accountIdentifier);

    /**
     * Check if a merchant is currently blacklisted.
     */
    boolean isMerchantBlacklisted(Long merchantId);

    /**
     * Get the number of transactions for a card in a time window.
     */
    int getCardVelocityCount(String cardHash, Duration window);

    /**
     * Get business hours configuration.
     * Returns [startHour, endHour] in 24h format.
     */
    default int[] getBusinessHours() {
        return new int[] { 6, 22 };
    }
}
```

### FraudConsequenceHandler Interface

```java
package com.interswitch.walletapp.fraud.api;

/**
 * Handle consequences of fraud evaluation.
 * Implement this in your application to define what happens
 * when fraud is detected.
 */
public interface FraudConsequenceHandler {

    /**
     * Called after fraud evaluation completes.
     * Implementation may block cards, send alerts, etc.
     *
     * This method should be async or very fast to avoid
     * blocking the request path.
     */
    void handle(FraudEvaluationResult result, FraudContext ctx);
}
```

---

## Progress Tracking

| Phase | Status | Completion Date | Notes |
|-------|--------|-----------------|-------|
| Phase 0 | Not Started | | |
| Phase 1 | Not Started | | |
| Phase 2 | Not Started | | |
| Phase 3 | Not Started | | |
| Phase 4 | Not Started | | |
| Phase 5 | Not Started | | Optional |
| Phase 6 | Not Started | | |

---

## References

- [Bucket4j Rate Limiting](https://github.com/bucket4j/bucket4j)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
- [JWT Best Practices](https://auth0.com/blog/a-look-at-the-latest-draft-for-jwt-bcp/)
- [Spring Security Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
