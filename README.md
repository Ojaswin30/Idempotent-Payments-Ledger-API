# Production-Grade Idempotent Payments Ledger API

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-black.svg)](https://kafka.apache.org/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-1.20-orange.svg)](https://www.testcontainers.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A production-grade, distributed financial transaction system engineered to guarantee **strict idempotency**, **transactional consistency**, and **eventual consistency** under high concurrency and failure scenarios. Built with **Spring Boot 3**, **PostgreSQL**, **Redis**, **Apache Kafka**, **OpenTelemetry**, and **Testcontainers**.

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Distributed Idempotency Layer](#4-distributed-idempotency-layer)
5. [Double-Entry Financial Ledger](#5-double-entry-financial-ledger)
6. [Transactional Outbox Pattern & Kafka Event Streaming](#6-transactional-outbox-pattern--kafka-event-streaming)
7. [Failure Scenarios & Self-Healing Resiliency](#7-failure-scenarios--self-healing-resiliency)
8. [Observability, Metrics & Structured Logging](#8-observability-metrics--structured-logging)
9. [Database Schema & Flyway Migrations](#9-database-schema--flyway-migrations)
10. [REST API Documentation & cURL Snippets](#10-rest-api-documentation--curl-snippets)
11. [Testing & Concurrency Verification](#11-testing--concurrency-verification)
12. [How to Run (Docker & Local)](#12-how-to-run-docker--local)
13. [Architectural Trade-offs & Deep Dive](#13-architectural-trade-offs--deep-dive)

---

## 1. Problem Statement

In distributed financial systems and payment gateways, network failures, timeouts, and client retries are inevitable. A naive REST implementation suffers from severe risks:
1. **Duplicate Charging**: A client sends `POST /payments`, the database commits the payment, but network drops before the HTTP response reaches the client. The client retries the request, leading to double-charging.
2. **Race Conditions / Concurrent Submissions**: Fast-clicking users or parallel microservices fire simultaneous requests with the same transaction intent, causing concurrent write races.
3. **Dual-Write Inconsistency (DB vs Broker)**: Updating the database and publishing to Kafka in separate steps leaves a consistency gap if Kafka is temporarily unavailable or if the application crashes mid-process.
4. **Unbalanced Financial State**: Storing single-table balance mutations without an immutable double-entry audit trail makes financial reconciliation impossible.

This system solves all four problems through **Redis-backed atomic distributed locking**, **SHA-256 payload fingerprinting**, **strict double-entry bookkeeping**, and a **Transactional Outbox with `SELECT ... FOR UPDATE SKIP LOCKED` and exponential backoff DLQ routing**.

---

## 2. High-Level Architecture

```
                    ┌────────────────────────┐
                    │      API Client        │
                    └───────────┬────────────┘
                                │
                                │ POST /api/v1/payments
                                │ Header: Idempotency-Key
                                ▼
                 ┌──────────────────────────────┐
                 │    Spring Boot 3 API Layer   │
                 │                              │
                 │  PaymentController           │
                 │  IdempotencyService (Redis)  │
                 │  PaymentService (JPA Tx)     │
                 │  LedgerService (Double-Entry)│
                 └──────────────┬───────────────┘
                                │
                     ┌──────────┴──────────┐
                     ▼                     ▼
          ┌────────────────────┐ ┌────────────────────────────────────────┐
          │    Redis Cache     │ │         PostgreSQL Database            │
          │                    │ │                                        │
          │ • IN_PROGRESS (30s)│ │  payments Table (@Version lock)        │
          │ • COMPLETED (24h)  │ │  ledger_entries Table (Balanced rows)  │
          │ • SHA-256 Hashes   │ │  outbox_events Table (JSONB payload)   │
          └────────────────────┘ └──────────────────┬─────────────────────┘
                                                    │
                                                    │ Poll (SKIP LOCKED)
                                                    ▼
                                       ┌───────────────────────────┐
                                       │    Outbox Worker Job      │
                                       │    Exponential Backoff    │
                                       └────────────┬──────────────┘
                                                    │
                                                    │ Publish Event
                                                    ▼
                                       ┌───────────────────────────┐
                                       │       Apache Kafka        │
                                       │                           │
                                       │ • payments.events         │
                                       │ • payments.events.dlq     │
                                       └────────────┬──────────────┘
                                                    │
                                                    ▼
                                       ┌───────────────────────────┐
                                       │   Downstream Consumers    │
                                       │   (Settlement/Analytics)  │
                                       └───────────────────────────┘
```

---

## 3. Technology Stack

| Component | Technology | Version | Purpose |
|---|---|---|---|
| **Framework** | Spring Boot | 3.3.4 | Core framework, dependency injection, MVC |
| **Language** | Java | 17 / 21 | Modern records, pattern matching, typed concurrency |
| **Primary Database** | PostgreSQL | 16 | ACID financial transactions, JSONB outbox |
| **Distributed Cache** | Redis | 7.x | Atomic `SETNX` locking, cached response replay |
| **Message Broker** | Apache Kafka | 3.x | Asynchronous event distribution & DLQ streaming |
| **Database Migrations**| Flyway | 10.x | Version-controlled database schema migrations |
| **Observability** | OpenTelemetry / Micrometer | Latest | Distributed W3C tracing, metrics, custom gauges |
| **Structured Logging** | Logstash Logback Encoder| 7.4 | Formatted JSON logs with MDC TraceId / SpanId |
| **API Docs** | Springdoc OpenAPI / Swagger | 2.6.0 | Interactive OpenAPI 3.0 specification |
| **Testing** | JUnit 5 + Testcontainers | 1.20.1 | Ephemeral containerized integration & concurrency tests |

---

## 4. Distributed Idempotency Layer

### The Mechanics

When a client calls `POST /api/v1/payments`, it **must** provide a unique `Idempotency-Key` header (e.g. UUID).

```
Client Request
      │
      ▼
Compute SHA-256 Hash of Payload (customerId + amount + currency + description)
      │
      ▼
Redis SET idempotency:payments:{key} {status: IN_PROGRESS} NX EX 30s
      │
      ├── [Acquired Lock: TRUE] ──> Execute DB Transaction ──> Cache COMPLETED in Redis (24h TTL) ──> Return 201 Created
      │
      └── [Acquired Lock: FALSE]
               │
               ▼
         Fetch existing Redis Record
               │
               ├── Payload Hash Mismatch? ────────> Return 422 Unprocessable Entity
               ├── Status == IN_PROGRESS? ────────> Return 409 Conflict (Retry-After: 2)
               └── Status == COMPLETED?   ────────> Return Cached Response (200 OK, X-Idempotent-Replay: true)
```

### Key Design Pillars

1. **Short `IN_PROGRESS` TTL (30 seconds)**:
   - Prevents permanent lockouts if an application node abruptly crashes mid-transaction.
   - If a crash occurs, the key naturally expires after 30s, allowing safe client retries.
2. **Long `COMPLETED` TTL (24 hours / 86400 seconds)**:
   - Once committed, subsequent retries receive the exact cached response instantaneously without touching PostgreSQL.
3. **Payload Fingerprint Verification**:
   - Computes a deterministic SHA-256 hash of `customerId|amount|currency|description`.
   - Reusing the same idempotency key for a different payment payload throws `IdempotencyPayloadMismatchException` (`422 Unprocessable Entity`).

---

## 5. Double-Entry Financial Ledger

In accordance with strict accounting principles, money is never created or destroyed; every financial event produces balanced debit and credit entries.

### Ledger Operations

#### 1. Payment Creation (`POST /payments`)
For a payment of `1500.00 INR` from customer `cust_123`:
| Account ID | Entry Type | Amount | Currency | Description |
|---|---|---|---|---|
| `cust_123` | **DEBIT** | `1500.00` | `INR` | Customer account deducted |
| `SYSTEM_ESCROW_ACCOUNT` | **CREDIT** | `1500.00` | `INR` | Held in clearing / escrow |

$$\sum \text{Debits} = \sum \text{Credits} = 1500.00 \text{ INR}$$

#### 2. Payment Cancellation (`POST /payments/{id}/cancel`)
Reverses the initial transaction with inverse entries:
| Account ID | Entry Type | Amount | Currency | Description |
|---|---|---|---|---|
| `cust_123` | **CREDIT** | `1500.00` | `INR` | Customer account refunded |
| `SYSTEM_ESCROW_ACCOUNT` | **DEBIT** | `1500.00` | `INR` | Released from escrow |

### Optimistic Locking (`@Version`)

The `Payment` entity carries an `@Version private Long version;` field. Concurrent modification attempts on payment status trigger Spring's `ObjectOptimisticLockingFailureException`, which the global handler translates into a clean `409 Conflict`.

---

## 6. Transactional Outbox Pattern & Kafka Event Streaming

### The Consistency Gap

A standard two-phase commit across PostgreSQL and Kafka is slow and error-prone:
```
saveToDatabase();    // Succeeded
publishToKafka();    // Broker unreachable / Network timeout -> Inconsistent state!
```

### The Solution: Transactional Outbox

1. **Atomic Enqueue**: Within the **same PostgreSQL transaction** as the `Payment` and `LedgerEntry` writes, we persist a row into `outbox_events`:
   ```sql
   INSERT INTO outbox_events (id, aggregate_id, event_type, payload, status, created_at)
   VALUES ('...', '...', 'PAYMENT_CREATED', '{"amount": 1500.00, ...}', 'PENDING', NOW());
   ```
2. **Outbox Worker with `SKIP LOCKED`**:
   A scheduled worker queries pending events using:
   ```sql
   SELECT * FROM outbox_events
   WHERE status = 'PENDING'
     AND (next_retry_at IS NULL OR next_retry_at <= :now)
   ORDER BY created_at ASC
   LIMIT :limit
   FOR UPDATE SKIP LOCKED;
   ```
   > **Why `SKIP LOCKED`?** Multiple application instances can poll the outbox simultaneously without lock contention or duplicate event processing. Each instance claims exclusive ownership of distinct batches.

3. **Exponential Backoff & Dead-Letter Queue (DLQ)**:
   - On publish failure, `retry_count` is incremented and `next_retry_at` is scheduled using exponential backoff:
     $$\text{delay} = \text{baseBackoffMs} \times 2^{(\text{retryCount} - 1)}$$
   - Once retries reach `max-retries` (default: 5), the event is marked `FAILED` and routed to the Kafka Dead Letter Queue topic (`payments.events.dlq`).

---

## 7. Failure Scenarios & Self-Healing Resiliency

| Failure Scenario | System Behavior & Self-Healing Mechanism |
|---|---|
| **App node crashes mid-transaction** | The Redis key is held in `IN_PROGRESS` state with a short 30-second TTL. Once expired, client retries are permitted and processed cleanly. |
| **Kafka broker is offline** | The PostgreSQL transaction succeeds normally. Outbox worker retries with exponential backoff ($1\text{s} \to 2\text{s} \to 4\text{s} \to 8\text{s} \to 16\text{s}$). Once Kafka recovers, events are published without data loss. |
| **Client network drops before receiving HTTP response** | Client retries `POST /payments` with the original `Idempotency-Key`. Redis returns the cached `PaymentResponse` (`200 OK`, `X-Idempotent-Replay: true`) with 0 database writes. |
| **100 concurrent requests with identical Idempotency-Key** | Atomic Redis `SETNX` allows exactly 1 thread to acquire the lock (`201 Created`). 99 threads receive `409 Conflict` (during execution) or cached `200 OK` (after completion). |
| **Client reuses Idempotency-Key with different amount** | SHA-256 payload hash verification detects the mismatch and rejects the request with `422 Unprocessable Entity` (`IDEMPOTENCY_PAYLOAD_MISMATCH`). |

---

## 8. Observability, Metrics & Structured Logging

### Distributed Tracing (OpenTelemetry / Brave)
- Every incoming HTTP request is tagged with a W3C-compliant `TraceId` and `SpanId`.
- The TraceId propagates through database queries, Redis calls, outbox workers, Kafka message headers, and error responses.

### Structured JSON Logging
Logback is configured with Logstash JSON formatting:
```json
{
  "@timestamp": "2026-09-23T10:30:00.123Z",
  "@version": "1",
  "message": "Payment created successfully with paymentId=7c2d1b82-8491-4e94-81d0-9dfa5dbb7e88, amount=1500.00 INR",
  "logger_name": "com.example.payments.service.PaymentService",
  "thread_name": "http-nio-8080-exec-1",
  "level": "INFO",
  "traceId": "c5f87b8d4e9c1a01",
  "spanId": "e1f98a2d3b4c5e6f"
}
```

### Custom Actuator Metrics & Prometheus Gauges
- `payments.outbox.pending.count`: Gauge tracking current backlog of pending outbox events.
- `payments.outbox.failed.count`: Gauge tracking failed outbox events routed to DLQ.
- `/actuator/health`: Deep health checks covering PostgreSQL, Redis, and Kafka.

---

## 9. Database Schema & Flyway Migrations

```
src/main/resources/db/migration/
├── V1__create_payments.sql
├── V2__create_ledger_entries.sql
├── V3__create_outbox_events.sql
└── V4__add_indexes.sql
```

```sql
-- V1: Payments Table
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- V2: Double-Entry Ledger Entries
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    account_id VARCHAR(100) NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- V3: Transactional Outbox Events
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

-- V4: Performance Indexes
CREATE INDEX idx_payments_customer_id ON payments (customer_id);
CREATE INDEX idx_payments_created_at ON payments (created_at DESC);
CREATE INDEX idx_ledger_payment_id ON ledger_entries (payment_id);
CREATE INDEX idx_ledger_account_id ON ledger_entries (account_id);
CREATE INDEX idx_outbox_pending_polling ON outbox_events (created_at ASC) WHERE status = 'PENDING';
```

---

## 10. REST API Documentation & cURL Snippets

### 1. Create Payment (Idempotent)
```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 8d9f4e2a-1c3b-4a5e-9f8d-123456789abc" \
  -d '{
    "customerId": "cust_123",
    "amount": 1500.00,
    "currency": "INR",
    "description": "Order #12345"
  }'
```

**Response (`201 Created` / `X-Idempotent-Replay: false`):**
```json
{
  "paymentId": "7c2d1b82-8491-4e94-81d0-9dfa5dbb7e88",
  "customerId": "cust_123",
  "amount": 1500.00,
  "currency": "INR",
  "description": "Order #12345",
  "status": "CREATED",
  "version": 0,
  "createdAt": "2026-09-23T10:30:00Z",
  "updatedAt": "2026-09-23T10:30:00Z",
  "ledgerEntries": [
    {
      "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "accountId": "cust_123",
      "entryType": "DEBIT",
      "amount": 1500.00,
      "currency": "INR",
      "createdAt": "2026-09-23T10:30:00Z"
    },
    {
      "id": "b1ffcd88-8b1a-4fe7-aa5c-5aa8ac270b22",
      "accountId": "SYSTEM_ESCROW_ACCOUNT",
      "entryType": "CREDIT",
      "amount": 1500.00,
      "currency": "INR",
      "createdAt": "2026-09-23T10:30:00Z"
    }
  ]
}
```

---

### 2. Get Payment by ID
```bash
curl -i -X GET http://localhost:8080/api/v1/payments/7c2d1b82-8491-4e94-81d0-9dfa5dbb7e88
```

---

### 3. List Customer Payments (Paginated)
```bash
curl -i -X GET "http://localhost:8080/api/v1/payments?customerId=cust_123&page=0&size=10"
```

---

### 4. Cancel Payment
```bash
curl -i -X POST "http://localhost:8080/api/v1/payments/7c2d1b82-8491-4e94-81d0-9dfa5dbb7e88/cancel?reason=CustomerRequestedRefund"
```

---

## 11. Testing & Concurrency Verification

The suite includes **Unit Tests**, **Integration Tests**, and **Multithreaded Concurrency Stress Tests** powered by **Testcontainers** (spinning up isolated PostgreSQL, Redis, and Kafka containers).

```bash
# Run all unit tests
mvn test "-Dtest=CurrencyValidatorTest,IdempotencyServiceTest,PaymentServiceTest"

# Run entire integration test suite with Testcontainers
mvn test
```

### Verified Test Matrix

| Test Suite | Scenario Verified | Result |
|---|---|---|
| `CurrencyValidatorTest` | Validates ISO-4217 currency codes (`USD`, `INR`, `EUR`) and rejects invalid strings | ✅ 21/21 Passed |
| `IdempotencyServiceTest` | Tests SETNX lock acquisition, 24h replay, hash mismatch, and conflict detection | ✅ 5/5 Passed |
| `PaymentServiceTest` | Verifies atomic payment creation, balanced double-entry ledgering, and outbox serialization | ✅ 2/2 Passed |
| `PaymentIntegrationTest` | End-to-end integration test with real PostgreSQL, Redis, and Flyway migrations | ✅ Passed |
| `IdempotencyConcurrencyTest` | 50 concurrent threads firing the same Idempotency-Key simultaneously $\to$ exactly 1 payment created | ✅ Passed |
| `OutboxPublisherIntegrationTest`| Verifies scheduled outbox worker publishing to Kafka and event consumption | ✅ Passed |

---

## 12. How to Run (Docker & Local)

### Option 1: Full Docker Compose Setup (One Command)

```bash
git clone https://github.com/example/idempotent-payments-ledger.git
cd idempotent-payments-ledger

docker compose up --build -d
```

Services exposed:
- **API Server & Swagger UI**: http://localhost:8080/swagger-ui.html
- **Spring Actuator Health**: http://localhost:8080/actuator/health
- **Prometheus Metrics**: http://localhost:8080/actuator/prometheus
- **PostgreSQL**: `localhost:5432` (User: `postgres`, Pass: `postgres`, DB: `payments_db`)
- **Redis**: `localhost:6379`
- **Kafka**: `localhost:9092`

---

### Option 2: Local Development Setup

1. **Start Infrastructure**:
   ```bash
   docker compose up postgres redis kafka zookeeper -d
   ```
2. **Run Spring Boot API**:
   ```bash
   mvn spring-boot:run
   ```

---

## 13. Architectural Trade-offs & Deep Dive

| Architecture Choice | Why We Chose It | Alternative Considered | Trade-off / Rationale |
|---|---|---|---|
| **Redis for Idempotency** | In-memory atomic `SETNX` executes in sub-millisecond time. | DB Unique Constraints | DB constraints add lock contention on hot transaction tables; Redis offloads duplicate traffic before hitting the database. |
| **Transactional Outbox Polling** | Clean, portable, zero external daemon requirements. | Debezium / CDC (Change Data Capture) | Debezium requires Kafka Connect infrastructure. Polling with `SKIP LOCKED` is lightweight, fault-tolerant, and easy to deploy in V1. |
| **Double-Entry Ledgering** | Provides balanced, auditable financial records (`DEBIT` + `CREDIT`). | Single Balance Column | Updating a mutable `balance` column loses transaction provenance and makes audit reconciliation impossible. |
| **Optimistic Locking (`@Version`)** | High throughput without holding database row locks during external RPC calls. | Pessimistic Locking (`SELECT FOR UPDATE`) | Pessimistic locking creates database connection exhaustion under high concurrency. Optimistic locking is superior for low-conflict write scenarios. |
