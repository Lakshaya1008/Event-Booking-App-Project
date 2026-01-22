# Event Booking App — Complete API Reference and Postman Guide

This document reflects the ACTUAL codebase state as of January 22, 2026 and shows how to test every API in Postman.

**Last Updated:** January 22, 2026  
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
  **Error Responses**:
  - 400 Bad Request: Sold out, invalid quantity, or validation errors
  - 401 Unauthorized: Invalid/missing token
  - 403 Forbidden: User doesn't have ATTENDEE or ORGANIZER role
  - 404 Not Found: Event or ticket type not found

### 5) User Tickets — /api/v1/tickets
**Required Role: ATTENDEE or ORGANIZER**

- **GET /api/v1/tickets**
  - Lists tickets for the authenticated user.
  - Headers: Authorization: Bearer {{access_token}}
  - Query (optional): page, size, sort
  - Response 200: Page<ListTicketResponseDto>

- **GET /api/v1/tickets/{ticketId}**
  - Gets ticket details for the authenticated user.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 GetTicketResponseDto | 404 Not Found

- **GET /api/v1/tickets/{ticketId}/qr-codes**
  - **DEPRECATED**: Legacy endpoint, use `/qr-codes/view` instead
  - Returns a PNG image of the ticket QR code.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 image/png | 404 Not Found

### 5A) QR Code Export APIs — /api/v1/tickets/{ticketId}/qr-codes
**Required Role: ATTENDEE or ORGANIZER**

**SECURITY MODEL (CRITICAL):**
- QR codes encode ONLY immutable ticket ID (UUID)
- QR image integrity is NOT a security boundary
- Security is enforced during backend validation at scan time, NOT at download
- QR codes are SAFE to re-download, re-view, or share
- Authorization checked: User must own ticket OR own the event
- STAFF and ADMIN have NO access to QR exports

**IDEMPOTENCY GUARANTEE:**
- Same ticket always generates identical QR code content
- No state changes during export operations
- Safe to download multiple times

**ACCESS CONTROL:**
- ATTENDEE: Own tickets only
- ORGANIZER: Tickets from events they own only
- STAFF: NO access (403 Forbidden)
- ADMIN: NO access (403 Forbidden, no bypass)

- **GET /api/v1/tickets/{ticketId}/qr-codes/view**
  - **View QR Code** (inline display, no download prompt)
  - Returns PNG image for browser/app display
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 image/png
    - Content-Type: image/png
    - Content-Disposition: inline; filename="qr-code.png"
    - Cache-Control: max-age=300, public (5-minute browser cache)
  - Audit: Logs QR_CODE_VIEWED action

- **GET /api/v1/tickets/{ticketId}/qr-codes/png**
  - **Download QR Code** (PNG format, forces download)
  - Returns PNG image with sanitized filename
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 image/png
    - Content-Type: image/png
    - Content-Disposition: attachment; filename="<event>_<type>_<user>_<id>.png"
    - Example filename: summer_festival_vip_john_doe_a1b2c3d4.png
  - Filename sanitization:
    - Lowercase conversion
    - Spaces replaced with underscores
    - Special characters removed
    - Max 50 characters per component
  - Audit: Logs QR_CODE_DOWNLOADED_PNG action

- **GET /api/v1/tickets/{ticketId}/qr-codes/pdf**
  - **Download QR Code** (PDF format with ticket details)
  - Returns PDF document containing QR code and ticket information
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 application/pdf
    - Content-Type: application/pdf
    - Content-Disposition: attachment; filename="<event>_<type>_<user>_<id>.pdf"
  - PDF Contents:
    - Title: "Event Ticket QR Code"
    - Event name
    - Ticket type
    - Ticket holder name
    - Ticket ID
    - QR code image (300x300px)
  - Audit: Logs QR_CODE_DOWNLOADED_PDF action

**QR Code Content (SECURITY CLARIFICATION):**
```
QR Code Data: <ticket-uuid>
Example: "550e8400-e29b-41d4-a716-446655440000"

Why this is secure:
- UUID is immutable (cannot be guessed or forged)
- Backend validates ticket ownership/status at scan time
- Sharing QR does not compromise security (validation fails if ticket already used)
- Multiple downloads of same QR are safe (same UUID, no replay risk)
```

**Error Responses:**
- 403 Forbidden: User doesn't own ticket AND doesn't own event
- 404 Not Found: Ticket doesn't exist
- 401 Unauthorized: Invalid/missing JWT

### 6) Ticket Validation — /api/v1/ticket-validations
**Required Role: STAFF or ORGANIZER**

