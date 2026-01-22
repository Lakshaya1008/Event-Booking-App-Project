# Event Booking App — Complete API Reference and Postman Guide

This document reflects the ACTUAL codebase state as of January 23, 2026 and shows how to test every API in Postman.

**Last Updated:** January 23, 2026  
**API Version:** v1.0  
**Status:** Production Ready with Complete Feature Set  

- Base URL: http://localhost:8081
- API version: v1
- Auth: OAuth2 Bearer JWT (Keycloak)
- Pagination: Spring Pageable (page, size, sort)

## Contents
- Prerequisites and environment
- Authentication (get a token)
- Approval Gate System (CRITICAL)
- Complete endpoint reference (ALL endpoints from code)
- Role-based security requirements
- Postman setup tips
- Troubleshooting

## Prerequisites and environment
- PostgreSQL: localhost:5432, DB: Event_Booking_App_db
- Keycloak: http://localhost:9090 (realm: event-ticket-platform)
- Spring Boot app: runs on port 8081 (server.port=8081)

Optional: docker-compose up to start Postgres, Adminer, and Keycloak:
- DB: postgres:latest on 5432
- Adminer UI: http://localhost:8888
- Keycloak dev: http://localhost:9090

## Authentication (get a token)
- Token endpoint: http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token
- Request (x-www-form-urlencoded):
  - grant_type: password
  - client_id: YOUR_CLIENT_ID
  - username: YOUR_USERNAME
  - password: YOUR_PASSWORD
  - scope: openid profile email
- Use the access_token as Authorization: Bearer <token>

## ⚠️ APPROVAL GATE SYSTEM (CRITICAL)

**ALL authenticated endpoints require approval status check EXCEPT:**
- `/api/v1/auth/register` - Public registration
- `/actuator/health/**` - Health checks
- `/actuator/info` - Application metadata  
- `/api/v1/invites/redeem` - Invite redemption (intentional exception)

**Approval States:**
- **PENDING**: New user awaiting admin review → 403 BLOCKED
- **APPROVED**: Admin approved → ALLOWED
- **REJECTED**: Admin rejected → 403 BLOCKED
- **NULL**: Legacy user → Auto-migrated to APPROVED

**Business Rule:** Approval gate is SEPARATE from Keycloak authentication and roles. Users with valid JWT and roles will still be blocked if not approved.

## Role-based Security Requirements
- **ADMIN**: Can manage all user roles, view all audit logs, but CANNOT bypass business rules (event ownership)
- **ORGANIZER**: Can create/manage events, ticket types, manage STAFF for their events, view own event audit logs, export sales reports
- **ATTENDEE**: Can browse events, purchase tickets, view/download their ticket QR codes
- **STAFF**: Can validate tickets ONLY for events they're assigned to, view own audit trail, NO QR export access

**IMPORTANT NOTES:**
- Roles are managed exclusively by Keycloak (no database persistence)
- ADMIN can assign/revoke roles but cannot access other organizers' events or bypass approval gate
- STAFF role alone provides no access - must be assigned to specific events by organizers
- All sensitive operations are audited
- **NEW:** Approval Gate - All users (including those with roles) must be approved by ADMIN before accessing business operations
  - New users are created with PENDING approval status
  - PENDING or REJECTED users receive 403 Forbidden even with valid JWT and roles
  - Approval status is a business gate, separate from Keycloak authentication

## Complete Endpoint Reference

This section is generated directly from Spring Boot controllers and DTOs. Each endpoint includes:
- HTTP method and path
- Required role
- Approval gate behavior
- Request DTO fields (required/optional based on validation annotations)
- Response description
- Minimum valid JSON payload (mandatory fields only)
- Full valid JSON payload (all fields)
- Validation constraints and error behavior

---

### 📁 0. Auth & Registration — /api/v1/auth

#### POST /api/v1/auth/register
**Required Role:** PUBLIC (no authentication required)  
**Approval Gate:** Bypassed (intentional exception)

