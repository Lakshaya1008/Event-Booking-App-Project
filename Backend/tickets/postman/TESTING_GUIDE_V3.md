# Event Booking Platform — Testing Guide

> [!WARNING]
> This file is legacy and may be out of date.
> Use `postman/COMPLETE_TESTING_GUIDE_AND_API_DOCS.md` as the source-of-truth.

**Base URL:** `http://localhost:8081` | **Keycloak:** `http://localhost:9090`

---

# SECTION 1 — KEYCLOAK SETUP

Complete this section once before running any tests.

---

## Step 1 — Start Docker Services

```bash
# From the project root (where docker-compose.yml is)
docker-compose up -d
```

| Service | URL | Credentials |
|---------|-----|-------------|
| Spring Boot App | http://localhost:8081 | — |
| Keycloak Admin UI | http://localhost:9090 | admin / admin |
| PostgreSQL | localhost:5433 | postgres / postgres123 |
| Adminer (DB UI) | http://localhost:8888 | System: PostgreSQL, Server: db, User: postgres, Pass: postgres123 |

Wait **30 seconds** after starting before accessing Keycloak.

---

## Step 2 — Create the Realm

1. Open **http://localhost:9090**
2. Log in: **admin / admin**
3. Top-left dropdown (shows "Keycloak") → click **Create Realm**
4. **Realm name:** `event-ticket-platform`
5. **Enabled:** Toggle ON
6. Click **Create**

All remaining steps are done inside the `event-ticket-platform` realm.

---

## Step 3 — Create the Client

1. Left menu → **Clients** → **Create client**
2. **Client ID:** `event-ticket-platform-app`
3. **Client type:** `OpenID Connect`
4. Click **Next**
5. **Client authentication:** Toggle **ON** (makes it confidential — required)
6. **Authorization:** Toggle **OFF**
7. Under Authentication flow check only:
    - ✅ **Standard flow**
    - ✅ **Direct access grants**
8. Click **Next**
9. **Valid redirect URIs:** `http://localhost:8081/*`
10. **Web origins:** `http://localhost:8081`
11. Click **Save**

**Get the client secret (needed for every token request):**
1. On the client page → click **Credentials** tab
2. Copy the **Client secret** value
3. Save it — you will use it in every token request

---

## Step 4 — Create Realm Roles

Left menu → **Realm roles** → **Create role**

Create all four roles one at a time:

| Role Name |
|-----------|
| `ADMIN` |
| `ORGANIZER` |
| `ATTENDEE` |
| `STAFF` |

For each: enter the role name → click **Save**.

---

## Step 5 — Create Test Users in Keycloak

For **each user** in the table, follow these steps:

**Create the user:**
1. Left menu → **Users** → **Add user**
2. **Username:** use the email address
3. **Email:** as shown
4. **First name:** any value
5. **Email verified:** Toggle **ON**
6. **Enabled:** Toggle **ON**
7. Click **Create**

**Set the password:**
1. Go to **Credentials** tab
2. Click **Set password**
3. Enter the password
4. **Temporary:** Toggle **OFF**
5. Click **Save password** → **Confirm**

**Assign the realm role:**
1. Go to **Role mapping** tab
2. Click **Assign role**
3. Select **Filter by realm roles** from the dropdown
4. Check the role → click **Assign**

| Email | Password | Realm Role |
|-------|----------|-----------|
| admin@test.com | Admin123! | ADMIN |
| organizer@test.com | Organizer1! | ORGANIZER |
| organizer2@test.com | Organizer1! | ORGANIZER |
| staff@test.com | Staff1! | STAFF |
| attendee@test.com | Attendee1! | *(none — assigned via registration)* |

> **Password requirement:** The API requires uppercase + lowercase + digit + special character from `!@#$%^&*`. `Password1!` format is required. `Password1` (no special char) will be **rejected with 400**.

---

## Step 6 — Register Users via the API

Keycloak holds authentication. The Spring Boot app holds the `approval_status`. You must register each user via the API so the DB record is created — without it the `ApprovalGateFilter` blocks everyone.

Run each of these:

```
POST http://localhost:8081/api/v1/auth/register
Content-Type: application/json
```

```json
{ "email": "organizer@test.com",  "password": "Organizer1!", "name": "Test Organizer" }
{ "email": "organizer2@test.com", "password": "Organizer1!", "name": "Organizer Two" }
{ "email": "staff@test.com",      "password": "Staff1!",      "name": "Test Staff" }
{ "email": "attendee@test.com",   "password": "Attendee1!",   "name": "Test Attendee" }
```

Each returns `201`. All are `PENDING` at this point.

> **Admin user:** Do NOT register `admin@test.com` via the API. The `DatabaseInitializer` creates a DB record for admin automatically on startup. If it does not, run this SQL manually:
> ```sql
> INSERT INTO users (id, name, email, approval_status)
> VALUES ('00000000-0000-0000-0000-000000000001', 'Admin', 'admin@test.com', 'APPROVED')
> ON CONFLICT DO NOTHING;
> ```

