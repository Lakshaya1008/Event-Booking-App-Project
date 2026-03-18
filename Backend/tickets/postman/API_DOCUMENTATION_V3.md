# Event Booking App — Complete API Reference and Postman Guide

This document reflects the ACTUAL codebase. Every endpoint, every request field, every response field is sourced directly from the controllers and DTOs.

**Last Updated:** March 18, 2026
**API Version:** v1.0
**Base URL:** `http://localhost:8081`
**Auth:** OAuth2 Bearer JWT (Keycloak)
**Pagination:** Spring Pageable — use `?page=0&size=20&sort=createdAt,desc`
**Max page size:** 50 (server enforces — `?size=999` is capped at 50)

---

## Prerequisites and Environment

| Service | URL | Credentials |
|---------|-----|-------------|
| Spring Boot App | http://localhost:8081 | — |
| Keycloak Admin | http://localhost:9090 | admin / admin |
| Adminer (DB UI) | http://localhost:8888 | System: PostgreSQL, Server: db, User: postgres, Pass: postgres123 |

Start everything: `docker-compose up -d`

---

## Authentication — Get a Token

**POST** `http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token`

**Content-Type:** `application/x-www-form-urlencoded`

| Field | Value |
|-------|-------|
| `grant_type` | `password` |
| `client_id` | `event-ticket-platform-app` |
| `client_secret` | *(from Keycloak → Clients → Credentials tab)* |
| `username` | your username |
| `password` | your password |

**Response:**
```json
{
  "access_token": "eyJhbGc...",
  "expires_in": 300,
  "token_type": "Bearer"
}
```

Use the token as: `Authorization: Bearer <access_token>`

---

## ⚠️ Approval Gate — CRITICAL

All authenticated endpoints block users unless their account is APPROVED.

**Bypass paths (no approval check):**
- `POST /api/v1/auth/register`
- `POST /api/v1/invites/redeem`
- `/actuator/**`
- `/swagger-ui/**`, `/v3/api-docs/**`

**Approval states:**

| State | Effect |
|-------|--------|
| PENDING | 403 `APPROVAL_PENDING` on all non-bypass endpoints |
| APPROVED | Access allowed (subject to role checks) |
| REJECTED | 403 `APPROVAL_REJECTED` on all non-bypass endpoints |

---

## Error Response Shape

Every error returns `ErrorDto`:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Email is required",
  "statusCode": 400,
  "statusDescription": "BAD REQUEST - Invalid input data",
  "timestamp": "2026-03-18T10:30:00",
  "path": "/api/v1/auth/register",
  "possibleCauses": ["Missing required field"],
  "solutions": ["Provide the required field"]
}
```

For 400 validation errors, ALL failing fields are returned simultaneously in the `validationErrors` list (not just the first one).

---

## Role Summary

| Role | Can Access |
|------|-----------|
| ADMIN | User governance, approvals, audit logs. Cannot access event/ticket/published-events endpoints |
| ORGANIZER | Own events, ticket types, discounts, staff management, invite codes (STAFF only) |
| ATTENDEE | Published events, ticket purchase, own tickets, QR codes |
| STAFF | Ticket validation for assigned events only |

Roles live in `realm_access.roles` in the JWT — never in the request body.

---

---

# ENDPOINT REFERENCE

---

## 0. Auth

---

### POST /api/v1/auth/register

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/auth/register` |
| **Auth required** | No |
| **Role required** | None (public) |
| **Approval gate** | Bypassed |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `email` | String | ✅ Required | `@NotBlank` `@Email` `@Size(max=255)` |
| `password` | String | ✅ Required | `@NotBlank` `@Size(min=8, max=128)` must contain uppercase + lowercase + digit |
| `name` | String | ✅ Required | `@NotBlank` `@Size(min=2, max=100)` |
| `inviteCode` | String | ❌ Optional | `@Pattern(^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$)` — uppercase letters and digits only |

**Minimum valid payload:**
```json
{
  "email": "user@example.com",
  "password": "Password1",
  "name": "John Doe"
}
```

**Full payload (with invite code):**
```json
{
  "email": "user@example.com",
  "password": "Password1",
  "name": "John Doe",
  "inviteCode": "ABCD-1234-EFGH-5678"
}
```

**Success Response — 201 Created:**
```json
{
  "message": "Registration successful! Your account is pending admin approval.",
  "email": "user@example.com",
  "requiresApproval": true,
  "assignedRole": "ATTENDEE",
  "instructions": "You will receive an email once your account has been reviewed."
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | Missing/invalid fields — all failures listed together |
| 400 | `INVALID_INVITE_CODE` | Code found but is expired / redeemed / revoked |
| 404 | `INVITE_CODE_NOT_FOUND` | Invite code string not in database |
| 409 | `EMAIL_ALREADY_REGISTERED` | Email already in use |
| 422 | `REGISTRATION_FAILED` | Keycloak/DB system error |

---

---

## 1. Event Management — /api/v1/events

**Role required for all:** ORGANIZER (must own the event for PUT/GET/{id}/DELETE)

---

### POST /api/v1/events

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/events` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER |
| **Approval gate** | Yes |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `name` | String | ✅ Required | `@NotBlank` `@Size(max=200)` |
| `venue` | String | ✅ Required | `@NotBlank` `@Size(max=500)` |
| `status` | String | ✅ Required | `@NotNull` — must be `DRAFT`, `PUBLISHED`, or `CANCELLED` |
| `ticketTypes` | Array | ✅ Required | `@NotEmpty` `@Valid` — at least 1 element |
| `ticketTypes[].name` | String | ✅ Required | `@NotBlank` |
| `ticketTypes[].price` | Decimal | ✅ Required | `@NotNull` `@DecimalMin("0.00")` — 0.00 valid (free event) |
| `ticketTypes[].totalAvailable` | Integer | ✅ Required | `@NotNull` `@Min(1)` |
| `ticketTypes[].description` | String | ❌ Optional | — |
| `start` | DateTime | ❌ Optional | Format: `YYYY-MM-DDTHH:mm:ss` |
| `end` | DateTime | ❌ Optional | Must be after `start` if both provided |
| `salesStart` | DateTime | ❌ Optional | |
| `salesEnd` | DateTime | ❌ Optional | Must be after `salesStart` if both provided |
| `maxCapacity` | Integer | ❌ Optional | `@Min(1)` if provided — null = no cap |

