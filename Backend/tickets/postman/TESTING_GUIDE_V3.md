# Event Booking App — Complete Testing Guide

**Last Updated:** March 18, 2026
**Version:** 3.0
**Base URL:** `http://localhost:8081`

---

## How to Use This Guide

Each section covers one endpoint with:
- Method + URL
- Required role and auth
- Every valid and invalid payload with exact JSON
- Every expected response with exact JSON

**Conventions:**

| Symbol | Meaning |
|--------|---------|
| ✅ | Valid — expect 2xx success |
| ❌ | Invalid — expect 4xx error |
| ⚠️ | Boundary value test |
| `{{var}}` | Postman environment variable |

**Postman Variables to set:**

```
base_url         = http://localhost:8081
keycloak_url     = http://localhost:9090
realm            = event-ticket-platform
client_id        = event-ticket-platform-app
client_secret    = (from Keycloak Credentials tab)
```

---

## Step 0 — Get Tokens First

Run each of these before testing any other endpoint. Save the `access_token` from each.

### Get Admin Token
```
Method:  POST
URL:     {{keycloak_url}}/realms/{{realm}}/protocol/openid-connect/token
Body:    x-www-form-urlencoded

grant_type    = password
client_id     = {{client_id}}
client_secret = {{client_secret}}
username      = admin@test.com
password      = Admin123!
```
**Expected 200 — save `access_token` as `admin_token`**

---

### Get Organizer Token
```
username      = organizer@test.com
password      = Password1
```
**Save as `organizer_token`**

---

### Get Attendee Token
```
username      = attendee@test.com
password      = Password1
```
**Save as `attendee_token`**

---

### Get Staff Token
```
username      = staff@test.com
password      = Password1
```
**Save as `staff_token`**

---

---

## 1. POST /api/v1/auth/register

```
Method:       POST
URL:          {{base_url}}/api/v1/auth/register
Auth:         None required
Role:         Public
Approval:     Bypassed
Content-Type: application/json
```

---

✅ **Minimum valid — gets ATTENDEE role:**
```json
{
  "email": "attendee@test.com",
  "password": "Password1",
  "name": "Test Attendee"
}
```
**Expected 201:**
```json
{
  "message": "Registration successful! Your account is pending admin approval.",
  "email": "attendee@test.com",
  "requiresApproval": true,
  "assignedRole": "ATTENDEE",
  "instructions": "You will receive an email once your account has been reviewed."
}
```

---

✅ **With invite code (role depends on invite):**
```json
{
  "email": "organizer@test.com",
  "password": "Password1",
  "name": "Test Organizer",
  "inviteCode": "{{organizer_invite_code}}"
}
```
**Expected 201 — `assignedRole` matches the invite code's role**

---

⚠️ **name exactly 2 chars (min boundary):**
```json
{ "email": "u1@test.com", "password": "Password1", "name": "Jo" }
```
**Expected 201**

⚠️ **name exactly 100 chars (max boundary):**
```json
{ "email": "u2@test.com", "password": "Password1", "name": "Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
```
**Expected 201**

⚠️ **password exactly 8 chars (min boundary):**
```json
{ "email": "u3@test.com", "password": "Passw0rd", "name": "Test" }
```
**Expected 201**

---

❌ **Empty body — all 3 required fields missing:**
```json
{}
```
**Expected 400 — `validationErrors` lists ALL three at once:**
```json
{ "error": "Validation Error", "statusCode": 400, "validationErrors": ["email: Email is required", "password: Password is required", "name: Name is required"] }
```

---

❌ **Missing email:**
```json
{ "password": "Password1", "name": "Test" }
```
**Expected 400**

---

❌ **Missing password:**
```json
{ "email": "u@test.com", "name": "Test" }
```
**Expected 400**

---

❌ **Missing name:**
```json
{ "email": "u@test.com", "password": "Password1" }
```
**Expected 400**

---

❌ **Invalid email format:**
```json
{ "email": "notanemail", "password": "Password1", "name": "Test" }
```
**Expected 400**

---

⚠️ **Email too long — 256 chars:**
```json
{ "email": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@x.com", "password": "Password1", "name": "Test" }
```
**Expected 400**

---

⚠️ **Password 7 chars — one below minimum:**
```json
{ "email": "u@test.com", "password": "Pass1rd", "name": "Test" }
```
**Expected 400**

---

❌ **Password no uppercase:**
```json
{ "email": "u@test.com", "password": "password1", "name": "Test" }
```
**Expected 400**

---

❌ **Password no lowercase:**
```json
{ "email": "u@test.com", "password": "PASSWORD1", "name": "Test" }
```
**Expected 400**

---

❌ **Password no digit:**
```json
{ "email": "u@test.com", "password": "PasswordX", "name": "Test" }
```
**Expected 400**

---

⚠️ **name 1 char — below minimum:**
```json
{ "email": "u@test.com", "password": "Password1", "name": "A" }
```
**Expected 400**

---

❌ **name whitespace only (@NotBlank):**
```json
{ "email": "u@test.com", "password": "Password1", "name": "   " }
```
**Expected 400**

---

❌ **inviteCode wrong format (lowercase):**
```json
{ "email": "u@test.com", "password": "Password1", "name": "Test", "inviteCode": "abcd-1234-efgh-5678" }
```
**Expected 400**

---

❌ **inviteCode valid format but not in database:**
```json
{ "email": "u@test.com", "password": "Password1", "name": "Test", "inviteCode": "ZZZZ-9999-ZZZZ-9999" }
```
**Expected 404 INVITE_CODE_NOT_FOUND**

---

