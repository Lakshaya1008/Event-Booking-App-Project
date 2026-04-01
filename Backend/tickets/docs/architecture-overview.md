# Event Booking App - Architecture Overview & Developer Onboarding

Welcome to the Event Booking App backend! This document provides a "Big Picture" conceptual understanding of how the system is designed. While `application-report.md` will tell you *exactly* what a single file does, this document explains *how the pieces fit together*.

---

## 1. System Architecture

The application is built on **Spring Boot 3** and **Java 17+**, using a monolithic architecture backed by:
- **PostgreSQL**: The primary relational database for all domain data (Events, Tickets, Users, Approvals).
- **Keycloak**: An external Identity and Access Management (IAM) server used exclusively for Authentication and JWT generation.

The backend acts as an OAuth2 Resource Server. It validates JWT tokens issued by Keycloak but maintains its own local `users` table to enforce application-specific workflows (like admin approvals).

---

## 2. Authentication & The Approval Workflow

Security in this application is handled in two distinct layers: **Token Verification** and **Business Approval**.

### Layer 1: Keycloak Authentication
1. A user authenticates via the frontend against Keycloak.
2. Keycloak issues a JWT containing the user's details and roles (e.g., `ROLE_ATTENDEE`, `ROLE_ORGANIZER`).
3. The Spring Boot backend intercepts incoming requests, verifies the JWT signature (via `SecurityConfig`), and extracts the roles.

### Layer 2: The Approval Gate (`ApprovalGateFilter`)
Just because a user has a valid Keycloak token does **not** mean they can use the application. 
- When a user registers, they are inserted into the local PostgreSQL database with an `ApprovalStatus` of `PENDING`.
- An Admin must explicitly review and approve them (changing their status to `APPROVED`).
- The `ApprovalGateFilter` intercepts *every authenticated request*. If the user's local DB status is `PENDING` or `REJECTED`, the filter blocks the request with a `403 Forbidden` error, safely isolating them from all business endpoints.

> [!NOTE] 
> Public endpoints (like `/api/v1/auth/register`), Swagger documentation, and Spring Actuator health checks explicitly bypass the Approval Gate.

---

## 3. Core Domain Entities & Relationships

The database schema is highly normalized. Here is how the core event and ticketing data relates:

```mermaid
erDiagram
    EVENT ||--o{ TICKET_TYPE : has
    EVENT ||--o{ EVENT_STAFF : employs
    TICKET_TYPE ||--o{ TICKET : issues
    TICKET_TYPE ||--o{ DISCOUNT : offers
    TICKET ||--o| QR_CODE : generates
    TICKET ||--o{ TICKET_VALIDATION : records
    USER ||--o{ TICKET : owns
    USER ||--o{ EVENT : creates/manages
```

- **Event**: The root aggregate. Holds high-level details (venue, timing, status).
- **TicketType**: Belongs to an Event. Defines pricing and capacity (e.g., "VIP Pass", "General Admission"). *Capacity limits are enforced here.*
- **Ticket**: Represents a single purchased admission belonging to a User.
- **QrCode**: A 1-to-1 secure mapping to a Ticket, generated asynchronously to allow validation at the door.
- **TicketValidation**: An append-only log of every time a ticket is scanned, preventing duplicate check-ins.

---

## 4. Concurrency & Data Integrity (Preventing Oversales)

One of the most complex challenges in event ticketing is ensuring that high-demand events do not accidentally oversell tickets if hundreds of users try to purchase them simultaneously.

This application uses a defense-in-depth strategy mixing **Pessimistic Locking**, **Optimistic Locking**, and **Database Constraints**.

### 1. Pessimistic Locking on the Event Row
When a user attempts to purchase a ticket, the `TicketTypeServiceImpl` issues a `SELECT ... FOR UPDATE` query on the parent `Event` row within a transaction. 
- This physically locks the event row in PostgreSQL. 
- Other concurrent purchase threads for *any ticket type* in that event must wait in line. 
- This strictly synchronizes the checkout process per event, guaranteeing that the `totalAvailable` inventory check is mathematically safe.

### 2. Optimistic Locking (`@Version`)
Entities like `Event` and `TicketType` contain a `@Version` field. If two admins attempt to update an event simultaneously, Spring Data JPA will throw an `ObjectOptimisticLockingFailureException` on the second transaction, which the `GlobalExceptionHandler` cleanly maps to a `409 CONFLICT` (Concurrent Modification).

### 3. Database Integrity Constraints
All capacity drops and ticket insertions rely on hard SQL constraints. Even if a race condition theoretically bypassed Java, PostgreSQL `CHECK (total_available >= 0)` constraints would abort the transaction, guaranteeing zero data corruption.

---

## 5. Security & Ownership Guards

Beyond roles, the Service layer enforces strict resource ownership:
- An Organizer can only update or delete an `Event` *they* created.
- An Attendee can only view a `Ticket` *they* purchased.
- Validation Staff can only scan tickets for an `Event` *they* are assigned to.

These checks are strictly enforced in the service layer using the `RequestUtil.getCurrentRequest()` helper and `AuthorizationService` logic, ensuring horizontal scaling without security leaks.

---

## 6. Where to go from here?

- **To test APIs:** Read the `docs/api-testing-guide.md`.
- **To view exactly which files map to which feature:** Read `docs/codebase-functionalities-and-file-map.md`.
- **To inspect the methods or dependencies of a specific service:** Open `docs/application-report.md`.
