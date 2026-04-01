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

## 6) Endpoint Testing (Execution Order + Full Request Details)

Base URL: `http://localhost:8081`

### 6.1 Standard request headers
- Public endpoints: `Content-Type: application/json` (if body is present)
- Protected JSON endpoints:
  - `Authorization: Bearer <JWT_TOKEN>`
  - `Content-Type: application/json`
  - `Accept: application/json`
- Binary download endpoints:
  - `Authorization: Bearer <JWT_TOKEN>`
  - `Accept: */*`

### 6.2 Phase 1 - Registration and account bootstrap

#### Test Case: Register user
- URL: `POST /api/v1/auth/register`
- Preconditions: none
- Path params: none
- Query params: none
- Request body:
```json
{
  "inviteCode": "ABCD-EFGH-IJKL-MNOP",
  "email": "new.user@example.com",
  "password": "Pass@1234",
  "name": "New User"
}
```
- Expected success: `201 Created`, body = `RegisterResponseDto`
- Possible failures: `400 VALIDATION_ERROR`, `400 INVALID_INVITE_CODE`, `404 INVITE_CODE_NOT_FOUND`, `409 EMAIL_ALREADY_REGISTERED`, `422 REGISTRATION_FAILED`, `500`
- Verification: user exists in local DB with `PENDING` status.

### 6.3 Phase 2 - Admin approval and role governance

#### Test Case: Get pending approvals
- URL: `GET /api/v1/admin/approvals/pending`
- Preconditions: admin JWT
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<UserApprovalDto>`
- Possible failures: `401`, `403`

#### Test Case: Approve user
- URL: `POST /api/v1/admin/approvals/{userId}/approve`
- Preconditions: pending user exists
- Path params: `userId` (UUID)
- Request body: none
- Expected success: `200` with `status=APPROVED`
- Possible failures: `401`, `403`, `404 USER_NOT_FOUND`, `409 INVALID_APPROVAL_STATE`

#### Test Case: Reject user
- URL: `POST /api/v1/admin/approvals/{userId}/reject`
- Preconditions: pending user exists
- Path params: `userId` (UUID)
- Request body:
```json
{ "reason": "Incomplete organizer profile details" }
```
- Expected success: `200` with `status=REJECTED`
- Possible failures: `400 VALIDATION_ERROR`, `401`, `403`, `404 USER_NOT_FOUND`, `409 INVALID_APPROVAL_STATE`

#### Test Case: List all users with approval status
- URL: `GET /api/v1/admin/approvals`
- Preconditions: admin JWT
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<UserApprovalDto>`
- Possible failures: `401`, `403`

#### Test Case: Get available roles
- URL: `GET /api/v1/admin/roles`
- Preconditions: admin JWT
- Request body: none
- Expected success: `200 AvailableRolesResponseDto`
- Possible failures: `401`, `403`, `500`

#### Test Case: Assign role
- URL: `POST /api/v1/admin/users/{userId}/roles`
- Preconditions: admin JWT, target user exists
- Path params: `userId` (UUID)
- Request body:
```json
{ "roleName": "STAFF" }
```
- Expected success: `200 UserRolesResponseDto`
- Possible failures: `400 VALIDATION_ERROR`, `401`, `403`, `404 USER_NOT_FOUND`, `500`

#### Test Case: Get user roles
- URL: `GET /api/v1/admin/users/{userId}/roles`
- Preconditions: admin JWT
- Path params: `userId` (UUID)
- Request body: none
- Expected success: `200 UserRolesResponseDto`
- Possible failures: `401`, `403`, `404 USER_NOT_FOUND`, `500`

#### Test Case: Revoke role
- URL: `DELETE /api/v1/admin/users/{userId}/roles/{roleName}`
- Preconditions: admin JWT
- Path params: `userId` (UUID), `roleName` (string)
- Request body: none
- Expected success: `200 UserRolesResponseDto`
- Possible failures: `401`, `403`, `404 USER_NOT_FOUND`, `500`

### 6.4 Phase 3 - Organizer setup (event -> ticket type -> discount -> staff)