Registers a new user via invite code or as ATTENDEE (no invite).

**Request DTO: RegisterRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| inviteCode | String | ❌ No | @Pattern(XXXX-XXXX-XXXX-XXXX) | Optional invite code |
| email | String | ✅ Yes | @NotBlank, @Email, @Size(max=255) | User email |
| password | String | ✅ Yes | @NotBlank, @Size(8-128), @Pattern | Password with complexity requirements |
| name | String | ✅ Yes | @NotBlank, @Size(2-100) | User display name |

**Minimum Valid JSON Payload:**
```json
{
  "email": "user@example.com",
  "password": "Password123",
  "name": "John Doe"
}
```

**Full Valid JSON Payload:**
```json
{
  "inviteCode": "ABCD-EFGH-IJKL-MNOP",
  "email": "user@example.com",
  "password": "Password123",
  "name": "John Doe"
}
```

**Response:** 201 Created - RegisterResponseDto

**Validation Errors:**
- 400 Bad Request: Invalid email format, weak password, invalid name length
- 400 Bad Request: Invalid invite code format

### 1) Event Management — /api/v1/events
**Required Role: ORGANIZER**

#### POST /api/v1/events
Creates a new event for the authenticated organizer.

**Request DTO: CreateEventRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| name | String | ✅ Yes | @NotBlank | Event name |
| start | LocalDateTime | ❌ No | - | Event start date/time |
| end | LocalDateTime | ❌ No | - | Event end date/time |
| venue | String | ✅ Yes | @NotBlank | Event venue |
| salesStart | LocalDateTime | ❌ No | - | Ticket sales start date/time |
| salesEnd | LocalDateTime | ❌ No | - | Ticket sales end date/time |
| status | EventStatusEnum | ✅ Yes | @NotNull | Event status (DRAFT, PUBLISHED, CANCELLED, COMPLETED) |
| ticketTypes | List<CreateTicketTypeRequestDto> | ✅ Yes | @NotEmpty, @Valid | List of ticket types |

**Minimum Valid JSON Payload:**
```json
{
  "name": "Tech Conference 2025",
  "venue": "Convention Center",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "name": "General Admission",
      "price": 199.99
    }
  ]
}
```

**Full Valid JSON Payload:**
```json
{
  "name": "Tech Conference 2025",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "venue": "Convention Center",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "name": "Early Bird",
      "price": 199.99,
      "description": "Discounted ticket",
      "totalAvailable": 100
    }
  ]
}
```

**Response:** 201 Created - CreateEventResponseDto (event details with ID and timestamps)

**Validation Errors:**
- 400 Bad Request: Missing required fields (name, venue, status, ticketTypes)
- 400 Bad Request: Invalid status value
- 400 Bad Request: Empty ticketTypes list

#### PUT /api/v1/events/{eventId}
Updates an event owned by the authenticated organizer.

**Request DTO: UpdateEventRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| id | UUID | ❌ No | - | Event ID (ignored, sourced from URL) |
| name | String | ✅ Yes | @NotBlank | Event name |
| start | LocalDateTime | ❌ No | - | Event start date/time |
| end | LocalDateTime | ❌ No | - | Event end date/time |
| venue | String | ✅ Yes | @NotBlank | Event venue |
| salesStart | LocalDateTime | ❌ No | - | Ticket sales start date/time |
| salesEnd | LocalDateTime | ❌ No | - | Ticket sales end date/time |
| status | EventStatusEnum | ✅ Yes | @NotNull | Event status |
| ticketTypes | List<UpdateTicketTypeRequestDto> | ✅ Yes | @NotEmpty, @Valid | List of ticket types |

**Minimum Valid JSON Payload:**
```json
{
  "name": "Updated Tech Conference",
  "venue": "Updated Venue",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "name": "General Admission",
      "price": 199.99
    }
  ]
}
```