- **POST /api/v1/ticket-validations**
  - Validates a ticket (MANUAL or QR_SCAN method).
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (TicketValidationRequestDto):
    ```json
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "method": "MANUAL"
    }
    ```
  - **Required Fields:**
    | Field | Required | Values |
    |-------|----------|--------|
    | `id` | ✅ Yes | UUID of the ticket |
    | `method` | ✅ Yes | "MANUAL" or "QR_SCAN" |
  - **Note**: For both methods, `id` is the ticket UUID.
  - Response 200 (TicketValidationResponseDto): ticketId, status

- **GET /api/v1/ticket-validations/events/{eventId}**
  - Lists all validations for a specific event (paginated).
  - Headers: Authorization: Bearer {{access_token}}
  - Query (optional): page, size, sort
  - Response 200: Page<TicketValidationResponseDto>

- **GET /api/v1/ticket-validations/tickets/{ticketId}**
  - Gets validation history for a specific ticket.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200: List<TicketValidationResponseDto>

---

### 7A) Admin Governance APIs — /api/v1/admin
**Required Role: ADMIN**

**CRITICAL:** ADMIN can manage roles but CANNOT bypass business authorization. ADMINs cannot access events they don't own or validate tickets without proper assignment.

- **POST /api/v1/admin/users/{userId}/roles**
  - Assigns a role to a user via Keycloak Admin API.
  - Backend is the SOLE authority for Keycloak Admin operations.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (AssignRoleRequestDto):
    ```json
    {
      "roleName": "ORGANIZER"
    }
    ```
    **Valid roles:** ADMIN, ORGANIZER, ATTENDEE, STAFF
  - Response 200 (UserRolesResponseDto):
    ```json
    {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "userName": "john.doe",
      "email": "john@example.com",
      "roles": ["ATTENDEE", "ORGANIZER"]
    }
    ```

- **DELETE /api/v1/admin/users/{userId}/roles/{roleName}**
  - Revokes a role from a user.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (UserRolesResponseDto): Updated user roles

- **GET /api/v1/admin/users/{userId}/roles**
  - Gets all roles assigned to a user.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (UserRolesResponseDto)

- **GET /api/v1/admin/roles**
  - Gets all available roles in the system.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (AvailableRolesResponseDto):
    ```json
    {
      "roles": ["ADMIN", "ORGANIZER", "ATTENDEE", "STAFF"],
      "message": "Available roles in the system"
    }
    ```

---

### 8) Admin Approval Management — /api/v1/admin/approvals
**Required Role: ADMIN**

ADMIN-only endpoints for managing user account approvals. Part of the Approval Gate system.

- **GET /api/v1/admin/approvals**
  - Lists all users with their approval status.
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<UserApprovalDto>
    ```json
    {
      "content": [
        {
          "userId": "550e8400-e29b-41d4-a716-446655440000",
          "userName": "john.doe",
          "email": "john@example.com",
          "approvalStatus": "PENDING",
          "createdAt": "2026-01-18T10:00:00"
        }
      ],
      "totalElements": 1
    }
    ```

- **GET /api/v1/admin/approvals/pending**
  - Lists only users with PENDING approval status.
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<UserApprovalDto>

- **POST /api/v1/admin/approvals/{userId}/approve**
  - Approves a pending user account.
  - Headers: Authorization: Bearer {{access_token}}
  - No request body required
  - Response 200:
    ```json
    {
      "message": "User approved successfully",
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "status": "APPROVED"
    }
    ```

- **POST /api/v1/admin/approvals/{userId}/reject**
  - Rejects a user account permanently.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (RejectReasonDto):
    ```json
    {
      "reason": "Account violates terms of service or does not meet requirements"
    }
    ```
  - **Required Fields:**
    | Field | Required | Validation |
    |-------|----------|------------|
    | `reason` | ✅ Yes | @NotBlank, @Size(min=10, max=500) |
  - Response 200:
    ```json
    {
      "message": "User rejected successfully",
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "status": "REJECTED",
      "reason": "Account violates terms of service or does not meet requirements"
    }
    ```

---

### 9) Event Staff Management — /api/v1/events/{eventId}/staff
**Required Role: ORGANIZER**

Organizers can assign/remove STAFF for their own events. STAFF must first have the STAFF role (assigned by ADMIN), then be assigned to specific events.

- **POST /api/v1/events/{eventId}/staff**
  - Assigns a STAFF member to an event.
  - Prerequisites: User must have STAFF role in Keycloak
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (AssignStaffRequestDto):
    ```json
    {
      "userId": "550e8400-e29b-41d4-a716-446655440000"
    }
    ```
  - Response 201 (EventStaffResponseDto):
    ```json
    {
      "eventId": "660e8400-e29b-41d4-a716-446655440000",
      "eventName": "Tech Conference 2025",
      "staffMembers": [
        {
          "userId": "550e8400-e29b-41d4-a716-446655440000",
          "userName": "john.staff",
          "email": "john@example.com"
        }
      ],
      "totalStaffCount": 1
    }
    ```