**Minimum valid payload:**
```json
{
  "name": "Tech Conference 2025",
  "venue": "Convention Center",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "name": "General Admission",
      "price": 199.99,
      "totalAvailable": 100
    }
  ]
}
```

**Full payload:**
```json
{
  "name": "Tech Conference 2025",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "venue": "Convention Center, Building A",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "maxCapacity": 500,
  "ticketTypes": [
    {
      "name": "Early Bird",
      "price": 149.99,
      "description": "Limited discounted slots",
      "totalAvailable": 100
    },
    {
      "name": "Regular",
      "price": 199.99,
      "description": "Standard admission",
      "totalAvailable": 400
    }
  ]
}
```

**Success Response — 201 Created:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Tech Conference 2025",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "venue": "Convention Center, Building A",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "id": "uuid",
      "name": "Early Bird",
      "price": 149.99,
      "description": "Limited discounted slots",
      "totalAvailable": 100,
      "createdAt": "2026-03-18T10:00:00",
      "updatedAt": "2026-03-18T10:00:00"
    }
  ],
  "createdAt": "2026-03-18T10:00:00",
  "updatedAt": "2026-03-18T10:00:00"
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | Missing required fields, invalid status value, totalAvailable < 1 |
| 409 | `INVALID_BUSINESS_STATE` | `end` not after `start`; `salesEnd` not after `salesStart` |
| 403 | `ACCESS_DENIED` | Not ORGANIZER role |

---

### PUT /api/v1/events/{eventId}

| | |
|--|--|
| **Method** | PUT |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own event) |
| **Approval gate** | Yes |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation / Notes |
|-------|------|----------|--------------------|
| `id` | UUID | ❌ Optional | If provided, must match URL `{eventId}` |
| `name` | String | ✅ Required | `@NotBlank` `@Size(max=200)` |
| `venue` | String | ✅ Required | `@NotBlank` `@Size(max=500)` |
| `status` | String | ✅ Required | `@NotNull` — `DRAFT`, `PUBLISHED`, or `CANCELLED` |
| `ticketTypes` | Array | ✅ Required | `@NotEmpty` `@Valid` |
| `ticketTypes[].id` | UUID | ❌ Optional | Omit to create a new type inline; include to update existing |
| `ticketTypes[].name` | String | ✅ Required | `@NotBlank` |
| `ticketTypes[].price` | Decimal | ✅ Required | `@NotNull` `@DecimalMin("0.00")` |
| `ticketTypes[].description` | String | ❌ Optional | |
| `ticketTypes[].totalAvailable` | Integer | ❌ Optional | Cannot be set below active (non-cancelled) sold count |
| `start` | DateTime | ❌ Optional | |
| `end` | DateTime | ❌ Optional | Must be after `start` if both provided |
| `salesStart` | DateTime | ❌ Optional | |
| `salesEnd` | DateTime | ❌ Optional | Must be after `salesStart` if both provided |
| `maxCapacity` | Integer | ❌ Optional | **Always send current value to preserve it** — omitting sends null which removes the cap |

**Minimum valid payload:**
```json
{
  "name": "Updated Conference",
  "venue": "Updated Venue",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "id": "{{ticket_type_id}}",
      "name": "General Admission",
      "price": 199.99,
      "totalAvailable": 200
    }
  ]
}
```

**Full payload:**
```json
{
  "name": "Updated Tech Conference 2025",
  "start": "2025-12-15T10:00:00",
  "end": "2025-12-15T19:00:00",
  "venue": "Updated Convention Center",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "maxCapacity": 500,
  "ticketTypes": [
    {
      "id": "{{ticket_type_id}}",
      "name": "Updated Early Bird",
      "price": 149.99,
      "description": "Updated description",
      "totalAvailable": 120
    }
  ]
}
```

**Cancel event payload (bulk-cancels all PURCHASED tickets):**
```json
{
  "name": "Tech Conference 2025",
  "venue": "Convention Center",
  "status": "CANCELLED",
  "ticketTypes": [
    { "id": "{{ticket_type_id}}", "name": "General", "price": 199.99, "totalAvailable": 100 }
  ]
}
```