---

## Step 7 — Get Admin Token and Approve All Users

**Get the admin token:**
```
POST http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=event-ticket-platform-app
client_secret=<YOUR_CLIENT_SECRET>
username=admin@test.com
password=Admin123!
```

Save the `access_token` as `admin_token`.

**List pending users:**
```
GET http://localhost:8081/api/v1/admin/approvals/pending?page=0&size=20
Authorization: Bearer <admin_token>
```

Copy each `userId` from the response.

**Approve each user (no body needed):**
```
POST http://localhost:8081/api/v1/admin/approvals/{userId}/approve
Authorization: Bearer <admin_token>
```

Repeat for each user. Now all users are `APPROVED` and can use the system.

---

## Step 8 — Postman Environment Variables

Create a Postman Environment with these variables:

| Variable | Value |
|----------|-------|
| `base_url` | `http://localhost:8081` |
| `keycloak_url` | `http://localhost:9090` |
| `realm` | `event-ticket-platform` |
| `client_id` | `event-ticket-platform-app` |
| `client_secret` | *(from Keycloak Credentials tab)* |
| `admin_token` | *(fill after login)* |
| `organizer_token` | *(fill after login)* |
| `organizer2_token` | *(fill after login)* |
| `attendee_token` | *(fill after login)* |
| `staff_token` | *(fill after login)* |
| `event_id` | *(fill as you test)* |
| `ticket_type_id` | *(fill as you test)* |
| `ticket_id` | *(fill as you test)* |
| `discount_id` | *(fill as you test)* |
| `invite_code_id` | *(fill as you test)* |
| `user_id` | *(fill as you test)* |

---

## Step 9 — Get All Tokens

Run this once for each user — same URL, different credentials:

```
POST {{keycloak_url}}/realms/{{realm}}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id={{client_id}}
client_secret={{client_secret}}
username=<email>
password=<password>
```

| User | Email | Password | Save `access_token` as |
|------|-------|----------|----------------------|
| Admin | admin@test.com | Admin123! | `admin_token` |
| Organizer | organizer@test.com | Organizer1! | `organizer_token` |
| Organizer 2 | organizer2@test.com | Organizer1! | `organizer2_token` |
| Attendee | attendee@test.com | Attendee1! | `attendee_token` |
| Staff | staff@test.com | Staff1! | `staff_token` |

**Tokens expire in 5 minutes.** If you get 401, refresh.

---

# SECTION 2 — KEY RULES BEFORE TESTING

| Rule | Detail |
|------|--------|
| All users start PENDING | Must be approved via `POST /admin/approvals/{id}/approve` before they can use most endpoints |
| Approval gate bypass paths | `POST /auth/register` and `POST /invites/redeem` work for PENDING users |
| Ownership returns 404 | When an organizer accesses another organizer's event, they get 404 — not 403 |
| ADMIN cannot access events/tickets | ADMIN role is blocked from `/published-events`, `/tickets`, etc. |
| Ticket type `id` in PUT event | When updating an event, include `"id"` in each ticket type element to update it. Omitting `id` creates a new ticket type instead |
| Validation method values | Only `MANUAL` and `QR_SCAN` are valid. `QR_CODE`, `SCAN` return 400 |
| Second ticket scan | Returns 200 with `status: "INVALID"` — not an error |
| Per-user ticket limit | Max 10 tickets per user per ticket type |
| Password special char | Password must contain a special char from `!@#$%^&*`. `Password1` fails. Use `Password1!` |

---

# SECTION 3 — TEST CASES FOR ALL 50 ENDPOINTS

Symbols: ✅ valid | ❌ invalid | ⚠️ boundary value

---

## GROUP 1 — Authentication

### EP-01 · POST /api/v1/auth/register

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ No invite — gets ATTENDEE | `{"email":"new@test.com","password":"Password1!","name":"New User"}` | 201, `assignedRole:"ATTENDEE"`, `requiresApproval:true` |
| 2 | ✅ With ORGANIZER invite code | Add `"inviteCode":"{{organizer_invite_code}}"` | 201, `assignedRole:"ORGANIZER"` |
| 3 | ✅ Email stored lowercase | `"email":"New@Test.COM"` | 201, email stored as `new@test.com` |
| 4 | ⚠️ name exactly 2 chars (min) | `"name":"Jo"` | 201 |
| 5 | ⚠️ name exactly 100 chars (max) | 100-char name | 201 |
| 6 | ⚠️ password exactly 8 chars (min) | `"password":"Passw0r!"` | 201 |
| 7 | ❌ Empty body | `{}` | 400 — all 3 field errors returned at once |
| 8 | ❌ Missing email | `{"password":"Password1!","name":"Test"}` | 400 |
| 9 | ❌ Missing password | `{"email":"u@test.com","name":"Test"}` | 400 |
| 10 | ❌ Missing name | `{"email":"u@test.com","password":"Password1!"}` | 400 |
| 11 | ❌ Invalid email format | `"email":"notanemail"` | 400 |
| 12 | ❌ Password no uppercase | `"password":"password1!"` | 400 |
| 13 | ❌ Password no lowercase | `"password":"PASSWORD1!"` | 400 |
| 14 | ❌ Password no digit | `"password":"Password!!"` | 400 |
| 15 | ❌ Password no special char | `"password":"Password1"` | **400 — special char `!@#$%^&*` required** |
| 16 | ⚠️ name exactly 1 char (below min) | `"name":"A"` | 400 |
| 17 | ❌ Duplicate email | Register same email twice | 409 `EMAIL_ALREADY_REGISTERED` |
| 18 | ❌ inviteCode wrong format (lowercase) | `"inviteCode":"abcd-1234-efgh-5678"` | 400 |
| 19 | ❌ inviteCode not in DB | `"inviteCode":"ZZZZ-9999-ZZZZ-9999"` | 404 `INVITE_CODE_NOT_FOUND` |
| 20 | ❌ inviteCode already redeemed | Use a previously redeemed code | 400 `INVALID_INVITE_CODE` — "already been redeemed by..." |
| 21 | ❌ inviteCode expired | Use an expired code | 400 `INVALID_INVITE_CODE` — "expired on..." |

