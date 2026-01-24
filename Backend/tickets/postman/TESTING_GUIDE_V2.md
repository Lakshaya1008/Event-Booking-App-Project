# Testing Guide V2

This guide provides execution-ready test cases for all API endpoints. All tests are based on codebase audit and include exact requests and expected results.

## General Testing Notes

- All requests require `Content-Type: application/json` unless specified
- Authorization header: `Authorization: Bearer <jwt_token>`
- JWT tokens must be obtained from Keycloak (backend does not issue tokens)
- Approval gate blocks unapproved users (PENDING/REJECTED status)
- Admin users bypass approval gate

## AuthController

### POST /api/v1/auth/register

#### ✅ Test with ONLY mandatory fields
**Request:**
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "Password123",
  "name": "Test User"
}
```
**Expected:** 201 Created, RegisterResponseDto with requiresApproval=true

#### ✅ Test with ALL fields (including optional)
**Request:**
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "inviteCode": "ABCD-EFGH-IJKL-MNOP",
  "email": "test@example.com",
  "password": "Password123",
  "name": "Test User"
}
```
**Expected:** 201 Created, RegisterResponseDto with assignedRole from invite

#### ❌ Missing mandatory field (email)
**Request:**
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "password": "Password123",
  "name": "Test User"
}
```
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Invalid value (invalid email format)
**Request:**
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "invalid-email",
  "password": "Password123",
  "name": "Test User"
}
```
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Invalid value (password too short)
**Request:**
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "123",
  "name": "Test User"
}
```
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Invalid value (invalid invite code format)
**Request:**
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "inviteCode": "INVALID",
  "email": "test@example.com",
  "password": "Password123",
  "name": "Test User"
}
```
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Email already in use
**Request:** Same as valid request, but email already registered
**Expected:** 409 Conflict, service-level error

## ApprovalController

### GET /api/v1/admin/approvals/pending

#### ✅ Test with valid ADMIN token
**Request:**
```
GET /api/v1/admin/approvals/pending?page=0&size=10
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, Page<UserApprovalDto>

#### ❌ Access with wrong role (ORGANIZER token)
**Request:**
```
GET /api/v1/admin/approvals/pending
Authorization: Bearer <organizer_jwt>
```
**Expected:** 403 Forbidden, role check (Spring Security)

#### ❌ Access without token
**Request:**
```
GET /api/v1/admin/approvals/pending
```
**Expected:** 401 Unauthorized, JWT validation

#### ❌ Access with malformed token
**Request:**
```
GET /api/v1/admin/approvals/pending
Authorization: Bearer invalid.jwt.token
```
**Expected:** 401 Unauthorized, JWT validation

### POST /api/v1/admin/approvals/{userId}/approve

#### ✅ Test with valid ADMIN token
**Request:**
```
POST /api/v1/admin/approvals/123e4567-e89b-12d3-a456-426614174000/approve
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, success message

#### ❌ User not in PENDING state
**Request:** Same as above, but user already APPROVED
**Expected:** 400 Bad Request, service-level error

#### ❌ Access with wrong role
**Request:** Same as valid, but ORGANIZER token
**Expected:** 403 Forbidden, role check

### POST /api/v1/admin/approvals/{userId}/reject

#### ✅ Test with valid ADMIN token and reason
**Request:**
```
POST /api/v1/admin/approvals/123e4567-e89b-12d3-a456-426614174000/reject
Authorization: Bearer <admin_jwt>
Content-Type: application/json

{
  "reason": "This is a valid rejection reason with more than 10 characters"
}
```
**Expected:** 200 OK, success message

#### ❌ Missing mandatory field (reason)
**Request:**
```
POST /api/v1/admin/approvals/123e4567-e89b-12d3-a456-426614174000/reject
Authorization: Bearer <admin_jwt>
Content-Type: application/json

{}
```
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Invalid value (reason too short)
**Request:**
```
POST /api/v1/admin/approvals/123e4567-e89b-12d3-a456-426614174000/reject
Authorization: Bearer <admin_jwt>
Content-Type: application/json

{
  "reason": "Short"
}
```
**Expected:** 400 Bad Request, validation error (DTO-level)

