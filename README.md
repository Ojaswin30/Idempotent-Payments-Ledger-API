# Production-Grade Idempotent Payments Ledger API & NovaStore

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React 18](https://img.shields.io/badge/React-18-61dafb.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue.svg)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-5.4-purple.svg)](https://vitejs.dev/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-3.4-38bdf8.svg)](https://tailwindcss.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-black.svg)](https://kafka.apache.org/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-1.20-orange.svg)](https://www.testcontainers.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A production-grade, distributed financial transaction system engineered to guarantee **strict idempotency**, **transactional consistency**, and **eventual consistency** under high concurrency and failure scenarios. Includes an e-commerce storefront (**NovaStore** in React + TypeScript) and an interactive **Developer Testing Lab & Ledger Inspector**.

---

## Table of Contents

1. [Problem Statement & Motivation](#1-problem-statement--motivation)
2. [Target Architecture](#2-target-architecture)
3. [Technology Stack](#3-technology-stack)
4. [Distributed Idempotency Layer Deep Dive](#4-distributed-idempotency-layer-deep-dive)
5. [Double-Entry Financial Bookkeeping & Ledger](#5-double-entry-financial-bookkeeping--ledger)
6. [Transactional Outbox Pattern & Kafka Event Streaming](#6-transactional-outbox-pattern--kafka-event-streaming)
7. [User Interfaces: Storefront vs. Developer Lab](#7-user-interfaces-storefront-vs-developer-lab)
8. [Failure Scenarios & Self-Healing Resiliency](#8-failure-scenarios--self-healing-resiliency)
9. [Observability, Metrics & Structured Logging](#9-observability-metrics--structured-logging)
10. [Database Schema & Flyway Migrations](#10-database-schema--flyway-migrations)
11. [REST API Documentation & cURL Snippets](#11-rest-api-documentation--curl-snippets)
12. [Testing & Concurrency Verification](#12-testing--concurrency-verification)
13. [How to Run (Docker, Local & Frontend)](#13-how-to-run-docker-local--frontend)
14. [Architectural Trade-offs & Engineering Decisions](#14-architectural-trade-offs--engineering-decisions)

---

## 1. Problem Statement & Motivation

In distributed payment networks, network drops, client retries, and race conditions are everyday realities. A naive REST implementation exposes systems to critical failure modes:

1. **Duplicate Charging**: A client sends `POST /payments`, the database commits the transaction, but the mobile network disconnects before the HTTP response arrives. The client retries, causing the customer to be double-charged.
2. **Concurrent Request Races**: Rapid double-clicking on checkout buttons sends identical requests simultaneously, racing to write duplicate rows.
3. **Dual-Write Inconsistency (DB vs Broker)**: Saving to PostgreSQL and publishing to Kafka in separate steps causes lost events if the message broker is temporarily unreachable or the app crashes.
4. **Unbalanced Financial Ledger**: Mutating a mutable `balance` column without an immutable double-entry ledger makes reconciliation and audit tracking impossible.

This system solves all four challenges through **atomic Redis distributed locking**, **SHA-256 request fingerprinting**, **balanced double-entry accounting**, and a **Transactional Outbox with `SELECT ... FOR UPDATE SKIP LOCKED` and exponential backoff DLQ routing**.

---

## 2. Target Architecture

```
                    ┌────────────────────────┐
                    │    NovaStore Client    │
                    │  (React 18 Storefront) │
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

### Backend & Infrastructure
| Component | Technology | Version | Purpose |
|---|---|---|---|
| **Framework** | Spring Boot | 3.3.4 | REST MVC, Transaction Management, Dependency Injection |
| **Language** | Java | 17 / 21 | Modern records, pattern matching, typed concurrency |
| **Database** | PostgreSQL | 16 | ACID financial transactions, JSONB outbox events |
| **Cache / Lock** | Redis | 7.x | Atomic `SETNX` locking, 24h cached replay |
| **Message Broker** | Apache Kafka | 3.x (KRaft) | Asynchronous event publishing & DLQ routing |
| **Migrations** | Flyway | 10.x | Versioned database schema migrations (`V1` - `V4`) |
| **Tracing** | OpenTelemetry / Brave | Latest | W3C distributed trace context propagation |
| **Logging** | Logstash Logback | 7.4 | Structured JSON logging with trace/span metadata |
| **API Docs** | Springdoc OpenAPI | 2.6.0 | OpenAPI 3.0 specification & Swagger UI |
| **Testing** | Testcontainers | 1.20.1 | Ephemeral containerized integration & concurrency tests |

### Frontend Applications
| Application | Tech Stack | Role | URL |
|---|---|---|---|
| **NovaStore Storefront** | React 18, TypeScript, Vite, Tailwind CSS, Lucide | Consumer Checkout App | `http://localhost:5173` |
| **Dev Testing Lab** | Vanilla HTML5 / JS, Tailwind CSS, FontAwesome | Engineering & Ledger Inspector | `http://localhost:8080` |

---

## 4. Distributed Idempotency Layer Deep Dive

```
Client Request (POST /api/v1/payments with Header: Idempotency-Key)
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

### Key Design Highlights:
1. **Short `IN_PROGRESS` TTL (30 seconds)**:
   - If an application node crashes mid-transaction, the key automatically expires after 30s, allowing client retries without manual admin intervention.
2. **Long `COMPLETED` TTL (24 hours)**:
   - Subsequent retries receive the original `PaymentResponse` instantly with `X-Idempotent-Replay: true` and 0 database writes.
3. **SHA-256 Payload Fingerprinting**:
   - Reusing an existing key with altered parameters (e.g. changing the amount) throws `IdempotencyPayloadMismatchException` (`422 Unprocessable Entity`).

---

## 5. Double-Entry Financial Bookkeeping & Ledger

Money is never created or destroyed; every payment and cancellation records balanced debit and credit entries.

### Ledger Structure
- **Payment Creation (`POST /payments`)**:
  - `DEBIT` `cust_123` (₹1500.00 INR)
  - `CREDIT` `SYSTEM_ESCROW_ACCOUNT` (₹1500.00 INR)
  $$\sum \text{Debits} = \sum \text{Credits} = 1500.00 \text{ INR} \quad (\Delta = 0.00)$$

- **Payment Cancellation (`POST /payments/{id}/cancel`)**:
  - `CREDIT` `cust_123` (₹1500.00 INR)
  - `DEBIT` `SYSTEM_ESCROW_ACCOUNT` (₹1500.00 INR)

### Optimistic Locking (`@Version`)
The `Payment` entity carries `@Version private Long version;`. Concurrent cancellation attempts trigger `ObjectOptimisticLockingFailureException`, translated to `409 Conflict`.

---

## 6. Transactional Outbox Pattern & Kafka Event Streaming

### The Consistency Guarantee
In a single atomic PostgreSQL transaction:
1. `Payment` is persisted.
2. `LedgerEntry` records are persisted.
3. `OutboxEvent` (containing JSONB payload of `PaymentCreatedEvent`) is staged with `status = 'PENDING'`.

### The Outbox Worker Job
```sql
SELECT * FROM outbox_events
WHERE status = 'PENDING'
  AND (next_retry_at IS NULL OR next_retry_at <= :now)
ORDER BY created_at ASC
LIMIT :limit
FOR UPDATE SKIP LOCKED;
```
- **`SKIP LOCKED`**: Multiple worker nodes process distinct outbox batches concurrently with zero row lock contention.
- **Exponential Retry Backoff**: On broker failure, delays next attempt by:
  $$\text{delay} = \text{baseBackoffMs} \times 2^{(\text{retryCount} - 1)}$$
- **Dead-Letter Queue (DLQ)**: When `retry_count >= 5`, the event is marked `FAILED` and routed to `payments.events.dlq`.

---

## 7. User Interfaces: Storefront vs. Developer Lab

### 1. NovaStore E-Commerce Storefront (`frontend/`)
A consumer-facing storefront built with **React 18, TypeScript, and Vite**:
- **Product Catalog & Cart**: Dynamic quantity updates, 18% GST tax calculation, slide-over cart drawer.
- **Client-Side Idempotency**: Automatically creates and manages UUID `Idempotency-Key` headers on checkout.
- **Flaky Network / Double-Click Simulator**: An educational toggle directly in checkout that rapidly fires concurrent requests to visually prove that duplicate clicks never double-charge.
- **Order Receipts & Tracking**: Shows payment status, unique payment IDs, and ledger breakdown.
- **My Orders & Instant Refund**: View past orders and trigger one-click cancellation with ledger reversals.

```bash
cd frontend
npm install
npm run dev
# Open http://localhost:5173
```

### 2. Developer Testing Lab & Ledger Inspector
Embedded directly in Spring Boot at **`http://localhost:8080/index.html`**:
- Manual `Idempotency-Key` customization and payload testing.
- **10x Concurrent Multi-Thread Stress Fire**: Verifies Redis atomic SETNX under load.
- **Live Double-Entry General Ledger Table**: Inspects real-time debit/credit rows and balance metrics.
- **Raw HTTP Telemetry Inspector**: Displays response codes, latencies, and headers.

---

## 8. Failure Scenarios & Self-Healing Resiliency

| Failure Scenario | System Handling & Resiliency Mechanism |
|---|---|
| **App node crashes mid-transaction** | Redis `IN_PROGRESS` key expires automatically after 30 seconds, allowing client retries to proceed. |
| **Kafka broker is offline** | PostgreSQL transaction succeeds. Outbox worker retries with exponential backoff ($1\text{s} \to 2\text{s} \to 4\text{s} \to 8\text{s} \to 16\text{s}$). Once Kafka recovers, all events are published in order. |
| **Client network drops after DB commit** | Client retries with the original `Idempotency-Key`. Redis returns the cached `PaymentResponse` (`200 OK`, `X-Idempotent-Replay: true`) with 0 database writes. |
| **100 concurrent requests with same key** | Redis `SETNX` permits exactly 1 thread (`201 Created`). 99 threads receive `409 Conflict` (during execution) or cached `200 OK` (after completion). |
| **Key reused with different amount** | SHA-256 fingerprint mismatch triggers `422 Unprocessable Entity` (`IDEMPOTENCY_PAYLOAD_MISMATCH`). |

---

## 9. Observability, Metrics & Structured Logging

### Distributed Tracing (W3C Standard)
Every request is assigned a `TraceId` and `SpanId`, propagated across HTTP headers, database queries, Redis calls, Kafka event headers, and error responses.

### Structured JSON Logs (Logstash Encoder)
```json
{
  "@timestamp": "2026-09-23T10:30:00.123Z",
  "message": "Payment created successfully with paymentId=7c2d1b82-8491-4e94-81d0-9dfa5dbb7e88, amount=1500.00 INR",
  "logger_name": "com.example.payments.service.PaymentService",
  "level": "INFO",
  "traceId": "c5f87b8d4e9c1a01",
  "spanId": "e1f98a2d3b4c5e6f"
}
```

### Actuator Metrics & Prometheus Endpoints
- `payments.outbox.pending.count`: Gauge tracking pending outbox backlog.
- `payments.outbox.failed.count`: Gauge tracking failed DLQ events.
- `/actuator/health`: Health status covering PostgreSQL, Redis, and Kafka.

---

## 10. Database Schema & Flyway Migrations

```
src/main/resources/db/migration/
├── V1__create_payments.sql
├── V2__create_ledger_entries.sql
├── V3__create_outbox_events.sql
└── V4__add_indexes.sql
```

```sql
-- Payments Table
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

-- Double-Entry Ledger Entries
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    account_id VARCHAR(100) NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Transactional Outbox Events
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

-- Performance Indexes
CREATE INDEX idx_payments_customer_id ON payments (customer_id);
CREATE INDEX idx_payments_created_at ON payments (created_at DESC);
CREATE INDEX idx_ledger_payment_id ON ledger_entries (payment_id);
CREATE INDEX idx_ledger_account_id ON ledger_entries (account_id);
CREATE INDEX idx_outbox_pending_polling ON outbox_events (created_at ASC) WHERE status = 'PENDING';
```

---

## 11. REST API Documentation & cURL Snippets

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

## 12. Testing & Concurrency Verification

```bash
# Run unit tests
mvn test "-Dtest=CurrencyValidatorTest,IdempotencyServiceTest,PaymentServiceTest"

# Run integration & concurrency tests (requires Testcontainers / Docker)
mvn test
```

### Verified Test Matrix

| Test Suite | Scenario Verified | Status |
|---|---|---|
| `CurrencyValidatorTest` | Validates ISO-4217 currency codes (`USD`, `INR`, `EUR`) and rejects invalid codes | ✅ Passed (21/21) |
| `IdempotencyServiceTest` | Tests SETNX lock acquisition, 24h replay, hash mismatch, and conflict detection | ✅ Passed (5/5) |
| `PaymentServiceTest` | Verifies atomic payment creation, balanced double-entry ledgering, and outbox serialization | ✅ Passed (2/2) |
| `PaymentIntegrationTest` | End-to-end integration test with real PostgreSQL, Redis, and Flyway migrations | ✅ Passed |
| `IdempotencyConcurrencyTest` | 50 concurrent threads firing the same Idempotency-Key $\to$ exactly 1 payment created | ✅ Passed |
| `OutboxPublisherIntegrationTest`| Verifies scheduled outbox worker publishing to Kafka and event consumption | ✅ Passed |

---

## 13. How to Run (Docker, Local & Frontend)

### Step 1: Start Full Backend Stack via Docker Compose
```bash
docker compose up --build -d
```

Services started:
- **API Server & Developer Lab**: http://localhost:8080/
- **Interactive Swagger UI**: http://localhost:8080/swagger-ui.html
- **Spring Actuator Health**: http://localhost:8080/actuator/health
- **PostgreSQL 16**: `localhost:5432`
- **Redis 7**: `localhost:6379`
- **Apache Kafka (KRaft)**: `localhost:9092`

---

### Step 2: Start NovaStore React Storefront
```bash
cd frontend
npm install
npm run dev
```
Open **`http://localhost:5173`** in your browser.

---

## 14. Architectural Trade-offs & Engineering Decisions

| Architecture Choice | Why We Chose It | Alternative Considered | Trade-off / Rationale |
|---|---|---|---|
| **Redis for Idempotency** | In-memory atomic `SETNX` executes in sub-millisecond time. | DB Unique Constraints | DB constraints add lock contention on hot transaction tables; Redis offloads duplicate traffic before touching PostgreSQL. |
| **Transactional Outbox Polling** | Clean, portable, zero external daemon requirements. | Debezium / CDC | Debezium requires Kafka Connect cluster infrastructure. Polling with `SKIP LOCKED` is lightweight, highly reliable, and easy to deploy in V1. |
| **Double-Entry Bookkeeping** | Provides balanced, auditable financial records (`DEBIT` + `CREDIT`). | Single Balance Column | Updating a mutable balance column destroys audit provenance and makes financial reconciliation impossible. |
| **Optimistic Locking (`@Version`)** | High throughput without holding database row locks during external RPC calls. | Pessimistic Locking (`SELECT FOR UPDATE`) | Pessimistic locking creates database connection pool exhaustion under high concurrency. Optimistic locking is superior for payment state transitions. |
