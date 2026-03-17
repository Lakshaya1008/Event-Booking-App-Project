# Testing Guide V4 — Complete

Every endpoint. Every field. Every validation boundary. Every error case.

---

## Conventions

| Symbol | Meaning |
|---|---|
| ✅ | Should return 2xx — valid request |
| ❌ | Should return 4xx — invalid request |
| ⚠️ | Boundary value test |
| **BOLD** | Mandatory field |
| _italic_ | Optional field |

---

## Common Pageable Parameters

All paginated endpoints accept: `?page=0&size=20&sort=createdAt,desc`

---

## 1. AuthController — POST /api/v1/auth/register

No auth required. No approval check.

Fields: **email** @NotBlank @Email @Size(max=255) | **password** @NotBlank @Size(min=8,max=128) @Pattern(upper+lower+digit) | **name** @NotBlank @Size(min=2,max=100) | _inviteCode_ @Pattern(^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$)

**✅ Valid — minimum:**
```json
{ "email": "user@example.com", "password": "Password1", "name": "Jo" }
```

**✅ Valid — all fields:**
```json
{ "email": "user@example.com", "password": "Password1", "name": "Test User", "inviteCode": "ABCD-1234-EFGH-5678" }
```

**✅ name exactly 2 chars (min boundary):**
```json
{ "email": "u@ex.com", "password": "Password1", "name": "Jo" }
```

**✅ name exactly 100 chars (max boundary):**
```json
{ "email": "u@ex.com", "password": "Password1", "name": "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
```

**✅ password exactly 8 chars (min boundary):**
```json
{ "email": "u@ex.com", "password": "Passw0rd", "name": "Test" }
```

**✅ password exactly 128 chars (max boundary):**
```json
{ "email": "u@ex.com", "password": "Passw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rd", "name": "Test" }
```

**❌ Missing email → 400:**
```json
{ "password": "Password1", "name": "Test" }
```

**❌ Missing password → 400:**
```json
{ "email": "u@ex.com", "name": "Test" }
```

**❌ Missing name → 400:**
```json
{ "email": "u@ex.com", "password": "Password1" }
```

**❌ Empty body → 400:**
```json
{}
```

**❌ Invalid email format → 400:**
```json
{ "email": "notanemail", "password": "Password1", "name": "Test" }
```

**❌ Email too long (256 chars) → 400:**
```json
{ "email": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@x.com", "password": "Password1", "name": "Test" }
```

**❌ password too short (7 chars) → 400:**
```json
{ "email": "u@ex.com", "password": "Pass1rd", "name": "Test" }
```

**❌ password too long (129 chars) → 400:**
```json
{ "email": "u@ex.com", "password": "Passw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdPassw0rdX", "name": "Test" }
```

**❌ password no uppercase → 400:**
```json
{ "email": "u@ex.com", "password": "password1", "name": "Test" }
```

**❌ password no lowercase → 400:**
```json
{ "email": "u@ex.com", "password": "PASSWORD1", "name": "Test" }
```

**❌ password no digit → 400:**
```json
{ "email": "u@ex.com", "password": "PasswordX", "name": "Test" }
```

**❌ name too short (1 char) → 400:**
```json
{ "email": "u@ex.com", "password": "Password1", "name": "A" }
```

**❌ name too long (101 chars) → 400:**
```json
{ "email": "u@ex.com", "password": "Password1", "name": "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
```

**❌ name whitespace only (@NotBlank) → 400:**
```json
{ "email": "u@ex.com", "password": "Password1", "name": "   " }
```

**❌ inviteCode wrong format → 400:**
```json
{ "email": "u@ex.com", "password": "Password1", "name": "Test", "inviteCode": "badformat" }
```

**❌ inviteCode lowercase (pattern requires uppercase A-Z0-9) → 400:**
```json
{ "email": "u@ex.com", "password": "Password1", "name": "Test", "inviteCode": "abcd-1234-efgh-5678" }
```

**❌ Duplicate email → 409**

**Response (201):**
```json
{
  "message": "Registration successful! Your account is pending admin approval.",
  "email": "user@example.com",
  "requiresApproval": true,
  "assignedRole": "ATTENDEE",
  "instructions": "You will receive an email once your account has been reviewed."
}
```

---

## 2. InviteCodeController

### POST /api/v1/invites — Requires ADMIN or ORGANIZER

Fields: **roleName** @NotBlank @Pattern(^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$) | **expirationHours** @NotNull @Positive | _eventId_ UUID (required when roleName=STAFF)

**✅ ADMIN creates ATTENDEE invite:**
```json
{ "roleName": "ATTENDEE", "expirationHours": 24 }
```

**✅ ADMIN creates ORGANIZER invite:**
```json
{ "roleName": "ORGANIZER", "expirationHours": 72 }
```

**✅ ADMIN creates ADMIN invite:**
```json
{ "roleName": "ADMIN", "expirationHours": 24 }
```

**✅ ORGANIZER creates STAFF invite:**
```json
{ "roleName": "STAFF", "eventId": "{{event_id}}", "expirationHours": 48 }
```

**✅ expirationHours=1 (min @Positive boundary):**
```json
{ "roleName": "ATTENDEE", "expirationHours": 1 }
```

**✅ expirationHours=8760 (1 year):**
```json
{ "roleName": "ATTENDEE", "expirationHours": 8760 }
```

**❌ Missing roleName → 400:**
```json
{ "expirationHours": 24 }
```