---

## GROUP 2 — Approval Management

### EP-02 · GET /api/v1/admin/approvals/pending

| # | | Expected |
|---|--|----------|
| 1 | ✅ Admin lists pending | 200, list of PENDING users, `roles:[]` always empty |
| 2 | ❌ ORGANIZER token | 403 |
| 3 | ❌ No token | 401 |

### EP-03 · POST /api/v1/admin/approvals/{userId}/approve

| # | | Expected |
|---|--|----------|
| 1 | ✅ Approve PENDING user | 200, `{"status":"APPROVED"}` — user can now log in |
| 2 | ❌ Already APPROVED | 409 `INVALID_APPROVAL_STATE` |
| 3 | ❌ Already REJECTED | 409 `INVALID_APPROVAL_STATE` |
| 4 | ❌ User not found | 404 `USER_NOT_FOUND` |
| 5 | ❌ ORGANIZER token | 403 |

### EP-04 · POST /api/v1/admin/approvals/{userId}/reject

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ Valid reason | `{"reason":"Account violates terms."}` | 200, `{"status":"REJECTED","reason":"..."}` |
| 2 | ⚠️ reason exactly 10 chars (min) | `{"reason":"Duplicate."}` | 200 |
| 3 | ⚠️ reason 9 chars (1 below min) | `{"reason":"Too short"}` | 400 |
| 4 | ❌ Empty body | `{}` | 400 |
| 5 | ❌ Whitespace-only reason | `{"reason":"   "}` | 400 |
| 6 | ❌ Already REJECTED | — | 409 |

### EP-05 · GET /api/v1/admin/approvals

| # | | Expected |
|---|--|----------|
| 1 | ✅ All users, all statuses | 200 |
| 2 | ❌ ORGANIZER token | 403 |

---

## GROUP 3 — Admin Role Management

### EP-06 · POST /api/v1/admin/users/{userId}/roles

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ Assign ATTENDEE | `{"roleName":"ATTENDEE"}` | 200, `{"roles":["ATTENDEE"]}` |
| 2 | ✅ Assign ORGANIZER | `{"roleName":"ORGANIZER"}` | 200 |
| 3 | ✅ Assign STAFF | `{"roleName":"STAFF"}` | 200 |
| 4 | ✅ Assign ADMIN | `{"roleName":"ADMIN"}` | 200 |
| 5 | ❌ Invalid role | `{"roleName":"SUPERUSER"}` | 400 |
| 6 | ❌ Empty body | `{}` | 400 |
| 7 | ❌ ORGANIZER token | — | 403 |

### EP-07 · DELETE /api/v1/admin/users/{userId}/roles/{roleName}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Revoke STAFF from staff user | 200, updated roles list |
| 2 | ❌ ORGANIZER token | 403 |

### EP-08 · GET /api/v1/admin/users/{userId}/roles

| # | | Expected |
|---|--|----------|
| 1 | ✅ Get roles for known user | 200, `{"userId":"...","roles":[...]}` |
| 2 | ❌ Unknown userId | 404 |
| 3 | ❌ ORGANIZER token | 403 |

### EP-09 · GET /api/v1/admin/roles

| # | | Expected |
|---|--|----------|
| 1 | ✅ Admin calls | 200, `{"roles":["ADMIN","ORGANIZER","ATTENDEE","STAFF"]}` |
| 2 | ❌ ORGANIZER token | 403 |

---

## GROUP 4 — Invite Codes

### EP-10 · POST /api/v1/invites