**Full Valid JSON Payload:**
```json
{
  "name": "Updated Tech Conference 2025",
  "start": "2025-12-15T10:00:00",
  "end": "2025-12-15T19:00:00",
  "venue": "Updated Convention Center",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "id": "existing-ticket-type-uuid",
      "name": "Updated Early Bird",
      "price": 179.99,
      "description": "Updated description",
      "totalAvailable": 120
    }
  ]
}
```

**Response:** 200 OK - UpdateEventResponseDto

**Validation Errors:**
- 400 Bad Request: Missing required fields
- 400 Bad Request: ID in body doesn't match URL path
- 403 Forbidden: User doesn't own the event
- 404 Not Found: Event not found

#### GET /api/v1/events
Lists events for the authenticated organizer.

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<ListEventResponseDto>

#### GET /api/v1/events/{eventId}
Gets event details owned by the authenticated organizer.

**Response:** 200 OK - GetEventDetailsResponseDto | 404 Not Found

#### DELETE /api/v1/events/{eventId}
Deletes an event owned by the authenticated organizer.

**Response:** 204 No Content | 403 Forbidden | 404 Not Found

#### GET /api/v1/events/{eventId}/sales-dashboard
Gets sales analytics for the event.

**Response:** 200 OK - Map<String, Object> (dashboard data)

#### GET /api/v1/events/{eventId}/attendees-report
Gets attendee information for the event.

**Response:** 200 OK - Map<String, Object> (attendee data)

#### GET /api/v1/events/{eventId}/sales-report.xlsx
Exports sales report as Excel file.

**Response:** 200 OK - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet

### 2) Published Events — /api/v1/published-events
**Required Role: ATTENDEE or ORGANIZER**

#### GET /api/v1/published-events
Lists published events with optional search.

**Request:** Query parameters: q (optional search), page, size, sort

**Response:** 200 OK - Page<ListPublishedEventResponseDto>

#### GET /api/v1/published-events/{eventId}
Gets published event details.

**Response:** 200 OK - GetPublishedEventDetailsResponseDto | 404 Not Found

### 3) Ticket Management — /api/v1/tickets
**Required Role: ATTENDEE or ORGANIZER**

#### GET /api/v1/tickets
Lists tickets for the authenticated user.

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<ListTicketResponseDto>

#### GET /api/v1/tickets/{ticketId}
Gets ticket details for the authenticated user.

**Response:** 200 OK - GetTicketResponseDto | 404 Not Found

#### GET /api/v1/tickets/{ticketId}/qr-codes
**DEPRECATED** - Legacy QR code endpoint.

**Response:** 200 OK - image/png

#### GET /api/v1/tickets/{ticketId}/qr-codes/view
Views QR code inline for display.

**Response:** 200 OK - image/png (inline disposition)

#### GET /api/v1/tickets/{ticketId}/qr-codes/png
Downloads QR code as PNG file.

**Response:** 200 OK - image/png (attachment disposition)

#### GET /api/v1/tickets/{ticketId}/qr-codes/pdf
Downloads QR code as PDF with ticket details.

**Response:** 200 OK - application/pdf (attachment disposition)

### 4) Ticket Type Management — /api/v1/events/{eventId}/ticket-types
**Required Role: ORGANIZER (except purchase)**

#### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets
Purchases tickets for the authenticated user.

**Required Role: ATTENDEE or ORGANIZER**

**Request DTO: PurchaseTicketRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| quantity | Integer | ❌ No | @Min(1), @Max(10) | Number of tickets (default 1) |

**Minimum Valid JSON Payload:**
```json
{}
```

**Full Valid JSON Payload:**
```json
{
  "quantity": 2
}
```

**Response:** 201 Created - List<GetTicketResponseDto>

**Validation Errors:**
- 400 Bad Request: quantity < 1 or > 10