#### Test Case: Create event
- URL: `POST /api/v1/events`
- Preconditions: approved organizer JWT
- Request body (full example):
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
- Expected success: `201 CreateEventResponseDto`
- Possible failures: `400 VALIDATION_ERROR`, `401`, `403`, `409`, `500`

#### Test Case: List organizer events
- URL: `GET /api/v1/events`
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<ListEventResponseDto>`
- Possible failures: `401`, `403`

#### Test Case: Get event details
- URL: `GET /api/v1/events/{eventId}`
- Path params: `eventId` (UUID)
- Request body: none
- Expected success: `200 GetEventDetailsResponseDto`
- Possible failures: `401`, `403`, `404`

#### Test Case: Update event
- URL: `PUT /api/v1/events/{eventId}`
- Path params: `eventId` (UUID)
- Request body: `UpdateEventRequestDto`
- Expected success: `200 UpdateEventResponseDto`
- Possible failures: `400 INVALID_ARGUMENT|VALIDATION_ERROR`, `401`, `403`, `404`, `409`, `500`

#### Test Case: Create ticket type
- URL: `POST /api/v1/events/{eventId}/ticket-types`
- Path params: `eventId` (UUID)
- Request body:
```json
{
  "name": "VIP",
  "price": 1299.00,
  "description": "Front-row + lounge",
  "totalAvailable": 50
}
```
- Expected success: `201 CreateTicketTypeResponseDto`
- Possible failures: `400`, `401`, `403`, `404`, `409`, `500`

#### Test Case: List ticket types
- URL: `GET /api/v1/events/{eventId}/ticket-types`
- Path params: `eventId` (UUID)
- Request body: none
- Expected success: `200 List<CreateTicketTypeResponseDto>`
- Possible failures: `401`, `403`, `404`

#### Test Case: Get ticket type
- URL: `GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
- Path params: `eventId`, `ticketTypeId` (UUID)
- Request body: none
- Expected success: `200 CreateTicketTypeResponseDto`
- Possible failures: `401`, `403`, `404`

#### Test Case: Update ticket type
- URL: `PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
- Path params: `eventId`, `ticketTypeId` (UUID)
- Request body: `UpdateTicketTypeRequestDto`
- Expected success: `200 UpdateTicketTypeResponseDto`
- Possible failures: `400`, `401`, `403`, `404`, `409`, `500`

#### Test Case: Create discount
- URL: `POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts`
- Path params: `eventId`, `ticketTypeId` (UUID)
- Request body:
```json
{
  "discountType": "PERCENTAGE",
  "value": 10.00,
  "validFrom": "2026-06-01T00:00:00",
  "validTo": "2026-07-01T00:00:00",
  "active": true,
  "description": "Early bird"
}
```
- Expected success: `201 DiscountResponseDto`
- Possible failures: `400`, `401`, `403`, `404`, `409 DISCOUNT_ALREADY_EXISTS`, `500`

#### Test Case: List discounts
- URL: `GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts`
- Path params: `eventId`, `ticketTypeId`
- Request body: none
- Expected success: `200 List<DiscountResponseDto>`
- Possible failures: `401`, `403`, `404`

#### Test Case: Update discount
- URL: `PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}`
- Path params: `eventId`, `ticketTypeId`, `discountId`
- Request body: `CreateDiscountRequestDto`
- Expected success: `200 DiscountResponseDto`
- Possible failures: `400`, `401`, `403`, `404`, `409`, `500`

#### Test Case: Get discount
- URL: `GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}`
- Path params: `eventId`, `ticketTypeId`, `discountId`
- Request body: none
- Expected success: `200 DiscountResponseDto`
- Possible failures: `401`, `403`, `404 DISCOUNT_NOT_FOUND`

#### Test Case: Assign staff
- URL: `POST /api/v1/events/{eventId}/staff`
- Path params: `eventId`
- Request body:
```json
{ "userId": "11111111-1111-1111-1111-111111111111" }
```
- Expected success: `201 EventStaffResponseDto`
- Possible failures: `400`, `401`, `403`, `404`, `409`, `500`

#### Test Case: List staff
- URL: `GET /api/v1/events/{eventId}/staff`
- Path params: `eventId`
- Request body: none
- Expected success: `200 EventStaffResponseDto`
- Possible failures: `401`, `403`, `404`

### 6.5 Phase 4 - Invite flow

#### Test Case: Generate invite code
- URL: `POST /api/v1/invites`
- Preconditions: admin or organizer JWT
- Request body:
```json
{
  "roleName": "STAFF",
  "eventId": "22222222-2222-2222-2222-222222222222",
  "expirationHours": 48
}
```
- Expected success: `201 InviteCodeResponseDto`
- Possible failures: `400 INVALID_ARGUMENT|VALIDATION_ERROR`, `401`, `403`, `404`, `409`, `500`

#### Test Case: List all invite codes
- URL: `GET /api/v1/invites`
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<InviteCodeResponseDto>`
- Possible failures: `401`, `403`