❌ **inviteCode already redeemed:**
```json
{ "email": "u@test.com", "password": "Password1", "name": "Test", "inviteCode": "{{redeemed_code}}" }
```
**Expected 400 INVALID_INVITE_CODE — message: "has already been redeemed"**

---

❌ **Duplicate email (register same email twice):**
```json
{ "email": "attendee@test.com", "password": "Password1", "name": "Test" }
```
**Expected 409 EMAIL_ALREADY_REGISTERED**

---

---

## 2. POST /api/v1/invites — Generate Invite Code

```
Method:       POST
URL:          {{base_url}}/api/v1/invites
Auth:         Bearer {{admin_token}} or {{organizer_token}}
Role:         ADMIN or ORGANIZER
Approval:     Required
Content-Type: application/json
```

---

✅ **ADMIN creates ATTENDEE invite:**
```json
{ "roleName": "ATTENDEE", "expirationHours": 24 }
```
**Expected 201:**
```json
{
  "id": "uuid",
  "code": "ABCD-1234-EFGH-5678",
  "roleName": "ATTENDEE",
  "eventId": null,
  "eventName": null,
  "status": "PENDING",
  "createdBy": "admin@test.com",
  "createdAt": "...",
  "expiresAt": "...",
  "redeemedBy": null,
  "redeemedAt": null
}
```

---

✅ **ADMIN creates ORGANIZER invite:**
```json
{ "roleName": "ORGANIZER", "expirationHours": 72 }
```
**Expected 201**

---

✅ **ADMIN creates ADMIN invite:**
```json
{ "roleName": "ADMIN", "expirationHours": 24 }
```
**Expected 201**

---

✅ **ORGANIZER creates STAFF invite for own event:**
```json
{ "roleName": "STAFF", "eventId": "{{event_id}}", "expirationHours": 48 }
```
**Expected 201**

---

⚠️ **expirationHours=1 (min boundary):**
```json
{ "roleName": "ATTENDEE", "expirationHours": 1 }
```
**Expected 201**

---

❌ **Missing roleName:**
```json
{ "expirationHours": 24 }
```
**Expected 400**

---

❌ **Missing expirationHours:**
```json
{ "roleName": "ATTENDEE" }
```
**Expected 400**

---

❌ **Invalid roleName:**
```json
{ "roleName": "SUPERUSER", "expirationHours": 24 }
```
**Expected 400**

---

⚠️ **expirationHours=0 — must be @Positive (> 0):**
```json
{ "roleName": "ATTENDEE", "expirationHours": 0 }
```
**Expected 400**

---

❌ **STAFF without eventId:**
```json
{ "roleName": "STAFF", "expirationHours": 24 }
```
**Expected 400 — message: "Event ID is required for STAFF role invites"**

---

❌ **Non-STAFF role with eventId:**
```json
{ "roleName": "ORGANIZER", "eventId": "{{event_id}}", "expirationHours": 24 }
```
**Expected 400 — message: "Event ID should only be provided for STAFF invites"**

---

❌ **ORGANIZER creates ORGANIZER invite:**
```json
{ "roleName": "ORGANIZER", "expirationHours": 24 }
```
*Use ORGANIZER token*
**Expected 400 — message: "Organizers can only create STAFF invites..."**

---

❌ **ORGANIZER creates STAFF invite for event they don't own:**
*Use ORGANIZER token with another organizer's eventId*
**Expected 403 ACCESS_DENIED**

---

❌ **ATTENDEE token:**
**Expected 403 ACCESS_DENIED**

---

## 3. POST /api/v1/invites/redeem

```
Method:       POST
URL:          {{base_url}}/api/v1/invites/redeem
Auth:         Bearer (any token including PENDING users)
Role:         Any authenticated
Approval:     BYPASSED
Content-Type: application/json
```

---

✅ **Valid code:**
```json
{ "code": "{{valid_invite_code}}" }
```
**Expected 200:**
```json
{
  "message": "Invite code redeemed successfully",
  "roleAssigned": "STAFF",
  "eventName": "Tech Conference 2025",
  "currentRoles": ["STAFF"]
}
```

---

✅ **PENDING user can redeem (bypass path):**
*Use PENDING user token*
**Expected 200**

---

❌ **Missing code:**
```json
{}
```
**Expected 400**

---

❌ **Blank code:**
```json
{ "code": "" }
```
**Expected 400**

---

❌ **Code not in database:**
```json
{ "code": "ZZZZ-9999-ZZZZ-9999" }
```
**Expected 404 INVITE_CODE_NOT_FOUND**

---

❌ **Code already redeemed:**
**Expected 400 INVALID_INVITE_CODE — message: "has already been redeemed by ... on ..."**

---

❌ **Code expired:**
**Expected 400 INVALID_INVITE_CODE — message: "expired on ..."**

---

❌ **Code revoked:**
**Expected 400 INVALID_INVITE_CODE — message: "has been revoked. Reason: ..."**

---

## 4. DELETE /api/v1/invites/{codeId}

```
Method:  DELETE
URL:     {{base_url}}/api/v1/invites/{{invite_code_id}}
Auth:    Bearer {{admin_token}} or {{organizer_token}}
Role:    ADMIN or ORGANIZER
Body:    None
Query:   reason (optional)
```

---

✅ **Creator revokes own code:**
`DELETE {{base_url}}/api/v1/invites/{{invite_code_id}}`
**Expected 204 No Content**

---

✅ **With reason:**
`DELETE {{base_url}}/api/v1/invites/{{invite_code_id}}?reason=Event+was+cancelled`
**Expected 204**

---

✅ **ADMIN revokes any code:**
**Expected 204**

---

❌ **Code already REDEEMED:**
**Expected 400 — message: "Cannot revoke invite code: current status is REDEEMED"**

