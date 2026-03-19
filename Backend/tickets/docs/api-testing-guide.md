# Event Booking Backend - API Testing Guide (Audited)

Last audited: 2026-03-20  
Source of truth: controllers under `src/main/java/com/event/tickets/controllers`, security config, filter config, and DTO validations.

## 1) Scope and Goal

This guide is for **manual and automated API testing** with:
- URL to hit
- HTTP method
- authorization requirements
- payload examples
- expected success and failure responses
- end-to-end test checklist to validate behavior and catch regressions

---

## 2) Local Setup (Backend + DB + Keycloak)

### 2.1 Start dependencies (Postgres, Keycloak)

```powershell
Set-Location "C:\Users\LAKSHAYA\Desktop\CODING\java\Projects\project 2 Event booking App\Event-Booking-App-Project - new\Backend\tickets"
docker compose up -d db keycloak
```

Optional health checks:

```powershell
docker compose ps
curl.exe http://localhost:9090/
```

### 2.2 Configure environment variables

1. Copy `.env.example` to `.env`.
2. Fill at least:
   - `DB_PASSWORD`
   - `KEYCLOAK_ADMIN_USERNAME`
   - `KEYCLOAK_ADMIN_PASSWORD`

### 2.3 Start backend

```powershell
Set-Location "C:\Users\LAKSHAYA\Desktop\CODING\java\Projects\project 2 Event booking App\Event-Booking-App-Project - new\Backend\tickets"
.\start-local.ps1
```

Default API base URL from config is:
- `http://localhost:8081`

---

## 3) Keycloak Setup - Click-by-Click (Detailed)

> Keycloak container in this project runs at `http://localhost:9090`.

### 3.1 Login to Admin Console

1. Open browser: `http://localhost:9090`.
2. Click **Administration Console**.
3. Username: `admin` (or your `KEYCLOAK_ADMIN_USERNAME`).
4. Password: `admin` (or your `KEYCLOAK_ADMIN_PASSWORD`).
5. Click **Sign In**.

### 3.2 Create realm `event-ticket-platform`

1. Top-left realm dropdown (usually shows `master`) -> click.
2. Click **Create realm**.
3. In **Realm name**, type `event-ticket-platform`.
4. Click **Create**.

### 3.3 Create realm roles

1. Ensure you are inside realm `event-ticket-platform`.
2. Left menu -> **Realm roles**.
3. Click **Create role**.
4. Create these roles one by one:
   - `ADMIN`
   - `ORGANIZER`
   - `ATTENDEE`
   - `STAFF`
5. For each role:
   - Enter role name
   - Click **Save**.

### 3.4 Create client `event-ticket-platform-app`

1. Left menu -> **Clients**.
2. Click **Create client**.
3. Client type: **OpenID Connect**.
4. Client ID: `event-ticket-platform-app`.
5. Click **Next**.
6. Client authentication:
   - For easiest API testing: set **Client authentication = Off** (public client).
7. Standard flow: can stay On/Off (not critical for password token testing).
8. Enable **Direct access grants** (important for password grant testing).
9. Click **Save**.
10. Open client -> **Settings** tab:
    - Confirm Client ID = `event-ticket-platform-app`.
    - Save if changed.

### 3.5 Add optional audience mapper (recommended for cleaner tokens)

1. Open client `event-ticket-platform-app`.
2. Go to **Client scopes** or **Mappers** (UI wording can vary by Keycloak version).
3. Click **Add mapper**.
4. Choose mapper type **Audience**.
5. Name: `aud-event-ticket-platform-app`.
6. Included Client Audience: `event-ticket-platform-app`.
7. Save.

### 3.6 Create test users

Create at least these 6 users so all role and approval tests are possible.

1. Left menu -> **Users**.
2. Click **Add user**.
3. Fill required fields:
   - Username: (example `admin1`)
   - Email verified: On (optional but useful)
   - Email: (example `admin1@example.com`)
   - First name / Last name (optional)
   - Enabled: On