**Success Response — 200 OK:**
```json
{
  "id": "uuid",
  "name": "Updated Tech Conference 2025",
  "start": "2025-12-15T10:00:00",
  "end": "2025-12-15T19:00:00",
  "venue": "Updated Convention Center",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "id": "uuid",
      "name": "Updated Early Bird",
      "price": 149.99,
      "description": "Updated description",
      "totalAvailable": 120,
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | Missing required fields |
| 400 | `EVENT_UPDATE_ERROR` | Body `id` does not match path `{eventId}` |
| 403 | `ACCESS_DENIED` | Not owner of this event |
| 404 | `EVENT_NOT_FOUND` | Event ID not found |
| 404 | `TICKET_TYPE_NOT_FOUND` | A `ticketTypes[].id` doesn't belong to this event |
| 409 | `INVALID_BUSINESS_STATE` | Attempting to update a CANCELLED event; re-publishing CANCELLED event; `end` before `start`; `maxCapacity` below sold count; removing ticket type with active tickets |

---

### GET /api/v1/events

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events?page=0&size=20&sort=start,desc` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER |
| **Approval gate** | Yes |
| **Body** | None |

Returns only the authenticated organizer's own events.

**Success Response — 200 OK:**
```json
{
  "content": [
    {
      "id": "uuid",
      "name": "Tech Conference 2025",
      "start": "2025-12-15T09:00:00",
      "end": "2025-12-15T18:00:00",
      "venue": "Convention Center",
      "salesStart": "2025-11-01T00:00:00",
      "salesEnd": "2025-12-14T23:59:59",
      "status": "PUBLISHED",
      "ticketTypes": [
        { "id": "uuid", "name": "Early Bird", "price": 149.99 }
      ]
    }
  ],
  "totalElements": 3,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### GET /api/v1/events/{eventId}

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Approval gate** | Yes |
| **Body** | None |

**Success Response — 200 OK:**
```json
{
  "id": "uuid",
  "name": "Tech Conference 2025",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "venue": "Convention Center",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "id": "uuid",
      "name": "Early Bird",
      "price": 149.99,
      "description": "Limited discounted slots",
      "totalAvailable": 100,
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

**Error Responses:** 404 (not found or not owned by this organizer)

---

### DELETE /api/v1/events/{eventId}

| | |
|--|--|
| **Method** | DELETE |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Approval gate** | Yes |
| **Body** | None |

**Business rule:** Cannot delete if any active (non-cancelled) tickets exist. Cancel the event first.

**Success Response — 204 No Content**

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 409 | `INVALID_BUSINESS_STATE` | Active tickets exist — message includes count |
| 403 | `ACCESS_DENIED` | Not owner |

---

### GET /api/v1/events/{eventId}/sales-dashboard

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/sales-dashboard` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Success Response — 200 OK:**
```json
{
  "eventName": "Tech Conference 2025",
  "totalTicketsSold": 45,
  "totalRevenueBeforeDiscount": 8955.00,
  "totalDiscountGiven": 895.50,
  "totalRevenueFinal": 8059.50,
  "ticketTypeBreakdown": [
    {
      "ticketTypeName": "Early Bird",
      "basePrice": 199.00,
      "totalAvailable": 100,
      "sold": 45,
      "remaining": 55,
      "revenueBeforeDiscount": 8955.00,
      "discountGiven": 895.50,
      "revenueFinal": 8059.50
    }
  ]
}
```

**Note:** CANCELLED tickets excluded from all counts and revenue. `remaining` is `null` when `totalAvailable` is null (unlimited ticket type — no cap set).

---

### GET /api/v1/events/{eventId}/attendees-report

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/attendees-report` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Success Response — 200 OK:**
```json
{
  "eventName": "Tech Conference 2025",
  "totalAttendees": 45,
  "attendees": [
    {
      "attendeeName": "Jane Doe",
      "attendeeEmail": "jane@example.com",
      "ticketType": "Early Bird",
      "ticketStatus": "PURCHASED",
      "purchaseDate": "2025-11-10T14:30:00",
      "validationCount": 1
    }
  ]
}
```

**Note:** CANCELLED tickets excluded.

---

### GET /api/v1/events/{eventId}/sales-report.xlsx

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/sales-report.xlsx` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Success Response — 200 OK**
- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="<event-name>_sales_report_<timestamp>.xlsx"`

---

---

## 2. Published Events — /api/v1/published-events

**Role required:** ATTENDEE, ORGANIZER, or STAFF
**Note: ADMIN cannot access these endpoints — returns 403**

---

### GET /api/v1/published-events

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/published-events?page=0&size=20&sort=start,asc` |
| **Auth required** | Yes |
| **Role required** | ATTENDEE or ORGANIZER or STAFF |
| **Body** | None |
| **Query params** | `q` (optional) — full-text search on event name and venue |

**Success Response — 200 OK:**
```json
{
  "content": [
    {
      "id": "uuid",
      "name": "Tech Conference 2025",
      "start": "2025-12-15T09:00:00",
      "end": "2025-12-15T18:00:00",
      "venue": "Convention Center"
    }
  ],
  "totalElements": 5,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### GET /api/v1/published-events/{eventId}

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/published-events/{{event_id}}` |
| **Auth required** | Yes |
| **Role required** | ATTENDEE or ORGANIZER or STAFF |
| **Body** | None |

Returns PUBLISHED events only. DRAFT and CANCELLED events return 404.

**Success Response — 200 OK:**
```json
{
  "id": "uuid",
  "name": "Tech Conference 2025",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "venue": "Convention Center",
  "ticketTypes": [
    {
      "id": "uuid",
      "name": "Early Bird",
      "price": 149.99,
      "description": "Limited discounted slots"
    }
  ]
}
```

**Note:** `totalAvailable`, `salesStart`, `salesEnd`, `maxCapacity` are NOT exposed in this public response.

**Error Responses:** 404 (not found or not PUBLISHED)

---

---

## 3. Ticket Management — /api/v1/tickets

**Role required:** ATTENDEE or ORGANIZER
**Note: STAFF cannot access these endpoints — returns 403**

---

### GET /api/v1/tickets

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/tickets?page=0&size=20&sort=id,desc` |
| **Auth required** | Yes |
| **Role required** | ATTENDEE or ORGANIZER |
| **Body** | None |

Returns only tickets belonging to the authenticated user.

**Success Response — 200 OK:**
```json
{
  "content": [
    {
      "id": "uuid",
      "status": "PURCHASED",
      "ticketType": {
        "id": "uuid",
        "name": "Early Bird",
        "price": 149.99
      }
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### GET /api/v1/tickets/{ticketId}

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/tickets/{{ticket_id}}` |
| **Auth required** | Yes |
| **Role required** | ATTENDEE or ORGANIZER (must own the ticket) |
| **Body** | None |

**Success Response — 200 OK:**
```json
{
  "id": "uuid",
  "status": "PURCHASED",
  "price": 149.99,
  "pricePaid": 119.99,
  "originalPrice": 149.99,
  "discountApplied": 30.00,
  "description": "Early Bird",
  "eventName": "Tech Conference 2025",
  "eventVenue": "Convention Center",
  "eventStart": "2025-12-15T09:00:00",
  "eventEnd": "2025-12-15T18:00:00"
}
```

**Error Responses:** 404 (not found or doesn't belong to authenticated user)

---

### GET /api/v1/tickets/{ticketId}/qr-codes/view

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/view` |
| **Auth required** | Yes |
| **Role required** | ATTENDEE or ORGANIZER |
| **Body** | None |

Returns QR code for inline display.

**Success Response — 200 OK**
- `Content-Type: image/png`
- `Content-Disposition: inline; filename="qr-code.png"`
- `Cache-Control: max-age=300, private`

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 403 | `ACCESS_DENIED` | Doesn't own ticket |
| 404 | `QR_CODE_NOT_FOUND` | QR deactivated (ticket cancelled) |

---

### GET /api/v1/tickets/{ticketId}/qr-codes/png

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/png` |
| **Auth required** | Yes |
| **Role required** | ATTENDEE or ORGANIZER |
| **Body** | None |

Returns QR code as downloadable PNG file.

**Success Response — 200 OK**
- `Content-Type: image/png`
- `Content-Disposition: attachment; filename="<event>_<type>_<user>_<id>.png"`

---

### GET /api/v1/tickets/{ticketId}/qr-codes/pdf

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/pdf` |
| **Auth required** | Yes |
| **Role required** | ATTENDEE or ORGANIZER |
| **Body** | None |

Returns QR code embedded in a PDF with ticket details.

**Success Response — 200 OK**
- `Content-Type: application/pdf`
- `Content-Disposition: attachment; filename="<event>_<type>_<user>_<id>.pdf"`

---

---

## 4. Ticket Purchase — /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets

---

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/tickets` |
| **Auth required** | Yes |
| **Role required** | ATTENDEE or ORGANIZER |
| **Approval gate** | Yes |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `quantity` | Integer | ❌ Optional | `@Min(1)` `@Max(10)` — defaults to **1** if body is `{}` or omitted |

**Minimum valid payload (defaults to quantity=1):**
```json
{}
```

**Full payload:**
```json
{
  "quantity": 3
}
```

**Business rules enforced:**
- Event must be `PUBLISHED`
- Current time must be within `salesStart`–`salesEnd` window (if set)
- Ticket type `totalAvailable` not exceeded (cancelled ticket slots free back up)
- Event `maxCapacity` not exceeded (if set)
- Active discounts automatically applied at time of purchase

**Success Response — 201 Created (returns a LIST, even for quantity=1):**
```json
[
  {
    "id": "uuid",
    "status": "PURCHASED",
    "price": 149.99,
    "pricePaid": 119.99,
    "originalPrice": 149.99,
    "discountApplied": 30.00,
    "description": "Early Bird",
    "eventName": "Tech Conference 2025",
    "eventVenue": "Convention Center",
    "eventStart": "2025-12-15T09:00:00",
    "eventEnd": "2025-12-15T18:00:00"
  }
]
```

If no discount was active: `pricePaid == originalPrice`, `discountApplied == 0.00`

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | `quantity` < 1 or > 10 |
| 400 | `TICKETS_SOLD_OUT` | Ticket type sold out or event capacity reached |
| 403 | `ACCESS_DENIED` | STAFF role; PENDING/REJECTED user |
| 404 | `EVENT_NOT_FOUND` | eventId not found |
| 404 | `TICKET_TYPE_NOT_FOUND` | ticketTypeId not found or wrong event |
| 409 | `INVALID_BUSINESS_STATE` | Event not PUBLISHED; outside sales window; event cancelled |

---

---

## 5. Ticket Type Management — /api/v1/events/{eventId}/ticket-types

**Role required:** ORGANIZER (must own the event)

---

### POST /api/v1/events/{eventId}/ticket-types

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `name` | String | ✅ Required | `@NotBlank` |
| `price` | Decimal | ✅ Required | `@NotNull` `@DecimalMin("0.00")` |
| `totalAvailable` | Integer | ✅ Required | `@NotNull` `@Min(1)` |
| `description` | String | ❌ Optional | — |

**Minimum valid payload:**
```json
{
  "name": "VIP Pass",
  "price": 499.99,
  "totalAvailable": 50
}
```

**Full payload:**
```json
{
  "name": "VIP Pass",
  "price": 499.99,
  "description": "Premium access with dinner",
  "totalAvailable": 50
}
```

**Success Response — 201 Created:**
```json
{
  "id": "uuid",
  "name": "VIP Pass",
  "price": 499.99,
  "description": "Premium access with dinner",
  "totalAvailable": 50,
  "createdAt": "...",
  "updatedAt": "..."
}
```

---

### GET /api/v1/events/{eventId}/ticket-types

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Success Response — 200 OK:** `List<CreateTicketTypeResponseDto>`

---

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Success Response — 200 OK** or **404** (not found or wrong event)

---

### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

| | |
|--|--|
| **Method** | PUT |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `name` | String | ✅ Required | `@NotBlank` |
| `price` | Decimal | ✅ Required | `@NotNull` `@DecimalMin("0.00")` |
| `description` | String | ❌ Optional | Send `null` to remove existing description |
| `totalAvailable` | Integer | ❌ Optional | Cannot be set below active (non-cancelled) sold count |

**Minimum valid payload:**
```json
{
  "name": "Updated VIP Pass",
  "price": 549.99
}
```

**Full payload:**
```json
{
  "name": "Updated VIP Pass",
  "price": 549.99,
  "description": "Updated premium access",
  "totalAvailable": 75
}
```

**Success Response — 200 OK:**
```json
{
  "id": "uuid",
  "name": "Updated VIP Pass",
  "price": 549.99,
  "description": "Updated premium access",
  "totalAvailable": 75,
  "createdAt": "...",
  "updatedAt": "..."
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 409 | `INVALID_BUSINESS_STATE` | `totalAvailable` below active sold count |
| 403 | `ACCESS_DENIED` | Not owner |

---

### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

| | |
|--|--|
| **Method** | DELETE |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Business rule:** Cannot delete if any active (non-cancelled) tickets have been sold.

**Success Response — 204 No Content**

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 409 | `TICKET_TYPE_DELETE_NOT_ALLOWED` | Active sold tickets exist |
| 403 | `ACCESS_DENIED` | Not owner |

---

---

## 6. Discount Management

**Base URL:** `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts`
**⚠️ Note: URL ends in `/discounts` (plural). Using `/discount` (singular) returns 404.**
**Role required:** ORGANIZER (must own the event)

---

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `discountType` | String | ✅ Required | `@NotNull` — `PERCENTAGE` or `FIXED_AMOUNT` |
| `value` | Decimal | ✅ Required | `@NotNull` `@DecimalMin("0.01")`. PERCENTAGE: max 100.0. FIXED_AMOUNT: any positive value |
| `validFrom` | DateTime | ✅ Required | `@NotNull` `@FutureOrPresent` |
| `validTo` | DateTime | ✅ Required | `@NotNull` `@Future` — must be after `validFrom` |
| `active` | Boolean | ❌ Optional | null/omitted = inactive (won't apply at purchase time) |
| `description` | String | ❌ Optional | — |

**Minimum valid payload:**
```json
{
  "discountType": "PERCENTAGE",
  "value": 20.00,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59"
}
```

**Full payload — percentage discount:**
```json
{
  "discountType": "PERCENTAGE",
  "value": 20.00,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59",
  "active": true,
  "description": "Early Bird Special"
}
```

**Full payload — fixed amount discount:**
```json
{
  "discountType": "FIXED_AMOUNT",
  "value": 50.00,
  "validFrom": "2025-12-01T00:00:00",
  "validTo": "2025-12-25T23:59:59",
  "active": true,
  "description": "Holiday Discount"
}
```

**Success Response — 201 Created:**
```json
{
  "id": "uuid",
  "ticketTypeId": "uuid",
  "ticketTypeName": "Early Bird",
  "discountType": "PERCENTAGE",
  "value": 20.00,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59",
  "active": true,
  "description": "Early Bird Special",
  "createdAt": "...",
  "updatedAt": "..."
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | Missing required fields, value ≤ 0, `validFrom` in past |
| 400 | `INVALID_INPUT` | `validTo` not after `validFrom`; PERCENTAGE > 100 |
| 409 | `DISCOUNT_ALREADY_EXISTS` | Another active non-expired discount already exists for this ticket type |
| 403 | `ACCESS_DENIED` | Not owner |
| 404 | `TICKET_TYPE_NOT_FOUND` | ticketTypeId not found or wrong event |

---

### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}

| | |
|--|--|
| **Method** | PUT |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts/{{discount_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Content-Type** | application/json |

**Request body:** Same as POST create.

**Success Response — 200 OK:** Same shape as `DiscountResponseDto`

---

### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}

| | |
|--|--|
| **Method** | DELETE |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts/{{discount_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Success Response — 204 No Content**

---

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts/{{discount_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Success Response — 200 OK:** `DiscountResponseDto` or **404**

---

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

Returns all discounts (active and inactive) for this ticket type.

**Success Response — 200 OK:** `List<DiscountResponseDto>`

---

---

## 7. Ticket Validation — /api/v1/ticket-validations

**Role required:** STAFF or ORGANIZER (must be assigned to / own the event)

---

### POST /api/v1/ticket-validations

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/ticket-validations` |
| **Auth required** | Yes |
| **Role required** | STAFF or ORGANIZER |
| **Approval gate** | Yes |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `id` | UUID | ✅ Required | `@NotNull` — for `MANUAL`: pass ticket UUID; for `QR_SCAN`: pass QR code UUID |
| `method` | String | ✅ Required | `@NotNull` — `MANUAL` or `QR_SCAN` (**not** `QR_CODE` — that returns 400) |

**Manual validation payload:**
```json
{
  "id": "{{ticket_id}}",
  "method": "MANUAL"
}
```

**QR scan payload (id is the QR code UUID scanned from image — NOT the ticket UUID):**
```json
{
  "id": "{{qr_code_id}}",
  "method": "QR_SCAN"
}
```

**Success Response — 200 OK (first scan):**
```json
{
  "ticketId": "uuid",
  "status": "VALID",
  "validatedById": "uuid",
  "validatedByName": "John Staff",
  "validatedAt": "2025-12-15T10:23:45"
}
```

**Success Response — 200 OK (second/duplicate scan — NOT an error):**
```json
{
  "ticketId": "uuid",
  "status": "INVALID",
  "validatedById": "uuid",
  "validatedByName": "John Staff",
  "validatedAt": "2025-12-15T10:25:00"
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | Missing `id` or `method`; invalid method value (e.g. `QR_CODE`) |
| 403 | `ACCESS_DENIED` | ATTENDEE role; STAFF not assigned to this event |
| 404 | `TICKET_NOT_FOUND` | Ticket UUID not found (MANUAL method) |
| 404 | `QR_CODE_NOT_FOUND` | QR code not found or EXPIRED (QR_SCAN method) |
| 409 | `INVALID_BUSINESS_STATE` | Ticket is CANCELLED — cannot be validated |

---

### GET /api/v1/ticket-validations/events/{eventId}

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/ticket-validations/events/{{event_id}}?page=0&size=20` |
| **Auth required** | Yes |
| **Role required** | STAFF or ORGANIZER (must be assigned/own) |
| **Body** | None |

**Success Response — 200 OK:** `Page<TicketValidationResponseDto>`

---

### GET /api/v1/ticket-validations/tickets/{ticketId}

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/ticket-validations/tickets/{{ticket_id}}` |
| **Auth required** | Yes |
| **Role required** | STAFF or ORGANIZER (must be assigned/own) |
| **Body** | None |

**Success Response — 200 OK:** `List<TicketValidationResponseDto>`

---

---

## 8. Admin Governance — /api/v1/admin

**Role required:** ADMIN

---

### POST /api/v1/admin/users/{userId}/roles

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/admin/users/{{user_id}}/roles` |
| **Auth required** | Yes |
| **Role required** | ADMIN |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `roleName` | String | ✅ Required | `@NotBlank` `@Pattern(^(ADMIN\|ORGANIZER\|ATTENDEE\|STAFF)$)` |

**Valid payload:**
```json
{
  "roleName": "ORGANIZER"
}
```

**Success Response — 200 OK:**
```json
{
  "userId": "uuid",
  "userName": "Test User",
  "email": "test@example.com",
  "roles": ["ORGANIZER"]
}
```

**Error Responses:** 400 (invalid roleName), 403 (not ADMIN)

---

### DELETE /api/v1/admin/users/{userId}/roles/{roleName}

| | |
|--|--|
| **Method** | DELETE |
| **URL** | `{{base_url}}/api/v1/admin/users/{{user_id}}/roles/STAFF` |
| **Auth required** | Yes |
| **Role required** | ADMIN |
| **Body** | None |

**Success Response — 200 OK:** `UserRolesResponseDto` with updated roles list

---

### GET /api/v1/admin/users/{userId}/roles

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/admin/users/{{user_id}}/roles` |
| **Auth required** | Yes |
| **Role required** | ADMIN |
| **Body** | None |

**Note:** The approval list endpoints always return `roles: []`. Use this endpoint to get a specific user's roles.

**Success Response — 200 OK:**
```json
{
  "userId": "uuid",
  "userName": "Test User",
  "email": "test@example.com",
  "roles": ["ORGANIZER", "ATTENDEE"]
}
```

---

### GET /api/v1/admin/roles

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/admin/roles` |
| **Auth required** | Yes |
| **Role required** | ADMIN |
| **Body** | None |

**Success Response — 200 OK:**
```json
{
  "roles": ["ADMIN", "ORGANIZER", "ATTENDEE", "STAFF"],
  "message": "Available roles in the system"
}
```

---

---

## 9. Approval Management — /api/v1/admin/approvals

**Role required:** ADMIN

---

### GET /api/v1/admin/approvals

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/admin/approvals?page=0&size=20` |
| **Auth required** | Yes |
| **Role required** | ADMIN |
| **Body** | None |

Returns all users with all approval statuses. `roles` is always `[]` in list responses.

**Success Response — 200 OK:**
```json
{
  "content": [
    {
      "userId": "uuid",
      "name": "Test User",
      "email": "test@example.com",
      "approvalStatus": "PENDING",
      "roles": [],
      "createdAt": "2026-03-18T10:00:00",
      "rejectionReason": null,
      "approvedAt": null,
      "rejectedAt": null,
      "approvedByName": null
    }
  ],
  "totalElements": 5,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### GET /api/v1/admin/approvals/pending

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/admin/approvals/pending?page=0&size=20` |
| **Auth required** | Yes |
| **Role required** | ADMIN |
| **Body** | None |

Returns only PENDING users. Same response shape as above.

---

### POST /api/v1/admin/approvals/{userId}/approve

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/admin/approvals/{{user_id}}/approve` |
| **Auth required** | Yes |
| **Role required** | ADMIN |
| **Body** | None (no request body) |

**Success Response — 200 OK:**
```json
{
  "message": "User approved successfully",
  "userId": "uuid",
  "status": "APPROVED"
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 409 | `INVALID_APPROVAL_STATE` | User is not PENDING (already APPROVED or REJECTED) |
| 404 | `USER_NOT_FOUND` | User not found |

---

### POST /api/v1/admin/approvals/{userId}/reject

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/admin/approvals/{{user_id}}/reject` |
| **Auth required** | Yes |
| **Role required** | ADMIN |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `reason` | String | ✅ Required | `@NotBlank` `@Size(min=10, max=500)` |

**Minimum valid payload (exactly 10 chars):**
```json
{
  "reason": "Duplicate."
}
```

**Full payload:**
```json
{
  "reason": "Account violates platform terms of service."
}
```

**Success Response — 200 OK:**
```json
{
  "message": "User rejected successfully",
  "userId": "uuid",
  "status": "REJECTED",
  "reason": "Account violates platform terms of service."
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | reason blank, too short (< 10), or too long (> 500) |
| 409 | `INVALID_APPROVAL_STATE` | User is not PENDING |

---

---

## 10. Event Staff Management — /api/v1/events/{eventId}/staff

**Role required:** ORGANIZER (must own the event)

---

### POST /api/v1/events/{eventId}/staff

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/staff` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `userId` | UUID | ✅ Required | `@NotNull` — target user must already have STAFF Keycloak role |

**Valid payload:**
```json
{
  "userId": "{{staff_user_id}}"
}
```

**Success Response — 201 Created:**
```json
{
  "eventId": "uuid",
  "eventName": "Tech Conference 2025",
  "staffMembers": [
    {
      "userId": "uuid",
      "userName": "John Staff",
      "email": "john@example.com"
    }
  ],
  "totalStaffCount": 1
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 409 | `INVALID_BUSINESS_STATE` | User doesn't have STAFF role; user already assigned to this event |
| 404 | `USER_NOT_FOUND` | userId not found |
| 403 | `ACCESS_DENIED` | Not owner |

---

### DELETE /api/v1/events/{eventId}/staff/{userId}

| | |
|--|--|
| **Method** | DELETE |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/staff/{{staff_user_id}}` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Success Response — 200 OK:** `EventStaffResponseDto` with updated staff list

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 409 | `INVALID_BUSINESS_STATE` | User is NOT currently assigned to this event's staff |
| 403 | `ACCESS_DENIED` | Not owner |

---

### GET /api/v1/events/{eventId}/staff

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/events/{{event_id}}/staff` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own) |
| **Body** | None |

**Success Response — 200 OK:** `EventStaffResponseDto`

---

---

## 11. Invite Code System — /api/v1/invites

---

### POST /api/v1/invites

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/invites` |
| **Auth required** | Yes |
| **Role required** | ADMIN or ORGANIZER |
| **Approval gate** | Yes |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `roleName` | String | ✅ Required | `@NotBlank` `@Pattern(^(ADMIN\|ORGANIZER\|ATTENDEE\|STAFF)$)` |
| `expirationHours` | Integer | ✅ Required | `@NotNull` `@Positive` (min 1) |
| `eventId` | UUID | Conditional | **Required** when `roleName=STAFF`; must **not** be provided for other roles |

**Business rules:**
- ADMIN can create invites for any role including ADMIN
- ORGANIZER can only create STAFF invites for events they own
- ORGANIZER cannot create ORGANIZER, ATTENDEE, or ADMIN invites

**ADMIN creates ATTENDEE invite:**
```json
{
  "roleName": "ATTENDEE",
  "expirationHours": 24
}
```

**ADMIN creates ADMIN invite:**
```json
{
  "roleName": "ADMIN",
  "expirationHours": 24
}
```

**ADMIN or ORGANIZER creates STAFF invite:**
```json
{
  "roleName": "STAFF",
  "eventId": "{{event_id}}",
  "expirationHours": 48
}
```

**Success Response — 201 Created:**
```json
{
  "id": "uuid",
  "code": "ABCD-1234-EFGH-5678",
  "roleName": "STAFF",
  "eventId": "uuid",
  "eventName": "Tech Conference 2025",
  "status": "PENDING",
  "createdBy": "organizer@test.com",
  "createdAt": "2026-03-18T10:00:00",
  "expiresAt": "2026-03-20T10:00:00",
  "redeemedBy": null,
  "redeemedAt": null
}
```

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | Missing roleName or expirationHours; invalid roleName value; expirationHours ≤ 0 |
| 400 | `INVALID_ARGUMENT` | STAFF without eventId; non-STAFF with eventId; ORGANIZER creating non-STAFF invite |
| 403 | `ACCESS_DENIED` | ORGANIZER on event they don't own |

---

### POST /api/v1/invites/redeem

| | |
|--|--|
| **Method** | POST |
| **URL** | `{{base_url}}/api/v1/invites/redeem` |
| **Auth required** | Yes |
| **Role required** | Any authenticated user |
| **Approval gate** | **Bypassed** — PENDING users can redeem |
| **Content-Type** | application/json |

**Request Body Fields:**

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `code` | String | ✅ Required | `@NotBlank` |

**Valid payload:**
```json
{
  "code": "ABCD-1234-EFGH-5678"
}
```

**Success Response — 200 OK:**
```json
{
  "message": "Invite code redeemed successfully",
  "roleAssigned": "STAFF",
  "eventName": "Tech Conference 2025",
  "currentRoles": ["STAFF"]
}
```

`eventName` is `null` for non-STAFF roles.

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `VALIDATION_ERROR` | Blank code |
| 400 | `INVALID_INVITE_CODE` | Code is expired, already redeemed, or revoked — message specifies which |
| 404 | `INVITE_CODE_NOT_FOUND` | Code not in database |

---

### DELETE /api/v1/invites/{codeId}

| | |
|--|--|
| **Method** | DELETE |
| **URL** | `{{base_url}}/api/v1/invites/{{invite_code_id}}?reason=No+longer+needed` |
| **Auth required** | Yes |
| **Role required** | ADMIN or ORGANIZER |
| **Body** | None |
| **Query param** | `reason` — optional, default `"Revoked by creator"` |

**Success Response — 204 No Content**

**Error Responses:**

| Status | Error | When |
|--------|-------|------|
| 400 | `INVALID_INVITE_CODE` | Code is not PENDING (already redeemed or revoked) |
| 403 | `ACCESS_DENIED` | Not creator and not ADMIN |
| 404 | `INVITE_CODE_NOT_FOUND` | Code not found |

---

### GET /api/v1/invites

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/invites?page=0&size=20&sort=createdAt,desc` |
| **Auth required** | Yes |
| **Role required** | ADMIN or ORGANIZER |
| **Body** | None |

**ADMIN sees all codes; ORGANIZER sees only own codes.**

**Success Response — 200 OK:** `Page<InviteCodeResponseDto>`

---

### GET /api/v1/invites/events/{eventId}

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/invites/events/{{event_id}}?page=0&size=20` |
| **Auth required** | Yes |
| **Role required** | ADMIN or ORGANIZER |
| **Body** | None |

ADMIN: any event. ORGANIZER: must own event.

**Success Response — 200 OK:** `Page<InviteCodeResponseDto>`

---

---

## 12. Audit Logs — /api/v1/audit

---

### GET /api/v1/audit

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/audit?page=0&size=20&sort=createdAt,desc` |
| **Auth required** | Yes |
| **Role required** | ADMIN |
| **Body** | None |

**Success Response — 200 OK:**
```json
{
  "content": [
    {
      "id": "uuid",
      "action": "TICKET_PURCHASED",
      "actorId": "uuid",
      "actorName": "Jane Doe",
      "targetUserId": "uuid",
      "targetUserName": "Jane Doe",
      "eventId": "uuid",
      "eventName": "Tech Conference 2025",
      "resourceType": "TICKET",
      "resourceId": "uuid",
      "details": "ticketType=Early Bird,quantity=2",
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0 ...",
      "createdAt": "2025-12-15T10:30:00"
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0
}
```

---

### GET /api/v1/audit/events/{eventId}

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/audit/events/{{event_id}}?page=0&size=20` |
| **Auth required** | Yes |
| **Role required** | ORGANIZER (must own the event) |
| **Body** | None |

**Success Response — 200 OK:** `Page<AuditLogDto>` scoped to that event

---

### GET /api/v1/audit/me

| | |
|--|--|
| **Method** | GET |
| **URL** | `{{base_url}}/api/v1/audit/me?page=0&size=20` |
| **Auth required** | Yes |
| **Role required** | Any authenticated approved user |
| **Body** | None |

Returns audit entries where the authenticated user is the actor.

**Success Response — 200 OK:** `Page<AuditLogDto>`

---

---

## Enum Values Reference

| Enum | Values |
|------|--------|
| `EventStatusEnum` | `DRAFT`, `PUBLISHED`, `CANCELLED` |
| `TicketStatusEnum` | `PURCHASED`, `CANCELLED` |
| `TicketValidationStatusEnum` | `VALID`, `INVALID` |
| `TicketValidationMethod` | `MANUAL`, `QR_SCAN` (**not** `QR_CODE`) |
| `DiscountType` | `PERCENTAGE`, `FIXED_AMOUNT` |
| `ApprovalStatus` | `PENDING`, `APPROVED`, `REJECTED` |
| `InviteCodeStatus` | `PENDING`, `REDEEMED`, `EXPIRED`, `REVOKED` |

---

## Common HTTP Codes

| Code | Meaning |
|------|---------|
| 200 | OK |
| 201 | Created |
| 204 | No Content (DELETE success) |
| 400 | Validation error or bad request |
| 401 | Missing, expired, or malformed token |
| 403 | Wrong role, PENDING/REJECTED user, ownership violation |
| 404 | Resource not found |
| 409 | Business rule violation, state conflict |
| 422 | Registration system error |
| 429 | Rate limit exceeded |
| 500 | Server error |