---

❌ **ORGANIZER revoking someone else's code:**
**Expected 403**

---

❌ **Code not found:**
**Expected 404**

---

## 5. GET /api/v1/invites

```
Method:  GET
URL:     {{base_url}}/api/v1/invites?page=0&size=20&sort=createdAt,desc
Auth:    Bearer {{admin_token}}
Role:    ADMIN or ORGANIZER
Body:    None
```

✅ **ADMIN sees all codes, ORGANIZER sees own only**
**Expected 200 — `Page<InviteCodeResponseDto>`**

❌ **ATTENDEE token → 403**

---

## 6. GET /api/v1/invites/events/{eventId}

```
Method:  GET
URL:     {{base_url}}/api/v1/invites/events/{{event_id}}?page=0&size=20
Auth:    Bearer {{admin_token}} or {{organizer_token}}
Role:    ADMIN or ORGANIZER
Body:    None
```

✅ **ADMIN — any event → 200**
✅ **ORGANIZER — own event → 200**
❌ **ORGANIZER — event they don't own → 403**

---

---

## 7. POST /api/v1/events — Create Event

```
Method:       POST
URL:          {{base_url}}/api/v1/events
Auth:         Bearer {{organizer_token}}
Role:         ORGANIZER
Approval:     Required
Content-Type: application/json
```

---

✅ **Minimum valid:**
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
**Expected 201 — save `id` as `event_id`, `ticketTypes[0].id` as `ticket_type_id`**

---

✅ **All fields:**
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
**Expected 201:**
```json
{
  "id": "uuid",
  "name": "Tech Conference 2025",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "venue": "Convention Center, Building A",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "ticketTypes": [
    { "id": "uuid", "name": "Early Bird", "price": 149.99, "description": "Limited discount slots", "totalAvailable": 100, "createdAt": "...", "updatedAt": "..." }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

---

✅ **Free event (price=0.00):**
```json
{
  "name": "Free Workshop",
  "venue": "Community Centre",
  "status": "PUBLISHED",
  "ticketTypes": [{ "name": "Free Entry", "price": 0.00, "totalAvailable": 50 }]
}
```
**Expected 201**

---

✅ **start provided without end (both are independently optional):**
```json
{
  "name": "Open Event", "venue": "Outdoors", "status": "DRAFT",
  "start": "2025-12-15T09:00:00",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 50 }]
}
```
**Expected 201**

---

⚠️ **totalAvailable=1 (min boundary):**
```json
{
  "name": "Exclusive", "venue": "Private", "status": "DRAFT",
  "ticketTypes": [{ "name": "Exclusive", "price": 1000.00, "totalAvailable": 1 }]
}
```
**Expected 201**

---

❌ **Missing name:**
```json
{
  "venue": "City Hall", "status": "DRAFT",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 50 }]
}
```
**Expected 400**

---

❌ **Missing venue:**
```json
{
  "name": "Event", "status": "DRAFT",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 50 }]
}
```
**Expected 400**

---

❌ **Missing status:**
```json
{
  "name": "Event", "venue": "Venue",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 50 }]
}
```
**Expected 400**

---

❌ **Missing ticketTypes:**
```json
{ "name": "Event", "venue": "Venue", "status": "DRAFT" }
```
**Expected 400**

---

❌ **Empty ticketTypes array:**
```json
{ "name": "Event", "venue": "Venue", "status": "DRAFT", "ticketTypes": [] }
```
**Expected 400**

---

❌ **ticketType missing totalAvailable:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "name": "T", "price": 50.00 }]
}
```
**Expected 400 — `totalAvailable` is @NotNull @Min(1)**

---

❌ **ticketType price negative:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "name": "T", "price": -0.01, "totalAvailable": 10 }]
}
```
**Expected 400**

---

⚠️ **totalAvailable=0:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 0 }]
}
```
**Expected 400**

---

⚠️ **maxCapacity=0:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "maxCapacity": 0,
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 10 }]
}
```
**Expected 400**

---

❌ **Invalid status value:**
```json
{
  "name": "Event", "venue": "Venue", "status": "OPEN",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 10 }]
}
```
**Expected 400**

---

❌ **end before start:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "start": "2025-12-15T18:00:00", "end": "2025-12-15T09:00:00",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 10 }]
}
```
**Expected 409 — message: "Event end date must be after start date."**

---

❌ **salesEnd before salesStart:**
```json
{
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "salesStart": "2025-12-01T00:00:00", "salesEnd": "2025-11-01T00:00:00",
  "ticketTypes": [{ "name": "T", "price": 10.00, "totalAvailable": 10 }]
}
```
**Expected 409 — message: "Sales end date must be after sales start date."**

---

❌ **ATTENDEE token → 403**

---

## 8. PUT /api/v1/events/{eventId} — Update Event

```
Method:       PUT
URL:          {{base_url}}/api/v1/events/{{event_id}}
Auth:         Bearer {{organizer_token}}
Role:         ORGANIZER (must own)
Content-Type: application/json
```

---

✅ **Basic update:**
```json
{
  "name": "Updated Name",
  "venue": "Same Venue",
  "status": "PUBLISHED",
  "ticketTypes": [
    { "id": "{{ticket_type_id}}", "name": "General", "price": 99.99, "totalAvailable": 200 }
  ]
}
```
**Expected 200**

---

✅ **Add new ticket type inline (no id = creates new):**
```json
{
  "name": "Event", "venue": "Venue", "status": "PUBLISHED",
  "ticketTypes": [
    { "id": "{{ticket_type_id}}", "name": "Existing", "price": 99.99, "totalAvailable": 200 },
    { "name": "New VIP", "price": 499.99, "totalAvailable": 20 }
  ]
}
```
**Expected 200 — response includes both ticket types, new one has a new UUID**