#### POST /api/v1/events/{eventId}/ticket-types
Creates a new ticket type for the event.

**Request DTO: CreateTicketTypeRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| name | String | ✅ Yes | @NotBlank | Ticket type name |
| price | Double | ✅ Yes | @NotNull, @PositiveOrZero | Ticket price |
| description | String | ❌ No | - | Ticket description |
| totalAvailable | Integer | ❌ No | - | Total tickets available |

**Minimum Valid JSON Payload:**
```json
{
  "name": "VIP Pass",
  "price": 499.99
}
```

**Full Valid JSON Payload:**
```json
{
  "name": "VIP Pass",
  "price": 499.99,
  "description": "Premium access",
  "totalAvailable": 50
}
```

**Response:** 201 Created - CreateTicketTypeResponseDto

#### GET /api/v1/events/{eventId}/ticket-types
Lists all ticket types for the event.

**Response:** 200 OK - List<CreateTicketTypeResponseDto>

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
Gets specific ticket type details.

**Response:** 200 OK - CreateTicketTypeResponseDto | 404 Not Found

#### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
Updates a ticket type.

**Request DTO: UpdateTicketTypeRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| id | UUID | ❌ No | - | Ticket type ID (ignored) |
| name | String | ✅ Yes | @NotBlank | Ticket type name |
| price | Double | ✅ Yes | @NotNull, @PositiveOrZero | Ticket price |
| description | String | ❌ No | - | Ticket description |
| totalAvailable | Integer | ❌ No | - | Total tickets available |

**Minimum Valid JSON Payload:**
```json
{
  "name": "Updated VIP Pass",
  "price": 549.99
}
```

**Full Valid JSON Payload:**
```json
{
  "name": "Updated VIP Pass",
  "price": 549.99,
  "description": "Updated premium access",
  "totalAvailable": 75
}
```

**Response:** 200 OK - UpdateTicketTypeResponseDto

#### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
Deletes a ticket type (only if no tickets sold).

**Response:** 204 No Content | 400 Bad Request | 403 Forbidden | 404 Not Found

### 5) Discount Management — /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
**Required Role: ORGANIZER**

#### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
Creates a new discount for a ticket type.

**Request DTO: CreateDiscountRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| discountType | DiscountType | ✅ Yes | @NotNull | PERCENTAGE or FIXED_AMOUNT |
| value | BigDecimal | ✅ Yes | @NotNull, @DecimalMin("0.01") | Discount value |
| validFrom | LocalDateTime | ✅ Yes | @NotNull, @FutureOrPresent | Discount start date |
| validTo | LocalDateTime | ✅ Yes | @NotNull, @Future | Discount end date |
| active | Boolean | ❌ No | - | Whether discount is active |
| description | String | ❌ No | - | Discount description |

**Minimum Valid JSON Payload:**
```json
{
  "discountType": "PERCENTAGE",
  "value": 20.00,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59"
}
```

**Full Valid JSON Payload:**
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

**Response:** 201 Created - DiscountResponseDto

**Validation Errors:**
- 400 Bad Request: Invalid discountType
- 400 Bad Request: value <= 0
- 400 Bad Request: validTo before validFrom

#### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
Updates an existing discount.

**Request DTO:** Same as create

**Response:** 200 OK - DiscountResponseDto

#### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
Deletes a discount.

**Response:** 204 No Content

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
Gets a specific discount.

**Response:** 200 OK - DiscountResponseDto | 404 Not Found

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
Lists all discounts for a ticket type.

**Response:** 200 OK - List<DiscountResponseDto>

### 6) Ticket Validation — /api/v1/ticket-validations
**Required Role: STAFF or ORGANIZER**

#### POST /api/v1/ticket-validations
Validates a ticket (MANUAL or QR_SCAN method).

**Request DTO: TicketValidationRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| id | UUID | ❌ No | - | Ticket or QR code ID |
| method | TicketValidationMethod | ❌ No | - | MANUAL or QR_SCAN |