4. Click **Create**.
5. Open **Credentials** tab:
   - Set password (e.g., `Pass@1234`)
   - Turn **Temporary** Off
   - Click **Set password** -> confirm.
6. Open **Role mapping** tab:
   - Click **Assign role**
   - Select realm role
   - Click **Assign**.

Repeat for:
- `admin1` -> role `ADMIN`
- `organizer1` -> role `ORGANIZER`
- `organizer2` -> role `ORGANIZER`
- `attendee1` -> role `ATTENDEE`
- `staff1` -> role `STAFF`
- `pending1` -> role `ORGANIZER` (or `ATTENDEE`) to test approval gate

### 3.7 Create local DB user records for Keycloak users

The backend approval gate checks local DB user approval status, not just Keycloak roles.  
So Keycloak-only users are not enough for full API tests.

Use app registration endpoint where possible (`/api/v1/auth/register`) so local row is created automatically with `PENDING`.

If you manually create users in Keycloak admin UI, ensure corresponding local `users` table rows exist with matching UUID and approval status.

### 3.8 Approve/reject users for approval-gate tests

1. Obtain admin token (see Section 4).
2. Call:
   - `GET /api/v1/admin/approvals/pending`
   - `POST /api/v1/admin/approvals/{userId}/approve`
   - `POST /api/v1/admin/approvals/{userId}/reject`
3. Verify pending user receives:
   - `403 APPROVAL_PENDING` before approval
   - normal access after approval
   - `403 APPROVAL_REJECTED` after rejection

---

## 4) Getting JWT Tokens for API Calls

Token endpoint format:
- `POST http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token`

### 4.1 Example token request (password grant)

```powershell
$tokenResponse = curl.exe -s -X POST "http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token" ^
  -H "Content-Type: application/x-www-form-urlencoded" ^
  -d "client_id=event-ticket-platform-app" ^
  -d "grant_type=password" ^
  -d "username=admin1" ^
  -d "password=Pass@1234"

$tokenResponse
```

Use `access_token` in API calls:

```powershell
$ADMIN_TOKEN = "<paste_access_token_here>"
curl.exe -X GET "http://localhost:8081/api/v1/admin/roles" -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## 5) Auth and Approval Gate Rules You Must Test

### 5.1 SecurityConfig rules

- Public (`permitAll`):
  - `POST /api/v1/auth/register`
  - Swagger/OpenAPI: `/swagger-ui.html`, `/swagger-ui/**`, `/api-docs`, `/api-docs/**`, `/v3/api-docs/**`
  - Health/info/metrics endpoints listed in `SecurityConfig`
- All other API endpoints: authenticated JWT required.

### 5.2 ApprovalGateFilter behavior

Authenticated users are blocked if local approval status is not approved:
- `PENDING` -> 403 with error `APPROVAL_PENDING`
- `REJECTED` -> 403 with error `APPROVAL_REJECTED`

Bypass paths (no approval check):
- `/api/v1/auth/register`
- `/api/v1/invites/redeem`
- `/actuator/**`
- `/swagger-ui/**`
- `/api-docs/**`
- `/v3/api-docs/**`

---

## 6) Endpoint Test Matrix (URL + Method + Auth + Payload)

Base URL: `http://localhost:8081`

## 6.1 Auth

### `POST /api/v1/auth/register`
- Auth: `PUBLIC`
- Payload:
```json
{
  "inviteCode": "ABCD-EFGH-IJKL-MNOP",
  "email": "new.user@example.com",
  "password": "Pass@1234",
  "name": "New User"
}
```
- Success: `201 Created` (`RegisterResponseDto`)
- Common failures:
  - `400 VALIDATION_ERROR`
  - `400 INVALID_INVITE_CODE`
  - `404 INVITE_CODE_NOT_FOUND`
  - `409 EMAIL_ALREADY_REGISTERED`
  - `422 REGISTRATION_FAILED`

## 6.2 Admin approvals

### `GET /api/v1/admin/approvals/pending`
- Auth: `ADMIN`
- Payload: none
- Success: `200` page of `UserApprovalDto`

### `POST /api/v1/admin/approvals/{userId}/approve`
- Auth: `ADMIN`
- Payload: none
- Success: `200` map `{message,userId,status}`
- Failure: `409 INVALID_APPROVAL_STATE`, `404 USER_NOT_FOUND`

### `POST /api/v1/admin/approvals/{userId}/reject`
- Auth: `ADMIN`
- Payload:
```json
{ "reason": "Incomplete organizer profile details" }
```
- Success: `200`
- Failure: `400 VALIDATION_ERROR`, `409 INVALID_APPROVAL_STATE`

### `GET /api/v1/admin/approvals`
- Auth: `ADMIN`
- Success: `200` page of `UserApprovalDto`

## 6.3 Admin role governance

### `POST /api/v1/admin/users/{userId}/roles`
- Auth: `ADMIN`
- Payload:
```json
{ "roleName": "STAFF" }
```
- Success: `200 UserRolesResponseDto`
- Failure: `400 VALIDATION_ERROR`, `404 USER_NOT_FOUND`, `500 KEYCLOAK_OPERATION_FAILED`

### `DELETE /api/v1/admin/users/{userId}/roles/{roleName}`
- Auth: `ADMIN`
- Success: `200 UserRolesResponseDto`

### `GET /api/v1/admin/users/{userId}/roles`
- Auth: `ADMIN`
- Success: `200 UserRolesResponseDto`

### `GET /api/v1/admin/roles`
- Auth: `ADMIN`
- Success: `200 AvailableRolesResponseDto`

## 6.4 Audit logs

### `GET /api/v1/audit`
- Auth: `ADMIN`
- Success: `200` page of `AuditLogDto`

### `GET /api/v1/audit/events/{eventId}`
- Auth: `ORGANIZER` (must own event)
- Success: `200`
- Failure: `403 ACCESS_DENIED`, `404 EVENT_NOT_FOUND`

### `GET /api/v1/audit/me`
- Auth: any authenticated role
- Success: `200`

## 6.5 Events

### `POST /api/v1/events`
- Auth: `ORGANIZER`
- Payload:
```json
{
  "name": "Tech Summit 2026",
  "start": "2026-07-20T10:00:00",
  "end": "2026-07-20T18:00:00",
  "venue": "City Convention Center",
  "salesStart": "2026-05-01T00:00:00",
  "salesEnd": "2026-07-19T23:59:00",
  "status": "DRAFT",
  "maxCapacity": 500,
  "ticketTypes": [
    {
      "name": "General",
      "price": 499.00,
      "description": "General pass",
      "totalAvailable": 300
    }
  ]
}
```
- Success: `201 CreateEventResponseDto`
- Failures: `400 VALIDATION_ERROR`, `409 DATA_CONFLICT`

### `PUT /api/v1/events/{eventId}`
- Auth: `ORGANIZER` owner
- Payload shape similar to create (`UpdateEventRequestDto`)
- Success: `200 UpdateEventResponseDto`
- Failures: `400 INVALID_ARGUMENT` (path/body ID mismatch), `404 EVENT_NOT_FOUND`

### `GET /api/v1/events`
- Auth: `ORGANIZER`
- Success: `200 Page<ListEventResponseDto>`

### `GET /api/v1/events/{eventId}`
- Auth: `ORGANIZER` owner
- Success: `200 GetEventDetailsResponseDto`
- Not found: `404` (controller returns notFound)

### `DELETE /api/v1/events/{eventId}`
- Auth: `ORGANIZER` owner
- Success: `204`

### `GET /api/v1/events/{eventId}/sales-dashboard`
- Auth: `ORGANIZER` owner
- Success: `200 Map<String,Object>`

### `GET /api/v1/events/{eventId}/attendees-report`
- Auth: `ORGANIZER` owner
- Success: `200 Map<String,Object>`

### `GET /api/v1/events/{eventId}/sales-report.xlsx`
- Auth: `ORGANIZER` owner
- Success: `200` binary XLSX with attachment header
- Failure: `500 REPORT_GENERATION_FAILED`

## 6.6 Ticket types + purchases

### `POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets`
- Auth: `ATTENDEE` or `ORGANIZER`
- Payload:
```json
{ "quantity": 2 }
```
- Success: `201` list of `GetTicketResponseDto`
- Failures:
  - `400 TICKETS_SOLD_OUT`
  - `400 VALIDATION_ERROR` (quantity out of range)
  - `404 TICKET_TYPE_NOT_FOUND`

### `POST /api/v1/events/{eventId}/ticket-types`
- Auth: `ORGANIZER`
- Payload: `CreateTicketTypeRequestDto`
- Success: `201`

### `GET /api/v1/events/{eventId}/ticket-types`
- Auth: `ORGANIZER`
- Success: `200`

### `GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
- Auth: `ORGANIZER`
- Success: `200`
- Not found: `404`

### `PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
- Auth: `ORGANIZER`
- Payload: `UpdateTicketTypeRequestDto`
- Success: `200`

### `DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
- Auth: `ORGANIZER`
- Success: `204`
- Failure: `409 TICKET_TYPE_DELETE_NOT_ALLOWED`

## 6.7 Tickets + QR

### `GET /api/v1/tickets`
- Auth: `ATTENDEE` or `ORGANIZER`
- Success: `200 Page<ListTicketResponseDto>`

### `GET /api/v1/tickets/{ticketId}`
- Auth: `ATTENDEE` or `ORGANIZER`
- Success: `200 GetTicketResponseDto`
- Not found: `404`

### `GET /api/v1/tickets/{ticketId}/qr-codes`
- Auth: `ATTENDEE` or `ORGANIZER`
- Success: `200 image/png`

### `GET /api/v1/tickets/{ticketId}/qr-codes/view`
- Auth: `ATTENDEE` or `ORGANIZER`
- Success: `200 image/png` (inline, private cache)

### `GET /api/v1/tickets/{ticketId}/qr-codes/png`
- Auth: `ATTENDEE` or `ORGANIZER`
- Success: `200 image/png` attachment

### `GET /api/v1/tickets/{ticketId}/qr-codes/pdf`
- Auth: `ATTENDEE` or `ORGANIZER`
- Success: `200 application/pdf` attachment

## 6.8 Published events

### `GET /api/v1/published-events?q=<optional>`
- Auth: `ATTENDEE` or `ORGANIZER` or `STAFF`
- Success: `200 Page<ListPublishedEventResponseDto>`

### `GET /api/v1/published-events/{eventId}`
- Auth: `ATTENDEE` or `ORGANIZER` or `STAFF`
- Success: `200 GetPublishedEventDetailsResponseDto`
- Not found: `404`

## 6.9 Event staff

### `POST /api/v1/events/{eventId}/staff`
- Auth: `ORGANIZER` owner
- Payload:
```json
{ "userId": "11111111-1111-1111-1111-111111111111" }
```
- Success: `201 EventStaffResponseDto`

### `DELETE /api/v1/events/{eventId}/staff/{userId}`
- Auth: `ORGANIZER` owner
- Success: `200 EventStaffResponseDto`

### `GET /api/v1/events/{eventId}/staff`
- Auth: `ORGANIZER` owner
- Success: `200 EventStaffResponseDto`

## 6.10 Invite codes

### `POST /api/v1/invites`
- Auth: `ADMIN` or `ORGANIZER`
- Payload:
```json
{
  "roleName": "STAFF",
  "eventId": "22222222-2222-2222-2222-222222222222",
  "expirationHours": 48
}
```
- Success: `201 InviteCodeResponseDto`
- Failures: `400 INVALID_ARGUMENT`, `400 VALIDATION_ERROR`

### `POST /api/v1/invites/redeem`
- Auth: authenticated user (`isAuthenticated`) and bypasses approval gate
- Payload:
```json
{ "code": "ABCD-EFGH-IJKL-MNOP" }
```
- Success: `200 RedeemInviteCodeResponseDto`
- Failures: `400 INVALID_INVITE_CODE`, `404 INVITE_CODE_NOT_FOUND`

### `DELETE /api/v1/invites/{codeId}?reason=...`
- Auth: `ADMIN` or `ORGANIZER`
- Success: `204`

### `GET /api/v1/invites`
- Auth: `ADMIN` or `ORGANIZER`
- Success: `200 Page<InviteCodeResponseDto>`

### `GET /api/v1/invites/events/{eventId}`
- Auth: `ADMIN` or event owner `ORGANIZER`
- Success: `200 Page<InviteCodeResponseDto>`

## 6.11 Ticket validations

### `POST /api/v1/ticket-validations`
- Auth: `STAFF` or `ORGANIZER`
- Payload:
```json
{
  "id": "33333333-3333-3333-3333-333333333333",
  "method": "MANUAL"
}
```
or
```json
{
  "id": "44444444-4444-4444-4444-444444444444",
  "method": "QR_SCAN"
}
```
- Success: `200 TicketValidationResponseDto`
- Failures: `400 VALIDATION_ERROR`, `404 TICKET_NOT_FOUND` or `QR_CODE_NOT_FOUND`

### `GET /api/v1/ticket-validations/events/{eventId}`
- Auth: `STAFF` or `ORGANIZER`
- Success: `200 Page<TicketValidationResponseDto>`

### `GET /api/v1/ticket-validations/tickets/{ticketId}`
- Auth: `STAFF` or `ORGANIZER`
- Success: `200 List<TicketValidationResponseDto>`

## 6.12 Payload Coverage for Every Endpoint (Complete)

Use this as the strict payload reference. `None` means no JSON body is sent.

- `POST /api/v1/auth/register`
  - Path params: none
  - Query params: none
  - Body: `RegisterRequestDto` JSON

- `GET /api/v1/admin/approvals/pending`
  - Path params: none
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `POST /api/v1/admin/approvals/{userId}/approve`
  - Path params: `userId` (UUID)
  - Query params: none
  - Body: none

- `POST /api/v1/admin/approvals/{userId}/reject`
  - Path params: `userId` (UUID)
  - Query params: none
  - Body: `{ "reason": "..." }`

- `GET /api/v1/admin/approvals`
  - Path params: none
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `POST /api/v1/admin/users/{userId}/roles`
  - Path params: `userId` (UUID)
  - Query params: none
  - Body: `{ "roleName": "ADMIN|ORGANIZER|ATTENDEE|STAFF" }`

- `DELETE /api/v1/admin/users/{userId}/roles/{roleName}`
  - Path params: `userId` (UUID), `roleName` (string)
  - Query params: none
  - Body: none

- `GET /api/v1/admin/users/{userId}/roles`
  - Path params: `userId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/admin/roles`
  - Path params: none
  - Query params: none
  - Body: none

- `GET /api/v1/audit`
  - Path params: none
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `GET /api/v1/audit/events/{eventId}`
  - Path params: `eventId` (UUID)
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `GET /api/v1/audit/me`
  - Path params: none
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `POST /api/v1/events`
  - Path params: none
  - Query params: none
  - Body: `CreateEventRequestDto` JSON

- `PUT /api/v1/events/{eventId}`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: `UpdateEventRequestDto` JSON

- `GET /api/v1/events`
  - Path params: none
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `GET /api/v1/events/{eventId}`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: none

- `DELETE /api/v1/events/{eventId}`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/events/{eventId}/sales-dashboard`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/events/{eventId}/attendees-report`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/events/{eventId}/sales-report.xlsx`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: none

- `POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets`
  - Path params: `eventId` (UUID), `ticketTypeId` (UUID)
  - Query params: none
  - Body: `{ "quantity": 1..10 }`

- `POST /api/v1/events/{eventId}/ticket-types`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: `CreateTicketTypeRequestDto` JSON

- `GET /api/v1/events/{eventId}/ticket-types`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
  - Path params: `eventId` (UUID), `ticketTypeId` (UUID)
  - Query params: none
  - Body: none

- `PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
  - Path params: `eventId` (UUID), `ticketTypeId` (UUID)
  - Query params: none
  - Body: `UpdateTicketTypeRequestDto` JSON

- `DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
  - Path params: `eventId` (UUID), `ticketTypeId` (UUID)
  - Query params: none
  - Body: none

- `POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts`
  - Path params: `eventId` (UUID), `ticketTypeId` (UUID)
  - Query params: none
  - Body: `CreateDiscountRequestDto` JSON

- `PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}`
  - Path params: `eventId` (UUID), `ticketTypeId` (UUID), `discountId` (UUID)
  - Query params: none
  - Body: `CreateDiscountRequestDto` JSON

- `DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}`
  - Path params: `eventId` (UUID), `ticketTypeId` (UUID), `discountId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}`
  - Path params: `eventId` (UUID), `ticketTypeId` (UUID), `discountId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts`
  - Path params: `eventId` (UUID), `ticketTypeId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/tickets`
  - Path params: none
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `GET /api/v1/tickets/{ticketId}`
  - Path params: `ticketId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/tickets/{ticketId}/qr-codes`
  - Path params: `ticketId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/tickets/{ticketId}/qr-codes/view`
  - Path params: `ticketId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/tickets/{ticketId}/qr-codes/png`
  - Path params: `ticketId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/tickets/{ticketId}/qr-codes/pdf`
  - Path params: `ticketId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/published-events`
  - Path params: none
  - Query params: `q` (optional), `page`, `size`, `sort` (optional)
  - Body: none

- `GET /api/v1/published-events/{eventId}`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: none

- `POST /api/v1/events/{eventId}/staff`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: `{ "userId": "<uuid>" }`

- `DELETE /api/v1/events/{eventId}/staff/{userId}`
  - Path params: `eventId` (UUID), `userId` (UUID)
  - Query params: none
  - Body: none

- `GET /api/v1/events/{eventId}/staff`
  - Path params: `eventId` (UUID)
  - Query params: none
  - Body: none

- `POST /api/v1/invites`
  - Path params: none
  - Query params: none
  - Body: `{ "roleName": "ADMIN|ORGANIZER|ATTENDEE|STAFF", "eventId": "<uuid|null>", "expirationHours": <positive-int> }`

- `POST /api/v1/invites/redeem`
  - Path params: none
  - Query params: none
  - Body: `{ "code": "XXXX-XXXX-XXXX-XXXX" }`

- `DELETE /api/v1/invites/{codeId}`
  - Path params: `codeId` (UUID)
  - Query params: `reason` (optional)
  - Body: none

- `GET /api/v1/invites`
  - Path params: none
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `GET /api/v1/invites/events/{eventId}`
  - Path params: `eventId` (UUID)
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `POST /api/v1/ticket-validations`
  - Path params: none
  - Query params: none
  - Body: `{ "id": "<uuid>", "method": "MANUAL|QR_SCAN" }`

- `GET /api/v1/ticket-validations/events/{eventId}`
  - Path params: `eventId` (UUID)
  - Query params: `page`, `size`, `sort` (optional)
  - Body: none

- `GET /api/v1/ticket-validations/tickets/{ticketId}`
  - Path params: `ticketId` (UUID)
  - Query params: none
  - Body: none

---

## 7) Response Types You Can Get

## 7.1 Success status codes
- `200 OK`
- `201 Created`
- `204 No Content`

## 7.2 Error status codes (audited from handlers + controllers)
- `400` validation / invalid input / invalid arguments / sold out
- `401` missing/invalid JWT
- `403` role denied or approval gate blocked
- `404` resource not found / endpoint not found
- `405` method not allowed
- `409` business-state conflicts / data integrity / duplicate email
- `422` registration flow failed
- `500` unexpected or dependency failures

## 7.3 Error body models

Most errors use `ErrorDto`:
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Validation failed on 2 field(s). See validationErrors for details.",
  "statusCode": 400,
  "statusDescription": "BAD REQUEST - Validation failed",
  "timestamp": "2026-03-20T10:11:12",
  "path": "/api/v1/auth/register",
  "validationErrors": [
    "email: Email is required"
  ],
  "possibleCauses": [],
  "solutions": []
}
```

Approval gate 403 uses compact map format (not `ErrorDto`):
```json
{
  "error": "APPROVAL_PENDING",
  "message": "Your account is awaiting approval from an administrator. You will be notified once your account has been reviewed.",
  "status": "403",
  "timestamp": "2026-03-20T10:11:12Z"
}
```

---

## 8) Comprehensive Test Checklist

Use this as a release checklist.

- [ ] **Authentication basics**: no token -> 401 on protected endpoint; malformed token -> 401; expired token -> 401.
- [ ] **Role authorization**: each endpoint denies wrong role with 403.
- [ ] **Approval gate**: `PENDING` and `REJECTED` users blocked everywhere except bypass routes.
- [ ] **Pagination controls**: verify default page and max page size behavior (`size > 50` handling).
- [ ] **Validation coverage**: send invalid payloads for each request DTO and check `validationErrors` list includes all field issues.
- [ ] **Ownership rules**: organizer cannot access another organizer's event/ticket/staff/invite resources.
- [ ] **State transitions**: approval transitions (pending->approved/rejected), invite statuses, ticket validation idempotency behavior.
- [ ] **Ticket purchase constraints**: quantity min/max, sold out path, sales-window constraints, event status constraints.
- [ ] **Discount rules**: only one active discount per ticket type, date windows, percentage/fixed constraints.
- [ ] **Binary endpoints**: verify content-type + content-disposition for QR PNG/PDF and sales-report XLSX.
- [ ] **Audit side-effects**: key actions appear in `/api/v1/audit` and include expected actor/event metadata.
- [ ] **Error consistency**: status code and `error` code match expected handler mapping.
- [ ] **CORS smoke test**: browser preflight allowed from configured origin.
- [ ] **Actuator and docs**: health and swagger endpoints reachable as configured.

---

## 9) High-Value Negative Tests (Must Run)

1. Register with invalid password pattern -> 400 validation.  
2. Register with duplicate email -> 409.  
3. `ORGANIZER` tries `/api/v1/admin/roles` -> 403.  
4. `ATTENDEE` tries to create event -> 403.  
5. Pending user calls `/api/v1/events` -> 403 `APPROVAL_PENDING`.  
6. Rejected user calls `/api/v1/tickets` -> 403 `APPROVAL_REJECTED`.  
7. Organizer A fetches Organizer B event audit -> 403/404 depending path.  
8. Purchase quantity 0 or 11 -> 400 validation.  
9. Delete ticket type with sold tickets -> 409 `TICKET_TYPE_DELETE_NOT_ALLOWED`.  
10. Invite creation mismatch (`roleName=STAFF` + no `eventId`) -> 400 invalid argument.  
11. Ticket validation payload missing `method` -> 400 validation.  
12. Invalid endpoint path -> 404 `ENDPOINT_NOT_FOUND`.

---

## 10) Useful Quick Commands

### OpenAPI JSON

```powershell
curl.exe "http://localhost:8081/api-docs"
```

### Swagger UI

```powershell
Start-Process "http://localhost:8081/swagger-ui.html"
```

### Health endpoint

```powershell
curl.exe "http://localhost:8081/actuator/health"
```

---

## 11) Notes

- Role checks are done via `@PreAuthorize(...)` and mapped from Keycloak `realm_access.roles` / `resource_access`.
- Endpoint access can still fail after role success due to ownership checks in service layer.
- For production profile, error message sanitization is applied in `GlobalExceptionHandler`.