---

✅ **Cancel event:**
```json
{
  "name": "Event", "venue": "Venue", "status": "CANCELLED",
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10.00, "totalAvailable": 100 }]
}
```
**Expected 200 — all PURCHASED tickets become CANCELLED**

---

✅ **Set maxCapacity:**
```json
{
  "name": "Event", "venue": "Venue", "status": "PUBLISHED",
  "maxCapacity": 600,
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10.00, "totalAvailable": 600 }]
}
```
**Expected 200**

---

✅ **Remove maxCapacity (send null = no cap):**
```json
{
  "name": "Event", "venue": "Venue", "status": "PUBLISHED",
  "maxCapacity": null,
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10.00, "totalAvailable": 600 }]
}
```
**Expected 200**

---

❌ **Body id doesn't match URL eventId:**
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Event", "venue": "Venue", "status": "DRAFT",
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10.00, "totalAvailable": 100 }]
}
```
**Expected 400 EVENT_UPDATE_ERROR**

---

❌ **Re-publish a CANCELLED event:**
*Set event to CANCELLED first, then attempt to change status to PUBLISHED*
```json
{
  "name": "Event", "venue": "Venue", "status": "PUBLISHED",
  "ticketTypes": [{ "id": "{{ticket_type_id}}", "name": "T", "price": 10.00, "totalAvailable": 100 }]
}
```
**Expected 409 — message: "Cannot modify a cancelled event..."**

---

❌ **maxCapacity below already-sold count:**
*Buy 5 tickets first, then set maxCapacity=4*
**Expected 409**

---

❌ **Not owner → 403**

---

## 9. GET /api/v1/events

```
Method:  GET
URL:     {{base_url}}/api/v1/events?page=0&size=20&sort=start,desc
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER
Body:    None
```

✅ **Expected 200 — `Page<ListEventResponseDto>`**
❌ **ATTENDEE token → 403**

---

## 10. GET /api/v1/events/{eventId}

```
Method:  GET
URL:     {{base_url}}/api/v1/events/{{event_id}}
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```

✅ **Own event → 200 — `GetEventDetailsResponseDto`**
❌ **Another organizer's event → 404**

---

## 11. DELETE /api/v1/events/{eventId}

```
Method:  DELETE
URL:     {{base_url}}/api/v1/events/{{event_id}}
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```

✅ **Event with no active tickets → 204**
❌ **Event with active tickets → 409 — message: "Cannot delete event ... N active ticket(s) exist. Cancel the event first."**

---

## 12. GET /api/v1/events/{eventId}/sales-dashboard

```
Method:  GET
URL:     {{base_url}}/api/v1/events/{{event_id}}/sales-dashboard
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```

✅ **Expected 200:**
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

*After cancelling event: `totalTicketsSold` = 0, all revenue = 0 (CANCELLED excluded)*
*`remaining` = null when totalAvailable is null (unlimited)*

---

## 13. GET /api/v1/events/{eventId}/attendees-report

```
Method:  GET
URL:     {{base_url}}/api/v1/events/{{event_id}}/attendees-report
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```

✅ **Expected 200:**
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

---

## 14. GET /api/v1/events/{eventId}/sales-report.xlsx

```
Method:  GET
URL:     {{base_url}}/api/v1/events/{{event_id}}/sales-report.xlsx
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```

✅ **Expected 200**
- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment`

---

---

## 15. Ticket Purchase — POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets

```
Method:       POST
URL:          {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/tickets
Auth:         Bearer {{attendee_token}}
Role:         ATTENDEE or ORGANIZER
Approval:     Required
Content-Type: application/json
```

---

✅ **No body — defaults to quantity=1:**
```json
{}
```
**Expected 201 — array of 1 ticket — save `[0].id` as `ticket_id`:**
```json
[
  {
    "id": "uuid",
    "status": "PURCHASED",
    "price": 149.99,
    "pricePaid": 149.99,
    "originalPrice": 149.99,
    "discountApplied": 0.00,
    "description": "Early Bird",
    "eventName": "Tech Conference 2025",
    "eventVenue": "Convention Center",
    "eventStart": "2025-12-15T09:00:00",
    "eventEnd": "2025-12-15T18:00:00"
  }
]
```

---

✅ **quantity=2:**
```json
{ "quantity": 2 }
```
**Expected 201 — array of 2 tickets**

---

⚠️ **quantity=1 (min boundary):**
```json
{ "quantity": 1 }
```
**Expected 201**

---

⚠️ **quantity=10 (max boundary):**
```json
{ "quantity": 10 }
```
**Expected 201**

---

✅ **ORGANIZER purchases own event ticket (logged as ORGANIZER_SELF_PURCHASE):**
*Use ORGANIZER token*
**Expected 201**

---

⚠️ **quantity=0 → 400:**
```json
{ "quantity": 0 }
```
**Expected 400**

---

⚠️ **quantity=11 → 400:**
```json
{ "quantity": 11 }
```
**Expected 400**

---

❌ **Event is DRAFT:**
**Expected 409 — message: "Tickets are not available — the event is not open for sales."**

---

❌ **Event is CANCELLED:**
**Expected 409 — message: "This event has been cancelled."**

---

❌ **Before salesStart:**
**Expected 409 — message: "Sales have not started yet. Sales open at ..."**

---

❌ **After salesEnd:**
**Expected 409 — message: "Sales have closed. Sales ended at ..."**

---

❌ **Ticket type sold out:**
*Create event with totalAvailable=2, buy 2, then try to buy 1 more*
**Expected 400 TICKETS_SOLD_OUT**