**❌ Missing expirationHours → 400:**
```json
{ "roleName": "ATTENDEE" }
```

**❌ Empty body → 400:**
```json
{}
```

**❌ roleName=INVALID → 400:**
```json
{ "roleName": "SUPERUSER", "expirationHours": 24 }
```

**❌ expirationHours=0 (@Positive must be > 0) → 400:**
```json
{ "roleName": "ATTENDEE", "expirationHours": 0 }
```

**❌ expirationHours=-1 → 400:**
```json
{ "roleName": "ATTENDEE", "expirationHours": -1 }
```

**❌ STAFF without eventId → 400:**
```json
{ "roleName": "STAFF", "expirationHours": 24 }
```

**❌ Non-STAFF with eventId → 400:**
```json
{ "roleName": "ORGANIZER", "eventId": "{{event_id}}", "expirationHours": 24 }
```

**❌ ATTENDEE with eventId → 400:**
```json
{ "roleName": "ATTENDEE", "eventId": "{{event_id}}", "expirationHours": 24 }
```

**❌ ORGANIZER creates ORGANIZER invite → 400:**
```json
{ "roleName": "ORGANIZER", "expirationHours": 24 }
```
Expected message: `Organizers can only create STAFF invites.`

**❌ ORGANIZER creates ADMIN invite → 400:**
```json
{ "roleName": "ADMIN", "expirationHours": 24 }
```

**❌ ORGANIZER creates STAFF for event they don't own → 403**

**❌ ATTENDEE token → 403**

**Response (201):**
```json
{
  "id": "uuid",
  "code": "ABCD-1234-EFGH-5678",
  "roleName": "STAFF",
  "eventId": "uuid",
  "eventName": "Tech Conference",
  "status": "PENDING",
  "createdBy": "organizer@example.com",
  "createdAt": "2025-01-01T10:00:00",
  "expiresAt": "2025-01-03T10:00:00",
  "redeemedBy": null,
  "redeemedAt": null
}
```

---

### POST /api/v1/invites/redeem — Any authenticated user, no approval check

Fields: **code** @NotBlank

**✅ Valid:**
```json
{ "code": "ABCD-1234-EFGH-5678" }
```

**✅ PENDING user can redeem (bypass path):**
Send with PENDING user's token → 200

**❌ Missing code → 400:**
```json
{}
```

**❌ Empty code → 400:**
```json
{ "code": "" }
```

**❌ Code not found → 400**

**❌ Code already used → 400**

**❌ Code expired → 400**

**❌ Code revoked → 400**

**Response (200):**
```json
{
  "message": "Invite code redeemed successfully",
  "roleAssigned": "STAFF",
  "eventName": "Tech Conference 2025",
  "currentRoles": ["STAFF"]
}
```

---

### DELETE /api/v1/invites/{codeId}

Query: _reason_ (optional, default "Revoked by creator")

**✅ Creator revokes own code → 204**

**✅ ADMIN revokes any code → 204**

**✅ With reason param:**
`DELETE /api/v1/invites/{{invite_code_id}}?reason=Event+was+cancelled` → 204

**✅ Without reason (uses default):**
`DELETE /api/v1/invites/{{invite_code_id}}` → 204

**❌ ORGANIZER revoking another person's code → 400**

**❌ Code already REDEEMED → 400**

**❌ Code already REVOKED → 400**

**❌ Code not found → 404**

**❌ ATTENDEE token → 403**

---

### GET /api/v1/invites — ADMIN sees ALL, ORGANIZER sees own only

**✅** `GET /api/v1/invites?page=0&size=20&sort=createdAt,desc`

**✅** `GET /api/v1/invites` (defaults)

**❌ ATTENDEE token → 403**

---

### GET /api/v1/invites/events/{eventId}

**✅ ADMIN — any event → 200 (no ownership check)**

**✅ ORGANIZER — own event → 200**

**❌ ORGANIZER — event they don't own → 403**

---

## 3. EventController

### POST /api/v1/events — Requires ORGANIZER

Fields: **name** @NotBlank @Size(max=200) | **venue** @NotBlank @Size(max=500) | **status** @NotNull (DRAFT|PUBLISHED|CANCELLED) | **ticketTypes** @NotEmpty @Valid | _start_ | _end_ (must be after start if both) | _salesStart_ | _salesEnd_ (must be after salesStart if both) | _maxCapacity_ @Min(1) if provided

Each ticket type: **name** @NotBlank | **price** @NotNull @DecimalMin("0.00") | **totalAvailable** @NotNull @Min(1) | _description_

**✅ Minimum required:**
```json
{
  "name": "My Event",
  "venue": "City Hall",
  "status": "DRAFT",
  "ticketTypes": [
    { "name": "General", "price": 50.00, "totalAvailable": 100 }
  ]
}
```

**✅ All fields provided:**
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
    { "name": "Early Bird", "price": 149.99, "description": "Limited discount slots", "totalAvailable": 100 },
    { "name": "Regular", "price": 199.99, "description": "Standard admission", "totalAvailable": 400 }
  ]
}
```

**✅ Free event (price=0.00 is valid):**
```json
{
  "name": "Free Workshop",
  "venue": "Community Centre",
  "status": "PUBLISHED",
  "ticketTypes": [{ "name": "Free Entry", "price": 0.00, "totalAvailable": 50 }]
}
```

**✅ start without end (both optional independently):**
```json
{
  "name": "Open Event", "venue": "Outdoors", "status": "DRAFT",
  "start": "2025-12-15T09:00:00",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 50 }]
}
```

**✅ salesStart without salesEnd (valid):**
```json
{
  "name": "Always Open", "venue": "Venue", "status": "PUBLISHED",
  "salesStart": "2025-11-01T00:00:00",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 50 }]
}
```

**✅ Ticket type WITH description:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "name": "VIP", "price": 299.99, "description": "Includes dinner and backstage", "totalAvailable": 20 }]
}
```