**Minimum Valid JSON Payload:**
```json
{}
```

**Full Valid JSON Payload:**
```json
{
  "id": "ticket-uuid",
  "method": "MANUAL"
}
```

**Response:** 200 OK - TicketValidationResponseDto

#### GET /api/v1/ticket-validations/events/{eventId}
Lists all validations for a specific event.

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<TicketValidationResponseDto>

#### GET /api/v1/ticket-validations/tickets/{ticketId}
Gets validation history for a specific ticket.

**Response:** 200 OK - List<TicketValidationResponseDto>

### 7) Admin Governance — /api/v1/admin
**Required Role: ADMIN**

#### POST /api/v1/admin/users/{userId}/roles
Assigns a role to a user.

**Request DTO: AssignRoleRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| roleName | String | ✅ Yes | @NotBlank, @Pattern | Role name (ADMIN, ORGANIZER, ATTENDEE, STAFF) |

**Minimum Valid JSON Payload:**
```json
{
  "roleName": "ORGANIZER"
}
```

**Full Valid JSON Payload:**
```json
{
  "roleName": "ORGANIZER"
}
```

**Response:** 200 OK - UserRolesResponseDto

**Validation Errors:**
- 400 Bad Request: Invalid role name

#### DELETE /api/v1/admin/users/{userId}/roles/{roleName}
Revokes a role from a user.

**Response:** 200 OK - UserRolesResponseDto

#### GET /api/v1/admin/users/{userId}/roles
Gets all roles assigned to a user.

**Response:** 200 OK - UserRolesResponseDto

#### GET /api/v1/admin/roles
Gets all available roles in the system.

**Response:** 200 OK - AvailableRolesResponseDto

### 8) Approval Management — /api/v1/admin/approvals
**Required Role: ADMIN**

#### GET /api/v1/admin/approvals
Lists all users with their approval status.

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<UserApprovalDto>

#### GET /api/v1/admin/approvals/pending
Lists only users with PENDING approval status.

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<UserApprovalDto>

#### POST /api/v1/admin/approvals/{userId}/approve
Approves a user account.

**Response:** 200 OK - Map<String, String>

#### POST /api/v1/admin/approvals/{userId}/reject
Rejects a user account.

**Request DTO: RejectReasonDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| reason | String | ✅ Yes | @NotBlank, @Size(min=10, max=500) | Rejection reason |

**Minimum Valid JSON Payload:**
```json
{
  "reason": "Account violates terms of service or does not meet requirements"
}
```

**Full Valid JSON Payload:**
```json
{
  "reason": "Account violates terms of service or does not meet requirements"
}
```

**Response:** 200 OK - Map<String, String>

**Validation Errors:**
- 400 Bad Request: reason too short or too long

### 9) Event Staff Management — /api/v1/events/{eventId}/staff
**Required Role: ORGANIZER**

#### POST /api/v1/events/{eventId}/staff
Assigns a staff member to an event.

**Request DTO: AssignStaffRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| userId | UUID | ✅ Yes | @NotNull | User ID to assign as staff |

**Minimum Valid JSON Payload:**
```json
{
  "userId": "user-uuid"
}
```

**Full Valid JSON Payload:**
```json
{
  "userId": "user-uuid"
}
```

**Response:** 201 Created - EventStaffResponseDto

#### DELETE /api/v1/events/{eventId}/staff/{userId}
Removes a staff member from an event.

**Response:** 200 OK - EventStaffResponseDto

#### GET /api/v1/events/{eventId}/staff
Lists all staff members assigned to an event.

**Response:** 200 OK - EventStaffResponseDto

### 10) Invite Code System — /api/v1/invites
**Required Role: ADMIN or ORGANIZER**

#### POST /api/v1/invites
Generates a new invite code.