| # | | Payload | Token | Expected |
|---|--|---------|-------|----------|
| 1 | ✅ ADMIN creates ATTENDEE | `{"roleName":"ATTENDEE","expirationHours":24}` | admin | 201 |
| 2 | ✅ ADMIN creates ORGANIZER | `{"roleName":"ORGANIZER","expirationHours":72}` | admin | 201 |
| 3 | ✅ ADMIN creates ADMIN | `{"roleName":"ADMIN","expirationHours":24}` | admin | 201 |
| 4 | ✅ ORGANIZER creates STAFF for own event | `{"roleName":"STAFF","eventId":"{{event_id}}","expirationHours":48}` | organizer | 201 |
| 5 | ⚠️ expirationHours=1 (min) | `{"roleName":"ATTENDEE","expirationHours":1}` | admin | 201 |
| 6 | ❌ expirationHours=0 | `{"roleName":"ATTENDEE","expirationHours":0}` | admin | 400 |
| 7 | ❌ Missing roleName | `{"expirationHours":24}` | admin | 400 |
| 8 | ❌ Invalid roleName | `{"roleName":"BOSS","expirationHours":24}` | admin | 400 |
| 9 | ❌ STAFF without eventId | `{"roleName":"STAFF","expirationHours":24}` | admin | 400 — "Event ID is required" |
| 10 | ❌ Non-STAFF with eventId | `{"roleName":"ATTENDEE","eventId":"{{event_id}}","expirationHours":24}` | admin | 400 — "should only be provided for STAFF" |
| 11 | ❌ ORGANIZER tries ORGANIZER | `{"roleName":"ORGANIZER","expirationHours":24}` | organizer | 400 — "can only create STAFF invites" |
| 12 | ❌ ORGANIZER tries ADMIN | `{"roleName":"ADMIN","expirationHours":24}` | organizer | 400 — "Only ADMINs can create ADMIN role invites" |
| 13 | ❌ ORGANIZER, other org's event | STAFF invite for another organizer's event | organizer | 403 |
| 14 | ❌ ATTENDEE token | — | attendee | 403 |

### EP-11 · POST /api/v1/invites/redeem (Approval gate BYPASSED)

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ Valid code | `{"code":"{{valid_invite_code}}"}` | 200, `{"roleAssigned":"STAFF","currentRoles":["STAFF"]}` |
| 2 | ✅ PENDING user can redeem | Same with PENDING user token | 200 — bypass confirmed |
| 3 | ❌ Empty body | `{}` | 400 |
| 4 | ❌ Blank code | `{"code":""}` | 400 |
| 5 | ❌ Code not in DB | `{"code":"ZZZZ-9999-ZZZZ-9999"}` | 404 `INVITE_CODE_NOT_FOUND` |
| 6 | ❌ Already redeemed | Redeemed code | 400 `INVALID_INVITE_CODE` — "already been redeemed by..." |
| 7 | ❌ Expired | Expired code | 400 `INVALID_INVITE_CODE` — "expired on..." |
| 8 | ❌ Revoked | Revoked code | 400 `INVALID_INVITE_CODE` — "has been revoked. Reason:..." |

### EP-12 · DELETE /api/v1/invites/{codeId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Creator revokes own code | 204 |
| 2 | ✅ With `?reason=Event+cancelled` | 204 |
| 3 | ✅ ADMIN revokes any code | 204 |
| 4 | ❌ Code is REDEEMED | 400 — "Cannot revoke: current status is REDEEMED" |
| 5 | ❌ ORGANIZER revoking another's code | 403 |
| 6 | ❌ Code not found | 404 |

### EP-13 · GET /api/v1/invites

| # | | Expected |
|---|--|----------|
| 1 | ✅ ADMIN | 200 — sees ALL invite codes |
| 2 | ✅ ORGANIZER | 200 — sees only their own |
| 3 | ❌ ATTENDEE | 403 |

### EP-14 · GET /api/v1/invites/events/{eventId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ ADMIN any event | 200 |
| 2 | ✅ ORGANIZER own event | 200 |
| 3 | ❌ ORGANIZER other's event | 403 |

---

## GROUP 5 — Event Management

### EP-15 · POST /api/v1/events

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ Minimum valid | `{"name":"Event","venue":"Venue","status":"PUBLISHED","ticketTypes":[{"name":"GA","price":99.99,"totalAvailable":100}]}` | 201 — save `event_id`, `ticket_type_id` |
| 2 | ✅ Full with all optional fields | Add dates, maxCapacity, 2 ticket types | 201 |
| 3 | ✅ Free event | `"price":0.00` | 201 |
| 4 | ✅ DRAFT status | `"status":"DRAFT"` | 201 |
| 5 | ⚠️ totalAvailable=1 (min) | — | 201 |
| 6 | ❌ Missing name | — | 400 |
| 7 | ❌ Missing venue | — | 400 |
| 8 | ❌ Missing status | — | 400 |
| 9 | ❌ Invalid status value | `"status":"OPEN"` | 400 |
| 10 | ❌ Empty ticketTypes | `"ticketTypes":[]` | 400 |
| 11 | ❌ totalAvailable=0 | — | 400 |
| 12 | ❌ price=-0.01 | — | 400 |
| 13 | ❌ end before start | — | 409 — "Event end date must be after start date" |
| 14 | ❌ salesEnd before salesStart | — | 409 |
| 15 | ❌ ATTENDEE token | — | 403 |