## AuditController

### GET /api/v1/audit

#### ✅ Test with valid ADMIN token
**Request:**
```
GET /api/v1/audit?page=0&size=10
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, Page<AuditLogDto>

#### ❌ Access with wrong role (ORGANIZER token)
**Request:**
```
GET /api/v1/audit
Authorization: Bearer <organizer_jwt>
```
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval (PENDING user)
**Request:**
```
GET /api/v1/audit
Authorization: Bearer <pending_user_jwt>
```
**Expected:** 403 Forbidden, approval gate

### GET /api/v1/audit/events/{eventId}

#### ✅ Test with valid ORGANIZER token (owns event)
**Request:**
```
GET /api/v1/audit/events/123e4567-e89b-12d3-a456-426614174000?page=0&size=10
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, Page<AuditLogDto>

#### ❌ Access with ORGANIZER who doesn't own event
**Request:** Same as above, but different event ID
**Expected:** 403 Forbidden, AuthorizationService

#### ❌ Access with wrong role (ATTENDEE token)
**Request:**
```
GET /api/v1/audit/events/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer <attendee_jwt>
```
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

### GET /api/v1/audit/me

#### ✅ Test with authenticated user
**Request:**
```
GET /api/v1/audit/me?page=0&size=10
Authorization: Bearer <any_valid_jwt>
```
**Expected:** 200 OK, Page<AuditLogDto>

#### ❌ Access without approval
**Request:** Same as above, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Access without token
**Request:**
```
GET /api/v1/audit/me
```
**Expected:** 401 Unauthorized, JWT validation

#### ❌ Access with malformed token
**Request:**
```
GET /api/v1/audit/me
Authorization: Bearer invalid.jwt.token
```
**Expected:** 401 Unauthorized, JWT validation

## DiscountController

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts

#### ✅ Test with ONLY mandatory fields
**Request:**
```
POST /api/v1/events/123e4567-e89b-12d3-a456-426614174000/ticket-types/456e7890-e89b-12d3-a456-426614174001/discounts
Authorization: Bearer <organizer_jwt>
Content-Type: application/json

{
  "discountType": "PERCENTAGE",
  "value": 10.00,
  "validFrom": "2024-01-01T00:00:00",
  "validTo": "2024-12-31T23:59:59"
}
```
**Expected:** 201 Created, DiscountResponseDto

#### ✅ Test with ALL fields
**Request:** Same as above, plus:
```
{
  "discountType": "PERCENTAGE",
  "value": 10.00,
  "validFrom": "2024-01-01T00:00:00",
  "validTo": "2024-12-31T23:59:59",
  "active": true,
  "description": "Early bird discount"
}
```
**Expected:** 201 Created, DiscountResponseDto

#### ❌ Missing mandatory field (discountType)
**Request:** Omit discountType
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Invalid value (value <= 0)
**Request:** value: 0
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Invalid value (validTo before validFrom)
**Request:** validTo before validFrom
**Expected:** 400 Bad Request, service-level error

#### ❌ Invalid value (percentage > 100)
**Request:** discountType: PERCENTAGE, value: 150
**Expected:** 400 Bad Request, service-level error

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

#### ❌ Active discount already exists
**Request:** Same as valid, but ticket type already has active discount
**Expected:** 409 Conflict, service-level error

## EventController

### POST /api/v1/events

#### ✅ Test with ONLY mandatory fields
**Request:**
```
POST /api/v1/events
Authorization: Bearer <organizer_jwt>
Content-Type: application/json

{
  "name": "Test Event",
  "venue": "Test Venue",
  "status": "DRAFT",
  "ticketTypes": [
    {
      "name": "General Admission",
      "price": 50.00
    }
  ]
}
```
**Expected:** 201 Created, CreateEventResponseDto

#### ✅ Test with ALL fields
**Request:** Add optional fields like start, end, salesStart, salesEnd, description, totalAvailable
**Expected:** 201 Created

#### ❌ Missing mandatory field (name)
**Request:** Omit name
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Missing ticket types
**Request:** ticketTypes: []
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

### PUT /api/v1/events/{eventId}