**✅ Ticket type WITHOUT description (null):**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "name": "General", "price": 99.99, "totalAvailable": 200 }]
}
```

**✅ totalAvailable=1 (min boundary):**
```json
{
  "name": "Exclusive", "venue": "Private", "status": "DRAFT",
  "ticketTypes": [{ "name": "Exclusive", "price": 1000.00, "totalAvailable": 1 }]
}
```

**✅ maxCapacity=1 (min boundary):**
```json
{
  "name": "Private", "venue": "Restaurant", "status": "DRAFT",
  "maxCapacity": 1,
  "ticketTypes": [{ "name": "Seat", "price": 200.00, "totalAvailable": 1 }]
}
```

**✅ Multiple ticket types:**
```json
{
  "name": "Conference", "venue": "Hall", "status": "PUBLISHED",
  "ticketTypes": [
    { "name": "VIP", "price": 499.99, "totalAvailable": 20 },
    { "name": "Standard", "price": 199.99, "totalAvailable": 200 },
    { "name": "Student", "price": 99.99, "totalAvailable": 100 }
  ]
}
```

**❌ Missing name → 400**

**❌ Missing venue → 400**

**❌ Missing status → 400**

**❌ Missing ticketTypes → 400:**
```json
{ "name": "Event", "venue": "Venue", "status": "DRAFT" }
```

**❌ Empty ticketTypes array (@NotEmpty) → 400:**
```json
{ "name": "Event", "venue": "Venue", "status": "DRAFT", "ticketTypes": [] }
```

**❌ Ticket type missing name → 400:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "price": 50.00, "totalAvailable": 100 }]
}
```

**❌ Ticket type missing price → 400:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "name": "T", "totalAvailable": 100 }]
}
```

**❌ Ticket type missing totalAvailable → 400:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "name": "T", "price": 50.00 }]
}
```

**❌ name too long (201 chars) → 400:**
```json
{
  "name": "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{"name":"T","price":10,"totalAvailable":10}]
}
```

**❌ venue too long (501 chars) → 400:**
```json
{
  "name": "Event",
  "venue": "Vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv",
  "status": "DRAFT",
  "ticketTypes": [{"name":"T","price":10,"totalAvailable":10}]
}
```

**❌ status invalid enum value → 400:**
```json
{ "name": "Event", "venue": "Venue", "status": "OPEN", "ticketTypes": [{"name":"T","price":10,"totalAvailable":10}] }
```

**❌ maxCapacity=0 → 400:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "maxCapacity": 0,
  "ticketTypes": [{"name":"T","price":10,"totalAvailable":10}]
}
```

**❌ maxCapacity=-1 → 400**

**❌ ticket price negative → 400:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "name": "T", "price": -0.01, "totalAvailable": 10 }]
}
```

**❌ totalAvailable=0 → 400:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 0 }]
}
```

**❌ totalAvailable=-1 → 400**

**❌ end before start → 400:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "start": "2025-12-15T18:00:00", "end": "2025-12-15T09:00:00",
  "ticketTypes": [{"name":"T","price":10,"totalAvailable":10}]
}
```

**❌ end equals start → 400:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "start": "2025-12-15T09:00:00", "end": "2025-12-15T09:00:00",
  "ticketTypes": [{"name":"T","price":10,"totalAvailable":10}]
}
```

**❌ salesEnd before salesStart → 400:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "salesStart": "2025-12-01T00:00:00", "salesEnd": "2025-11-01T00:00:00",
  "ticketTypes": [{"name":"T","price":10,"totalAvailable":10}]
}
```

**❌ ATTENDEE token → 403**

**Response (201):**
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
    { "id": "uuid", "name": "Early Bird", "price": 149.99, "description": "Limited", "totalAvailable": 100, "createdAt": "...", "updatedAt": "..." }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

---

### PUT /api/v1/events/{eventId} — Requires ORGANIZER (must own event)

Same field validations as POST, plus:
- _id_ in body must match path eventId if provided
- **ticketTypes** each entry: _id_ (omit to create new type inline), **name**, **price**, _description_, _totalAvailable_
- _maxCapacity_ @Min(1) if provided

**✅ Basic update:**
```json
{
  "id": "{{event_id}}",
  "name": "Updated Name",
  "venue": "New Venue",
  "status": "PUBLISHED",
  "ticketTypes": [
    { "id": "{{ticket_type_id}}", "name": "General", "price": 99.99, "totalAvailable": 200 }
  ]
}
```

**✅ Add new ticket type inline (no id = create new):**
```json
{
  "id": "{{event_id}}",
  "name": "Event",
  "venue": "Venue",
  "status": "PUBLISHED",
  "ticketTypes": [
    { "id": "{{ticket_type_id}}", "name": "Existing", "price": 99.99, "totalAvailable": 200 },
    { "name": "New VIP", "price": 499.99, "totalAvailable": 20 }
  ]
}
```

**✅ Set maxCapacity:**
```json
{
  "id": "{{event_id}}", "name": "Event", "venue": "Venue", "status": "PUBLISHED",
  "maxCapacity": 600,
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10, "totalAvailable": 600 }]
}
```