- **DELETE /api/v1/events/{eventId}/staff/{userId}**
  - Removes a STAFF member from an event.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (EventStaffResponseDto): Updated staff list

- **GET /api/v1/events/{eventId}/staff**
  - Lists all STAFF members assigned to an event.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (EventStaffResponseDto)

**Staff Assignment Flow:**
1. ADMIN assigns STAFF role to user via `/api/v1/admin/users/{userId}/roles`
2. ORGANIZER assigns staff to their event via `/api/v1/events/{eventId}/staff`
3. STAFF can now validate tickets for that event

---

### 10) Invite Code System — /api/v1/invites
**Required Role: ADMIN or ORGANIZER**

Role-specific, single-use, time-bound invite codes for controlled onboarding.

**CRITICAL RULES:**
- One invite code = one role (cannot be reused for different roles)
- STAFF invites MUST have eventId and are event-scoped
- Other roles (ORGANIZER, ATTENDEE) must NOT have eventId
- ORGANIZER can ONLY create STAFF invites for their own events
- ADMIN can create invites for any role
- Invite codes are single-use with optimistic locking

- **POST /api/v1/invites**
  - Generates a new invite code.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (GenerateInviteCodeRequestDto):
    ```json
    {
      "roleName": "STAFF",
      "eventId": "660e8400-e29b-41d4-a716-446655440000",
      "expirationHours": 48
    }
    ```
  - **Required Fields:**
    | Field | Required | Validation |
    |-------|----------|------------|
    | `roleName` | ✅ Yes | @NotBlank, @Pattern("ORGANIZER\|ATTENDEE\|STAFF") |
    | `eventId` | Conditional | Required ONLY for STAFF role, must be null/omitted for others |
    | `expirationHours` | ✅ Yes | @NotNull, @Positive |
  - **Notes:**
    - `roleName`: **ORGANIZER, ATTENDEE, or STAFF only** (NOT ADMIN - admins cannot be created via invite)
    - `eventId`: Required ONLY for STAFF invites, must be null for others
    - `expirationHours`: Hours until code expires (positive integer)
  - Response 201 (InviteCodeResponseDto):
    ```json
    {
      "id": "770e8400-e29b-41d4-a716-446655440000",
      "code": "ABCD-EFGH-IJKL-MNOP",
      "roleName": "STAFF",
      "eventId": "660e8400-e29b-41d4-a716-446655440000",
      "eventName": "Tech Conference 2025",
      "status": "PENDING",
      "createdBy": "admin.user",
      "createdAt": "2026-01-18T10:00:00",
      "expiresAt": "2026-01-20T10:00:00",
      "redeemedBy": null,
      "redeemedAt": null
    }
    ```

- **POST /api/v1/invites/redeem**
  - Redeems an invite code (any authenticated user).
  - Process: Validates code → Assigns role via Keycloak → Creates event-staff assignment (if STAFF) → Marks code USED
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (RedeemInviteCodeRequestDto):
    ```json
    {
      "code": "ABCD-EFGH-IJKL-MNOP"
    }
    ```
  - Response 200 (RedeemInviteCodeResponseDto):
    ```json
    {
      "message": "Invite code redeemed successfully",
      "roleAssigned": "STAFF",
      "eventName": "Tech Conference 2025",
      "currentRoles": ["ATTENDEE", "STAFF"]
    }
    ```
  - Error Responses:
    - 400: Code already redeemed, expired, or revoked
    - 404: Code not found

- **DELETE /api/v1/invites/{codeId}?reason=No%20longer%20needed**
  - Revokes an invite code (before redemption).
  - Authorization: ADMIN or creator only
  - Headers: Authorization: Bearer {{access_token}}
  - Query: reason (optional)
  - Response 204 No Content

- **GET /api/v1/invites**
  - Lists invite codes.
  - ADMIN: sees all codes; ORGANIZER: sees only their own
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<InviteCodeResponseDto>

- **GET /api/v1/invites/events/{eventId}**
  - Lists invite codes for a specific event.
  - Authorization: ADMIN or event organizer
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<InviteCodeResponseDto>

---

### 10A) Admin Approval Management — /api/v1/admin/approvals
**Required Role: ADMIN**

ADMIN-only endpoints for managing user account approvals. Part of the Approval Gate system.