#### ✅ Test with valid ORGANIZER (owns event)
**Request:**
```
PUT /api/v1/events/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer <organizer_jwt>
Content-Type: application/json

{
  "name": "Updated Event",
  "venue": "Updated Venue",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "id": "456e7890-e89b-12d3-a456-426614174001",
      "name": "VIP",
      "price": 100.00
    }
  ]
}
```
**Expected:** 200 OK, UpdateEventResponseDto

#### ❌ Not event organizer
**Request:** Same as above, but different event
**Expected:** 403 Forbidden, AuthorizationService

#### ❌ ID mismatch (body vs URL)
**Request:** Include id in body that doesn't match URL
**Expected:** 400 Bad Request, controller-level error

## EventStaffController

### POST /api/v1/events/{eventId}/staff

#### ✅ Test with valid ORGANIZER
**Request:**
```
POST /api/v1/events/123e4567-e89b-12d3-a456-426614174000/staff
Authorization: Bearer <organizer_jwt>
Content-Type: application/json

{
  "userId": "789e0123-e89b-12d3-a456-426614174002"
}
```
**Expected:** 200 OK, EventStaffResponseDto

#### ❌ Missing mandatory field (userId)
**Request:** Omit userId
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ User not found or no STAFF role
**Request:** Invalid userId
**Expected:** 404 Not Found, service-level error

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

## InviteCodeController

### POST /api/v1/invites

#### ✅ Test with ADMIN token
**Request:**
```
POST /api/v1/invites
Authorization: Bearer <admin_jwt>
Content-Type: application/json

{
  "roleName": "ORGANIZER"
}
```
**Expected:** 201 Created, InviteCodeResponseDto

#### ✅ Test with ORGANIZER for STAFF role
**Request:**
```
POST /api/v1/invites
Authorization: Bearer <organizer_jwt>
Content-Type: application/json

{
  "roleName": "STAFF",
  "eventId": "123e4567-e89b-12d3-a456-426614174000"
}
```
**Expected:** 201 Created

#### ❌ Missing eventId for STAFF role
**Request:** roleName: STAFF, no eventId
**Expected:** 400 Bad Request, service-level error

#### ❌ ORGANIZER creating non-STAFF role
**Request:** ORGANIZER token, roleName: ADMIN
**Expected:** 403 Forbidden, service-level error

### POST /api/v1/invites/redeem

#### ✅ Test with valid invite code
**Request:**
```
POST /api/v1/invites/redeem
Authorization: Bearer <any_jwt>
Content-Type: application/json

{
  "code": "ABCD-EFGH-IJKL-MNOP"
}
```
**Expected:** 200 OK, RedeemInviteCodeResponseDto

#### ❌ Invalid invite code
**Request:** Invalid or used code
**Expected:** 400 Bad Request, service-level error

#### ❌ Missing mandatory field (code)
**Request:** Omit code
**Expected:** 400 Bad Request, validation error (DTO-level)

## AdminGovernanceController

### POST /api/v1/admin/users/{userId}/roles

#### ✅ Test with valid ADMIN
**Request:**
```
POST /api/v1/admin/users/123e4567-e89b-12d3-a456-426614174000/roles
Authorization: Bearer <admin_jwt>
Content-Type: application/json

{
  "roleName": "ORGANIZER"
}
```
**Expected:** 200 OK, UserRolesResponseDto

#### ❌ Missing mandatory field (roleName)
**Request:** Omit roleName
**Expected:** 400 Bad Request, validation error (DTO-level)

#### ❌ Access with wrong role
**Request:** Same as valid, but ORGANIZER token
**Expected:** 403 Forbidden, role check

#### ❌ User not found
**Request:** Invalid userId
**Expected:** 404 Not Found, service-level error

### DELETE /api/v1/admin/users/{userId}/roles/{roleName}

#### ✅ Test with valid ADMIN
**Request:**
```
DELETE /api/v1/admin/users/123e4567-e89b-12d3-a456-426614174000/roles/ORGANIZER
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, UserRolesResponseDto

#### ❌ Access with wrong role
**Request:** Same as valid, but ORGANIZER token
**Expected:** 403 Forbidden, role check

### GET /api/v1/admin/users/{userId}/roles

#### ✅ Test with valid ADMIN
**Request:**
```
GET /api/v1/admin/users/123e4567-e89b-12d3-a456-426614174000/roles
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, UserRolesResponseDto