### EP-16 · PUT /api/v1/events/{eventId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Update name — include ticket type `id` | 200 |
| 2 | ✅ Add new ticket type — omit `id` in that element | 200, new UUID created |
| 3 | ✅ Cancel event — `"status":"CANCELLED"` | 200, all PURCHASED tickets auto-cancelled |
| 4 | ❌ Body `id` ≠ URL eventId | 400 `INVALID_ARGUMENT` |
| 5 | ❌ Re-publish a CANCELLED event | 409 — "Cannot modify a cancelled event" |
| 6 | ❌ maxCapacity below already-sold count | 409 |
| 7 | ❌ Not owner | 404 |

### EP-17 · GET /api/v1/events

| # | | Expected |
|---|--|----------|
| 1 | ✅ Organizer calls | 200 — only their own events |
| 2 | ❌ ATTENDEE token | 403 |

### EP-18 · GET /api/v1/events/{eventId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own event | 200 |
| 2 | ❌ Another organizer's event | 404 (ownership hidden — not 403) |

### EP-19 · DELETE /api/v1/events/{eventId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Event with no active tickets | 204 |
| 2 | ❌ Event has active tickets | 409 — "N active ticket(s) exist. Cancel the event first." |

### EP-20 · GET /api/v1/events/{eventId}/sales-dashboard

| # | | Expected |
|---|--|----------|
| 1 | ✅ After buying tickets | 200, `totalTicketsSold > 0`, revenue fields populated |
| 2 | ✅ After cancelling event | 200, `totalTicketsSold:0`, all revenue `0.00` |
| 3 | ✅ Event with unlimited tickets (`totalAvailable` null) | 200, `remaining:null` (not NPE) |

### EP-21 · GET /api/v1/events/{eventId}/attendees-report

| # | | Expected |
|---|--|----------|
| 1 | ✅ After validating a ticket | 200, attendee listed with `ticketStatus:"VALIDATED"`, `validationCount:1` |
| 2 | ✅ After second scan | `validationCount:2` |

### EP-22 · GET /api/v1/events/{eventId}/sales-report.xlsx

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own event | 200, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, file downloads |

---

## GROUP 6 — Published Events

### EP-23 · GET /api/v1/published-events

| # | | Expected |
|---|--|----------|
| 1 | ✅ ATTENDEE — all published | 200 |
| 2 | ✅ With search `?q=tech` | 200, filtered list |
| 3 | ❌ ADMIN token | 403 — ADMIN not allowed |
| 4 | ❌ No token | 401 |

### EP-24 · GET /api/v1/published-events/{eventId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ PUBLISHED event | 200 with ticket types |
| 2 | ❌ DRAFT event | 404 |
| 3 | ❌ CANCELLED event | 404 |

---

## GROUP 7 — Ticket Types

### EP-25 · POST /api/v1/events/{eventId}/ticket-types

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ Valid | `{"name":"VIP","price":499.99,"totalAvailable":50}` | 201 |
| 2 | ✅ Free ticket | `"price":0.00` | 201 |
| 3 | ❌ Missing name | — | 400 |
| 4 | ❌ totalAvailable=0 | — | 400 |
| 5 | ❌ price=-0.01 | — | 400 |

### EP-26 · GET /api/v1/events/{eventId}/ticket-types

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own event | 200, list of all ticket types |
| 2 | ❌ ATTENDEE token | 403 |

### EP-27 · GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Correct event and type | 200 |
| 2 | ❌ Wrong eventId for the type | 404 |

### EP-28 · PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Update name and price | 200 |
| 2 | ✅ Raise totalAvailable | 200 |
| 3 | ❌ Lower totalAvailable below sold count | 409 |

### EP-29 · DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ No sold tickets | 204 |
| 2 | ❌ Has active sold tickets | 409 `TICKET_TYPE_DELETE_NOT_ALLOWED` |

---

## GROUP 8 — Ticket Purchase

### EP-30 · POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ Default quantity | `{}` | 201, array of 1 ticket — save `ticket_id` |
| 2 | ✅ quantity=2 | `{"quantity":2}` | 201, 2 tickets |
| 3 | ⚠️ quantity=1 (min) | — | 201 |
| 4 | ⚠️ quantity=10 (max) | — | 201 |
| 5 | ✅ With active 20% discount | Buy after creating discount | 201, `discountApplied > 0`, `pricePaid < originalPrice` |
| 6 | ✅ ORGANIZER buys own event | organizer token | 201 |
| 7 | ❌ quantity=0 | — | 400 |
| 8 | ❌ quantity=11 | — | 400 |
| 9 | ❌ DRAFT event | — | 409 — "not open for sales" |
| 10 | ❌ CANCELLED event | — | 409 — "has been cancelled" |
| 11 | ❌ Before salesStart | — | 409 — "Sales have not started yet. Sales open at..." |
| 12 | ❌ After salesEnd | — | 409 — "Sales have closed. Sales ended at..." |
| 13 | ❌ Ticket type sold out | totalAvailable=2, buy 2, try 1 more | 400 `TICKETS_SOLD_OUT` |
| 14 | ❌ Per-user limit exceeded | Buy 10, try 1 more | 409 — "Purchase limit reached. You already own 10..." |
| 15 | ❌ STAFF token | — | 403 |