---

❌ **STAFF token → 403**

---

---

## 16. GET /api/v1/tickets

```
Method:  GET
URL:     {{base_url}}/api/v1/tickets?page=0&size=20&sort=id,desc
Auth:    Bearer {{attendee_token}}
Role:    ATTENDEE or ORGANIZER
Body:    None
```

✅ **Expected 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "status": "PURCHASED",
      "ticketType": { "id": "uuid", "name": "Early Bird", "price": 149.99 }
    }
  ]
}
```

❌ **STAFF token → 403**

---

## 17. GET /api/v1/tickets/{ticketId}

```
Method:  GET
URL:     {{base_url}}/api/v1/tickets/{{ticket_id}}
Auth:    Bearer {{attendee_token}}
Role:    ATTENDEE or ORGANIZER (must own ticket)
Body:    None
```

✅ **Own ticket → 200:**
```json
{
  "id": "uuid",
  "status": "PURCHASED",
  "price": 149.99,
  "pricePaid": 149.99,
  "originalPrice": 149.99,
  "discountApplied": 0.00,
  "description": "Early Bird",
  "eventName": "Tech Conference 2025",
  "eventVenue": "Convention Center",
  "eventStart": "2025-12-15T09:00:00",
  "eventEnd": "2025-12-15T18:00:00"
}
```

❌ **Another user's ticket → 404**
❌ **STAFF token → 403**

---

## 18. GET /api/v1/tickets/{ticketId}/qr-codes/view

```
Method:  GET
URL:     {{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/view
Auth:    Bearer {{attendee_token}}
Role:    ATTENDEE or ORGANIZER
Body:    None
```

✅ **Own ticket → 200 image/png inline**
- Verify `Content-Type: image/png`
- Verify `Cache-Control: max-age=300, private` (NOT public)

❌ **Another user's ticket → 403**
❌ **Cancelled ticket's QR → 404 QR_CODE_NOT_FOUND**

---

## 19. GET /api/v1/tickets/{ticketId}/qr-codes/png

```
Method:  GET
URL:     {{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/png
Auth:    Bearer {{attendee_token}}
Role:    ATTENDEE or ORGANIZER
Body:    None
```

✅ **Expected 200**
- `Content-Type: image/png`
- `Content-Disposition: attachment; filename="..."`

---

## 20. GET /api/v1/tickets/{ticketId}/qr-codes/pdf

```
Method:  GET
URL:     {{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/pdf
Auth:    Bearer {{attendee_token}}
Role:    ATTENDEE or ORGANIZER
Body:    None
```

✅ **Expected 200**
- `Content-Type: application/pdf`
- `Content-Disposition: attachment`

---

---

## 21. Ticket Type Management

### POST /api/v1/events/{eventId}/ticket-types

```
Method:       POST
URL:          {{base_url}}/api/v1/events/{{event_id}}/ticket-types
Auth:         Bearer {{organizer_token}}
Role:         ORGANIZER (must own)
Content-Type: application/json
```

✅ **Minimum valid:**
```json
{ "name": "General", "price": 99.99, "totalAvailable": 200 }
```
**Expected 201**

✅ **Free ticket:**
```json
{ "name": "Free Entry", "price": 0.00, "totalAvailable": 100 }
```
**Expected 201**

✅ **All fields:**
```json
{ "name": "VIP Pass", "price": 499.99, "description": "Premium access", "totalAvailable": 50 }
```
**Expected 201:**
```json
{ "id": "uuid", "name": "VIP Pass", "price": 499.99, "description": "Premium access", "totalAvailable": 50, "createdAt": "...", "updatedAt": "..." }
```

❌ **Missing totalAvailable → 400**
❌ **price=-0.01 → 400**
❌ **totalAvailable=0 → 400**

---

### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

```
Method:       PUT
URL:          {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}
Auth:         Bearer {{organizer_token}}
Role:         ORGANIZER (must own)
Content-Type: application/json
```

✅ **Update name and price:**
```json
{ "name": "VIP Updated", "price": 549.99 }
```
**Expected 200**

✅ **Remove description:**
```json
{ "name": "VIP Updated", "price": 549.99, "description": null }
```
**Expected 200**

✅ **Raise totalAvailable:**
```json
{ "name": "General", "price": 99.99, "totalAvailable": 500 }
```
**Expected 200**

❌ **Lower totalAvailable below active sold count → 409**

---

### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

```
Method:  DELETE
URL:     {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```

✅ **No sold tickets → 204**
❌ **Has active sold tickets → 409 TICKET_TYPE_DELETE_NOT_ALLOWED**

---

### GET /api/v1/events/{eventId}/ticket-types

```
Method:  GET
URL:     {{base_url}}/api/v1/events/{{event_id}}/ticket-types
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```
✅ **Expected 200 — `List<CreateTicketTypeResponseDto>`**

---

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

```
Method:  GET
URL:     {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```
✅ **Expected 200** | ❌ **Wrong event → 404**

---

---

## 22. Discount Management

**⚠️ All URLs end in `/discounts` (plural). `/discount` (singular) returns 404.**

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts

```
Method:       POST
URL:          {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts
Auth:         Bearer {{organizer_token}}
Role:         ORGANIZER (must own)
Content-Type: application/json
```

✅ **PERCENTAGE minimum:**
```json
{
  "discountType": "PERCENTAGE",
  "value": 20.0,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59"
}
```
**Expected 201 — then purchase a ticket and verify `discountApplied > 0`**

✅ **PERCENTAGE all fields:**
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
**Expected 201:**
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

✅ **FIXED_AMOUNT:**
```json
{
  "discountType": "FIXED_AMOUNT",
  "value": 50.00,
  "validFrom": "2025-12-01T00:00:00",
  "validTo": "2025-12-25T23:59:59",
  "active": true
}
```
**Expected 201 — purchase and verify `pricePaid = originalPrice - 50`**

⚠️ **PERCENTAGE value=100.0 (100% free):**
```json
{
  "discountType": "PERCENTAGE", "value": 100.0,
  "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59", "active": true
}
```
**Expected 201 — purchase and verify `pricePaid = 0.00`**

✅ **FIXED_AMOUNT larger than ticket price (clamped to 0, not an error):**
```json
{
  "discountType": "FIXED_AMOUNT", "value": 9999.00,
  "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59", "active": true
}
```
**Expected 201 — purchase and verify `pricePaid = 0.00`**

✅ **active=false (exists but won't apply at purchase time):**
```json
{
  "discountType": "PERCENTAGE", "value": 15.0,
  "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59", "active": false
}
```
**Expected 201 — purchase and verify `pricePaid = full price`**

---

❌ **Missing discountType → 400**
❌ **Missing value → 400**
❌ **Missing validFrom → 400**
❌ **Missing validTo → 400**

⚠️ **value=0 → 400:**
```json
{ "discountType": "PERCENTAGE", "value": 0, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```
**Expected 400**

⚠️ **PERCENTAGE value=100.01 (above max) → 400:**
```json
{ "discountType": "PERCENTAGE", "value": 100.01, "validFrom": "2025-11-01T00:00:00", "validTo": "2025-11-30T23:59:59" }
```
**Expected 400**

❌ **validTo before validFrom → 400:**
```json
{ "discountType": "PERCENTAGE", "value": 10, "validFrom": "2025-11-30T00:00:00", "validTo": "2025-11-01T00:00:00" }
```
**Expected 400**

❌ **Second active discount for same ticket type → 409 DISCOUNT_ALREADY_EXISTS**

✅ **Expired discount doesn't block new one:**
*Delete or deactivate existing discount, then create new one*
**Expected 201**

---

### PUT .../discounts/{discountId}

```
Method:       PUT
URL:          {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts/{{discount_id}}
Auth:         Bearer {{organizer_token}}
Content-Type: application/json
```

Same payload as POST. **Expected 200**

---

### DELETE .../discounts/{discountId}

```
Method:  DELETE
URL:     {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts/{{discount_id}}
Auth:    Bearer {{organizer_token}}
Body:    None
```
✅ **Expected 204**

---

### GET .../discounts/{discountId}

```
Method:  GET
URL:     {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts/{{discount_id}}
Auth:    Bearer {{organizer_token}}
Body:    None
```
✅ **Expected 200 DiscountResponseDto** | ❌ **Not found → 404**

---

### GET .../discounts

```
Method:  GET
URL:     {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts
Auth:    Bearer {{organizer_token}}
Body:    None
```
✅ **Expected 200 — `List<DiscountResponseDto>`**

---

---

## 23. GET /api/v1/published-events

```
Method:  GET
URL:     {{base_url}}/api/v1/published-events?page=0&size=20&sort=start,asc
Auth:    Bearer {{attendee_token}}
Role:    ATTENDEE or ORGANIZER or STAFF
Body:    None
```

✅ **No params → 200**
✅ **With search: `?q=tech&page=0&size=20` → 200**

❌ **ADMIN token → 403 (ADMIN role not allowed here)**
❌ **No token → 401**

---

## 24. GET /api/v1/published-events/{eventId}

```
Method:  GET
URL:     {{base_url}}/api/v1/published-events/{{event_id}}
Auth:    Bearer {{attendee_token}}
Role:    ATTENDEE or ORGANIZER or STAFF
Body:    None
```

✅ **PUBLISHED event → 200:**
```json
{
  "id": "uuid",
  "name": "Tech Conference 2025",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "venue": "Convention Center",
  "ticketTypes": [
    { "id": "uuid", "name": "Early Bird", "price": 149.99, "description": "..." }
  ]
}
```

❌ **DRAFT event → 404**
❌ **CANCELLED event → 404**

---

---

## 25. Ticket Validation

### POST /api/v1/ticket-validations

```
Method:       POST
URL:          {{base_url}}/api/v1/ticket-validations
Auth:         Bearer {{staff_token}} or {{organizer_token}}
Role:         STAFF or ORGANIZER
Approval:     Required
Content-Type: application/json
```

---

✅ **Manual validation (first scan):**
```json
{ "id": "{{ticket_id}}", "method": "MANUAL" }
```
**Expected 200:**
```json
{
  "ticketId": "uuid",
  "status": "VALID",
  "validatedById": "uuid",
  "validatedByName": "John Staff",
  "validatedAt": "2025-12-15T10:23:45"
}
```

---

✅ **QR scan (id is the QR code UUID — NOT the ticket UUID):**
```json
{ "id": "{{qr_code_id}}", "method": "QR_SCAN" }
```
**Expected 200 with `status: "VALID"`**

---

✅ **Second scan of same ticket (NOT an error — returns 200 with INVALID):**
```json
{ "id": "{{ticket_id}}", "method": "MANUAL" }
```
**Expected 200:**
```json
{
  "ticketId": "uuid",
  "status": "INVALID",
  "validatedById": "uuid",
  "validatedByName": "John Staff",
  "validatedAt": "..."
}
```

---

❌ **Empty body — both fields are @NotNull:**
```json
{}
```
**Expected 400 with validationErrors listing both missing fields**

---

❌ **Missing method:**
```json
{ "id": "{{ticket_id}}" }
```
**Expected 400**

---

❌ **Missing id:**
```json
{ "method": "MANUAL" }
```
**Expected 400**

---

❌ **method="QR_CODE" (wrong — correct is "QR_SCAN"):**
```json
{ "id": "{{ticket_id}}", "method": "QR_CODE" }
```
**Expected 400**

---

❌ **method="SCAN" (wrong):**
```json
{ "id": "{{ticket_id}}", "method": "SCAN" }
```
**Expected 400**

---

❌ **CANCELLED ticket:**
```json
{ "id": "{{cancelled_ticket_id}}", "method": "MANUAL" }
```
**Expected 409 — message: "Ticket ... has been cancelled and cannot be validated."**

---

❌ **Ticket not found → 404**
❌ **QR code not found → 404**
❌ **ATTENDEE token → 403**
❌ **STAFF not assigned to this event → 403**

---

### GET /api/v1/ticket-validations/events/{eventId}

```
Method:  GET
URL:     {{base_url}}/api/v1/ticket-validations/events/{{event_id}}?page=0&size=20
Auth:    Bearer {{staff_token}} or {{organizer_token}}
Role:    STAFF or ORGANIZER (must be assigned/own)
Body:    None
```
✅ **Expected 200 — `Page<TicketValidationResponseDto>`**

---

### GET /api/v1/ticket-validations/tickets/{ticketId}

```
Method:  GET
URL:     {{base_url}}/api/v1/ticket-validations/tickets/{{ticket_id}}
Auth:    Bearer {{staff_token}} or {{organizer_token}}
Role:    STAFF or ORGANIZER
Body:    None
```
✅ **Expected 200 — `List<TicketValidationResponseDto>`**

---

---

## 26. Admin Role Management

### POST /api/v1/admin/users/{userId}/roles

```
Method:       POST
URL:          {{base_url}}/api/v1/admin/users/{{user_id}}/roles
Auth:         Bearer {{admin_token}}
Role:         ADMIN
Content-Type: application/json
```

✅ **Assign ATTENDEE:**
```json
{ "roleName": "ATTENDEE" }
```
**Expected 200:**
```json
{ "userId": "uuid", "userName": "Test User", "email": "test@example.com", "roles": ["ATTENDEE"] }
```

✅ **Assign ORGANIZER:** `{ "roleName": "ORGANIZER" }` → 200
✅ **Assign STAFF:** `{ "roleName": "STAFF" }` → 200
✅ **Assign ADMIN:** `{ "roleName": "ADMIN" }` → 200

❌ **Invalid role:** `{ "roleName": "SUPERUSER" }` → 400
❌ **Missing roleName:** `{}` → 400
❌ **ORGANIZER token → 403**

---

### DELETE /api/v1/admin/users/{userId}/roles/{roleName}

```
Method:  DELETE
URL:     {{base_url}}/api/v1/admin/users/{{user_id}}/roles/STAFF
Auth:    Bearer {{admin_token}}
Role:    ADMIN
Body:    None
```
✅ **Expected 200 — `UserRolesResponseDto` with updated roles**
❌ **ORGANIZER token → 403**

---

### GET /api/v1/admin/users/{userId}/roles

```
Method:  GET
URL:     {{base_url}}/api/v1/admin/users/{{user_id}}/roles
Auth:    Bearer {{admin_token}}
Role:    ADMIN
Body:    None
```
✅ **Expected 200 — `UserRolesResponseDto`**

---

### GET /api/v1/admin/roles

```
Method:  GET
URL:     {{base_url}}/api/v1/admin/roles
Auth:    Bearer {{admin_token}}
Role:    ADMIN
Body:    None
```
✅ **Expected 200:**
```json
{ "roles": ["ADMIN", "ORGANIZER", "ATTENDEE", "STAFF"], "message": "Available roles in the system" }
```

---

---

## 27. Approval Management

### GET /api/v1/admin/approvals/pending

```
Method:  GET
URL:     {{base_url}}/api/v1/admin/approvals/pending?page=0&size=20
Auth:    Bearer {{admin_token}}
Role:    ADMIN
Body:    None
```
✅ **Expected 200 — `roles: []` always in list (use GET /admin/users/{id}/roles for roles)**

---

### POST /api/v1/admin/approvals/{userId}/approve

```
Method:  POST
URL:     {{base_url}}/api/v1/admin/approvals/{{user_id}}/approve
Auth:    Bearer {{admin_token}}
Role:    ADMIN
Body:    None (no request body)
```

✅ **PENDING user → 200:**
```json
{ "message": "User approved successfully", "userId": "uuid", "status": "APPROVED" }
```

❌ **Already APPROVED → 409 INVALID_APPROVAL_STATE**
❌ **Already REJECTED → 409**
❌ **User not found → 404**
❌ **ORGANIZER token → 403**

---

### POST /api/v1/admin/approvals/{userId}/reject

```
Method:       POST
URL:          {{base_url}}/api/v1/admin/approvals/{{user_id}}/reject
Auth:         Bearer {{admin_token}}
Role:         ADMIN
Content-Type: application/json
```

✅ **Valid reason:**
```json
{ "reason": "Account violates platform terms of service." }
```
**Expected 200:**
```json
{ "message": "User rejected successfully", "userId": "uuid", "status": "REJECTED", "reason": "Account violates platform terms of service." }
```

⚠️ **reason exactly 10 chars (min boundary):**
```json
{ "reason": "Duplicate." }
```
**Expected 200**

⚠️ **reason 9 chars (one below min) → 400:**
```json
{ "reason": "Too short" }
```
**Expected 400**

❌ **Missing reason → 400:** `{}`
❌ **reason="" → 400**
❌ **reason whitespace only → 400:** `{ "reason": "          " }`
❌ **Already REJECTED → 409**

---

### GET /api/v1/admin/approvals

```
Method:  GET
URL:     {{base_url}}/api/v1/admin/approvals?page=0&size=20
Auth:    Bearer {{admin_token}}
Role:    ADMIN
Body:    None
```
✅ **All users all statuses → 200**

---

---

## 28. Event Staff Management

### POST /api/v1/events/{eventId}/staff

```
Method:       POST
URL:          {{base_url}}/api/v1/events/{{event_id}}/staff
Auth:         Bearer {{organizer_token}}
Role:         ORGANIZER (must own)
Content-Type: application/json
```

✅ **Valid — user has STAFF role:**
```json
{ "userId": "{{staff_user_id}}" }
```
**Expected 201:**
```json
{
  "eventId": "uuid",
  "eventName": "Tech Conference 2025",
  "staffMembers": [{ "userId": "uuid", "userName": "John Staff", "email": "john@example.com" }],
  "totalStaffCount": 1
}
```

❌ **Missing userId → 400:** `{}`
❌ **User doesn't have STAFF role → 409**
❌ **User already assigned → 409**
❌ **Not owner → 403**

---

### DELETE /api/v1/events/{eventId}/staff/{userId}

```
Method:  DELETE
URL:     {{base_url}}/api/v1/events/{{event_id}}/staff/{{staff_user_id}}
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```

✅ **Assigned user → 200** with updated `EventStaffResponseDto`
❌ **User NOT assigned → 409**
❌ **Not owner → 403**

---

### GET /api/v1/events/{eventId}/staff

```
Method:  GET
URL:     {{base_url}}/api/v1/events/{{event_id}}/staff
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```
✅ **Expected 200 — `EventStaffResponseDto`**

---

---

## 29. Audit Logs

### GET /api/v1/audit

```
Method:  GET
URL:     {{base_url}}/api/v1/audit?page=0&size=20&sort=createdAt,desc
Auth:    Bearer {{admin_token}}
Role:    ADMIN
Body:    None
```
✅ **Expected 200 — `Page<AuditLogDto>` including `userAgent` field**
❌ **ORGANIZER token → 403**

---

### GET /api/v1/audit/events/{eventId}

```
Method:  GET
URL:     {{base_url}}/api/v1/audit/events/{{event_id}}?page=0&size=20
Auth:    Bearer {{organizer_token}}
Role:    ORGANIZER (must own)
Body:    None
```
✅ **Expected 200**

---

### GET /api/v1/audit/me

```
Method:  GET
URL:     {{base_url}}/api/v1/audit/me?page=0&size=20
Auth:    Bearer (any approved user)
Role:    Any authenticated
Body:    None
```
✅ **Expected 200 — own audit trail**

---

---

## 30. Security Tests

| # | Token | URL | Expected |
|---|-------|-----|----------|
| 1 | None | `GET /api/v1/published-events` | 401 |
| 2 | `"Bearer bad"` | Any | 401 |
| 3 | PENDING | `GET /api/v1/published-events` | 403 APPROVAL_PENDING |
| 4 | PENDING | `POST /api/v1/auth/register` | 201 ✅ (bypass) |
| 5 | PENDING | `POST /api/v1/invites/redeem` | 200 ✅ (bypass) |
| 6 | REJECTED | `GET /api/v1/published-events` | 403 APPROVAL_REJECTED |
| 7 | ATTENDEE | `GET /api/v1/events` | 403 |
| 8 | ATTENDEE | `GET /api/v1/admin/roles` | 403 |
| 9 | ATTENDEE | `POST /api/v1/ticket-validations` | 403 |
| 10 | STAFF | `GET /api/v1/tickets` | 403 |
| 11 | STAFF | `POST /api/v1/events/{{id}}/ticket-types/{{id}}/tickets` | 403 |
| 12 | ORGANIZER | `GET /api/v1/admin/approvals` | 403 |
| 13 | ADMIN | `GET /api/v1/published-events` | 403 |
| 14 | ADMIN | `GET /api/v1/tickets` | 403 |
| 15 | ORGANIZER B | `GET /api/v1/events/{{organizer-A-event}}` | 404 |

---

## 31. End-to-End Happy Path

Run in order:

1. `POST /api/v1/auth/register` → 201 PENDING
2. `POST /api/v1/admin/approvals/{id}/approve` (ADMIN) → 200
3. Get ATTENDEE token from Keycloak
4. `POST /api/v1/events` (ORGANIZER) → 201 — save `event_id`, `ticket_type_id`
5. `POST /api/v1/events/{id}/ticket-types/{id}/discounts` (20% PERCENTAGE, active=true) → 201
6. `POST /api/v1/events/{id}/ticket-types/{id}/tickets` (ATTENDEE, quantity=2) → 201 — save `ticket_id`, verify `discountApplied = 29.998`
7. `GET /api/v1/tickets/{ticket_id}` → 200, verify `pricePaid < originalPrice`
8. `GET /api/v1/tickets/{ticket_id}/qr-codes/png` → 200 PNG
9. `POST /api/v1/ticket-validations` (STAFF, MANUAL, ticket_id) → 200 `status: "VALID"`, `validatedByName` populated
10. `POST /api/v1/ticket-validations` same ticket again → 200 `status: "INVALID"` (not an error)
11. `GET /api/v1/events/{id}/sales-dashboard` → verify revenue and discounts
12. `PUT /api/v1/events/{id}` with `status: "CANCELLED"` → 200
13. `POST /api/v1/ticket-validations` on cancelled ticket → 409
14. `GET /api/v1/events/{id}/sales-dashboard` → totalTicketsSold=0, all revenue=0
15. `DELETE /api/v1/events/{id}` → 204