#### ❌ Access with wrong role
**Request:** Same as valid, but ORGANIZER token
**Expected:** 403 Forbidden, role check

#### ❌ User not found
**Request:** Invalid userId
**Expected:** 404 Not Found, Service

### GET /api/v1/admin/roles

#### ✅ Test with valid ADMIN
**Request:**
```
GET /api/v1/admin/roles
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, AvailableRolesResponseDto

#### ❌ Access with wrong role
**Request:** Same as valid, but ORGANIZER token
**Expected:** 403 Forbidden, role check

## DiscountController

### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}

#### ✅ Test with ONLY mandatory fields
**Request:**
```
PUT /api/v1/events/123e4567-e89b-12d3-a456-426614174000/ticket-types/456e7890-e89b-12d3-a456-426614174001/discounts/789e0123-e89b-12d3-a456-426614174002
Authorization: Bearer <organizer_jwt>
Content-Type: application/json

{
  "discountType": "PERCENTAGE",
  "value": 15.00,
  "validFrom": "2024-01-01T00:00:00",
  "validTo": "2024-12-31T23:59:59"
}
```
**Expected:** 200 OK, DiscountResponseDto

#### ✅ Test with ALL fields
**Request:** Same as above, plus optional fields
**Expected:** 200 OK

#### ❌ Missing mandatory field
**Request:** Omit discountType
**Expected:** 400 Bad Request, DTO validation

#### ❌ Invalid value
**Request:** value: -1
**Expected:** 400 Bad Request, DTO validation

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}

#### ✅ Test with valid request
**Request:**
```
DELETE /api/v1/events/123e4567-e89b-12d3-a456-426614174000/ticket-types/456e7890-e89b-12d3-a456-426614174001/discounts/789e0123-e89b-12d3-a456-426614174002
Authorization: Bearer <organizer_jwt>
```
**Expected:** 204 No Content

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}

#### ✅ Test with valid request
**Request:**
```
GET /api/v1/events/123e4567-e89b-12d3-a456-426614174000/ticket-types/456e7890-e89b-12d3-a456-426614174001/discounts/789e0123-e89b-12d3-a456-426614174002
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, DiscountResponseDto

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

#### ❌ Discount not found
**Request:** Invalid discountId
**Expected:** 404 Not Found, Controller

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts

#### ✅ Test with valid request
**Request:**
```
GET /api/v1/events/123e4567-e89b-12d3-a456-426614174000/ticket-types/456e7890-e89b-12d3-a456-426614174001/discounts
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, List<DiscountResponseDto>

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

## EventController

### GET /api/v1/events

#### ✅ Test with valid ORGANIZER
**Request:**
```
GET /api/v1/events?page=0&size=10
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, Page<ListEventResponseDto>

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

### GET /api/v1/events/{eventId}

#### ✅ Test with valid ORGANIZER (owns event)
**Request:**
```
GET /api/v1/events/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, GetEventDetailsResponseDto

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

#### ❌ Event not found
**Request:** Invalid eventId
**Expected:** 404 Not Found, Controller

### DELETE /api/v1/events/{eventId}

#### ✅ Test with valid ORGANIZER (owns event)
**Request:**
```
DELETE /api/v1/events/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer <organizer_jwt>
```
**Expected:** 204 No Content

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

### GET /api/v1/events/{eventId}/sales-dashboard

#### ✅ Test with valid ORGANIZER (owns event)
**Request:**
```
GET /api/v1/events/123e4567-e89b-12d3-a456-426614174000/sales-dashboard
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, Map<String, Object>

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

### GET /api/v1/events/{eventId}/attendees-report

