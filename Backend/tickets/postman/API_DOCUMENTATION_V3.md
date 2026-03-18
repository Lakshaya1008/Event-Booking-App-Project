# Event Booking Platform — API Documentation
**Version:** 1.0 | **Base URL:** `http://localhost:8081` | **Auth:** OAuth2 Bearer JWT (Keycloak `http://localhost:9090`)

---

## Authentication

All endpoints require `Authorization: Bearer <token>` except `POST /api/v1/auth/register`.

**Get a token:**
```
POST http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=event-ticket-platform-app
client_secret=<client_secret>
username=<email>
password=<password>
```

---

## Role Matrix

| Role | Access |
|------|--------|
| ADMIN | Approvals, role management, audit logs (all). Cannot access events/tickets/published-events |
| ORGANIZER | Own events, ticket types, discounts, staff, invite codes (STAFF only), audit (own events) |
| ATTENDEE | Published events, ticket purchase, own tickets, QR codes |
| STAFF | Ticket validation for assigned events only |

---

## Approval Gate

All users start `PENDING` after registration. The approval gate blocks `PENDING` and `REJECTED` users from all endpoints except:
- `POST /api/v1/auth/register`
- `POST /api/v1/invites/redeem`
- `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`

---

## Error Response Format

Every error returns:
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Validation failed on 2 field(s). See validationErrors for details.",
  "statusCode": 400,
  "statusDescription": "BAD REQUEST - Validation failed",
  "timestamp": "2026-03-18T10:30:00",
  "path": "/api/v1/auth/register",
  "validationErrors": ["email: Email is required", "name: Name is required"],
  "possibleCauses": ["Missing required fields"],
  "solutions": ["Fix ALL fields listed in the validationErrors array"]
}
```

All validation errors are returned **simultaneously** — never one at a time.

### Error Codes

| Status | Error Code | Cause |
|--------|-----------|-------|
| 400 | `VALIDATION_ERROR` | Field validation failed |
| 400 | `INVALID_INPUT` | Business rule input error |
| 400 | `INVALID_ARGUMENT` | Event ID mismatch in body vs URL |
| 400 | `INVALID_INVITE_CODE` | Invite expired / redeemed / revoked |
| 400 | `TICKETS_SOLD_OUT` | No tickets remaining |
| 401 | `AUTHENTICATION_FAILED` | No token or expired token |
| 403 | `ACCESS_DENIED` | Wrong role for endpoint |
| 403 | `APPROVAL_PENDING` | Account awaiting admin approval |
| 403 | `APPROVAL_REJECTED` | Account rejected — reason in message |
| 404 | `EVENT_NOT_FOUND` | Event does not exist or not owned by caller |
| 404 | `TICKET_NOT_FOUND` | Ticket not found or belongs to another user |
| 404 | `TICKET_TYPE_NOT_FOUND` | Ticket type not found |
| 404 | `USER_NOT_FOUND` | User not found |
| 404 | `INVITE_CODE_NOT_FOUND` | Code was never created |
| 404 | `QR_CODE_NOT_FOUND` | Ticket cancelled — QR deactivated |
| 404 | `DISCOUNT_NOT_FOUND` | Discount not found |
| 409 | `EMAIL_ALREADY_REGISTERED` | Duplicate email address |
| 409 | `INVALID_APPROVAL_STATE` | Approve/reject wrong state transition |
| 409 | `BUSINESS_RULE_VIOLATION` | Business state conflict |
| 409 | `DISCOUNT_ALREADY_EXISTS` | Second active discount for same ticket type |
| 409 | `TICKET_TYPE_DELETE_NOT_ALLOWED` | Ticket type has active sold tickets |
| 409 | `DATA_CONFLICT` | DB unique constraint violation |
| 422 | `REGISTRATION_FAILED` | Keycloak or DB system error |
| 500 | `INTERNAL_SERVER_ERROR` | QR generation failure, Keycloak admin down |

---

## Pagination

All list endpoints accept:
```
?page=0&size=20&sort=createdAt,desc
```
Max page size: **50** (server enforced). Default: 20.

Paginated response shape:
```json
{
  "content": ["..."],
  "pageable": { "pageNumber": 0, "pageSize": 20 },
  "totalElements": 45,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

---

---

# ENDPOINT REFERENCE

---

## 1. Authentication

---

### POST /api/v1/auth/register

| | |
|--|--|
| **Auth** | None |
| **Role** | Public |
| **Approval gate** | Bypassed |

**Request body:**
```json
{
  "email": "user@example.com",
  "password": "Password1!",
  "name": "John Doe",
  "inviteCode": "ABCD-1234-EFGH-5678"
}
```

| Field | Required | Validation |
|-------|----------|-----------|
| `email` | ✅ | @NotBlank, @Email, max 255 |
| `password` | ✅ | @NotBlank, min 8, max 128, must contain uppercase + lowercase + digit + special char `!@#$%^&*` |
| `name` | ✅ | @NotBlank, min 2, max 100 |
| `inviteCode` | ❌ | Pattern: `^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$` |

**Response 201:**
```json
{
  "message": "Registration successful! Your account is pending admin approval.",
  "email": "user@example.com",
  "requiresApproval": true,
  "assignedRole": "ATTENDEE",
  "instructions": "You will receive an email once your account has been reviewed."
}
```

| Error | Status | When |
|-------|--------|------|
| `VALIDATION_ERROR` | 400 | Invalid fields |
| `INVALID_INVITE_CODE` | 400 | Code expired / redeemed / revoked |
| `INVITE_CODE_NOT_FOUND` | 404 | Code not in database |
| `EMAIL_ALREADY_REGISTERED` | 409 | Duplicate email |
| `REGISTRATION_FAILED` | 422 | Keycloak / DB system error |

---

## 2. Approval Management

Base path: `/api/v1/admin/approvals` | Role: **ADMIN**

---

### GET /api/v1/admin/approvals/pending

```
GET /api/v1/admin/approvals/pending?page=0&size=20
Authorization: Bearer {{admin_token}}
```

Returns all users with `PENDING` approval status. `roles` field is always `[]` in the list — use `GET /admin/users/{id}/roles` for roles.

**Response 200:** `Page<UserApprovalDto>`

---

### GET /api/v1/admin/approvals

```
GET /api/v1/admin/approvals?page=0&size=20
Authorization: Bearer {{admin_token}}
```

Returns all users with all approval statuses.

**Response 200:** `Page<UserApprovalDto>`

---

### POST /api/v1/admin/approvals/{userId}/approve

```
POST /api/v1/admin/approvals/{userId}/approve
Authorization: Bearer {{admin_token}}
```

No request body. Approves a PENDING user. Also enables their Keycloak account.

**Response 200:**
```json
{
  "message": "User approved successfully",
  "userId": "uuid",
  "status": "APPROVED"
}
```

| Error | Status | When |
|-------|--------|------|
| `USER_NOT_FOUND` | 404 | User ID doesn't exist |
| `INVALID_APPROVAL_STATE` | 409 | User is already APPROVED or REJECTED |

---

### POST /api/v1/admin/approvals/{userId}/reject

```
POST /api/v1/admin/approvals/{userId}/reject
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

**Request body:**
```json
{ "reason": "Account violates platform terms of service." }
```

| Field | Required | Validation |
|-------|----------|-----------|
| `reason` | ✅ | @NotBlank, min 10, max 500 |

**Response 200:**
```json
{
  "message": "User rejected successfully",
  "userId": "uuid",
  "status": "REJECTED",
  "reason": "Account violates platform terms of service."
}
```

---

## 3. Admin Role Management

Base path: `/api/v1/admin` | Role: **ADMIN**

---

### POST /api/v1/admin/users/{userId}/roles

```
POST /api/v1/admin/users/{userId}/roles
Authorization: Bearer {{admin_token}}
Content-Type: application/json
```

**Request body:**
```json
{ "roleName": "ORGANIZER" }
```

| Field | Required | Validation |
|-------|----------|-----------|
| `roleName` | ✅ | Must be one of: `ADMIN`, `ORGANIZER`, `ATTENDEE`, `STAFF` |

**Response 200:**
```json
{
  "userId": "uuid",
  "userName": "Test User",
  "email": "user@test.com",
  "roles": ["ORGANIZER"]
}
```

---

### DELETE /api/v1/admin/users/{userId}/roles/{roleName}

```
DELETE /api/v1/admin/users/{userId}/roles/STAFF
Authorization: Bearer {{admin_token}}
```

No request body. Revokes the named role from the user.

**Response 200:** `UserRolesResponseDto` with updated roles list.

---

### GET /api/v1/admin/users/{userId}/roles

```
GET /api/v1/admin/users/{userId}/roles
Authorization: Bearer {{admin_token}}
```

**Response 200:**
```json
{
  "userId": "uuid",
  "userName": "Test User",
  "email": "user@test.com",
  "roles": ["ATTENDEE"]
}
```

---

### GET /api/v1/admin/roles

```
GET /api/v1/admin/roles
Authorization: Bearer {{admin_token}}
```

Returns all available realm roles.

**Response 200:**
```json
{
  "roles": ["ADMIN", "ORGANIZER", "ATTENDEE", "STAFF"],
  "message": "Available roles in the system"
}
```

---

## 4. Invite Codes

Base path: `/api/v1/invites`

---

### POST /api/v1/invites

```
POST /api/v1/invites
Authorization: Bearer {{admin_token}}    ← or organizer_token
Content-Type: application/json
```

**Request body:**
```json
{
  "roleName": "STAFF",
  "eventId": "uuid",
  "expirationHours": 48
}
```

| Field | Required | Validation |
|-------|----------|-----------|
| `roleName` | ✅ | `ADMIN`, `ORGANIZER`, `ATTENDEE`, or `STAFF` |
| `expirationHours` | ✅ | @NotNull, @Positive (> 0) |
| `eventId` | Conditional | Required when `roleName=STAFF`, must be omitted for all others |

**Organizer restrictions:** Can only create `STAFF` invites for their own events.

**Response 201:**
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

---

### POST /api/v1/invites/redeem

```
POST /api/v1/invites/redeem
Authorization: Bearer {{any_token}}
Content-Type: application/json
```

Approval gate: **BYPASSED** — PENDING users can redeem.

**Request body:**
```json
{ "code": "ABCD-1234-EFGH-5678" }
```

**Response 200:**
```json
{
  "message": "Invite code redeemed successfully",
  "roleAssigned": "STAFF",
  "eventName": "Tech Conference 2025",
  "currentRoles": ["STAFF"]
}
```

| Error | Status | When |
|-------|--------|------|
| `INVITE_CODE_NOT_FOUND` | 404 | Code not in database |
| `INVALID_INVITE_CODE` | 400 | Code expired / already redeemed / revoked — specific reason in message |

---

### DELETE /api/v1/invites/{codeId}

```
DELETE /api/v1/invites/{codeId}?reason=Event+was+cancelled
Authorization: Bearer {{admin_token}}    ← or organizer_token (own codes only)
```

Query param `reason` is optional (default: "Revoked by creator").

**Response 204:** No content.

---

### GET /api/v1/invites

```
GET /api/v1/invites?page=0&size=20&sort=createdAt,desc
Authorization: Bearer {{admin_token}}    ← or organizer_token
```

ADMIN sees all codes. ORGANIZER sees only their own.

**Response 200:** `Page<InviteCodeResponseDto>`

---

### GET /api/v1/invites/events/{eventId}

```
GET /api/v1/invites/events/{eventId}?page=0&size=20
Authorization: Bearer {{admin_token}}    ← or organizer_token (own event only)
```

**Response 200:** `Page<InviteCodeResponseDto>`

---

## 5. Event Management

Base path: `/api/v1/events` | Role: **ORGANIZER** (must own event for all except POST)

---

### POST /api/v1/events

```
POST /api/v1/events
Authorization: Bearer {{organizer_token}}
Content-Type: application/json
```

**Request body:**
```json
{
  "name": "Tech Conference 2025",
  "venue": "Convention Center, Building A",
  "status": "PUBLISHED",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "maxCapacity": 500,
  "ticketTypes": [
    { "name": "Early Bird", "price": 149.99, "description": "Limited slots", "totalAvailable": 100 },
    { "name": "Regular", "price": 199.99, "totalAvailable": 400 }
  ]
}
```

| Field | Required | Validation |
|-------|----------|-----------|
| `name` | ✅ | @NotBlank, max 200 |
| `venue` | ✅ | @NotBlank, max 500 |
| `status` | ✅ | `DRAFT`, `PUBLISHED`, or `CANCELLED` |
| `ticketTypes` | ✅ | @NotEmpty — at least 1 element |
| `ticketTypes[].name` | ✅ | @NotBlank |
| `ticketTypes[].price` | ✅ | @NotNull, @DecimalMin("0.00") |
| `ticketTypes[].totalAvailable` | ✅ | @NotNull, @Min(1) |
| `ticketTypes[].description` | ❌ | — |
| `start` | ❌ | Format: `YYYY-MM-DDTHH:mm:ss` |
| `end` | ❌ | Must be after `start` if both provided |
| `salesStart` | ❌ | — |
| `salesEnd` | ❌ | Must be after `salesStart` if both provided |
| `maxCapacity` | ❌ | @Min(1) if provided — null means no cap |

**Response 201:**
```json
{
  "id": "uuid",
  "name": "Tech Conference 2025",
  "venue": "Convention Center, Building A",
  "status": "PUBLISHED",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "ticketTypes": [
    { "id": "uuid", "name": "Early Bird", "price": 149.99, "description": "Limited slots", "totalAvailable": 100, "createdAt": "...", "updatedAt": "..." }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

---

### PUT /api/v1/events/{eventId}

```
PUT /api/v1/events/{eventId}
Authorization: Bearer {{organizer_token}}
Content-Type: application/json
```

Same body structure as POST. Two rules:
1. If `id` is included in the body it must match the URL `{eventId}`.
2. Include `id` in each `ticketTypes` element to **update** it — omit `id` to **create** a new ticket type.

**Response 200:** `UpdateEventResponseDto` — same shape as create response.

| Error | Status | When |
|-------|--------|------|
| `INVALID_ARGUMENT` | 400 | Body `id` does not match URL `eventId` |
| `BUSINESS_RULE_VIOLATION` | 409 | Re-publishing a cancelled event; maxCapacity below sold count |
| `EVENT_NOT_FOUND` | 404 | Not owner |

---

### GET /api/v1/events

```
GET /api/v1/events?page=0&size=20&sort=start,desc
Authorization: Bearer {{organizer_token}}
```

Returns only the caller's own events.

**Response 200:** `Page<ListEventResponseDto>`

---

### GET /api/v1/events/{eventId}

```
GET /api/v1/events/{eventId}
Authorization: Bearer {{organizer_token}}
```

Returns full event details. Returns 404 if not owned by caller (ownership is hidden — no 403).

**Response 200:** `GetEventDetailsResponseDto`

---

### DELETE /api/v1/events/{eventId}

```
DELETE /api/v1/events/{eventId}
Authorization: Bearer {{organizer_token}}
```

**Response 204:** No content.

| Error | Status | When |
|-------|--------|------|
| `BUSINESS_RULE_VIOLATION` | 409 | Event has active tickets — cancel first |

---

### GET /api/v1/events/{eventId}/sales-dashboard

```
GET /api/v1/events/{eventId}/sales-dashboard
Authorization: Bearer {{organizer_token}}
```

**Response 200:**
```json
{
  "eventName": "Tech Conference 2025",
  "totalTicketsSold": 5,
  "totalRevenueBeforeDiscount": 999.95,
  "totalDiscountGiven": 99.99,
  "totalRevenueFinal": 899.96,
  "ticketTypeBreakdown": [
    {
      "ticketTypeName": "Early Bird",
      "basePrice": 199.99,
      "totalAvailable": 100,
      "sold": 5,
      "remaining": 95,
      "revenueBeforeDiscount": 999.95,
      "discountGiven": 99.99,
      "revenueFinal": 899.96
    }
  ]
}
```

Note: `remaining` is `null` when `totalAvailable` is null (unlimited). Cancelled tickets are excluded from all counts.

---

### GET /api/v1/events/{eventId}/attendees-report

```
GET /api/v1/events/{eventId}/attendees-report
Authorization: Bearer {{organizer_token}}
```

**Response 200:**
```json
{
  "eventName": "Tech Conference 2025",
  "totalAttendees": 5,
  "attendees": [
    {
      "attendeeName": "Test Attendee",
      "attendeeEmail": "attendee@test.com",
      "ticketType": "Early Bird",
      "ticketStatus": "VALIDATED",
      "purchaseDate": "2025-11-10T14:30:00",
      "validationCount": 2
    }
  ]
}
```

---

### GET /api/v1/events/{eventId}/sales-report.xlsx

```
GET /api/v1/events/{eventId}/sales-report.xlsx
Authorization: Bearer {{organizer_token}}
```

**Response 200:**
- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="eventname_sales_report_YYYYMMDD_HHmmss.xlsx"`
- Body: binary Excel file

---

## 6. Published Events

Base path: `/api/v1/published-events` | Role: **ATTENDEE**, **ORGANIZER**, or **STAFF** (not ADMIN)

---

### GET /api/v1/published-events

```
GET /api/v1/published-events?page=0&size=20&sort=start,asc
GET /api/v1/published-events?q=tech&page=0&size=20
Authorization: Bearer {{attendee_token}}
```

Optional `q` parameter filters by event name.

**Response 200:** `Page<ListPublishedEventResponseDto>`

---

### GET /api/v1/published-events/{eventId}

```
GET /api/v1/published-events/{eventId}
Authorization: Bearer {{attendee_token}}
```

Returns 404 for DRAFT or CANCELLED events.

**Response 200:**
```json
{
  "id": "uuid",
  "name": "Tech Conference 2025",
  "venue": "Convention Center",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "ticketTypes": [
    { "id": "uuid", "name": "Early Bird", "price": 149.99, "description": "Limited slots" }
  ]
}
```

---

## 7. Ticket Types

Base path: `/api/v1/events/{eventId}/ticket-types` | Role: **ORGANIZER** (must own event)

---

### POST /api/v1/events/{eventId}/ticket-types

```
POST /api/v1/events/{eventId}/ticket-types
Authorization: Bearer {{organizer_token}}
Content-Type: application/json
```

**Request body:**
```json
{ "name": "VIP", "price": 499.99, "description": "Premium access", "totalAvailable": 50 }
```

| Field | Required | Validation |
|-------|----------|-----------|
| `name` | ✅ | @NotBlank |
| `price` | ✅ | @NotNull, @DecimalMin("0.00") |
| `totalAvailable` | ✅ | @NotNull, @Min(1) |
| `description` | ❌ | — |

**Response 201:**
```json
{ "id": "uuid", "name": "VIP", "price": 499.99, "description": "Premium access", "totalAvailable": 50, "createdAt": "...", "updatedAt": "..." }
```

---

### GET /api/v1/events/{eventId}/ticket-types

```
GET /api/v1/events/{eventId}/ticket-types
Authorization: Bearer {{organizer_token}}
```

**Response 200:** `List<CreateTicketTypeResponseDto>`

---

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

```
GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
Authorization: Bearer {{organizer_token}}
```

**Response 200:** `CreateTicketTypeResponseDto` | 404 if wrong event

---

### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

```
PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
Authorization: Bearer {{organizer_token}}
Content-Type: application/json
```

**Request body:** Same fields as create — `name` and `price` required, others optional.

**Response 200:** `UpdateTicketTypeResponseDto`

| Error | Status | When |
|-------|--------|------|
| `BUSINESS_RULE_VIOLATION` | 409 | `totalAvailable` set below already-sold count |

---

### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

```
DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
Authorization: Bearer {{organizer_token}}
```

**Response 204:** No content.

| Error | Status | When |
|-------|--------|------|
| `TICKET_TYPE_DELETE_NOT_ALLOWED` | 409 | Has active sold tickets |

---

## 8. Ticket Purchase

---

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets

```
POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets
Authorization: Bearer {{attendee_token}}    ← or organizer_token
Content-Type: application/json
```

**Request body:**
```json
{ "quantity": 2 }
```

| Field | Required | Default | Validation |
|-------|----------|---------|-----------|
| `quantity` | ❌ | 1 | @Min(1), @Max(10) |

**Response 201:** Array of tickets
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

| Error | Status | When |
|-------|--------|------|
| `VALIDATION_ERROR` | 400 | quantity < 1 or > 10 |
| `TICKETS_SOLD_OUT` | 400 | No tickets remaining |
| `BUSINESS_RULE_VIOLATION` | 409 | Event not PUBLISHED; before salesStart; after salesEnd; per-user limit reached (max 10/type) |

---

## 9. Ticket Viewing

Base path: `/api/v1/tickets` | Role: **ATTENDEE** or **ORGANIZER**

---

### GET /api/v1/tickets

```
GET /api/v1/tickets?page=0&size=20&sort=id,desc
Authorization: Bearer {{attendee_token}}
```

Returns only the caller's own tickets.

**Response 200:** `Page<ListTicketResponseDto>`
```json
{
  "content": [
    { "id": "uuid", "status": "PURCHASED", "ticketType": { "id": "uuid", "name": "Early Bird", "price": 149.99 } }
  ]
}
```

---

### GET /api/v1/tickets/{ticketId}

```
GET /api/v1/tickets/{ticketId}
Authorization: Bearer {{attendee_token}}
```

**Response 200:** `GetTicketResponseDto`
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

Returns 404 if ticket belongs to another user.

---

## 10. QR Codes

Base path: `/api/v1/tickets/{ticketId}/qr-codes` | Role: **ATTENDEE** or **ORGANIZER**

---

### GET /api/v1/tickets/{ticketId}/qr-codes *(Legacy)*

```
GET /api/v1/tickets/{ticketId}/qr-codes
Authorization: Bearer {{attendee_token}}
```

**Response 200:** Binary PNG bytes. `Content-Type: image/png`

---

### GET /api/v1/tickets/{ticketId}/qr-codes/view

```
GET /api/v1/tickets/{ticketId}/qr-codes/view
Authorization: Bearer {{attendee_token}}
```

Inline display. Cache-Control is `private` — QR codes must never be shared caches.

**Response 200:**
- `Content-Type: image/png`
- `Content-Disposition: inline; filename="qr-code.png"`
- `Cache-Control: max-age=300, private`
- Body: PNG bytes

---

### GET /api/v1/tickets/{ticketId}/qr-codes/png

```
GET /api/v1/tickets/{ticketId}/qr-codes/png
Authorization: Bearer {{attendee_token}}
```

**Response 200:**
- `Content-Type: image/png`
- `Content-Disposition: attachment; filename="eventname_tickettype_username_ticketid.png"`

---

### GET /api/v1/tickets/{ticketId}/qr-codes/pdf

```
GET /api/v1/tickets/{ticketId}/qr-codes/pdf
Authorization: Bearer {{attendee_token}}
```

**Response 200:**
- `Content-Type: application/pdf`
- `Content-Disposition: attachment; filename="....pdf"`

---

## 11. Discounts

Base path: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts` | Role: **ORGANIZER** (must own event)

---

### POST .../discounts

```
POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
Authorization: Bearer {{organizer_token}}
Content-Type: application/json
```

**Request body:**
```json
{
  "discountType": "PERCENTAGE",
  "value": 20.0,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59",
  "active": true,
  "description": "Black Friday 20% off"
}
```

| Field | Required | Validation |
|-------|----------|-----------|
| `discountType` | ✅ | `PERCENTAGE` or `FIXED_AMOUNT` |
| `value` | ✅ | > 0. PERCENTAGE: ≤ 100. FIXED_AMOUNT: > 0 |
| `validFrom` | ✅ | Must be in the future for new discounts |
| `validTo` | ✅ | Must be after `validFrom` |
| `active` | ❌ | Default: `true` |
| `description` | ❌ | — |

Only **one active** discount per ticket type at a time.

**Response 201:**
```json
{
  "id": "uuid",
  "ticketTypeId": "uuid",
  "ticketTypeName": "Early Bird",
  "discountType": "PERCENTAGE",
  "value": 20.0,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59",
  "active": true,
  "description": "Black Friday 20% off",
  "createdAt": "...",
  "updatedAt": "..."
}
```

| Error | Status | When |
|-------|--------|------|
| `DISCOUNT_ALREADY_EXISTS` | 409 | Another active discount already exists for this ticket type |

---

### PUT .../discounts/{discountId}

```
PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
Authorization: Bearer {{organizer_token}}
Content-Type: application/json
```

Same body as create. `validFrom` in the past is allowed on update.

**Response 200:** `DiscountResponseDto`

| Error | Status | When |
|-------|--------|------|
| `VALIDATION_ERROR` | 400 | Attempt to change `discountType` or `value` after tickets have been sold |

---

### DELETE .../discounts/{discountId}

```
DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
Authorization: Bearer {{organizer_token}}
```

**Response 204:** No content.

---

### GET .../discounts/{discountId}

```
GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
Authorization: Bearer {{organizer_token}}
```

**Response 200:** `DiscountResponseDto` | 404 `DISCOUNT_NOT_FOUND`

---

### GET .../discounts

```
GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
Authorization: Bearer {{organizer_token}}
```

Returns all discounts (active and inactive) for the ticket type.

**Response 200:** `List<DiscountResponseDto>`

---

## 12. Ticket Validation

Base path: `/api/v1/ticket-validations` | Role: **STAFF** or **ORGANIZER**

---

### POST /api/v1/ticket-validations

```
POST /api/v1/ticket-validations
Authorization: Bearer {{staff_token}}
Content-Type: application/json
```

**Request body:**
```json
{ "id": "uuid", "method": "MANUAL" }
```

| Field | Required | Validation |
|-------|----------|-----------|
| `id` | ✅ | @NotNull — ticket UUID for MANUAL; QR code UUID for QR_SCAN |
| `method` | ✅ | @NotNull — exactly `MANUAL` or `QR_SCAN` |

A second scan of the same ticket returns 200 with `status: "INVALID"` — this is expected behaviour, not an error.

**Response 200:**
```json
{
  "ticketId": "uuid",
  "status": "VALID",
  "validatedById": "uuid",
  "validatedByName": "Test Staff",
  "validatedAt": "2025-12-15T10:23:45"
}
```

`status` values: `VALID` (first scan) | `INVALID` (already scanned)

| Error | Status | When |
|-------|--------|------|
| `TICKET_NOT_FOUND` | 404 | Ticket UUID not found |
| `QR_CODE_NOT_FOUND` | 404 | QR code UUID not found |
| `BUSINESS_RULE_VIOLATION` | 409 | Ticket is CANCELLED |
| `ACCESS_DENIED` | 403 | STAFF not assigned to this event |

---

### GET /api/v1/ticket-validations/events/{eventId}

```
GET /api/v1/ticket-validations/events/{eventId}?page=0&size=20
Authorization: Bearer {{staff_token}}
```

STAFF must be assigned to the event. ORGANIZER must own it.

**Response 200:** `Page<TicketValidationResponseDto>`

---

### GET /api/v1/ticket-validations/tickets/{ticketId}

```
GET /api/v1/ticket-validations/tickets/{ticketId}
Authorization: Bearer {{staff_token}}
```

**Response 200:** `List<TicketValidationResponseDto>` — all scans for that ticket

---

## 13. Event Staff Management

Base path: `/api/v1/events/{eventId}/staff` | Role: **ORGANIZER** (must own event)

---

### POST /api/v1/events/{eventId}/staff

```
POST /api/v1/events/{eventId}/staff
Authorization: Bearer {{organizer_token}}
Content-Type: application/json
```

**Request body:**
```json
{ "userId": "uuid" }
```

The user must already have the `STAFF` Keycloak role (assign via `POST /admin/users/{id}/roles` first).

**Response 201:**
```json
{
  "eventId": "uuid",
  "eventName": "Tech Conference 2025",
  "staffMembers": [
    { "userId": "uuid", "userName": "Test Staff", "email": "staff@test.com" }
  ],
  "totalStaffCount": 1
}
```

| Error | Status | When |
|-------|--------|------|
| `BUSINESS_RULE_VIOLATION` | 409 | User has no STAFF role; user already assigned |

---

### DELETE /api/v1/events/{eventId}/staff/{userId}

```
DELETE /api/v1/events/{eventId}/staff/{userId}
Authorization: Bearer {{organizer_token}}
```

**Response 200:** Updated `EventStaffResponseDto`.

---

### GET /api/v1/events/{eventId}/staff

```
GET /api/v1/events/{eventId}/staff
Authorization: Bearer {{organizer_token}}
```

**Response 200:** `EventStaffResponseDto`

---

## 14. Audit Logs

Base path: `/api/v1/audit`

---

### GET /api/v1/audit

```
GET /api/v1/audit?page=0&size=20&sort=createdAt,desc
Authorization: Bearer {{admin_token}}
```

Role: **ADMIN** only.

**Response 200:** `Page<AuditLogDto>` — includes `ipAddress` and `userAgent` fields.

---

### GET /api/v1/audit/events/{eventId}

```
GET /api/v1/audit/events/{eventId}?page=0&size=20
Authorization: Bearer {{organizer_token}}
```

Role: **ORGANIZER** (must own the event).

**Response 200:** `Page<AuditLogDto>`

---

### GET /api/v1/audit/me

```
GET /api/v1/audit/me?page=0&size=20
Authorization: Bearer {{any_approved_token}}
```

Role: Any authenticated approved user. Returns the caller's own actions only.

**Response 200:** `Page<AuditLogDto>`

---

*Total: 50 endpoints across 14 groups.*