**Request DTO: GenerateInviteCodeRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| roleName | String | ✅ Yes | @NotBlank, @Pattern | Role (ORGANIZER, ATTENDEE, STAFF) |
| eventId | UUID | Conditional | - | Required only for STAFF role |
| expirationHours | Integer | ✅ Yes | @NotNull, @Positive | Hours until expiration |

**Minimum Valid JSON Payload (for ORGANIZER):**
```json
{
  "roleName": "ORGANIZER",
  "expirationHours": 48
}
```

**Full Valid JSON Payload (for STAFF):**
```json
{
  "roleName": "STAFF",
  "eventId": "event-uuid",
  "expirationHours": 48
}
```

**Response:** 201 Created - InviteCodeResponseDto

**Validation Errors:**
- 400 Bad Request: Invalid role name
- 400 Bad Request: eventId required for STAFF

#### POST /api/v1/invites/redeem
Redeems an invite code.

**Request DTO: RedeemInviteCodeRequestDto**
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| code | String | ✅ Yes | @NotBlank | Invite code |

**Minimum Valid JSON Payload:**
```json
{
  "code": "ABCD-EFGH-IJKL-MNOP"
}
```

**Full Valid JSON Payload:**
```json
{
  "code": "ABCD-EFGH-IJKL-MNOP"
}
```

**Response:** 200 OK - RedeemInviteCodeResponseDto

#### DELETE /api/v1/invites/{codeId}
Revokes an invite code.

**Request:** Query parameter: reason (optional)

**Response:** 204 No Content

#### GET /api/v1/invites
Lists invite codes.

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<InviteCodeResponseDto>

#### GET /api/v1/invites/events/{eventId}
Lists invite codes for a specific event.

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<InviteCodeResponseDto>

### 11) Audit Logs — /api/v1/audit
**Required Role: ADMIN (all logs), ORGANIZER (event logs), Any authenticated (own logs)**

#### GET /api/v1/audit
Gets all audit logs (ADMIN only).

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<AuditLogDto>

#### GET /api/v1/audit/events/{eventId}
Gets audit logs for a specific event (ORGANIZER must own event).

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<AuditLogDto>

#### GET /api/v1/audit/me
Gets audit trail for the authenticated user.

**Request:** Query parameters (Pageable): page, size, sort

**Response:** 200 OK - Page<AuditLogDto>

---

### 2A) Discount Management — /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
**Required Role: ORGANIZER**

**Business Rules:**
- Only ONE active discount per ticket type at a time
- Discounts apply at purchase time only (never retroactive)
- Two discount types: PERCENTAGE (0-100%) or FIXED_AMOUNT (currency)
- Discounts are automatically applied during ticket purchase

- **POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts**
  - Creates a new discount for a ticket type.
  - Authorization: ORGANIZER must own the event
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (CreateDiscountRequestDto):
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
    OR for fixed amount discount:
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
  - **Required Fields:**
    | Field | Required | Validation |
    |-------|----------|------------|
    | `discountType` | ✅ Yes | @NotNull ("PERCENTAGE" or "FIXED_AMOUNT") |
    | `value` | ✅ Yes | @NotNull, @DecimalMin("0.01") |
    | `validFrom` | ✅ Yes | @NotNull, @FutureOrPresent |
    | `validTo` | ✅ Yes | @NotNull, @Future (must be after validFrom) |
    | `active` | Optional | Boolean (defaults to true if not provided) |
    | `description` | Optional | String |
  - Validation Rules:
    - `discountType`: Required, must be "PERCENTAGE" or "FIXED_AMOUNT"
    - `value`: Required, positive number (min 0.01)
      - For PERCENTAGE: 0-100
      - For FIXED_AMOUNT: must not exceed ticket price
    - `validTo`: Must be after `validFrom`
  - Response 201 (DiscountResponseDto):
    ```json
    {
      "id": "660e8400-e29b-41d4-a716-446655440000",
      "discountType": "PERCENTAGE",
      "value": 20.00,
      "validFrom": "2025-11-01T00:00:00",
      "validTo": "2025-11-30T23:59:59",
      "active": true,
      "description": "Early Bird Special",
      "ticketTypeId": "440e8400-e29b-41d4-a716-446655440000",
      "createdAt": "2026-01-19T10:00:00"
    }
    ```
  - Error Responses:
    - 400 Bad Request: Validation errors or another active discount exists
    - 403 Forbidden: User doesn't own the event
    - 404 Not Found: Event or ticket type not found

