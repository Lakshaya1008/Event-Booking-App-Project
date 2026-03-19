# Event Booking Platform — Complete Testing Guide & API Documentation
**All 50 Endpoints | Full Keycloak Setup | Every Test Case**
**Base URL:** `http://localhost:8081` | **Keycloak:** `http://localhost:9090`

**Documentation status:** This is the current source-of-truth API/testing guide for this repository.

---

# PART A — KEYCLOAK SETUP (STEP BY STEP)

---

## A1. Start Docker Services

```bash
# From the project root where docker-compose.yml lives
docker-compose up -d
```

**Services started:**

| Service | URL | Credentials |
|---------|-----|-------------|
| Spring Boot App | http://localhost:8081 | — |
| Keycloak Admin UI | http://localhost:9090 | admin / admin |
| PostgreSQL | localhost:5433 | postgres / postgres123 |
| Adminer (DB UI) | http://localhost:8888 | System: PostgreSQL, Server: db, User: postgres, Pass: postgres123 |

Wait ~30 seconds after `docker-compose up -d` before accessing Keycloak.

---

## A2. Keycloak Admin Console — Create the Realm

1. Open **http://localhost:9090**
2. Log in with **admin / admin**
3. Click the realm dropdown (top-left, shows "Keycloak")
4. Click **Create Realm**
5. Set **Realm name:** `event-ticket-platform`
6. Toggle **Enabled:** ON
7. Click **Create**

You are now inside the `event-ticket-platform` realm. All further steps are done inside this realm.

---

## A3. Create the Client

1. Left menu → **Clients** → **Create client**
2. **Client ID:** `event-ticket-platform-app`
3. **Client type:** `OpenID Connect`
4. Click **Next**
5. **Client authentication:** ON (this makes it confidential)
6. **Authorization:** OFF
7. **Authentication flow:** check only `Standard flow` and `Direct access grants`
8. Click **Next**
9. **Valid redirect URIs:** `http://localhost:8081/*`
10. **Web origins:** `http://localhost:8081`
11. Click **Save**

**Get the client secret:**
1. Still on the client page → click **Credentials** tab
2. Copy the value under **Client secret** — you will use this in every token request

---

## A4. Create Realm Roles

Left menu → **Realm roles** → **Create role** — create all four:

| Role name | Description |
|-----------|-------------|
| `ADMIN` | Platform administrator |
| `ORGANIZER` | Event organizer |
| `ATTENDEE` | Event attendee |
| `STAFF` | Event staff (door scanner) |

Do this four times.

---

## A5. Create Test Users

For **each user** in the table below, do these steps:

1. Left menu → **Users** → **Add user**
2. Fill in:
   - **Username:** (use the email address)
   - **Email:** (as shown)
   - **First name:** (any name)
   - **Email verified:** Toggle ON
   - **Enabled:** Toggle ON
3. Click **Create**
4. Go to **Credentials** tab → **Set password**:
   - Enter the password
   - **Temporary:** Toggle OFF
   - Click **Save password** → confirm
5. Go to **Role mapping** tab → **Assign role**:
   - Filter by realm roles
   - Select the role → **Assign**

| Username / Email | Password    | Realm Role |
|-----------------|-------------|-----------|
| admin@test.com | Admin123!   | ADMIN |
| organizer@test.com | Organizer1! | ORGANIZER |
| organizer2@test.com | Organizer2! | ORGANIZER |
| staff@test.com | Staff1!     | STAFF |
| attendee@test.com | Attendee1!  | (no Keycloak role — gets ATTENDEE via registration invite) |

> **Password rule:** The `RegisterRequestDto` requires: uppercase + lowercase + digit + special character from `!@#$%^&*`. Always use `Password1!` style passwords, never `Password1`.

---

## A6. Register Users via the API (Creates DB Records)

After creating users in Keycloak, you should also register them via the API so a DB record exists with `approval_status`. Without a DB record, approval status cannot be enforced and behavior becomes desynced/unpredictable across business endpoints.

**Run these POST requests in order:**

```
POST http://localhost:8081/api/v1/auth/register
Content-Type: application/json

{ "email": "organizer@test.com", "password": "Organizer1!", "name": "Test Organizer" }
```
```
{ "email": "organizer2@test.com", "password": "Organizer2!", "name": "Organizer Two" }
```
```
{ "email": "staff@test.com", "password": "Staff1!", "name": "Test Staff" }
```
```
{ "email": "attendee@test.com", "password": "Attendee1!", "name": "Test Attendee" }
```

Each returns 201. All are `PENDING` at this point.

> **admin@test.com:** Do NOT register via API — create this user directly in DB or ensure your `DatabaseInitializer` creates them. The admin user needs `approval_status = APPROVED` to call the approve endpoints.

---

## A7. Approve All Users (Using Admin Token)

**Step 1 — Get admin token:**
```
POST http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=event-ticket-platform-app
client_secret=<YOUR_CLIENT_SECRET>
username=admin@test.com
password=Admin123!
```
Save the `access_token`.

**Step 2 — List pending users:**
```
GET http://localhost:8081/api/v1/admin/approvals/pending?page=0&size=20
Authorization: Bearer <admin_token>
```
Copy each `userId` from the response.

**Step 3 — Approve each user:**
```
POST http://localhost:8081/api/v1/admin/approvals/{userId}/approve
Authorization: Bearer <admin_token>
```
(No request body needed)

---

## A8. Postman Environment Variables

Create an environment in Postman with these variables:

| Variable | Value | Notes |
|----------|-------|-------|
| `base_url` | `http://localhost:8081` | |
| `keycloak_url` | `http://localhost:9090` | |
| `realm` | `event-ticket-platform` | |
| `client_id` | `event-ticket-platform-app` | |
| `client_secret` | `<from Keycloak Credentials tab>` | |
| `admin_token` | *(fill after login)* | |
| `organizer_token` | *(fill after login)* | |
| `organizer2_token` | *(fill after login)* | |
| `attendee_token` | *(fill after login)* | |
| `staff_token` | *(fill after login)* | |
| `event_id` | *(fill as you test)* | |
| `ticket_type_id` | *(fill as you test)* | |
| `ticket_id` | *(fill as you test)* | |
| `discount_id` | *(fill as you test)* | |
| `invite_code_id` | *(fill as you test)* | |
| `user_id` | *(fill as you test)* | |

---

## A9. Get All Tokens

Run this for each user — same URL, different credentials:

```
POST {{keycloak_url}}/realms/{{realm}}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id={{client_id}}
client_secret={{client_secret}}
username=admin@test.com
password=Admin123!
```