**✅ Remove maxCapacity (null = no cap):**
```json
{
  "id": "{{event_id}}", "name": "Event", "venue": "Venue", "status": "PUBLISHED",
  "maxCapacity": null,
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10, "totalAvailable": 600 }]
}
```

**✅ Cancel event (bulk-cancels all tickets):**
```json
{
  "id": "{{event_id}}", "name": "Event", "venue": "Venue",
  "status": "CANCELLED",
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10, "totalAvailable": 100 }]
}
```

**✅ Ticket type with description:**
```json
{
  "id": "{{event_id}}", "name": "Event", "venue": "Venue", "status": "PUBLISHED",
  "ticketTypes": [
    { "id": "{{ticket_type_id}}", "name": "VIP", "price": 499.99, "description": "Premium access", "totalAvailable": 50 }
  ]
}
```

**✅ Ticket type without description:**
```json
{
  "id": "{{event_id}}", "name": "Event", "venue": "Venue", "status": "PUBLISHED",
  "ticketTypes": [
    { "id": "{{ticket_type_id}}", "name": "General", "price": 99.99, "totalAvailable": 200 }
  ]
}
```

**❌ id in body ≠ path eventId → 400:**
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10, "totalAvailable": 100 }]
}
```

**❌ maxCapacity=0 → 400**

**❌ Re-publish CANCELLED event → 400:**
```json
{
  "id": "{{cancelled_event_id}}",
  "name": "Event", "venue": "Venue", "status": "PUBLISHED",
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10, "totalAvailable": 100 }]
}
```
Expected: `Cannot change status of a cancelled event.`

**❌ maxCapacity below already-sold active ticket count → 400**

**❌ name=201 chars → 400**

**❌ venue=501 chars → 400**

**❌ date ordering violation → 400**

**❌ ticketType id not belonging to this event → 404**

**❌ Not owner → 403**

---

### GET /api/v1/events — Requires ORGANIZER

**✅** `GET /api/v1/events?page=0&size=20&sort=start,desc`

**Response (200):** `Page<ListEventResponseDto>` — includes all event fields + ticketTypes

---

### GET /api/v1/events/{eventId} — Requires ORGANIZER (must own)

**✅ Own event → 200**

**Response (200):** `GetEventDetailsResponseDto`
```json
{
  "id": "uuid", "name": "Tech Conference", "start": "...", "end": "...",
  "venue": "Convention Center", "salesStart": "...", "salesEnd": "...", "status": "PUBLISHED",
  "ticketTypes": [{ "id": "uuid", "name": "Early Bird", "price": 149.99, "description": "...", "totalAvailable": 100, "createdAt": "...", "updatedAt": "..." }],
  "createdAt": "...", "updatedAt": "..."
}
```

**❌ Not owner → 403 | Not found → 404**

---

### DELETE /api/v1/events/{eventId} — Requires ORGANIZER (must own)

**✅ Event with no active tickets → 204**

**❌ Event with active (non-CANCELLED) tickets → 400:**
Expected: `Cannot delete event — N active ticket(s) exist. Cancel the event first.`

---

### GET /api/v1/events/{eventId}/sales-dashboard

**✅** No body → 200. CANCELLED tickets excluded from all counts and revenue.

**Response (200):**
```json
{
  "eventName": "Tech Conference",
  "totalTicketsSold": 45,
  "totalRevenueBeforeDiscount": "8955.00",
  "totalDiscountGiven": "895.50",
  "totalRevenueFinal": "8059.50",
  "ticketTypeBreakdown": [
    {
      "ticketTypeName": "Early Bird", "basePrice": "199.00", "totalAvailable": 100,
      "sold": 45, "remaining": 55,
      "revenueBeforeDiscount": "8955.00", "discountGiven": "895.50", "revenueFinal": "8059.50"
    }
  ]
}
```

---

### GET /api/v1/events/{eventId}/attendees-report

**✅** No body → 200. CANCELLED tickets excluded.

---

### GET /api/v1/events/{eventId}/sales-report.xlsx

**✅** No body → 200, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

---

## 4. TicketTypeController

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets (Purchase)

Fields: _quantity_ @Min(1) @Max(10) default=1

**✅ No body (defaults to quantity=1):**
```json
{}
```

**✅ quantity=1 (min boundary):**
```json
{ "quantity": 1 }
```

**✅ quantity=5:**
```json
{ "quantity": 5 }
```

**✅ quantity=10 (max boundary):**
```json
{ "quantity": 10 }
```

**❌ quantity=0 → 400:**
```json
{ "quantity": 0 }
```

**❌ quantity=11 (above max) → 400:**
```json
{ "quantity": 11 }
```

**❌ quantity=-1 → 400:**
```json
{ "quantity": -1 }
```

**❌ Event not PUBLISHED → 400:** `Tickets are not available — the event is not open for sales.`

**❌ Event CANCELLED → 400:** `This event has been cancelled.`

**❌ Before salesStart → 400:** `Sales have not started yet. Sales open at <time>.`

**❌ After salesEnd → 400:** `Sales have closed. Sales ended at <time>.`

**❌ Sold out → 400** (TicketsSoldOutException)

**❌ Quantity exceeds maxCapacity remaining → 400**

**❌ STAFF token → 403**

**Response (201):** `List<GetTicketResponseDto>`
```json
[
  {
    "id": "uuid",
    "status": "PURCHASED",
    "price": 199.99,
    "pricePaid": 179.99,
    "originalPrice": 199.99,
    "discountApplied": 20.00,
    "description": "Early Bird",
    "eventName": "Tech Conference 2025",
    "eventVenue": "Convention Center",
    "eventStart": "2025-12-15T09:00:00",
    "eventEnd": "2025-12-15T18:00:00"
  }
]
```

---

### POST /api/v1/events/{eventId}/ticket-types — Requires ORGANIZER

Fields: **name** @NotBlank | **price** @NotNull @DecimalMin("0.00") | **totalAvailable** @NotNull @Min(1) | _description_

**✅ Minimum:**
```json
{ "name": "General", "price": 99.99, "totalAvailable": 200 }
```

**✅ All fields:**
```json
{ "name": "VIP Pass", "price": 499.99, "description": "Premium access with dinner", "totalAvailable": 50 }
```

**✅ Free ticket (price=0.00):**
```json
{ "name": "Free Entry", "price": 0.00, "totalAvailable": 100 }
```

**✅ totalAvailable=1 (min boundary):**
```json
{ "name": "Exclusive", "price": 1500.00, "totalAvailable": 1 }
```

**✅ With description:**
```json
{ "name": "Early Bird", "price": 149.99, "description": "20% off regular price", "totalAvailable": 50 }
```

**✅ Without description:**
```json
{ "name": "Regular", "price": 199.99, "totalAvailable": 400 }
```

**❌ Missing name → 400**

**❌ Missing price → 400**

**❌ Missing totalAvailable → 400:**
```json
{ "name": "T", "price": 10.00 }
```

**❌ name whitespace only → 400:**
```json
{ "name": "   ", "price": 10.00, "totalAvailable": 50 }
```

**❌ price=-0.01 → 400:**
```json
{ "name": "T", "price": -0.01, "totalAvailable": 50 }
```

**❌ totalAvailable=0 → 400:**
```json
{ "name": "T", "price": 10.00, "totalAvailable": 0 }
```

**❌ totalAvailable=-1 → 400**

**❌ Not owner → 403**

---

### GET /api/v1/events/{eventId}/ticket-types — Requires ORGANIZER (must own)

**✅** No body → 200, `List<CreateTicketTypeResponseDto>`

---

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

**✅** Own event → 200

**❌** Ticket type not for this event → 404

---

### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

Fields: **name** @NotBlank | **price** @NotNull @DecimalMin("0.00") | _description_ | _totalAvailable_ @Min(1) if provided

**✅ Update name and price:**
```json
{ "name": "VIP Updated", "price": 549.99 }
```

**✅ With description:**
```json
{ "name": "VIP Updated", "price": 549.99, "description": "Updated premium access" }
```

**✅ Remove description:**
```json
{ "name": "VIP Updated", "price": 549.99, "description": null }
```

**✅ Raise totalAvailable:**
```json
{ "name": "General", "price": 99.99, "totalAvailable": 500 }
```

**✅ totalAvailable null (don't change):**
```json
{ "name": "General", "price": 99.99, "totalAvailable": null }
```

**❌ Lower totalAvailable below already-sold active count → 400**

**❌ totalAvailable=0 → 400**

---

### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

**✅** No sold tickets → 204

**❌** Has sold tickets → 400

---

## 5. PublishedEventController

### GET /api/v1/published-events — Requires ATTENDEE, ORGANIZER, or STAFF

**✅** `GET /api/v1/published-events?q=tech&page=0&size=20&sort=start,asc`

**✅** No params → 200

**✅** Sort variations: `?sort=start,asc`, `?sort=name,asc`, `?sort=start,desc`

**❌ No token → 401**

**❌ ADMIN token → 403**

**Response (200):** `Page<ListPublishedEventResponseDto>`
```json
{
  "content": [{ "id": "uuid", "name": "Tech Conference", "start": "...", "end": "...", "venue": "Convention Center" }],
  "totalElements": 5, "totalPages": 1, "size": 20, "number": 0
}
```

---

### GET /api/v1/published-events/{eventId}

**✅ PUBLISHED event → 200**

**Response (200):** `GetPublishedEventDetailsResponseDto`
```json
{
  "id": "uuid", "name": "Tech Conference 2025",
  "start": "2025-12-15T09:00:00", "end": "2025-12-15T18:00:00",
  "venue": "Convention Center",
  "ticketTypes": [{ "id": "uuid", "name": "Early Bird", "price": 149.99, "description": "Limited" }]
}
```

**❌ DRAFT event → 404**

**❌ CANCELLED event → 404**

---

## 6. TicketController

### GET /api/v1/tickets — Requires ATTENDEE or ORGANIZER

**✅** `GET /api/v1/tickets?page=0&size=20&sort=id,desc`

**Response (200):** `Page<ListTicketResponseDto>`
```json
{
  "content": [
    { "id": "uuid", "status": "PURCHASED", "ticketType": { "id": "uuid", "name": "Early Bird", "price": 149.99 } }
  ]
}
```

**❌ STAFF token → 403**

---

### GET /api/v1/tickets/{ticketId} — Must own ticket

**✅** Own ticket → 200

**Response (200):** `GetTicketResponseDto`
```json
{
  "id": "uuid",
  "status": "PURCHASED",
  "price": 199.99,
  "pricePaid": 179.99,
  "originalPrice": 199.99,
  "discountApplied": 20.00,
  "description": "Early Bird",
  "eventName": "Tech Conference 2025",
  "eventVenue": "Convention Center",
  "eventStart": "2025-12-15T09:00:00",
  "eventEnd": "2025-12-15T18:00:00"
}
```

**❌ Not own ticket → 403 | Not found → 404 | STAFF token → 403**

---

### GET /api/v1/tickets/{ticketId}/qr-codes

**✅** Own ticket → 200, `image/png` bytes

**❌ QR code not found or not active → 404** (was 500 in V3)

---

### GET /api/v1/tickets/{ticketId}/qr-codes/view

**✅** Own ticket → 200, `image/png` inline

---

### GET /api/v1/tickets/{ticketId}/qr-codes/png

**✅** Own ticket → 200, `image/png` attachment, `Content-Disposition: attachment`

---

### GET /api/v1/tickets/{ticketId}/qr-codes/pdf

**✅** Own ticket → 200, `application/pdf`

---

## 7. TicketValidationController

### POST /api/v1/ticket-validations — Requires STAFF or ORGANIZER (assigned to event)

Fields: **id** UUID @NotNull | **method** TicketValidationMethod @NotNull (MANUAL | QR_SCAN)

**✅ Manual validation:**
```json
{ "id": "{{ticket_id}}", "method": "MANUAL" }
```

**✅ QR scan (id is QR code UUID, not ticket UUID):**
```json
{ "id": "{{qr_code_id}}", "method": "QR_SCAN" }
```

**✅ Second scan returns INVALID (not an error):**
```json
{ "id": "{{already_validated_ticket_id}}", "method": "MANUAL" }
```
→ 200 with `"status": "INVALID"`

**❌ Empty body → 400:**
```json
{}
```

**❌ Missing id → 400:**
```json
{ "method": "MANUAL" }
```

**❌ Missing method → 400:**
```json
{ "id": "{{ticket_id}}" }
```

**❌ id=null → 400:**
```json
{ "id": null, "method": "MANUAL" }
```

**❌ method=null → 400:**
```json
{ "id": "{{ticket_id}}", "method": null }
```

**❌ method=QR_CODE (wrong — correct is QR_SCAN) → 400:**
```json
{ "id": "{{ticket_id}}", "method": "QR_CODE" }
```

**❌ method=SCAN (wrong) → 400:**
```json
{ "id": "{{ticket_id}}", "method": "SCAN" }
```

**❌ id is not a valid UUID → 400:**
```json
{ "id": "not-a-uuid", "method": "MANUAL" }
```

**❌ CANCELLED ticket → 400:**
```json
{ "id": "{{cancelled_ticket_id}}", "method": "MANUAL" }
```
Expected: `Ticket ... has been cancelled and cannot be validated.`

**❌ Ticket not found → 404**

**❌ QR code not found → 404**

**❌ ATTENDEE token → 403**

**❌ STAFF not assigned to this event → 403**

**Response (200):**
```json
{
  "ticketId": "uuid",
  "status": "VALID",
  "validatedById": "uuid",
  "validatedByName": "John Staff",
  "validatedAt": "2025-12-15T10:23:45"
}
```

Re-scan response:
```json
{
  "ticketId": "uuid",
  "status": "INVALID",
  "validatedById": "uuid",
  "validatedByName": "John Staff",
  "validatedAt": "2025-12-15T10:25:00"
}
```

---

### GET /api/v1/ticket-validations/events/{eventId}

**✅** `GET /api/v1/ticket-validations/events/{{event_id}}?page=0&size=20` → 200, `Page<TicketValidationResponseDto>`

**❌ Not assigned to event → 403**

---

### GET /api/v1/ticket-validations/tickets/{ticketId}

**✅** No body → 200, `List<TicketValidationResponseDto>`

---

## 8. EventStaffController

### POST /api/v1/events/{eventId}/staff — Requires ORGANIZER (must own)

Fields: **userId** UUID @NotNull

**✅ Valid:**
```json
{ "userId": "{{staff_user_id}}" }
```

**❌ Missing userId → 400:**
```json
{}
```

**❌ userId=null → 400:**
```json
{ "userId": null }
```

**❌ User does not have STAFF Keycloak role → 400**

**❌ Not owner → 403**

---

### DELETE /api/v1/events/{eventId}/staff/{userId} — Requires ORGANIZER (must own)

**✅ Remove assigned staff → 200**

**❌ User NOT assigned to this event → 400:**
Expected: `User '...' is not assigned as staff to event '...'.`

**❌ Not owner → 403**

---

### GET /api/v1/events/{eventId}/staff

**✅** No body → 200

**Response (200):** `EventStaffResponseDto`
```json
{
  "eventId": "uuid",
  "eventName": "Tech Conference 2025",
  "staffMembers": [
    { "userId": "uuid", "userName": "John Staff", "email": "john@example.com" }
  ],
  "totalStaffCount": 1
}
```

---

## 9. AdminGovernanceController

### POST /api/v1/admin/users/{userId}/roles — Requires ADMIN

Fields: **roleName** @NotBlank @Pattern(^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$)

**✅ ATTENDEE:** `{ "roleName": "ATTENDEE" }`

**✅ STAFF:** `{ "roleName": "STAFF" }`

**✅ ORGANIZER:** `{ "roleName": "ORGANIZER" }`

**✅ ADMIN:** `{ "roleName": "ADMIN" }`

**❌ Missing roleName → 400:** `{}`

**❌ Invalid role → 400:** `{ "roleName": "SUPERUSER" }`

**❌ ORGANIZER token → 403**

**Response (200):** `UserRolesResponseDto`
```json
{ "userId": "uuid", "userName": "Test User", "email": "test@example.com", "roles": ["ATTENDEE"] }
```

---

### DELETE /api/v1/admin/users/{userId}/roles/{roleName}

**✅** `DELETE /api/v1/admin/users/{{user_id}}/roles/STAFF` → 200

**❌ ORGANIZER token → 403**

---

### GET /api/v1/admin/users/{userId}/roles

**✅** → 200, `UserRolesResponseDto`

*Use this to get role details — approval list no longer fetches roles per user (M-03 fix).*

---

### GET /api/v1/admin/roles

**✅** → 200, `{ "roles": ["ADMIN","ORGANIZER","ATTENDEE","STAFF"] }`

---

## 10. ApprovalController

### GET /api/v1/admin/approvals/pending — Requires ADMIN

**✅** `GET /api/v1/admin/approvals/pending?page=0&size=20` → 200

Note: `roles` field is empty. Use GET /api/v1/admin/users/{userId}/roles for roles.

**Response (200):** `Page<UserApprovalDto>`
```json
{
  "content": [{
    "userId": "uuid", "name": "Test User", "email": "test@example.com",
    "approvalStatus": "PENDING", "roles": [],
    "createdAt": "...", "rejectionReason": null,
    "approvedAt": null, "rejectedAt": null, "approvedByName": null
  }]
}
```

---

### POST /api/v1/admin/approvals/{userId}/approve — Requires ADMIN

**✅ Approve PENDING user → 200**

**❌ User already APPROVED → 400:**
Expected: `Cannot approve user with status 'APPROVED'. Only PENDING users can be approved.`

**❌ User REJECTED → 400:**
Expected: `Cannot approve user with status 'REJECTED'.`

**❌ User not found → 404**

**❌ ORGANIZER token → 403**

---

### POST /api/v1/admin/approvals/{userId}/reject — Requires ADMIN

Fields: **reason** @NotBlank @Size(min=10, max=500)

**✅ Valid reason:**
```json
{ "reason": "Account creation violated platform terms of service." }
```

**✅ reason exactly 10 chars (min boundary):**
```json
{ "reason": "Duplicate." }
```

**✅ reason exactly 500 chars (max boundary):**
```json
{ "reason": "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
```

**❌ reason too short (9 chars) → 400:**
```json
{ "reason": "Too short" }
```

**❌ reason too long (501 chars) → 400**

**❌ Missing reason → 400:**
```json
{}
```

**❌ Empty reason → 400:**
```json
{ "reason": "" }
```

**❌ Whitespace only → 400:**
```json
{ "reason": "          " }
```

**❌ User already REJECTED → 400**

---

### GET /api/v1/admin/approvals

**✅** `GET /api/v1/admin/approvals?page=0&size=20` → 200, all users

---

## 11. AuditController

### GET /api/v1/audit — Requires ADMIN

**✅** `GET /api/v1/audit?page=0&size=20&sort=createdAt,desc` → 200

**Response (200):** `Page<AuditLogDto>`
```json
{
  "content": [{
    "id": "uuid", "action": "TICKET_PURCHASED",
    "actorName": "Jane Doe", "actorId": "uuid",
    "targetUserName": "Jane Doe", "targetUserId": "uuid",
    "eventName": "Tech Conference", "eventId": "uuid",
    "resourceType": "TICKET", "resourceId": "uuid",
    "details": "ticketType=Early Bird,quantity=2",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0 ...",
    "createdAt": "2025-12-15T10:30:00"
  }]
}
```

**❌ ORGANIZER token → 403**

---

### GET /api/v1/audit/events/{eventId} — Requires ORGANIZER (must own)

**✅** `GET /api/v1/audit/events/{{event_id}}?page=0&size=20` → 200

---

### GET /api/v1/audit/me — Any authenticated approved user

**✅** `GET /api/v1/audit/me?page=0&size=20` → 200

---

## 12. DiscountController

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts — Requires ORGANIZER

Fields: **discountType** @NotNull (PERCENTAGE|FIXED_AMOUNT) | **value** @NotNull @DecimalMin("0.01") | **validFrom** @NotNull @FutureOrPresent | **validTo** @NotNull @Future | _active_ Boolean | _description_ String

**✅ PERCENTAGE minimum:**
```json
{ "discountType": "PERCENTAGE", "value": 10.0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**✅ PERCENTAGE all fields:**
```json
{ "discountType": "PERCENTAGE", "value": 20.0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59", "active": true, "description": "Black Friday 20% off" }
```

**✅ PERCENTAGE value=0.01 (min boundary):**
```json
{ "discountType": "PERCENTAGE", "value": 0.01, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**✅ PERCENTAGE value=100.0 (max boundary):**
```json
{ "discountType": "PERCENTAGE", "value": 100.0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**✅ FIXED_AMOUNT minimum:**
```json
{ "discountType": "FIXED_AMOUNT", "value": 50.00, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**✅ FIXED_AMOUNT all fields:**
```json
{ "discountType": "FIXED_AMOUNT", "value": 50.00, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59", "active": true, "description": "50 off promo" }
```

**✅ FIXED_AMOUNT value > ticket price (valid — price clamped to 0, no error):**
```json
{ "discountType": "FIXED_AMOUNT", "value": 9999.00, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**✅ active=false:**
```json
{ "discountType": "PERCENTAGE", "value": 15.0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59", "active": false }
```

**✅ active absent (null = inactive):**
```json
{ "discountType": "PERCENTAGE", "value": 15.0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**✅ description absent:**
```json
{ "discountType": "PERCENTAGE", "value": 15.0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**✅ description provided:**
```json
{ "discountType": "PERCENTAGE", "value": 15.0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59", "description": "Launch promo" }
```

**❌ Missing discountType → 400**

**❌ Missing value → 400**

**❌ Missing validFrom → 400**

**❌ Missing validTo → 400**

**❌ PERCENTAGE value=0 → 400:**
```json
{ "discountType": "PERCENTAGE", "value": 0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**❌ PERCENTAGE value=100.01 (above max) → 400:**
```json
{ "discountType": "PERCENTAGE", "value": 100.01, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**❌ FIXED_AMOUNT value=0 → 400:**
```json
{ "discountType": "FIXED_AMOUNT", "value": 0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```

**❌ FIXED_AMOUNT value=-1 → 400**

**❌ validTo before validFrom → 400:**
```json
{ "discountType": "PERCENTAGE", "value": 10, "validFrom": "2025-11-30T00:00:00", "validTo": "2025-11-01T00:00:00" }
```

**❌ validTo equals validFrom → 400:**
```json
{ "discountType": "PERCENTAGE", "value": 10, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-01T00:00:00" }
```

**❌ validFrom in the past → 400** (@FutureOrPresent)

**❌ Duplicate active discount for same ticket type → 409** (DiscountAlreadyExistsException)

**❌ ATTENDEE token → 403 | Not owner → 403**

**Response (201):** `DiscountResponseDto`
```json
{
  "id": "uuid", "ticketTypeId": "uuid", "ticketTypeName": "Early Bird",
  "discountType": "PERCENTAGE", "value": 20.0,
  "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59",
  "active": true, "description": "Black Friday",
  "createdAt": "...", "updatedAt": "..."
}
```

---

### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}

Same validation as POST → 200

---

### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}

**✅** → 204 | **❌** Not found → 404 | **❌** Not owner → 403

---

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}

**✅** → 200, `DiscountResponseDto`

---

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts

**✅** → 200, `List<DiscountResponseDto>`

---

## 13. Security Tests

| Test | Token | Endpoint | Expected |
|---|---|---|---|
| No token | None | Any | 401 |
| Expired token | Expired JWT | Any | 401 |
| Malformed token | "Bearer bad" | Any | 401 |
| PENDING user | PENDING | GET /api/v1/published-events | 403 APPROVAL_PENDING |
| REJECTED user | REJECTED | Any | 403 APPROVAL_REJECTED |
| PENDING bypass | PENDING | POST /api/v1/auth/register | 201 ✅ |
| PENDING bypass | PENDING | POST /api/v1/invites/redeem | 200 ✅ |
| ATTENDEE on ORGANIZER | ATTENDEE | GET /api/v1/events | 403 |
| ATTENDEE on ADMIN | ATTENDEE | GET /api/v1/admin/roles | 403 |
| ATTENDEE on VALIDATION | ATTENDEE | POST /api/v1/ticket-validations | 403 |
| STAFF purchases ticket | STAFF | POST /api/v1/.../tickets | 403 |
| STAFF views tickets | STAFF | GET /api/v1/tickets | 403 |
| ORGANIZER on admin list | ORGANIZER | GET /api/v1/admin/approvals | 403 |
| ADMIN browses events | ADMIN | GET /api/v1/published-events | 403 |
| CORS preflight | None | OPTIONS /api/v1/events | 200/204 + CORS headers |

---

## 14. End-to-End Flows

### Full Happy Path
1. `POST /api/v1/auth/register` → PENDING
2. `POST /api/v1/admin/approvals/{id}/approve` (ADMIN)
3. Get Keycloak token
4. `POST /api/v1/events` (ORGANIZER) → save event_id, ticket_type_id
5. `POST /api/v1/events/{id}/ticket-types/{tt_id}/tickets` (ATTENDEE, quantity=2) → save ticket_id, verify pricePaid
6. `GET /api/v1/tickets/{ticket_id}` → verify all price fields present
7. `GET /api/v1/tickets/{ticket_id}/qr-codes/png` → download PNG
8. `POST /api/v1/ticket-validations` (STAFF, MANUAL) → verify validatedById/Name/At
9. `POST /api/v1/ticket-validations` same ticket → 200 `"status": "INVALID"`
10. `GET /api/v1/events/{id}/sales-dashboard` → verify CANCELLED excluded from revenue

### Invite Code Flow
1. `POST /api/v1/invites` (ADMIN) → `{ "roleName": "STAFF", "eventId": "...", "expirationHours": 48 }`
2. New user `POST /api/v1/auth/register`
3. PENDING user `POST /api/v1/invites/redeem` with code → 200 (bypass)
4. `POST /api/v1/admin/approvals/{id}/approve`
5. `GET /api/v1/invites` (ADMIN) → sees all codes

### Event Cancellation Flow
1. Purchase tickets as ATTENDEE
2. `PUT /api/v1/events/{id}` with `"status": "CANCELLED"` → bulk-cancels all tickets
3. STAFF validates cancelled ticket → 400
4. `PUT /api/v1/events/{id}` with `"status": "PUBLISHED"` → 400 (cannot re-publish)
5. `GET /api/v1/events/{id}/sales-dashboard` → 0 revenue (CANCELLED excluded)
6. `DELETE /api/v1/events/{id}` → 204 (no active tickets)