---

## GROUP 9 — Ticket Viewing & QR Codes

### EP-31 · GET /api/v1/tickets

| # | | Expected |
|---|--|----------|
| 1 | ✅ Attendee lists own tickets | 200 — only their own |
| 2 | ❌ STAFF token | 403 |

### EP-32 · GET /api/v1/tickets/{ticketId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own ticket | 200 with all price fields |
| 2 | ❌ Another user's ticket | 404 |
| 3 | ❌ STAFF token | 403 |

### EP-33 · GET /api/v1/tickets/{ticketId}/qr-codes *(Legacy)*

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own ticket | 200, `Content-Type: image/png` |

### EP-34 · GET /api/v1/tickets/{ticketId}/qr-codes/view

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own ticket | 200, `Content-Type: image/png`, **`Cache-Control: max-age=300, private`** (verify private — not public) |
| 2 | ❌ Other user's ticket | 403 |
| 3 | ❌ Cancelled ticket | 404 `QR_CODE_NOT_FOUND` |

### EP-35 · GET /api/v1/tickets/{ticketId}/qr-codes/png

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own ticket | 200, `Content-Type: image/png`, `Content-Disposition: attachment` |

### EP-36 · GET /api/v1/tickets/{ticketId}/qr-codes/pdf

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own ticket | 200, `Content-Type: application/pdf`, `Content-Disposition: attachment` |

---

## GROUP 10 — Discounts

### EP-37 · POST .../discounts

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ PERCENTAGE 20% | `{"discountType":"PERCENTAGE","value":20.0,"validFrom":"2025-11-01T00:00:00","validTo":"2025-11-30T23:59:59","active":true}` | 201 |
| 2 | ✅ FIXED_AMOUNT $50 | `{"discountType":"FIXED_AMOUNT","value":50.00,"validFrom":"...","validTo":"...","active":true}` | 201 |
| 3 | ✅ PERCENTAGE 100% | value=100.0 | 201 — purchase gives `pricePaid:0.00` |
| 4 | ✅ FIXED larger than ticket price | value=9999 | 201 — purchase clamps to `pricePaid:0.00` |
| 5 | ✅ active=false | — | 201 — purchase at full price (discount not applied) |
| 6 | ❌ Second active discount same type | — | 409 `DISCOUNT_ALREADY_EXISTS` |
| 7 | ❌ validFrom in past | — | 400 — "must be in the future" |
| 8 | ❌ value=0 | — | 400 |
| 9 | ❌ PERCENTAGE value=100.01 | — | 400 |
| 10 | ❌ validTo before validFrom | — | 400 |

### EP-38 · PUT .../discounts/{discountId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Update description only | 200 |
| 2 | ✅ Deactivate — `"active":false` | 200 |
| 3 | ❌ Change discountType/value after tickets sold | 400 — "Cannot change discount type or value — N active ticket(s) sold" |
| 4 | ❌ Re-activate when another active exists | 409 `DISCOUNT_ALREADY_EXISTS` |

### EP-39 · DELETE .../discounts/{discountId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Delete own discount | 204 |
| 2 | ❌ Not owner | 403 |

### EP-40 · GET .../discounts/{discountId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Exists | 200 |
| 2 | ❌ Not found | 404 `DISCOUNT_NOT_FOUND` |

### EP-41 · GET .../discounts (list)

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own event | 200, all discounts (active and inactive) |

---

## GROUP 11 — Ticket Validation

### EP-42 · POST /api/v1/ticket-validations

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ MANUAL — first scan | `{"id":"{{ticket_id}}","method":"MANUAL"}` | 200, `"status":"VALID"`, `validatedByName` populated |
| 2 | ✅ QR_SCAN — first scan | `{"id":"{{qr_code_id}}","method":"QR_SCAN"}` | 200, `"status":"VALID"` |
| 3 | ✅ Second scan same ticket | Same payload again | 200, `"status":"INVALID"` — NOT an error, returns 200 |
| 4 | ❌ Empty body | `{}` | 400 — both fields listed in validationErrors |
| 5 | ❌ method="QR_CODE" | — | 400 — invalid enum |
| 6 | ❌ method="SCAN" | — | 400 |
| 7 | ❌ CANCELLED ticket | — | 409 — "has been cancelled and cannot be validated" |
| 8 | ❌ Random ticket UUID | — | 404 |
| 9 | ❌ ATTENDEE token | — | 403 |
| 10 | ❌ STAFF not assigned to this event | — | 403 |

### EP-43 · GET /api/v1/ticket-validations/events/{eventId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ STAFF assigned to event | 200, paginated validation records |
| 2 | ✅ ORGANIZER owns event | 200 |
| 3 | ❌ STAFF not assigned | 403 |
| 4 | ❌ ATTENDEE token | 403 |