#### ✅ Test with valid ORGANIZER (owns event)
**Request:**
```
GET /api/v1/events/123e4567-e89b-12d3-a456-426614174000/attendees-report
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, Map<String, Object>

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

### GET /api/v1/events/{eventId}/sales-report.xlsx

#### ✅ Test with valid ORGANIZER (owns event)
**Request:**
```
GET /api/v1/events/123e4567-e89b-12d3-a456-426614174000/sales-report.xlsx
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, Excel file

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

## EventStaffController

### DELETE /api/v1/events/{eventId}/staff/{userId}

#### ✅ Test with valid ORGANIZER
**Request:**
```
DELETE /api/v1/events/123e4567-e89b-12d3-a456-426614174000/staff/789e0123-e89b-12d3-a456-426614174002
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, EventStaffResponseDto

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

### GET /api/v1/events/{eventId}/staff

#### ✅ Test with valid ORGANIZER
**Request:**
```
GET /api/v1/events/123e4567-e89b-12d3-a456-426614174000/staff
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, List<StaffMemberDto>

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer
**Request:** Same as valid, but different event
**Expected:** 403 Forbidden, AuthorizationService

## InviteCodeController

### DELETE /api/v1/invites/{codeId}

#### ✅ Test with ADMIN
**Request:**
```
DELETE /api/v1/invites/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer <admin_jwt>
```
**Expected:** 204 No Content

#### ✅ Test with ORGANIZER (own invite)
**Request:**
```
DELETE /api/v1/invites/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer <organizer_jwt>
```
**Expected:** 204 No Content

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not creator (for ORGANIZER)
**Request:** ORGANIZER token, but different invite
**Expected:** 403 Forbidden, Service

### GET /api/v1/invites

#### ✅ Test with ADMIN
**Request:**
```
GET /api/v1/invites?page=0&size=10
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, Page<InviteCodeResponseDto>

#### ✅ Test with ORGANIZER
**Request:**
```
GET /api/v1/invites?page=0&size=10
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, Page<InviteCodeResponseDto> (own invites)

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

### GET /api/v1/invites/events/{eventId}

#### ✅ Test with ADMIN
**Request:**
```
GET /api/v1/invites/events/123e4567-e89b-12d3-a456-426614174000?page=0&size=10
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, Page<InviteCodeResponseDto>

#### ✅ Test with ORGANIZER (owns event)
**Request:**
```
GET /api/v1/invites/events/123e4567-e89b-12d3-a456-426614174000?page=0&size=10
Authorization: Bearer <organizer_jwt>
```
**Expected:** 200 OK, Page<InviteCodeResponseDto>

#### ❌ Access with wrong role
**Request:** Same as valid, but ATTENDEE token
**Expected:** 403 Forbidden, role check

#### ❌ Access without approval
**Request:** Same as valid, but PENDING user
**Expected:** 403 Forbidden, approval gate

#### ❌ Not event organizer (for ORGANIZER)
**Request:** ORGANIZER token, but different event
**Expected:** 403 Forbidden, AuthorizationService

## AdminGovernanceController

### DELETE /api/v1/admin/users/{userId}/roles/{roleName}

#### ✅ Test with valid ADMIN
**Request:**
```
DELETE /api/v1/admin/users/123e4567-e89b-12d3-a456-426614174000/roles/ORGANIZER
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, UserRolesResponseDto

#### ❌ Access with wrong role
**Request:** Same as valid, but ORGANIZER token
**Expected:** 403 Forbidden, role check

### GET /api/v1/admin/users/{userId}/roles

#### ✅ Test with valid ADMIN
**Request:**
```
GET /api/v1/admin/users/123e4567-e89b-12d3-a456-426614174000/roles
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, UserRolesResponseDto

#### ❌ Access with wrong role
**Request:** Same as valid, but ORGANIZER token
**Expected:** 403 Forbidden, role check

#### ❌ User not found
**Request:** Invalid userId
**Expected:** 404 Not Found, Service

### GET /api/v1/admin/roles

#### ✅ Test with valid ADMIN
**Request:**
```
GET /api/v1/admin/roles
Authorization: Bearer <admin_jwt>
```
**Expected:** 200 OK, AvailableRolesResponseDto

#### ❌ Access with wrong role
**Request:** Same as valid, but ORGANIZER token
**Expected:** 403 Forbidden, role check