- **GET /api/v1/admin/approvals**
  - Lists all users with their approval status.
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<UserApprovalDto>
    ```json
    {
      "content": [
        {
          "userId": "550e8400-e29b-41d4-a716-446655440000",
          "userName": "john.doe",
          "email": "john@example.com",
          "approvalStatus": "PENDING",
          "createdAt": "2026-01-18T10:00:00"
        }
      ],
      "totalElements": 1
    }
    ```

- **GET /api/v1/admin/approvals/pending**
  - Lists only users with PENDING approval status.
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<UserApprovalDto>

- **POST /api/v1/admin/approvals/{userId}/approve**
  - Approves a pending user account.
  - Headers: Authorization: Bearer {{access_token}}
  - No request body required
  - Response 200:
    ```json
    {
      "message": "User approved successfully",
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "status": "APPROVED"
    }
    ```

- **POST /api/v1/admin/approvals/{userId}/reject**
  - Rejects a user account permanently.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (RejectReasonDto):
    ```json
    {
      "reason": "Account violates terms of service or does not meet requirements"
    }
    ```
  - **Required Fields:**
    | Field | Required | Validation |
    |-------|----------|------------|
    | `reason` | ✅ Yes | @NotBlank, @Size(min=10, max=500) |
  - Response 200:
    ```json
    {
      "message": "User rejected successfully",
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "status": "REJECTED",
      "reason": "Account violates terms of service or does not meet requirements"
    }
    ```

---

### 11) Approval Gate System
**PRODUCTION SECURITY FEATURE**

The Approval Gate is a business-level authorization layer that runs AFTER Keycloak authentication.

**WHY IT EXISTS:**
- Keycloak handles authentication (who you are) ✓
- Keycloak assigns roles (what you claim to be) ✓
- Approval Gate enforces trust (whether we accept your claim) ✓

**SECURITY MODEL:**
- User authenticates with Keycloak → gets JWT with roles
- JWT is cryptographically valid
- User has legitimate role assignment
- BUT: User may not be approved for business operations
- Approval Gate blocks unapproved users DESPITE valid authentication

**APPROVAL STATES:**
- **PENDING**: New user awaiting admin review → 403 Forbidden on all business operations
- **APPROVED**: Admin approved user → Full access according to roles
- **REJECTED**: Admin rejected user → 403 Forbidden permanently
- **NULL**: Legacy user (auto-migrated to APPROVED on first access)

**EXECUTION FLOW:**
```
Request → JWT Validation (Keycloak) 
→ Role Extraction (Spring Security) 
→ User Provisioning (create if new) 
→ >>> APPROVAL GATE CHECK <<< 
→ Role-based Authorization (@PreAuthorize) 
→ Ownership/Scope Check (AuthorizationService) 
→ Business Logic
```

**ALLOWLISTED ENDPOINTS (Bypass Approval Gate):**
- `/api/v1/auth/register` - Public registration (no auth required)
- `/actuator/health` - Health checks (infrastructure)
- `/actuator/info` - Application metadata (infrastructure)
- `/api/v1/invites/redeem` - Invite redemption (deliberate exception for onboarding)

**ALL OTHER ENDPOINTS:** Approval required, NO exceptions

**ERROR RESPONSE (403 Forbidden):**
```json
{
  "error": "APPROVAL_PENDING",
  "message": "Your account is awaiting approval from an administrator. You will be notified once your account has been reviewed.",
  "status": "403",
  "timestamp": "2026-01-18T12:00:00Z"
}
```

**ADMIN APPROVAL ENDPOINTS:**
- `GET /api/v1/admin/approvals` - List all users with approval status
- `GET /api/v1/admin/approvals/pending` - List pending approvals only
- `POST /api/v1/admin/approvals/{userId}/approve` - Approve pending user
- `POST /api/v1/admin/approvals/{userId}/reject` - Reject pending user (requires body with `reason`)

**Reject Request Body (RejectReasonDto):**
```json
{
  "reason": "Account violates terms of service"
}
```
- `reason`: Required (@NotBlank), 10-500 characters

**CRITICAL NOTES:**
- Approval is SEPARATE from roles (user can have role but be unapproved)
- ADMIN does NOT bypass approval gate (even ADMIN can be pending)
- Approval gate applies to existing endpoints (backward compatible)
- Legacy users auto-migrate to APPROVED (zero downtime deployment)

---

# Event Booking App — Complete PowerShell Testing Guide

This guide includes ALL endpoints with copy/paste commands for Windows PowerShell.

## Quick facts
- Base URL: http://localhost:8081
- API version: v1
- Auth: OAuth2 Bearer JWT (Keycloak). All endpoints require a token by default.
- Time format: ISO-8601 (YYYY-MM-DDTHH:mm:ss)
- IDs: UUID