- **PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}**
  - Updates an existing discount.
  - Authorization: ORGANIZER must own the event
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (CreateDiscountRequestDto): Same as create
  - Response 200 (DiscountResponseDto)

- **DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}**
  - Deletes a discount.
  - Authorization: ORGANIZER must own the event
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 204 No Content

- **GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}**
  - Gets a specific discount.
  - Authorization: ORGANIZER must own the event
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (DiscountResponseDto) | 404 Not Found

- **GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts**
  - Lists all discounts for a ticket type (active and inactive).
  - Authorization: ORGANIZER must own the event
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200: List<DiscountResponseDto>

**Notes:**
- Discounts are automatically applied during ticket purchase based on purchase timestamp
- Only discounts valid at the time of purchase are applied
- Discount information is saved with each ticket for reporting purposes
- Organizers can view discount effectiveness in sales reports

---

### 3) Published Events — /api/v1/published-events
**Required Role: ATTENDEE or ORGANIZER**

- **GET /api/v1/published-events**
  - Lists published events with optional search.
  - Headers: Authorization: Bearer {{access_token}}
  - Query (optional): q, page, size, sort
  - Response 200: Page<ListPublishedEventResponseDto>

- **GET /api/v1/published-events/{eventId}**
  - Gets published event details.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 GetPublishedEventDetailsResponseDto | 404 Not Found

### 4) Ticket Purchase — /api/v1/events/{eventId}/ticket-types
**Required Role: ATTENDEE or ORGANIZER**

**Business Rules (Service-Enforced):**
- Event must be PUBLISHED (not DRAFT, CANCELLED, or COMPLETED)
- Sales window must be open (current time between salesStart and salesEnd)
- Ticket type must not be sold out (totalAvailable > 0)
- Quantity must not exceed remaining availability
- Discounts are automatically applied if active and valid at purchase time
- Each ticket gets a unique QR code
- Purchase is atomic (all tickets succeed or all fail)

**Error Responses:**
- 400 Bad Request: Event not published, sales window closed, sold out, quantity exceeds availability, or validation errors
- 401 Unauthorized: Invalid/missing token
- 403 Forbidden: Role mismatch (user does not have ATTENDEE or ORGANIZER role) or approval gate violation (user is PENDING or REJECTED)
- 404 Not Found: Event or ticket type not found

- **POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets**
  - Purchases tickets for the authenticated user with quantity support.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (PurchaseTicketRequestDto):
    ```json
    {
      "quantity": 3
    }
    ```
    **Note**: 
    - `quantity` is optional and defaults to 1 if not provided
    - Valid range: 1-10 tickets per purchase
    - Each ticket gets its own unique QR code
  - Response 201: List<GetTicketResponseDto> - Array of created tickets
  ```json
  [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "status": "PURCHASED",
      "price": 199.99,
      "description": "Early Bird ticket",
      "eventName": "Tech Conference 2025",
      "eventVenue": "Convention Center",
      "eventStart": "2025-12-15T09:00:00",
      "eventEnd": "2025-12-15T18:00:00"
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "status": "PURCHASED",
      "price": 199.99,
      "description": "Early Bird ticket",
      "eventName": "Tech Conference 2025",
      "eventVenue": "Convention Center",
      "eventStart": "2025-12-15T09:00:00",
      "eventEnd": "2025-12-15T18:00:00"
    }
  ]
  ```