| User | Username | Password | Save as |
|------|----------|----------|---------|
| Admin | admin@test.com | Admin123! | `admin_token` |
| Organizer | organizer@test.com | Organizer1! | `organizer_token` |
| Organizer 2 | organizer2@test.com | Organizer2! | `organizer2_token` |
| Attendee | attendee@test.com | Attendee1! | `attendee_token` |
| Staff | staff@test.com | Staff1! | `staff_token` |

**Token expires in 5 minutes by default.** If you get 401, refresh your token.

---

# PART B — ERROR RESPONSE FORMAT

Most API errors return the `ErrorDto` shape below:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Validation failed on 2 field(s). See validationErrors for details.",
  "statusCode": 400,
  "statusDescription": "BAD REQUEST - Validation failed",
  "timestamp": "2026-03-18T10:30:00",
  "path": "/api/v1/auth/register",
  "validationErrors": [
    "email: Email is required",
    "password: Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
  ],
  "possibleCauses": ["Missing required fields"],
  "solutions": ["Fix ALL fields listed in the validationErrors array"]
}
```

Approval-gate rejections (`APPROVAL_PENDING`, `APPROVAL_REJECTED`) are returned directly by `ApprovalGateFilter` and use a compact payload:

```json
{
  "error": "APPROVAL_PENDING",
  "message": "Your account is awaiting approval from an administrator. You will be notified once your account has been reviewed.",
  "status": "403",
  "timestamp": "2026-03-18T10:30:00Z"
}
```

All validation errors are returned **at once** — you never need to fix one field, retry, find the next error.

| HTTP Status | Error Code | When |
|-------------|-----------|------|
| 400 | `VALIDATION_ERROR` | @NotNull, @NotBlank, @Size, @Pattern failed |
| 400 | `CONSTRAINT_VIOLATION` | Constraint validation failed (non-body parameters) |
| 400 | `INVALID_INPUT` | Business rule input error |
| 400 | `INVALID_ARGUMENT` | Event ID mismatch |
| 400 | `INVALID_INVITE_CODE` | Invite expired/redeemed/revoked |
| 400 | `TICKETS_SOLD_OUT` | No tickets left |
| 401 | `AUTHENTICATION_FAILED` | No token / expired token |
| 403 | `ACCESS_DENIED` | Wrong role or resource ownership/authorization failure |
| 403 | `APPROVAL_PENDING` | Account awaiting approval |
| 403 | `APPROVAL_REJECTED` | Account rejected + reason |
| 404 | `EVENT_NOT_FOUND` | Event doesn't exist or you don't own it |
| 404 | `TICKET_NOT_FOUND` | Ticket not found or belongs to other user |
| 404 | `TICKET_TYPE_NOT_FOUND` | — |
| 404 | `USER_NOT_FOUND` | — |
| 404 | `INVITE_CODE_NOT_FOUND` | Code never created |
| 404 | `QR_CODE_NOT_FOUND` | Ticket cancelled, QR deactivated |
| 404 | `DISCOUNT_NOT_FOUND` | — |
| 409 | `EMAIL_ALREADY_REGISTERED` | Duplicate email |
| 409 | `INVALID_APPROVAL_STATE` | Approve/reject wrong state |
| 409 | `BUSINESS_RULE_VIOLATION` | Various business state conflicts |
| 409 | `DISCOUNT_ALREADY_EXISTS` | Second active discount |
| 409 | `TICKET_TYPE_DELETE_NOT_ALLOWED` | Has active sold tickets |
| 409 | `DATA_CONFLICT` | DB unique constraint violation |
| 422 | `REGISTRATION_FAILED` | Keycloak / DB system error |
| 500 | `INTERNAL_SERVER_ERROR` | QR generation, Keycloak admin down |
| 500 | `UNEXPECTED_ERROR` | Unhandled server-side error |

---

# PART C — APPROVAL GATE

After registration every user starts `PENDING`. The `ApprovalGateFilter` blocks PENDING/REJECTED users from ALL endpoints **except**:

| Bypassed path | Reason |
|--------------|--------|
| `POST /api/v1/auth/register` | Must be able to register before approval |
| `POST /api/v1/invites/redeem` | Part of invite workflow |
| `/actuator/**` | Health checks |
| `/swagger-ui/**` | Documentation |
| `/api-docs/**` | Documentation |
| `/v3/api-docs/**` | Documentation |

---

# PART D — ALL 50 ENDPOINTS WITH FULL TEST CASES

---

## GROUP 1 — Authentication (1 endpoint)

---

### EP-01 · POST /api/v1/auth/register

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/auth/register` |
| Auth | None |
| Role | Public |
| Approval gate | Bypassed |
| Content-Type | application/json |

**Request body fields:**

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `email` | String | ✅ | @NotBlank, @Email, max 255 chars |
| `password` | String | ✅ | @NotBlank, min 8, max 128, must have uppercase + lowercase + digit + special char `!@#$%^&*` |
| `name` | String | ✅ | @NotBlank, min 2, max 100 |
| `inviteCode` | String | ❌ Optional | Pattern: `^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$` |

**Test cases:**

| # | Test | Payload | Expected |
|---|------|---------|----------|
| 1 | ✅ No invite (ATTENDEE) | `{"email":"attendee@test.com","password":"Attendee1!","name":"Test Attendee"}` | 201 |
| 2 | ✅ With ORGANIZER invite | Add `"inviteCode":"{{organizer_invite_code}}"` | 201, `assignedRole:"ORGANIZER"` |
| 3 | ✅ Uppercase email normalised | `"email":"New@Test.COM"` | 201, stored as `new@test.com` |
| 4 | ⚠️ name=2 chars (min) | `"name":"Jo"` | 201 |
| 5 | ⚠️ name=100 chars (max) | 100-char string | 201 |
| 6 | ⚠️ password=8 chars (min) | `"password":"Passw0r!"` | 201 |
| 7 | ❌ Empty body | `{}` | 400, all 3 errors at once in `validationErrors` |
| 8 | ❌ Invalid email format | `"email":"notanemail"` | 400 |
| 9 | ❌ Password no uppercase | `"password":"password1!"` | 400 |
| 10 | ❌ Password no digit | `"password":"Password!!"` | 400 |
| 11 | ❌ Password no special char | `"password":"Password1"` | **400 — special char required** |
| 12 | ❌ name=1 char | `"name":"A"` | 400 |
| 13 | ❌ Duplicate email | Register same email twice | 409 `EMAIL_ALREADY_REGISTERED` |
| 14 | ❌ Invite wrong format | `"inviteCode":"abcd-1234"` | 400 |
| 15 | ❌ Invite not in DB | `"inviteCode":"ZZZZ-9999-ZZZZ-9999"` | 404 `INVITE_CODE_NOT_FOUND` |
| 16 | ❌ Invite redeemed | Use a redeemed code | 400 `INVALID_INVITE_CODE` — "already been redeemed" |
| 17 | ❌ Invite expired | Use an expired code | 400 `INVALID_INVITE_CODE` — "expired on..." |

**Success response 201:**
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

## GROUP 2 — Approval Management (4 endpoints)

---

### EP-02 · GET /api/v1/admin/approvals/pending

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/admin/approvals/pending?page=0&size=20` |
| Auth | Bearer `{{admin_token}}` |
| Role | ADMIN |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Admin lists pending | 200, `Page<UserApprovalDto>`, `roles:[]` always empty in list |
| 2 | ❌ ORGANIZER token | 403 |
| 3 | ❌ No token | 401 |

---

### EP-03 · POST /api/v1/admin/approvals/{userId}/approve

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/admin/approvals/{{user_id}}/approve` |
| Auth | Bearer `{{admin_token}}` |
| Body | None |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Approve PENDING user | 200 — user can now log in and use the system |
| 2 | ❌ Already APPROVED | 409 `INVALID_APPROVAL_STATE` |
| 3 | ❌ Already REJECTED | 409 `INVALID_APPROVAL_STATE` |
| 4 | ❌ User not found | 404 `USER_NOT_FOUND` |
| 5 | ❌ ORGANIZER token | 403 |

**Success response 200:**
```json
{
  "message": "User approved successfully",
  "userId": "uuid",
  "status": "APPROVED"
}
```

---

### EP-04 · POST /api/v1/admin/approvals/{userId}/reject

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/admin/approvals/{{user_id}}/reject` |
| Auth | Bearer `{{admin_token}}` |
| Content-Type | application/json |

| # | Test | Payload | Expected |
|---|------|---------|----------|
| 1 | ✅ Valid reason | `{"reason":"Account violates terms."}` | 200 |
| 2 | ⚠️ reason=10 chars (min) | `{"reason":"Duplicate."}` | 200 |
| 3 | ❌ reason=9 chars | `{"reason":"Too short"}` | 400 |
| 4 | ❌ Empty body | `{}` | 400 |
| 5 | ❌ Whitespace reason | `{"reason":"   "}` | 400 |
| 6 | ❌ Already REJECTED | — | 409 |

**Success response 200:**
```json
{
  "message": "User rejected successfully",
  "userId": "uuid",
  "status": "REJECTED",
  "reason": "Account violates terms."
}
```

---

### EP-05 · GET /api/v1/admin/approvals

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/admin/approvals?page=0&size=20` |
| Auth | Bearer `{{admin_token}}` |

✅ 200 — all users all statuses | ❌ ORGANIZER → 403

---

## GROUP 3 — Admin Role Management (4 endpoints)

---

### EP-06 · POST /api/v1/admin/users/{userId}/roles

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/admin/users/{{user_id}}/roles` |
| Auth | Bearer `{{admin_token}}` |
| Content-Type | application/json |

| # | Test | Payload | Expected |
|---|------|---------|----------|
| 1 | ✅ Assign ATTENDEE | `{"roleName":"ATTENDEE"}` | 200 with updated `roles` list |
| 2 | ✅ Assign ORGANIZER | `{"roleName":"ORGANIZER"}` | 200 |
| 3 | ✅ Assign STAFF | `{"roleName":"STAFF"}` | 200 |
| 4 | ✅ Assign ADMIN | `{"roleName":"ADMIN"}` | 200 |
| 5 | ❌ Invalid role | `{"roleName":"SUPERUSER"}` | 400 |
| 6 | ❌ Empty body | `{}` | 400 |
| 7 | ❌ ORGANIZER token | — | 403 |

**Success response 200:**
```json
{
  "userId": "uuid",
  "userName": "Test User",
  "email": "test@test.com",
  "roles": ["ORGANIZER"]
}
```

---

### EP-07 · DELETE /api/v1/admin/users/{userId}/roles/{roleName}

| Property | Value |
|----------|-------|
| Method | DELETE |
| URL | `{{base_url}}/api/v1/admin/users/{{user_id}}/roles/STAFF` |
| Auth | Bearer `{{admin_token}}` |
| Body | None |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Revoke existing role | 200 with updated `roles` list |
| 2 | ❌ ORGANIZER token | 403 |

---

### EP-08 · GET /api/v1/admin/users/{userId}/roles

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/admin/users/{{user_id}}/roles` |
| Auth | Bearer `{{admin_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Get user roles | 200, `UserRolesResponseDto` |
| 2 | ❌ User not found | 404 |
| 3 | ❌ ORGANIZER token | 403 |

**Success response 200:**
```json
{
  "userId": "uuid",
  "userName": "Test User",
  "email": "user@test.com",
  "roles": ["ATTENDEE"]
}
```

---

### EP-09 · GET /api/v1/admin/roles

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/admin/roles` |
| Auth | Bearer `{{admin_token}}` |

✅ **Expected 200:**
```json
{
  "roles": ["ADMIN", "ORGANIZER", "ATTENDEE", "STAFF"],
  "message": "Available roles in the system"
}
```
❌ ORGANIZER token → 403

---

## GROUP 4 — Invite Codes (5 endpoints)

---

### EP-10 · POST /api/v1/invites

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/invites` |
| Auth | Bearer `{{admin_token}}` or `{{organizer_token}}` |
| Content-Type | application/json |

**Request body fields:**

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `roleName` | String | ✅ | Must be ADMIN, ORGANIZER, ATTENDEE, or STAFF |
| `expirationHours` | Integer | ✅ | @NotNull, @Positive (must be > 0) |
| `eventId` | UUID | Conditional | Required when `roleName = STAFF`, forbidden for all others |

| # | Test | Payload | Token | Expected |
|---|------|---------|-------|----------|
| 1 | ✅ ADMIN creates ATTENDEE | `{"roleName":"ATTENDEE","expirationHours":24}` | admin | 201 |
| 2 | ✅ ADMIN creates ORGANIZER | `{"roleName":"ORGANIZER","expirationHours":72}` | admin | 201 |
| 3 | ✅ ADMIN creates ADMIN | `{"roleName":"ADMIN","expirationHours":24}` | admin | 201 |
| 4 | ✅ ORGANIZER creates STAFF | `{"roleName":"STAFF","eventId":"{{event_id}}","expirationHours":48}` | organizer | 201 |
| 5 | ⚠️ expirationHours=1 (min) | `{"roleName":"ATTENDEE","expirationHours":1}` | admin | 201 |
| 6 | ❌ expirationHours=0 | `{"roleName":"ATTENDEE","expirationHours":0}` | admin | 400 |
| 7 | ❌ Missing roleName | `{"expirationHours":24}` | admin | 400 |
| 8 | ❌ Invalid roleName | `{"roleName":"BOSS","expirationHours":24}` | admin | 400 |
| 9 | ❌ STAFF without eventId | `{"roleName":"STAFF","expirationHours":24}` | admin | 400 — "Event ID is required" |
| 10 | ❌ Non-STAFF with eventId | `{"roleName":"ATTENDEE","eventId":"{{event_id}}","expirationHours":24}` | admin | 400 — "should only be provided for STAFF" |
| 11 | ❌ ORGANIZER creates ORGANIZER | `{"roleName":"ORGANIZER","expirationHours":24}` | organizer | 400 — "can only create STAFF invites" |
| 12 | ❌ ORGANIZER creates ADMIN | `{"roleName":"ADMIN","expirationHours":24}` | organizer | 400 — "Only ADMINs can create ADMIN role invites" |
| 13 | ❌ ORGANIZER, not own event | STAFF invite for another org's event | organizer | 403 |
| 14 | ❌ ATTENDEE token | — | attendee | 403 |

**Success response 201:**
```json
{
  "id": "uuid",
  "code": "ABCD-1234-EFGH-5678",
  "roleName": "ATTENDEE",
  "eventId": null,
  "eventName": null,
  "status": "PENDING",
  "createdBy": "admin@test.com",
  "createdAt": "2026-03-18T10:00:00",
  "expiresAt": "2026-03-19T10:00:00",
  "redeemedBy": null,
  "redeemedAt": null
}
```

---

### EP-11 · POST /api/v1/invites/redeem

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/invites/redeem` |
| Auth | Bearer (any, including PENDING user) |
| Approval gate | **BYPASSED** |
| Content-Type | application/json |

| # | Test | Payload | Expected |
|---|------|---------|----------|
| 1 | ✅ Valid code | `{"code":"{{valid_invite_code}}"}` | 200 |
| 2 | ✅ PENDING user can redeem | Use PENDING user token | 200 — bypass works |
| 3 | ❌ Empty body | `{}` | 400 |
| 4 | ❌ Blank code | `{"code":""}` | 400 |
| 5 | ❌ Not in DB | `{"code":"ZZZZ-9999-ZZZZ-9999"}` | 404 `INVITE_CODE_NOT_FOUND` |
| 6 | ❌ Already redeemed | redeemed code | 400 `INVALID_INVITE_CODE` — "already been redeemed by..." |
| 7 | ❌ Expired code | expired code | 400 `INVALID_INVITE_CODE` — "expired on..." |
| 8 | ❌ Revoked code | revoked code | 400 `INVALID_INVITE_CODE` — "has been revoked. Reason:..." |

**Success response 200:**
```json
{
  "message": "Invite code redeemed successfully",
  "roleAssigned": "STAFF",
  "eventName": "Tech Conference 2025",
  "currentRoles": ["STAFF"]
}
```

---

### EP-12 · DELETE /api/v1/invites/{codeId}

| Property | Value |
|----------|-------|
| Method | DELETE |
| URL | `{{base_url}}/api/v1/invites/{{invite_code_id}}?reason=No+longer+needed` |
| Auth | Bearer `{{admin_token}}` or `{{organizer_token}}` |
| Query param | `reason` (optional, default: "Revoked by creator") |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Creator revokes own code | 204 No Content |
| 2 | ✅ With reason param | `?reason=Event+cancelled` | 204 |
| 3 | ✅ ADMIN revokes any code | 204 |
| 4 | ❌ Code is REDEEMED | 400 — "Cannot revoke: current status is REDEEMED" |
| 5 | ❌ ORGANIZER revoking other's code | 403 |
| 6 | ❌ Code not found | 404 |

---

### EP-13 · GET /api/v1/invites

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/invites?page=0&size=20&sort=createdAt,desc` |
| Auth | Bearer `{{admin_token}}` or `{{organizer_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ ADMIN | 200 — sees ALL invite codes |
| 2 | ✅ ORGANIZER | 200 — sees only OWN invite codes |
| 3 | ❌ ATTENDEE | 403 |

---

### EP-14 · GET /api/v1/invites/events/{eventId}

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/invites/events/{{event_id}}?page=0&size=20` |
| Auth | Bearer `{{admin_token}}` or `{{organizer_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ ADMIN — any event | 200 |
| 2 | ✅ ORGANIZER — own event | 200 |
| 3 | ❌ ORGANIZER — other's event | 403 |

---

## GROUP 5 — Event Management (8 endpoints)

---

### EP-15 · POST /api/v1/events

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/events` |
| Auth | Bearer `{{organizer_token}}` |
| Role | ORGANIZER |
| Content-Type | application/json |

**Request body fields:**

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `name` | String | ✅ | @NotBlank, max 200 |
| `venue` | String | ✅ | @NotBlank, max 500 |
| `status` | String | ✅ | @NotNull — `DRAFT`, `PUBLISHED`, `CANCELLED`, or `COMPLETED` |
| `ticketTypes` | Array | ✅ | @NotEmpty, min 1 element |
| `ticketTypes[].name` | String | ✅ | @NotBlank |
| `ticketTypes[].price` | Decimal | ✅ | @NotNull, @DecimalMin("0.00") |
| `ticketTypes[].totalAvailable` | Integer | ✅ | @NotNull, @Min(1) |
| `ticketTypes[].description` | String | ❌ | — |
| `start` | DateTime | ❌ | Format: `YYYY-MM-DDTHH:mm:ss` |
| `end` | DateTime | ❌ | Must be after `start` if both provided |
| `salesStart` | DateTime | ❌ | — |
| `salesEnd` | DateTime | ❌ | Must be after `salesStart` if both provided |
| `maxCapacity` | Integer | ❌ | @Min(1) if provided |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Minimum valid | 201 — save `event_id` and `ticket_type_id` |
| 2 | ✅ All optional fields | 201 |
| 3 | ✅ Free event `price:0.00` | 201 |
| 4 | ✅ DRAFT status | 201 |
| 5 | ⚠️ totalAvailable=1 (min) | 201 |
| 6 | ❌ Missing name | 400 |
| 7 | ❌ Missing venue | 400 |
| 8 | ❌ Missing status | 400 |
| 9 | ❌ Invalid status `"OPEN"` | 400 |
| 10 | ❌ Empty ticketTypes `[]` | 400 |
| 11 | ❌ totalAvailable=0 | 400 |
| 12 | ❌ price=-0.01 | 400 |
| 13 | ❌ end before start | 409 — "Event end date must be after start date" |
| 14 | ❌ salesEnd before salesStart | 409 |
| 15 | ❌ ATTENDEE token | 403 |

**Minimum valid payload:**
```json
{
  "name": "Tech Conference 2025",
  "venue": "Convention Center",
  "status": "PUBLISHED",
  "ticketTypes": [
    { "name": "General", "price": 199.99, "totalAvailable": 100 }
  ]
}
```

**Full payload:**
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
    { "name": "Regular", "price": 199.99, "description": "Standard", "totalAvailable": 400 }
  ]
}
```

**Success response 201:**
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
    { "id": "uuid", "name": "Early Bird", "price": 149.99, "description": "Limited slots", "totalAvailable": 100, "createdAt": "...", "updatedAt": "..." }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

---

### EP-16 · PUT /api/v1/events/{eventId}

| Property | Value |
|----------|-------|
| Method | PUT |
| URL | `{{base_url}}/api/v1/events/{{event_id}}` |
| Auth | Bearer `{{organizer_token}}` |

> ⚠️ **Critical:** When updating existing ticket types, include their `id` field. Omitting `id` creates a NEW ticket type instead of updating the existing one.

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Update with existing ticket type id | 200 |
| 2 | ✅ Add new ticket type (no id in element) | 200, new UUID assigned |
| 3 | ✅ Cancel event | `"status":"CANCELLED"` | 200, all PURCHASED tickets auto-cancelled |
| 4 | ❌ Body id ≠ URL id | 400 `INVALID_ARGUMENT` |
| 5 | ❌ Re-publish CANCELLED event | 409 — "Cannot modify a cancelled event" |
| 6 | ❌ maxCapacity below sold count | 409 |
| 7 | ❌ Not owner | 404 |

---

### EP-17 · GET /api/v1/events

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/events?page=0&size=20&sort=start,desc` |
| Auth | Bearer `{{organizer_token}}` |

✅ 200 — returns only **your** events | ❌ ATTENDEE → 403

---

### EP-18 · GET /api/v1/events/{eventId}

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/events/{{event_id}}` |
| Auth | Bearer `{{organizer_token}}` |

✅ Own event → 200 | ❌ Another org's event → **404** (not 403 — ownership is hidden)

---

### EP-19 · DELETE /api/v1/events/{eventId}

| Property | Value |
|----------|-------|
| Method | DELETE |
| URL | `{{base_url}}/api/v1/events/{{event_id}}` |
| Auth | Bearer `{{organizer_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ No active tickets | 204 |
| 2 | ❌ Has active tickets | 409 — "Cannot delete event... N active ticket(s) exist. Cancel the event first." |

---

### EP-20 · GET /api/v1/events/{eventId}/sales-dashboard

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/sales-dashboard` |
| Auth | Bearer `{{organizer_token}}` |

✅ **Success response 200:**
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
Note: `remaining: null` when `totalAvailable` is null (unlimited tickets)

---

### EP-21 · GET /api/v1/events/{eventId}/attendees-report

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/attendees-report` |
| Auth | Bearer `{{organizer_token}}` |

✅ **Success response 200:**
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
      "validationCount": 1
    }
  ]
}
```

---

### EP-22 · GET /api/v1/events/{eventId}/sales-report.xlsx

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/sales-report.xlsx` |
| Auth | Bearer `{{organizer_token}}` |

✅ **Expected 200:**
- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="tech_conference_2025_sales_report_20261101_120000.xlsx"`
- Body: binary Excel file

---

## GROUP 6 — Published Events (2 endpoints)

---

### EP-23 · GET /api/v1/published-events

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/published-events?page=0&size=20&sort=start,asc` |
| Auth | Bearer `{{attendee_token}}` |
| Role | ATTENDEE, ORGANIZER, or STAFF |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ ATTENDEE — list all | 200 |
| 2 | ✅ With search `?q=tech` | 200, filtered by name |
| 3 | ❌ ADMIN token | 403 — ADMIN not allowed here |
| 4 | ❌ No token | 401 |

---

### EP-24 · GET /api/v1/published-events/{eventId}

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/published-events/{{event_id}}` |
| Auth | Bearer `{{attendee_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ PUBLISHED event | 200 with ticket types and prices |
| 2 | ❌ DRAFT event | 404 |
| 3 | ❌ CANCELLED event | 404 |

---

## GROUP 7 — Ticket Types (5 endpoints)

---

### EP-25 · POST /api/v1/events/{eventId}/ticket-types

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types` |
| Auth | Bearer `{{organizer_token}}` |
| Content-Type | application/json |

**Request body:**

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `name` | String | ✅ | @NotBlank |
| `price` | Decimal | ✅ | @NotNull, @DecimalMin("0.00") |
| `totalAvailable` | Integer | ✅ | @NotNull, @Min(1) |
| `description` | String | ❌ | — |

| # | Test | Payload | Expected |
|---|------|---------|----------|
| 1 | ✅ Valid | `{"name":"VIP","price":499.99,"totalAvailable":50}` | 201 |
| 2 | ✅ Free `price:0.00` | — | 201 |
| 3 | ❌ Missing name | — | 400 |
| 4 | ❌ totalAvailable=0 | — | 400 |
| 5 | ❌ price=-0.01 | — | 400 |

**Success response 201:**
```json
{
  "id": "uuid",
  "name": "VIP",
  "price": 499.99,
  "description": null,
  "totalAvailable": 50,
  "createdAt": "...",
  "updatedAt": "..."
}
```

---

### EP-26 · GET /api/v1/events/{eventId}/ticket-types

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types` |
| Auth | Bearer `{{organizer_token}}` |

✅ 200 — `List<CreateTicketTypeResponseDto>` | ❌ ATTENDEE → 403

---

### EP-27 · GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}` |
| Auth | Bearer `{{organizer_token}}` |

✅ 200 — `CreateTicketTypeResponseDto` | ❌ Wrong event → 404

---

### EP-28 · PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

| Property | Value |
|----------|-------|
| Method | PUT |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}` |
| Auth | Bearer `{{organizer_token}}` |
| Content-Type | application/json |

**Request body:** same fields as create, all optional except `name` and `price`.

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Update name and price | 200 |
| 2 | ✅ Raise totalAvailable | 200 |
| 3 | ❌ Lower totalAvailable below sold | 409 — "Cannot set totalAvailable to X — Y already sold" |

**Success response 200:**
```json
{
  "id": "uuid",
  "name": "VIP Updated",
  "price": 549.99,
  "description": "Premium access",
  "totalAvailable": 100,
  "createdAt": "...",
  "updatedAt": "..."
}
```

---

### EP-29 · DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

| Property | Value |
|----------|-------|
| Method | DELETE |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}` |
| Auth | Bearer `{{organizer_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ No sold tickets | 204 |
| 2 | ❌ Has active sold tickets | 409 `TICKET_TYPE_DELETE_NOT_ALLOWED` |

---

## GROUP 8 — Ticket Purchase (1 endpoint)

---

### EP-30 · POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/tickets` |
| Auth | Bearer `{{attendee_token}}` |
| Role | ATTENDEE or ORGANIZER |
| Content-Type | application/json |

**Request body:**

| Field | Type | Required | Default | Validation |
|-------|------|----------|---------|-----------|
| `quantity` | Integer | ❌ | 1 | @Min(1), @Max(10) |

| # | Test | Payload | Expected |
|---|------|---------|----------|
| 1 | ✅ Default | `{}` | 201, array of 1 ticket — save `ticket_id` |
| 2 | ✅ quantity=2 | `{"quantity":2}` | 201, 2 tickets |
| 3 | ⚠️ quantity=1 (min) | — | 201 |
| 4 | ⚠️ quantity=10 (max) | — | 201 |
| 5 | ✅ With active discount | Buy after creating 20% discount | 201, `discountApplied > 0` |
| 6 | ✅ ORGANIZER buys own event | organizer token | 201 — audit `ORGANIZER_SELF_PURCHASE` |
| 7 | ❌ quantity=0 | — | 400 |
| 8 | ❌ quantity=11 | — | 400 |
| 9 | ❌ DRAFT event | — | 409 — "not open for sales" |
| 10 | ❌ CANCELLED event | — | 409 — "has been cancelled" |
| 11 | ❌ Before salesStart | — | 409 — "Sales have not started yet. Sales open at..." |
| 12 | ❌ After salesEnd | — | 409 — "Sales have closed. Sales ended at..." |
| 13 | ❌ Type sold out | totalAvailable=2, buy 2, try 1 more | 400 `TICKETS_SOLD_OUT` |
| 14 | ❌ Per-user limit | Buy 10, try 1 more | 409 — "Purchase limit reached. You already own 10..." |
| 15 | ❌ STAFF token | — | 403 |

**Success response 201:**
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

---

## GROUP 9 — Ticket Viewing & QR Codes (6 endpoints)

---

### EP-31 · GET /api/v1/tickets

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/tickets?page=0&size=20&sort=id,desc` |
| Auth | Bearer `{{attendee_token}}` |
| Role | ATTENDEE or ORGANIZER |

✅ 200 — only **your** tickets | ❌ STAFF → 403

**Response item shape:**
```json
{
  "id": "uuid",
  "status": "PURCHASED",
  "ticketType": { "id": "uuid", "name": "Early Bird", "price": 149.99 }
}
```

---

### EP-32 · GET /api/v1/tickets/{ticketId}

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/tickets/{{ticket_id}}` |
| Auth | Bearer `{{attendee_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Own ticket | 200 — full `GetTicketResponseDto` |
| 2 | ❌ Another user's ticket | 404 |
| 3 | ❌ STAFF token | 403 |

---

### EP-33 · GET /api/v1/tickets/{ticketId}/qr-codes *(Legacy)*

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes` |
| Auth | Bearer `{{attendee_token}}` |

✅ 200 — `Content-Type: image/png` — binary bytes (base64-decoded stored QR value)

---

### EP-34 · GET /api/v1/tickets/{ticketId}/qr-codes/view

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/view` |
| Auth | Bearer `{{attendee_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Own ticket | 200, `Content-Type: image/png`, `Cache-Control: max-age=300, private` |
| 2 | ❌ Other user's ticket | 403 |
| 3 | ❌ Cancelled ticket | 404 `QR_CODE_NOT_FOUND` |

---

### EP-35 · GET /api/v1/tickets/{ticketId}/qr-codes/png

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/png` |
| Auth | Bearer `{{attendee_token}}` |

✅ 200 — `Content-Type: image/png`, `Content-Disposition: attachment; filename="eventname_tickettype_username_ticketid.png"`

---

### EP-36 · GET /api/v1/tickets/{ticketId}/qr-codes/pdf

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/pdf` |
| Auth | Bearer `{{attendee_token}}` |

✅ 200 — `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="....pdf"`

---

## GROUP 10 — Discounts (5 endpoints)

**Base path:** `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts`

---

### EP-37 · POST .../discounts

| Property | Value |
|----------|-------|
| Method | POST |
| Auth | Bearer `{{organizer_token}}` |
| Role | ORGANIZER (must own event) |

**Request body fields:**

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `discountType` | String | ✅ | `PERCENTAGE` or `FIXED_AMOUNT` |
| `value` | Decimal | ✅ | >0. If PERCENTAGE: ≤100. If FIXED_AMOUNT: >0 |
| `validFrom` | DateTime | ✅ | Must be in the future (for new discounts) |
| `validTo` | DateTime | ✅ | Must be after `validFrom` |
| `active` | Boolean | ❌ | Default true |
| `description` | String | ❌ | — |

| # | Test | Payload | Expected |
|---|------|---------|----------|
| 1 | ✅ PERCENTAGE 20% | `{"discountType":"PERCENTAGE","value":20.0,"validFrom":"2025-11-01T00:00:00","validTo":"2025-11-30T23:59:59","active":true}` | 201 |
| 2 | ✅ FIXED_AMOUNT $50 | `{"discountType":"FIXED_AMOUNT","value":50.00,"validFrom":"...","validTo":"...","active":true}` | 201 |
| 3 | ✅ PERCENTAGE 100% | value=100.0 | 201 — purchase gives `pricePaid: 0.00` |
| 4 | ✅ FIXED > ticket price | value=9999 | 201 — purchase gives `pricePaid: 0.00` (clamped) |
| 5 | ✅ active=false | — | 201 — purchase at full price |
| 6 | ❌ Second active for same type | — | 409 `DISCOUNT_ALREADY_EXISTS` |
| 7 | ❌ validFrom in past | — | 400 — "must be in the future" |
| 8 | ❌ value=0 | — | 400 |
| 9 | ❌ PERCENTAGE value=100.01 | — | 400 |
| 10 | ❌ validTo before validFrom | — | 400 |

**Success response 201:**
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
  "description": null,
  "createdAt": "...",
  "updatedAt": "..."
}
```

---

### EP-38 · PUT .../discounts/{discountId}

| Property | Value |
|----------|-------|
| Method | PUT |
| Auth | Bearer `{{organizer_token}}` |

Same payload as create. `validFrom` in past is allowed on update (period may have started).

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Update description | 200 |
| 2 | ✅ Deactivate `active:false` | 200 |
| 3 | ❌ Change type/value after tickets sold | 400 — "Cannot change discount type or value — N active ticket(s) sold" |
| 4 | ❌ Re-activate when another active exists | 409 `DISCOUNT_ALREADY_EXISTS` |

---

### EP-39 · DELETE .../discounts/{discountId}

✅ 204 | ❌ Not owner → 403

---

### EP-40 · GET .../discounts/{discountId}

✅ 200 — `DiscountResponseDto` | ❌ Not found → 404 `DISCOUNT_NOT_FOUND`

---

### EP-41 · GET .../discounts

✅ 200 — `List<DiscountResponseDto>` (all discounts for this ticket type, active and inactive)

---

## GROUP 11 — Ticket Validation (3 endpoints)

---

### EP-42 · POST /api/v1/ticket-validations

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/ticket-validations` |
| Auth | Bearer `{{staff_token}}` or `{{organizer_token}}` |
| Role | STAFF or ORGANIZER |
| Content-Type | application/json |

**Request body fields:**

| Field | Type | Required | Validation |
|-------|------|----------|-----------|
| `id` | UUID | ✅ | @NotNull — ticket UUID (MANUAL) or QR code UUID (QR_SCAN) |
| `method` | Enum | ✅ | @NotNull — exactly `MANUAL` or `QR_SCAN` |

> **MANUAL:** `id` = ticket UUID
> **QR_SCAN:** `id` = QR code UUID (from the QR image — NOT the ticket UUID)

| # | Test | Payload | Expected |
|---|------|---------|----------|
| 1 | ✅ MANUAL first scan | `{"id":"{{ticket_id}}","method":"MANUAL"}` | 200, `status:"VALID"` |
| 2 | ✅ QR_SCAN first scan | `{"id":"{{qr_code_id}}","method":"QR_SCAN"}` | 200, `status:"VALID"` |
| 3 | ✅ Second scan same ticket | Same payload again | 200, `status:"INVALID"` — NOT an error, 200 OK |
| 4 | ❌ Empty body | `{}` | 400 — both fields listed in `validationErrors` |
| 5 | ❌ method="QR_CODE" | — | 400 — wrong enum value |
| 6 | ❌ method="SCAN" | — | 400 |
| 7 | ❌ CANCELLED ticket | — | 409 — "has been cancelled and cannot be validated" |
| 8 | ❌ Ticket not found | random UUID | 404 |
| 9 | ❌ ATTENDEE token | — | 403 |
| 10 | ❌ STAFF not assigned to this event | — | 403 |

**Success response 200:**
```json
{
  "ticketId": "uuid",
  "status": "VALID",
  "validatedById": "uuid",
  "validatedByName": "Test Staff",
  "validatedAt": "2025-12-15T10:23:45"
}
```

---

### EP-43 · GET /api/v1/ticket-validations/events/{eventId}

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/ticket-validations/events/{{event_id}}?page=0&size=20` |
| Auth | Bearer `{{staff_token}}` or `{{organizer_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ STAFF assigned to event | 200 — `Page<TicketValidationResponseDto>` |
| 2 | ✅ ORGANIZER owns event | 200 |
| 3 | ❌ STAFF not assigned | 403 |
| 4 | ❌ ATTENDEE | 403 |

---

### EP-44 · GET /api/v1/ticket-validations/tickets/{ticketId}

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/ticket-validations/tickets/{{ticket_id}}` |
| Auth | Bearer `{{staff_token}}` or `{{organizer_token}}` |

✅ 200 — `List<TicketValidationResponseDto>` (all scans for this ticket) | ❌ ATTENDEE → 403

---

## GROUP 12 — Event Staff Management (3 endpoints)

---

### EP-45 · POST /api/v1/events/{eventId}/staff

| Property | Value |
|----------|-------|
| Method | POST |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/staff` |
| Auth | Bearer `{{organizer_token}}` |
| Content-Type | application/json |

**Body:** `{"userId": "{{staff_user_id}}"}`

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ User has STAFF Keycloak role | 201 — `EventStaffResponseDto` with staff list |
| 2 | ❌ User has no STAFF role | 409 — "does not have STAFF role. STAFF role must be assigned by ADMIN first." |
| 3 | ❌ User already assigned | 409 — "already assigned as staff" |
| 4 | ❌ Empty body | 400 |
| 5 | ❌ Not owner | 403 |

**Success response 201:**
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

---

### EP-46 · DELETE /api/v1/events/{eventId}/staff/{userId}

| Property | Value |
|----------|-------|
| Method | DELETE |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/staff/{{staff_user_id}}` |
| Auth | Bearer `{{organizer_token}}` |

| # | Test | Expected |
|---|------|----------|
| 1 | ✅ Assigned user | 200 — updated `EventStaffResponseDto` |
| 2 | ❌ User not assigned | 409 |
| 3 | ❌ Not owner | 403 |

---

### EP-47 · GET /api/v1/events/{eventId}/staff

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/events/{{event_id}}/staff` |
| Auth | Bearer `{{organizer_token}}` |

✅ 200 — `EventStaffResponseDto` | ❌ ATTENDEE → 403

---

## GROUP 13 — Audit Logs (3 endpoints)

---

### EP-48 · GET /api/v1/audit

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/audit?page=0&size=20&sort=createdAt,desc` |
| Auth | Bearer `{{admin_token}}` |

✅ 200 — `Page<AuditLogDto>` with `userAgent` field | ❌ ORGANIZER → 403

---

### EP-49 · GET /api/v1/audit/events/{eventId}

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/audit/events/{{event_id}}?page=0&size=20` |
| Auth | Bearer `{{organizer_token}}` |

✅ Own event → 200 | ❌ Other org's event → 403

---

### EP-50 · GET /api/v1/audit/me

| Property | Value |
|----------|-------|
| Method | GET |
| URL | `{{base_url}}/api/v1/audit/me?page=0&size=20` |
| Auth | Bearer (any approved user) |

✅ 200 — your own audit trail (all roles)

---

# PART E — SECURITY TEST MATRIX

Run all 16. All must return the indicated response.

| # | Token | Method | URL | Expected |
|---|-------|--------|-----|----------|
| 1 | None | GET | /api/v1/published-events | 401 `AUTHENTICATION_FAILED` |
| 2 | `"Bearer garbage"` | GET | /api/v1/published-events | 401 |
| 3 | PENDING | GET | /api/v1/published-events | 403 `APPROVAL_PENDING` |
| 4 | PENDING | POST | /api/v1/auth/register | 201 ✅ bypass works |
| 5 | PENDING | POST | /api/v1/invites/redeem | 200 ✅ bypass works |
| 6 | REJECTED | GET | /api/v1/published-events | 403 `APPROVAL_REJECTED` + reason in message |
| 7 | ATTENDEE | GET | /api/v1/events | 403 |
| 8 | ATTENDEE | GET | /api/v1/admin/roles | 403 |
| 9 | ATTENDEE | POST | /api/v1/ticket-validations | 403 |
| 10 | STAFF | GET | /api/v1/tickets | 403 |
| 11 | STAFF | POST | /api/v1/events/{id}/ticket-types/{id}/tickets | 403 |
| 12 | ORGANIZER | GET | /api/v1/admin/approvals | 403 |
| 13 | ADMIN | GET | /api/v1/published-events | 403 |
| 14 | ADMIN | GET | /api/v1/tickets | 403 |
| 15 | Organizer B | GET | /api/v1/events/{organizer-A-event} | 404 (not 403) |
| 16 | ATTENDEE | GET | /api/v1/tickets/{id}/qr-codes/view | Check `Cache-Control: max-age=300, private` not `public` |

---

# PART F — END-TO-END HAPPY PATH (Run in Order)

This proves the entire system works together. Each step depends on the previous.

```
Step  1: POST /api/v1/auth/register                                    → 201 — new attendee (PENDING)
Step  2: POST /api/v1/admin/approvals/{userId}/approve (admin)          → 200 — approve them
Step  3: Get attendee token from Keycloak
Step  4: POST /api/v1/events (organizer)                               → 201 — save event_id, ticket_type_id
Step  5: POST /api/v1/invites (admin — STAFF for that event)           → 201 — save code
Step  6: POST /api/v1/invites/redeem (staff user with pending status)  → 200 — STAFF role assigned
Step  7: POST /api/v1/admin/approvals/{staffId}/approve (admin)         → 200
Step  8: POST /api/v1/events/{id}/staff (organizer, userId=staff)      → 201
Step  9: POST .../discounts (organizer — 20% PERCENTAGE, active=true)  → 201 — save discount_id
Step 10: POST .../ticket-types/{id}/tickets (attendee, quantity=2)     → 201 — save ticket_id
         Verify: discountApplied=29.998, pricePaid < originalPrice
Step 11: GET /api/v1/tickets/{ticket_id} (attendee)                   → 200
Step 12: GET /api/v1/tickets/{ticket_id}/qr-codes/png (attendee)      → 200 PNG file
Step 13: POST /api/v1/ticket-validations (staff, MANUAL, ticket_id)   → 200 status:"VALID"
         Verify: validatedByName populated
Step 14: POST /api/v1/ticket-validations (same ticket, same staff)    → 200 status:"INVALID" (not an error)
Step 15: GET /api/v1/ticket-validations/events/{event_id} (staff)     → 200, see both validation records
Step 16: GET /events/{id}/attendees-report (organizer)                → 200, validationCount=2 for that ticket
Step 17: GET /events/{id}/sales-dashboard (organizer)                 → verify revenue and discounts
Step 18: GET /events/{id}/sales-report.xlsx (organizer)               → 200 .xlsx downloads
Step 19: PUT /events/{id} with status:CANCELLED (organizer)           → 200
Step 20: POST /ticket-validations (staff, same cancelled ticket)      → 409 — "cancelled and cannot be validated"
Step 21: GET /events/{id}/sales-dashboard (organizer)                 → totalTicketsSold:0, all revenue:0
Step 22: DELETE /events/{id} (organizer)                              → 409 — still has tickets
Step 23: GET /api/v1/audit/me (attendee)                              → all your actions visible
Step 24: GET /api/v1/audit/events/{id} (organizer)                   → all event actions visible
```

---

# PART G — FIX VERIFICATION TESTS

These tests prove every bug fix from the audit works correctly.

| # | Fix Tested | Steps | Expected |
|---|-----------|-------|----------|
| F1 | Live event update (isCreate flag) | Create event with past salesStart, then PUT to update venue | 200 — update succeeds, not blocked |
| F2 | VALIDATED status written | Validate ticket once, GET ticket, check status | `"status":"VALIDATED"` not `"PURCHASED"` |
| F3 | Invite expiry race condition | Create invite, let scheduler expire it, try to redeem | 400 "expired on..." |
| F4 | Email race condition | Register same email twice in quick succession | Second gets 409 EMAIL_ALREADY_REGISTERED (not 500) |
| F5 | Discount post-sales guard | Create discount, buy tickets, try PUT to change discountType | 400 — "Cannot change discount type or value" |
| F6 | Expired discount unblocks | Create discount, expire it (`active:false`), create new | 201 — not blocked by inactive old one |
| F7 | Per-user ticket limit | Buy 10 tickets for same type, try 11th | 409 — "Purchase limit reached. You already own 10" |
| F8 | QR UUID correct | Download PNG, extract UUID from QR image, validate with QR_SCAN | 200 status:"VALID" (not 404) |
| F9 | Excel null totalAvailable | Create event with no `totalAvailable` cap, GET sales dashboard | `remaining: null` (not 500 NPE) |
| F10 | Approval gate bypass | PENDING user → POST /auth/register | 201 (not 403) |
| F11 | jakarta→spring transaction | Any service operation that fails mid-transaction | DB is fully rolled back |

---

# PART H — COMMON MISTAKES

| Mistake | Error You See | Fix |
|---------|--------------|-----|
| `Password1` (no special char) | 400 — "must contain special character `!@#$%^&*`" | Use `Password1!` |
| Forgot to approve user | 403 `APPROVAL_PENDING` | POST /admin/approvals/{id}/approve |
| Wrong token for endpoint | 403 `ACCESS_DENIED` | Check which token you're sending |
| PUT event without ticket type id | May create a new ticket type unintentionally; name conflicts can return 409 `DATA_CONFLICT` | Always include `"id":"uuid"` for existing ticket types |
| `method:"QR_CODE"` in validation | 400 | Use `"method":"QR_SCAN"` |
| Token from `localhost:8081` | 401 | Tokens come from `localhost:9090` |
| Other org's event | 404 (not 403) | Use your own organizer's event |
| Creating discount with past validFrom | 400 | New discounts must start in the future |
| Assign staff before ADMIN gives STAFF role | 409 | POST /admin/users/{id}/roles with `"roleName":"STAFF"` first |
| Expired token | 401 | Get a fresh token — expires in 5 minutes by default |
| ADMIN tries to access published events | 403 | ADMIN role is not allowed on /published-events — use ATTENDEE token |

---

# PART I — PAGINATION

All list endpoints support Spring Pageable:

```
?page=0&size=20&sort=createdAt,desc
?page=1&size=10&sort=name,asc
?page=0&size=50          ← max 50 per page (server enforces)
```

Response shape for paginated endpoints:
```json
{
  "content": ["..."],
  "pageable": { "pageNumber": 0, "pageSize": 20 },
  "totalElements": 45,
  "totalPages": 3,
  "last": false,
  "first": true
}
```

---

*50 endpoints total. All covered. Passwords must contain uppercase + lowercase + digit + special char (`!@#$%^&*`). Use `Password1!` as your base test password.*