## 1) Prerequisites
- App running on port 8081
- Keycloak on http://localhost:9090 with realm event-ticket-platform
- A Keycloak user and client (client_id) you can use with Resource Owner Password (password) grant

## 2) Set variables (PowerShell)
```powershell
$BASE_URL = "http://localhost:8081"
$KEYCLOAK_URL = "http://localhost:9090"
$REALM = "event-ticket-platform"
$CLIENT_ID = "<your-client-id>"
$USERNAME = "<your-username>"
$PASSWORD = "<your-password>"
```

## 3) Get access token
```powershell
$tokenResponse = Invoke-RestMethod -Method Post `
  -Uri "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" `
  -Headers @{ "Content-Type" = "application/x-www-form-urlencoded" } `
  -Body "grant_type=password&client_id=$CLIENT_ID&username=$USERNAME&password=$PASSWORD&scope=openid profile email"
$ACCESS_TOKEN = $tokenResponse.access_token
$ACCESS_TOKEN.Substring(0,20)  # sanity peek
```

## 4) ORGANIZER ENDPOINTS - Event Management

### Create an event (captures IDs)
```powershell
$createEventBody = @{
  name       = "Tech Conference 2025"
  start      = "2025-12-15T09:00:00"
  end        = "2025-12-15T18:00:00"
  venue      = "Convention Center"
  salesStart = "2025-11-01T00:00:00"
  salesEnd   = "2025-12-14T23:59:59"
  status     = "PUBLISHED"
  ticketTypes = @(@{
    name = "Early Bird"; price = 199.99; description = "Discounted"; totalAvailable = 100
  })
} | ConvertTo-Json -Depth 6

$createEventRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $createEventBody

$EVENT_ID = $createEventRes.id
$TICKET_TYPE_ID = $createEventRes.ticketTypes[0].id
$EVENT_ID; $TICKET_TYPE_ID
```

### List organizer events
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events?page=0&size=20&sort=start,desc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get organizer event by ID
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Update the event
```powershell
$updateEventBody = @{
  id         = $EVENT_ID
  name       = "Tech Conference 2025 - Updated"
  start      = "2025-12-15T10:00:00"
  end        = "2025-12-15T19:00:00"
  venue      = "Main Hall"
  salesStart = "2025-11-01T00:00:00"
  salesEnd   = "2025-12-14T23:59:59"
  status     = "PUBLISHED"
  ticketTypes = @(@{
    id = $TICKET_TYPE_ID; name = "Regular"; price = 299.99; description = "Standard"; totalAvailable = 400
  })
} | ConvertTo-Json -Depth 6

Invoke-RestMethod -Method Put `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $updateEventBody
```

### Get sales dashboard
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/sales-dashboard" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get attendees report
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/attendees-report" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Export sales report (Excel .xlsx)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/sales-report.xlsx" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile ".\sales-report.xlsx"
Write-Host "Sales report downloaded: sales-report.xlsx"
# Open in Excel (optional)
# Start-Process ".\sales-report.xlsx"
```

## 5) ORGANIZER ENDPOINTS - Ticket Type Management

### Create a new ticket type
```powershell
$createTicketTypeBody = @{
  name = "VIP Pass"
  price = 499.99
  description = "Premium access with perks"
  totalAvailable = 50
} | ConvertTo-Json

$newTicketType = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $createTicketTypeBody

$VIP_TICKET_TYPE_ID = $newTicketType.id
$VIP_TICKET_TYPE_ID
```

### List all ticket types for event
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get specific ticket type
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$VIP_TICKET_TYPE_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Update ticket type
```powershell
$updateTicketTypeBody = @{
  id = $VIP_TICKET_TYPE_ID
  name = "VIP Pass - Updated"
  price = 549.99
  description = "Premium access with even more perks"
  totalAvailable = 30
} | ConvertTo-Json

Invoke-RestMethod -Method Put `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$VIP_TICKET_TYPE_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $updateTicketTypeBody
```

### Delete ticket type (only if no tickets sold)
```powershell
Invoke-WebRequest -Method Delete `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$VIP_TICKET_TYPE_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } | Select-Object StatusCode
```

## 6) ATTENDEE ENDPOINTS

### Published events
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/published-events?q=tech&page=0&size=10&sort=start,asc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }

Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/published-events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Purchase tickets with quantity support
```powershell
# Purchase single ticket (default quantity=1)
$purchaseSingleBody = @{ quantity = 1 } | ConvertTo-Json
$purchaseSingleRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/tickets" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $purchaseSingleBody
$purchaseSingleRes  # Array with 1 ticket