#### Test Case: List event invite codes
- URL: `GET /api/v1/invites/events/{eventId}`
- Path params: `eventId`
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<InviteCodeResponseDto>`
- Possible failures: `401`, `403`, `404`

#### Test Case: Redeem invite code
- URL: `POST /api/v1/invites/redeem`
- Preconditions: authenticated user JWT
- Request body:
```json
{ "code": "ABCD-EFGH-IJKL-MNOP" }
```
- Expected success: `200 RedeemInviteCodeResponseDto`
- Possible failures: `400 INVALID_INVITE_CODE|VALIDATION_ERROR`, `401`, `403`, `404 INVITE_CODE_NOT_FOUND`, `409`, `500`

#### Test Case: Revoke invite code
- URL: `DELETE /api/v1/invites/{codeId}`
- Path params: `codeId` (UUID)
- Query params: `reason` (optional)
- Request body: none
- Expected success: `204 No Content`
- Possible failures: `401`, `403`, `404`, `409`, `500`

### 6.6 Phase 5 - Discovery and purchase

#### Test Case: List published events
- URL: `GET /api/v1/published-events`
- Query params: `q` (optional), `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<ListPublishedEventResponseDto>`
- Possible failures: `401`, `403`

#### Test Case: Get published event details
- URL: `GET /api/v1/published-events/{eventId}`
- Path params: `eventId`
- Request body: none
- Expected success: `200 GetPublishedEventDetailsResponseDto`
- Possible failures: `401`, `403`, `404`

#### Test Case: Purchase tickets
- URL: `POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets`
- Path params: `eventId`, `ticketTypeId`
- Request body:
```json
{ "quantity": 2 }
```
- Expected success: `201 List<GetTicketResponseDto>`
- Possible failures: `400 TICKETS_SOLD_OUT|VALIDATION_ERROR`, `401`, `403`, `404 TICKET_TYPE_NOT_FOUND`, `409`, `500`

### 6.7 Phase 6 - Ticket and QR retrieval

#### Test Case: List tickets
- URL: `GET /api/v1/tickets`
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<ListTicketResponseDto>`
- Possible failures: `401`, `403`

#### Test Case: Get ticket
- URL: `GET /api/v1/tickets/{ticketId}`
- Path params: `ticketId`
- Request body: none
- Expected success: `200 GetTicketResponseDto`
- Possible failures: `401`, `403`, `404 TICKET_NOT_FOUND`

#### Test Case: Download QR PNG (legacy)
- URL: `GET /api/v1/tickets/{ticketId}/qr-codes`
- Path params: `ticketId`
- Request body: none
- Expected success: `200 image/png`
- Possible failures: `401`, `403`, `404 QR_CODE_NOT_FOUND`, `500`

#### Test Case: View QR PNG inline
- URL: `GET /api/v1/tickets/{ticketId}/qr-codes/view`
- Path params: `ticketId`
- Request body: none
- Expected success: `200 image/png`
- Possible failures: `401`, `403`, `404`, `500`

#### Test Case: Download QR PNG file
- URL: `GET /api/v1/tickets/{ticketId}/qr-codes/png`
- Path params: `ticketId`
- Request body: none
- Expected success: `200 image/png` with attachment header
- Possible failures: `401`, `403`, `404`, `500`