### EP-44 · GET /api/v1/ticket-validations/tickets/{ticketId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ STAFF or ORGANIZER | 200, list of all scans for that ticket |
| 2 | ❌ ATTENDEE token | 403 |

---

## GROUP 12 — Event Staff Management

### EP-45 · POST /api/v1/events/{eventId}/staff

| # | | Payload | Expected |
|---|--|---------|----------|
| 1 | ✅ User has STAFF role | `{"userId":"{{staff_user_id}}"}` | 201, staff list with 1 member |
| 2 | ❌ User has no STAFF role | — | 409 — "does not have STAFF role. STAFF role must be assigned by ADMIN first." |
| 3 | ❌ User already assigned | Same userId again | 409 — "already assigned as staff" |
| 4 | ❌ Empty body | `{}` | 400 |
| 5 | ❌ Not owner | — | 403 |

### EP-46 · DELETE /api/v1/events/{eventId}/staff/{userId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ Assigned user | 200, updated staff list |
| 2 | ❌ User not assigned | 409 |
| 3 | ❌ Not owner | 403 |

### EP-47 · GET /api/v1/events/{eventId}/staff

| # | | Expected |
|---|--|----------|
| 1 | ✅ Own event | 200 |
| 2 | ❌ ATTENDEE token | 403 |

---

## GROUP 13 — Audit Logs

### EP-48 · GET /api/v1/audit

| # | | Expected |
|---|--|----------|
| 1 | ✅ ADMIN | 200, `userAgent` field populated |
| 2 | ❌ ORGANIZER | 403 |

### EP-49 · GET /api/v1/audit/events/{eventId}

| # | | Expected |
|---|--|----------|
| 1 | ✅ ORGANIZER own event | 200 |
| 2 | ❌ Other org's event | 403 |

### EP-50 · GET /api/v1/audit/me

| # | | Expected |
|---|--|----------|
| 1 | ✅ Any approved user | 200, their own actions only |

---

# SECTION 4 — SECURITY TEST MATRIX

Run all 16. All must return exactly the indicated response.

| # | Token | Method | URL | Expected |
|---|-------|--------|-----|----------|
| S-01 | None | GET | `/api/v1/published-events` | 401 `AUTHENTICATION_FAILED` |
| S-02 | `"Bearer garbage"` | GET | `/api/v1/published-events` | 401 |
| S-03 | PENDING user | GET | `/api/v1/published-events` | 403 `APPROVAL_PENDING` — message says "awaiting approval" |
| S-04 | PENDING user | POST | `/api/v1/auth/register` | 201 ✅ — approval gate bypassed |
| S-05 | PENDING user | POST | `/api/v1/invites/redeem` | 200 ✅ — approval gate bypassed |
| S-06 | REJECTED user | GET | `/api/v1/published-events` | 403 `APPROVAL_REJECTED` — rejection reason in message |
| S-07 | ATTENDEE | GET | `/api/v1/events` | 403 |
| S-08 | ATTENDEE | GET | `/api/v1/admin/roles` | 403 |
| S-09 | ATTENDEE | POST | `/api/v1/ticket-validations` | 403 |
| S-10 | STAFF | GET | `/api/v1/tickets` | 403 |
| S-11 | STAFF | POST | `/api/v1/events/{id}/ticket-types/{id}/tickets` | 403 |
| S-12 | ORGANIZER | GET | `/api/v1/admin/approvals` | 403 |
| S-13 | ADMIN | GET | `/api/v1/published-events` | 403 |
| S-14 | ADMIN | GET | `/api/v1/tickets` | 403 |
| S-15 | Organizer B | GET | `/api/v1/events/{organizer-A-event-id}` | **404** (not 403 — ownership is hidden) |
| S-16 | ATTENDEE | GET | `/api/v1/tickets/{id}/qr-codes/view` | 200 — check response header `Cache-Control: max-age=300, private` (must NOT be `public`) |

---

# SECTION 5 — END-TO-END HAPPY PATH

Run all 24 steps in order. Each step depends on the previous.