# Purchase multiple tickets
$purchaseMultipleBody = @{ quantity = 3 } | ConvertTo-Json
$purchaseMultipleRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/tickets" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $purchaseMultipleBody
$purchaseMultipleRes  # Array with 3 tickets
$TICKET_ID = $purchaseMultipleRes[0].id  # Capture first ticket ID
```

### List my tickets and capture ticket ID
```powershell
$ticketsPage = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/tickets?page=0&size=20&sort=id,desc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
$TICKET_ID = $ticketsPage.content[0].id
$TICKET_ID
```

### Get my ticket details
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get ticket QR code (PNG saved to file - DEPRECATED)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile (".\ticket-" + $TICKET_ID + "-qr.png")
```

### View QR code (inline display)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes/view" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile ".\ticket-qr-view.png"
Write-Host "QR code saved for viewing: ticket-qr-view.png"
```

### Download QR code (PNG with sanitized filename)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes/png" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile ".\ticket-qr-download.png"
Write-Host "QR code downloaded: ticket-qr-download.png"
```

### Download QR code (PDF with ticket details)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes/pdf" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile ".\ticket-qr.pdf"
Write-Host "QR code PDF downloaded: ticket-qr.pdf"
```

## 7) STAFF ENDPOINTS - Ticket Validation

### Validate ticket manually
```powershell
$validateManual = @{ id = $TICKET_ID; method = "MANUAL" } | ConvertTo-Json
$validationResult = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/ticket-validations" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $validateManual
$validationResult
```

### Get QR code ID for QR scan validation (from ticket details)
```powershell
$ticketDetails = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
$QR_CODE_ID = $ticketDetails.qrCodes[0].id  # assuming ticket has QR codes

$validateQr = @{ id = $QR_CODE_ID; method = "QR_SCAN" } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/ticket-validations" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $validateQr
```

### List all validations for an event
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/ticket-validations/events/$EVENT_ID?page=0&size=20" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get validation history for a specific ticket
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/ticket-validations/tickets/$TICKET_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

## 8) Cleanup

### Delete the event
```powershell
Invoke-WebRequest -Method Delete `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } | Select-Object StatusCode
```

---

## 9) ADMIN GOVERNANCE ENDPOINTS

### Get available roles
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/admin/roles" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Assign STAFF role to a user
```powershell
$USER_ID = "550e8400-e29b-41d4-a716-446655440000"  # Replace with actual user ID
$assignRoleBody = @{ roleName = "STAFF" } | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/admin/users/$USER_ID/roles" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $assignRoleBody
```

### Get user roles
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/admin/users/$USER_ID/roles" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Revoke role from user
```powershell
Invoke-RestMethod -Method Delete `
  -Uri "$BASE_URL/api/v1/admin/users/$USER_ID/roles/STAFF" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

---

## 10) EVENT STAFF MANAGEMENT ENDPOINTS (ORGANIZER)

### Assign staff to event
```powershell
$STAFF_USER_ID = "550e8400-e29b-41d4-a716-446655440000"  # User must have STAFF role
$assignStaffBody = @{ userId = $STAFF_USER_ID } | ConvertTo-Json

$staffAssignRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/staff" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $assignStaffBody
$staffAssignRes
```

### List event staff
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/staff" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Remove staff from event
```powershell
Invoke-RestMethod -Method Delete `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/staff/$STAFF_USER_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

---

## 11) INVITE CODE ENDPOINTS

### Generate STAFF invite for event (ORGANIZER)
```powershell
$createInviteBody = @{
  roleName = "STAFF"
  eventId = $EVENT_ID
  expirationHours = 48
} | ConvertTo-Json

$inviteRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/invites" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $createInviteBody

$INVITE_CODE = $inviteRes.code
$INVITE_ID = $inviteRes.id
Write-Host "Invite Code: $INVITE_CODE"
```

### Generate ORGANIZER invite (ADMIN only)
```powershell
$createOrganizerInviteBody = @{
  roleName = "ORGANIZER"
  eventId = $null  # Must be null for non-STAFF roles
  expirationHours = 72
} | ConvertTo-Json

$orgInviteRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/invites" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $createOrganizerInviteBody

$orgInviteRes.code
```

### Redeem invite code (any authenticated user)
```powershell
$redeemBody = @{ code = $INVITE_CODE } | ConvertTo-Json

$redeemRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/invites/redeem" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $redeemBody

$redeemRes  # Shows assigned role and current roles
```

