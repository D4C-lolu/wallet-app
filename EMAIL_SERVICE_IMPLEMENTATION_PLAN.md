# Email Service Implementation Plan

## Overview

Add an email notification service to send fraud alert emails to merchants when their cards are blocked due to suspected fraud. The service will use Gmail SMTP and leverage the existing Caffeine in-memory cache as a simple queue for asynchronous email delivery.

---

## Architecture

```
Card Blocked (Fraud) --> EmailQueueService --> Caffeine Cache (Queue)
                                                      |
                                                      v
                              EmailSenderJob (Scheduled) --> Gmail SMTP --> Merchant
```

---

## Components to Create

### 1. Email Model

**File:** `src/main/java/com/interswitch/walletapp/models/email/EmailMessage.java`

- Record containing: `to`, `subject`, `body`, `createdAt`, `retryCount`
- Used as the item stored in the email queue

### 2. Email Queue Cache Bean

**File:** `src/main/java/com/interswitch/walletapp/configuration/CacheConfig.java`

- Add a new Caffeine cache bean: `emailQueueCache`
- Type: `Cache<String, EmailMessage>` (key = unique message ID)
- TTL: 1 hour (messages not sent within this window are dropped)
- Max size: 1000 entries

### 3. Email Configuration Properties

**File:** `src/main/java/com/interswitch/walletapp/configuration/EmailProperties.java`

- `@ConfigurationProperties(prefix = "app.email")`
- Properties:
  - `host` (default: smtp.gmail.com)
  - `port` (default: 587)
  - `username`
  - `password`
  - `from`
  - `enabled` (default: false for dev)

### 4. Email Queue Service

**File:** `src/main/java/com/interswitch/walletapp/services/EmailQueueService.java`

- `enqueue(String to, String subject, String body)` - adds email to cache queue
- Generates unique ID (UUID) as cache key
- Logs enqueue action

### 5. Email Sender Service

**File:** `src/main/java/com/interswitch/walletapp/services/EmailSenderService.java`

- Uses `JavaMailSender` (Spring Mail)
- `send(EmailMessage message)` - sends a single email via Gmail SMTP
- Returns success/failure status
- Handles exceptions gracefully

### 6. Email Sender Job (Scheduled)

**File:** `src/main/java/com/interswitch/walletapp/cron/EmailSenderJob.java`

- `@Scheduled(fixedRate = 30000)` - runs every 30 seconds
- Iterates through `emailQueueCache` entries
- Attempts to send each email
- On success: removes from cache
- On failure: increments retry count, removes if max retries (3) exceeded
- Pattern follows existing `TransferRecoveryJob`

### 7. Fraud Alert Email Service

**File:** `src/main/java/com/interswitch/walletapp/services/FraudAlertService.java`

- `notifyMerchantCardBlocked(Card card, String reason)`
- Fetches merchant email via card -> account -> merchant -> user -> email
- Builds email body with card details (masked number, block reason)
- Calls `EmailQueueService.enqueue()`

### 8. Integration Point

**File:** `src/main/java/com/interswitch/walletapp/services/CardService.java`

- Inject `FraudAlertService`
- In `blockCard()` method: after blocking, check if fraud-related
- Call `fraudAlertService.notifyMerchantCardBlocked()`

---

## Configuration Changes

### Environment Variables

**File:** `src/main/resources/application.properties`

```properties
# Email Configuration
app.email.enabled=${EMAIL_ENABLED:false}
app.email.host=${EMAIL_HOST:smtp.gmail.com}
app.email.port=${EMAIL_PORT:587}
app.email.username=${EMAIL_USERNAME:dummy@gmail.com}
app.email.password=${EMAIL_PASSWORD:dummy-app-password}
app.email.from=${EMAIL_FROM:noreply@walletapp.com}
```

### Dummy `.env.example`

**File:** `.env.example` (update or create)

```
EMAIL_ENABLED=false
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your-app-password
EMAIL_FROM=noreply@walletapp.com
```

---

## Dependencies to Add

**File:** `pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

---

## Email Template

Subject: `[ALERT] Your card has been blocked - Action Required`

Body:
```
Dear Merchant,

Your card ending in ****1234 has been blocked due to suspected fraudulent activity.

Card Details:
- Card Number: ****1234
- Card Type: DEBIT
- Scheme: VISA
- Blocked At: 2026-04-07 10:30:00 UTC

If you believe this is a mistake, please contact our support team immediately.

Regards,
Wallet App Security Team
```

---

## Data Flow

1. Admin/System calls `CardService.blockCard(cardId)` with fraud reason
2. `CardService` blocks the card in DB
3. `CardService` calls `FraudAlertService.notifyMerchantCardBlocked(card, reason)`
4. `FraudAlertService` resolves merchant email: `card.account.merchant.user.email`
5. `FraudAlertService` builds email content and calls `EmailQueueService.enqueue()`
6. `EmailQueueService` stores `EmailMessage` in Caffeine cache with UUID key
7. `EmailSenderJob` (every 30s) picks up messages from cache
8. `EmailSenderService` sends via Gmail SMTP
9. On success: message removed from cache
10. On failure: retry up to 3 times, then discard with error log

---

## File Structure (New Files)

```
src/main/java/com/interswitch/walletapp/
  configuration/
    EmailProperties.java          # NEW
  models/
    email/
      EmailMessage.java           # NEW
  services/
    EmailQueueService.java        # NEW
    EmailSenderService.java       # NEW
    FraudAlertService.java        # NEW
  cron/
    EmailSenderJob.java           # NEW
```

---

## Testing Strategy

### Unit Tests
- `EmailQueueServiceTest` - test enqueue/dequeue operations
- `FraudAlertServiceTest` - test email content generation
- `EmailSenderJobTest` - test retry logic with mocked sender

### Integration Tests
- `EmailSenderServiceIntegrationTest` - test with real SMTP (GreenMail or similar)
- End-to-end: block card -> verify email queued -> verify email sent

---

## Considerations

### Why Caffeine Cache as Queue?

- Already in the project (no new dependencies for queue)
- Sufficient for low-volume alert emails
- Simple implementation
- Automatic TTL handles stuck messages
- **Limitation:** Messages lost on server restart (acceptable for alerts)

### Future Improvements (Out of Scope)

- Persistent queue (Redis, RabbitMQ) for guaranteed delivery
- Email templates with Thymeleaf
- Batch sending optimization
- Delivery status tracking in DB

---

## Implementation Order

1. Add `spring-boot-starter-mail` dependency to `pom.xml`
2. Create `EmailProperties.java` configuration class
3. Add email properties to `application.properties`
4. Create `EmailMessage.java` model
5. Add `emailQueueCache` bean to `CacheConfig.java`
6. Create `EmailQueueService.java`
7. Create `EmailSenderService.java`
8. Create `EmailSenderJob.java`
9. Create `FraudAlertService.java`
10. Modify `CardService.blockCard()` to trigger fraud alerts
11. Write unit tests
12. Write integration tests