| Step | Request | Expected | Save |
|------|---------|----------|------|
| 1 | `POST /api/v1/auth/register` (new attendee) | 201 PENDING | `new_user_id` |
| 2 | `POST /admin/approvals/{userId}/approve` (admin) | 200 | — |
| 3 | Get attendee token from Keycloak | 200 | `attendee_token` |
| 4 | `POST /api/v1/events` (organizer) | 201 | `event_id`, `ticket_type_id` |
| 5 | `POST /api/v1/invites` (admin, STAFF for that event) | 201 | `invite_code` |
| 6 | `POST /api/v1/invites/redeem` (staff user, PENDING status) | 200 | — |
| 7 | `POST /admin/approvals/{staffId}/approve` (admin) | 200 | — |
| 8 | `POST /api/v1/events/{id}/staff` (organizer, userId=staff) | 201 | — |
| 9 | `POST .../discounts` (organizer, 20% PERCENTAGE, active=true) | 201 | `discount_id` |
| 10 | `POST .../ticket-types/{id}/tickets` (attendee, quantity=2) | 201 | `ticket_id` — verify `discountApplied>0`, `pricePaid < originalPrice` |
| 11 | `GET /api/v1/tickets/{ticket_id}` (attendee) | 200 | — |
| 12 | `GET /api/v1/tickets/{ticket_id}/qr-codes/png` (attendee) | 200, PNG downloads | — |
| 13 | `POST /api/v1/ticket-validations` (staff, MANUAL, ticket_id) | 200, `status:"VALID"`, `validatedByName` populated | — |
| 14 | `POST /api/v1/ticket-validations` (same ticket, same staff) | 200, `status:"INVALID"` — NOT an error | — |
| 15 | `GET /ticket-validations/events/{event_id}` (staff) | 200, 2 records visible | — |
| 16 | `GET /events/{id}/attendees-report` (organizer) | 200, `validationCount:2` for that ticket | — |
| 17 | `GET /events/{id}/sales-dashboard` (organizer) | 200, revenue and discount totals correct | — |
| 18 | `GET /events/{id}/sales-report.xlsx` (organizer) | 200, Excel file downloads | — |
| 19 | `PUT /events/{id}` with `"status":"CANCELLED"` (organizer) | 200 | — |
| 20 | `POST /ticket-validations` (staff, cancelled ticket) | 409 — "cancelled and cannot be validated" | — |
| 21 | `GET /events/{id}/sales-dashboard` (organizer) | 200, `totalTicketsSold:0`, all revenue `0.00` | — |
| 22 | `DELETE /events/{id}` (organizer) | 409 — "N active ticket(s) exist. Cancel first." | — |
| 23 | `GET /api/v1/audit/me` (attendee) | 200, all attendee actions in trail | — |
| 24 | `GET /api/v1/audit/events/{id}` (organizer) | 200, all event actions in trail | — |

---

# SECTION 6 — FIX VERIFICATION TESTS

These prove every bug fixed during the audit works correctly in production.

| # | Fix | Steps | Expected |
|---|-----|-------|----------|
| F-01 | Live event update allowed | Create event with past `salesStart`, then PUT to change venue | 200 — succeeds, not blocked |
| F-02 | VALIDATED status written on scan | Validate ticket once, then GET ticket | `"status":"VALIDATED"` not `"PURCHASED"` |
| F-03 | Invite expiry race condition closed | Create invite, wait for scheduler to expire it, try to redeem | 400 `INVALID_INVITE_CODE` — "expired on..." |
| F-04 | Email registration race condition | Register same email twice rapidly | Second returns 409 EMAIL_ALREADY_REGISTERED (not 500) |
| F-05 | Discount post-sales guard | Create discount, buy 2 tickets, try PUT to change `discountType` | 400 — "Cannot change discount type or value — 2 active ticket(s) sold" |
| F-06 | Expired discount unblocks new | Set discount `active:false`, create new active discount | 201 — not blocked by inactive old one |
| F-07 | Per-user ticket limit enforced | Buy 10 tickets for same type, try 11th | 409 — "Purchase limit reached. You already own 10" |
| F-08 | QR UUID valid for QR_SCAN | Download PNG, extract QR code UUID, use in validation with `method:"QR_SCAN"` | 200 `status:"VALID"` (not 404) |
| F-09 | Null totalAvailable — no NPE | Create ticket type with no cap, view sales dashboard | `"remaining":null` (not 500) |
| F-10 | Approval gate bypass confirmed | PENDING user sends `POST /auth/register` | 201 (not 403 APPROVAL_PENDING) |

---

# SECTION 7 — COMMON MISTAKES AND FIXES

| Mistake | Error You See | Fix |
|---------|--------------|-----|
| Password without special char `!@#$%^&*` | 400 `VALIDATION_ERROR` | Use `Password1!` not `Password1` |
| User not approved | 403 `APPROVAL_PENDING` | `POST /admin/approvals/{id}/approve` |
| Wrong token for role | 403 `ACCESS_DENIED` | Use correct token |
| PUT event — no ticket type `id` in body | 500 duplicate key | Include `"id":"uuid"` for every existing ticket type |
| `"method":"QR_CODE"` in validation | 400 | Use `"method":"QR_SCAN"` |
| Getting token from port 8081 | 401 | Tokens come from Keycloak: port **9090** |
| Accessing another org's event | 404 | Use your own organizer token and events |
| Creating discount with past `validFrom` | 400 | New discounts must start in the future |
| Assigning staff before ADMIN gives STAFF role | 409 — "does not have STAFF role" | `POST /admin/users/{id}/roles` first |
| Expired token | 401 | Tokens expire in 5 minutes — refresh |
| ADMIN tries `/published-events` | 403 | ADMIN cannot access this — use attendee token |
| STAFF tries `/tickets` | 403 | STAFF cannot view tickets — use attendee token |

---

*50 endpoints. 215+ individual test cases. All roles covered.*
*Passwords: uppercase + lowercase + digit + special char from `!@#$%^&*` — use `Password1!`*