#### Test Case: Download QR PDF file
- URL: `GET /api/v1/tickets/{ticketId}/qr-codes/pdf`
- Path params: `ticketId`
- Request body: none
- Expected success: `200 application/pdf` with attachment header
- Possible failures: `401`, `403`, `404`, `500`

### 6.8 Phase 7 - Ticket validation at entry

#### Test Case: Validate ticket (manual)
- URL: `POST /api/v1/ticket-validations`
- Preconditions: staff or organizer JWT
- Request body:
```json
{
  "id": "33333333-3333-3333-3333-333333333333",
  "method": "MANUAL"
}
```
- Expected success: `200 TicketValidationResponseDto`
- Possible failures: `400 VALIDATION_ERROR`, `401`, `403`, `404 TICKET_NOT_FOUND`, `409`, `500`

#### Test Case: Validate ticket (QR)
- URL: `POST /api/v1/ticket-validations`
- Request body:
```json
{
  "id": "44444444-4444-4444-4444-444444444444",
  "method": "QR_SCAN"
}
```
- Expected success: `200 TicketValidationResponseDto`
- Possible failures: `400 VALIDATION_ERROR`, `401`, `403`, `404 QR_CODE_NOT_FOUND`, `409`, `500`

#### Test Case: List event validations
- URL: `GET /api/v1/ticket-validations/events/{eventId}`
- Path params: `eventId`
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<TicketValidationResponseDto>`
- Possible failures: `401`, `403`, `404`

#### Test Case: List validations by ticket
- URL: `GET /api/v1/ticket-validations/tickets/{ticketId}`
- Path params: `ticketId`
- Request body: none
- Expected success: `200 List<TicketValidationResponseDto>`
- Possible failures: `401`, `403`, `404`

### 6.9 Phase 8 - Audit and reports

#### Test Case: My audit trail
- URL: `GET /api/v1/audit/me`
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<AuditLogDto>`
- Possible failures: `401`, `403`

#### Test Case: Event audit (organizer)
- URL: `GET /api/v1/audit/events/{eventId}`
- Path params: `eventId`
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<AuditLogDto>`
- Possible failures: `401`, `403`, `404`

#### Test Case: All audit logs (admin)
- URL: `GET /api/v1/audit`
- Query params: `page`, `size`, `sort` (optional)
- Request body: none
- Expected success: `200 Page<AuditLogDto>`
- Possible failures: `401`, `403`

#### Test Case: Sales dashboard
- URL: `GET /api/v1/events/{eventId}/sales-dashboard`
- Path params: `eventId`
- Request body: none
- Expected success: `200 Map<String,Object>`
- Possible failures: `401`, `403`, `404`, `500`

#### Test Case: Attendees report
- URL: `GET /api/v1/events/{eventId}/attendees-report`
- Path params: `eventId`
- Request body: none
- Expected success: `200 Map<String,Object>`
- Possible failures: `401`, `403`, `404`, `500`

#### Test Case: Download sales report Excel
- URL: `GET /api/v1/events/{eventId}/sales-report.xlsx`
- Path params: `eventId`
- Request body: none
- Expected success: `200 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Possible failures: `401`, `403`, `404`, `500 REPORT_GENERATION_FAILED`

### 6.10 Cleanup tests (optional)

#### Test Case: Delete discount
- URL: `DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}`
- Expected success: `204`
- Possible failures: `401`, `403`, `404`, `409`, `500`

#### Test Case: Delete ticket type
- URL: `DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
- Expected success: `204`
- Possible failures: `401`, `403`, `404`, `409 TICKET_TYPE_DELETE_NOT_ALLOWED`, `500`

#### Test Case: Remove staff
- URL: `DELETE /api/v1/events/{eventId}/staff/{userId}`
- Expected success: `200 EventStaffResponseDto`
- Possible failures: `401`, `403`, `404`, `409`, `500`

#### Test Case: Delete event
- URL: `DELETE /api/v1/events/{eventId}`
- Expected success: `204`
- Possible failures: `401`, `403`, `404`, `409`, `500`

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
- `409` business-state conflicts / data integrity / duplicate email / concurrent modification
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