### List my invite codes
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/invites?page=0&size=20&sort=createdAt,desc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### List invites for specific event
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/invites/events/$EVENT_ID?page=0&size=20" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Revoke invite code
```powershell
Invoke-WebRequest -Method Delete `
  -Uri "$BASE_URL/api/v1/invites/${INVITE_ID}?reason=No%20longer%20needed" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } | Select-Object StatusCode
```

---

## 12) AUDIT LOG ENDPOINTS

### View all audit logs (ADMIN only)
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/audit?page=0&size=20&sort=createdAt,desc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### View audit logs for my event (ORGANIZER)
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/audit/events/$EVENT_ID?page=0&size=20" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### View my own audit trail (any authenticated user)
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/audit/me?page=0&size=20" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

---

## Notes
- **Role Requirements**: Each endpoint section specifies the required Keycloak role
- **Validation Methods**: Use ticket UUID for MANUAL, QR code UUID for QR_SCAN
- **Security**: All endpoints require proper Bearer token with correct role
- **Pagination**: Use page, size, sort parameters for list endpoints
- **Quantity Limits**: Ticket purchases are limited to 1-10 tickets per transaction
- **Atomic Operations**: Multiple ticket purchases are processed as a single transaction
- **Individual QR Codes**: Each purchased ticket receives its own unique QR code
- **NEW - QR Code Exports**: 
  - Three formats available: inline view (PNG), download (PNG), download (PDF)
  - QR codes encode only ticket UUID (immutable identifier)
  - Security enforced at validation time, not at export time
  - Safe to share, re-download, or re-view QR codes
  - ATTENDEE can export own tickets, ORGANIZER can export tickets from own events
  - STAFF and ADMIN have NO access to QR exports
- **NEW - Sales Report Export**: 
  - Excel (.xlsx) format with comprehensive sales analytics
  - Reuses same data source as sales dashboard (no duplicate logic)
  - ORGANIZER must own event, ADMIN does NOT bypass ownership
  - Filename includes timestamp for version tracking
- **NEW - Approval Gate**:
  - All users must be approved by ADMIN before accessing business operations
  - Valid JWT + roles ≠ automatic access (approval also required)
  - PENDING or REJECTED users receive 403 Forbidden
  - See "Approval Gate System" section for details

## Common Errors and Troubleshooting
- **401 Unauthorized**: Token missing/expired/invalid (re-run token step)
- **403 Forbidden**: User doesn't have required role or doesn't own the resource
  - ADMIN trying to access another organizer's event
  - ORGANIZER trying to create non-STAFF invites
  - STAFF trying to access audit logs or validate unassigned event tickets
- **400 Bad Request**: Validation errors, sold out, invalid quantity, or business rule violations
  - Invite code already redeemed
  - Invite code expired
  - User doesn't have STAFF role when assigning to event
  - Invalid role + eventId combination (e.g., ORGANIZER invite with eventId)
- **404 Not Found**: Wrong UUID or resource not visible to the user
- **409 Conflict**: Optimistic locking failure (concurrent modification detected)

## New Features Summary

### Admin Governance
- **Backend-only Keycloak access**: All role management through backend API
- **ADMIN limitations**: Can manage roles but CANNOT bypass business rules
- **Audit trail**: All role changes logged

### Event Staff Management
- **Two-step process**: 
  1. ADMIN assigns STAFF role via `/api/v1/admin/users/{userId}/roles`
  2. ORGANIZER assigns to specific events via `/api/v1/events/{eventId}/staff`
- **Event-scoped access**: STAFF can only validate for assigned events
- **Database persistence**: Event-staff relationships stored in `user_staffing_events` table

### Invite Code System
- **Role-specific**: One invite = one role only (cannot be reused)
- **Single-use**: Optimistic locking prevents double redemption
- **Time-bound**: Configurable expiration (hours)
- **Event-scoped STAFF invites**: Automatically creates event-staff assignment
- **Atomic redemption**: Role assignment + event assignment in one transaction

### Audit System
- **Immutable**: Append-only audit trail, no update/delete operations
- **Complete tracking**: Actor, target, event, resource, IP, user-agent
- **Role-based access**: 
  - ADMIN: All logs via `/api/v1/audit`
  - ORGANIZER: Own event logs via `/api/v1/audit/events/{eventId}`
  - Any user: Own action trail via `/api/v1/audit/me`
- **Actions logged**: ROLE_ASSIGNED, ROLE_REVOKED, STAFF_ASSIGNED, STAFF_REMOVED, INVITE_CREATED, INVITE_REDEEMED, INVITE_REVOKED, TICKET_VALIDATED, QR_CODE_VIEWED, QR_CODE_DOWNLOADED_PNG, QR_CODE_DOWNLOADED_PDF, SALES_REPORT_EXPORTED

### QR Code Export System (NEW)
- **Three export formats**: Inline view (PNG), Download PNG, Download PDF
- **Security Model**: 
  - QR encodes only ticket UUID (immutable, non-forgeable identifier)
  - Security boundary is backend validation, NOT QR image integrity
  - Safe to share, re-download, or re-view
- **Access Control**:
  - ATTENDEE: Own tickets only
  - ORGANIZER: Tickets from own events only
  - STAFF: NO access
  - ADMIN: NO access (no bypass)
- **Idempotency**: Same ticket always generates identical QR content
- **Audit Logging**: All QR operations logged (VIEWED, DOWNLOADED_PNG, DOWNLOADED_PDF)

### Sales Report Export (NEW)
- **Excel Format**: Comprehensive .xlsx report with formatting
- **Data Reuse**: Uses same `EventService.getSalesDashboard()` method (NO duplicate logic)
- **Contents**: 
  - Summary: Event name, total sold, total revenue, timestamp
  - Breakdown: Per ticket type analytics with currency formatting
- **Access Control**:
  - ORGANIZER: Must own event (ownership enforced)
  - ADMIN: NO bypass (must own event)
  - ATTENDEE/STAFF: NO access
- **Idempotency**: Same event state produces identical Excel content
- **Audit Logging**: SALES_REPORT_EXPORTED action logged

### Approval Gate System (NEW)
- **Business-Level Authorization**: Separate from Keycloak authentication
- **Execution Order**: JWT validation → Role extraction → User provisioning → **Approval Gate** → Business logic
- **States**: PENDING (blocked), APPROVED (allowed), REJECTED (blocked), NULL (legacy, auto-approved)
- **SAFE-BY-DEFAULT**: All endpoints require approval unless explicitly allowlisted
- **Allowlist**: Only 4 paths bypass approval (auth, health, info, invite redemption)
- **No Bypass**: Even ADMIN must be approved
- **Backward Compatible**: Legacy users auto-migrate to APPROVED

## Security Architecture
- **No roles in database**: All roles managed by Keycloak (single source of truth)
- **Centralized authorization**: AuthorizationService enforces all business rules
- **No bypasses**: Even ADMIN must follow event ownership rules
- **Approval Gate (NEW)**: Business-level authorization layer running after Keycloak auth
  - SAFE-BY-DEFAULT: All endpoints require approval unless explicitly allowlisted
  - Blocks PENDING/REJECTED users even with valid JWT and roles
  - Separate from authentication (approval = business trust, not identity verification)
- **QR Code Security (NEW)**:
  - QR encodes only ticket UUID (immutable, non-forgeable identifier)
  - Security boundary is backend validation at scan time, NOT QR image integrity
  - Ownership checked at export: User must own ticket OR own event
  - STAFF and ADMIN cannot export QR codes (no bypass)
- **Export Authorization (NEW)**:
  - Sales reports: ORGANIZER must own event, ADMIN does NOT bypass
  - QR exports: ATTENDEE owns ticket OR ORGANIZER owns event
  - All exports are read-only, idempotent operations
  - No file storage (streaming only)
- **Rate limiting**: 1000 req/min standard, 10 req/min for auth endpoints
- **Error sanitization**: Production mode removes sensitive information (UUIDs, stack traces)
- **Optimistic locking**: TicketType and InviteCode entities prevent concurrent modification issues
- **CORS**: Environment-configurable, strict in production
- **Transactional integrity**: All audit logging within service transactions

## Database Schema Notes
- **users**: User table with approval status fields (NEW)
  - `approval_status`: PENDING, APPROVED, REJECTED (VARCHAR, defaults to NULL for legacy users)
  - `approved_at`: Timestamp of approval (TIMESTAMP)
  - `approved_by`: User ID of admin who approved (UUID FK)
  - `rejection_reason`: Reason for rejection (VARCHAR)
- **audit_logs**: Immutable audit trail table
- **invite_codes**: Invite code management table
- **user_staffing_events**: Junction table for event-staff assignments
- **Version fields**: Added to TicketType and InviteCode for optimistic locking
- **Schema Management**: Hibernate auto-update (`spring.jpa.hibernate.ddl-auto=update`)
  - Development: Auto-creates columns on startup
  - Production: Uses validate mode (fail-fast if schema mismatch)
  - No manual SQL migrations required (code-driven schema)

---

**Documentation Version:** 3.0  
**Last Updated:** January 19, 2026  
**API Status:** Production Ready with Export Features  
**Security Status:** Hardened, Audited & Approval-Gated  
**New Features:** QR Code Exports (PNG/PDF), Sales Report Export (Excel), Approval Gate System
