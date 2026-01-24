# Testing Guide V3

This guide provides execution-ready tests for every endpoint, derived strictly from the codebase.

## Common Headers
- Content-Type: application/json
- Authorization: Bearer <jwt-token>

## Authentication Token Type
- JWT token from Keycloak, with roles in claims.

## Tests for Each Endpoint

### AuthController

#### POST /api/v1/auth/register
- URL: /api/v1/auth/register
- Headers: Content-Type: application/json
- Auth: None
- ✅ Valid with ONLY mandatory: {"email":"test@example.com","password":"Pass1234","name":"Test"}
- ✅ Valid with ALL: {"email":"test@example.com","password":"Pass1234","name":"Test","inviteCode":"ABCD-1234-EFGH-5678"}
- ❌ Missing mandatory: {"password":"Pass1234","name":"Test"} → 400
- ❌ Invalid email: {"email":"invalid","password":"Pass1234","name":"Test"} → 400
- ❌ Invalid password: {"email":"test@example.com","password":"weak","name":"Test"} → 400
- ❌ Invalid invite code: {"email":"test@example.com","password":"Pass1234","name":"Test","inviteCode":"invalid"} → 400
- ❌ Wrong role: N/A
- ❌ Not approved: N/A
- ❌ Ownership: N/A

### InviteCodeController

#### POST /api/v1/invites
- URL: /api/v1/invites
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid with ONLY mandatory: {"roleName":"ATTENDEE","expirationHours":24}
- ✅ Valid with ALL: {"roleName":"STAFF","eventId":"uuid","expirationHours":24}
- ❌ Missing mandatory: {"roleName":"ATTENDEE"} → 400
- ❌ Invalid role: {"roleName":"INVALID","expirationHours":24} → 400
- ❌ STAFF without eventId: {"roleName":"STAFF","expirationHours":24} → 400 (if ORGANIZER)
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event for STAFF → 400

#### POST /api/v1/invites/redeem
- URL: /api/v1/invites/redeem
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: {"code":"ABCD-1234-EFGH-5678"}
- ✅ Valid with ALL: same
- ❌ Missing code: {} → 400
- ❌ Invalid code: {"code":"invalid"} → 400
- ❌ Wrong role: N/A
- ❌ Not approved: N/A (bypass)
- ❌ Ownership: N/A

#### DELETE /api/v1/invites/{codeId}
- URL: /api/v1/invites/{codeId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not creator → 400

#### GET /api/v1/invites
- URL: /api/v1/invites
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403

#### GET /api/v1/invites/events/{eventId}
- URL: /api/v1/invites/events/{eventId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

### EventController

#### POST /api/v1/events
- URL: /api/v1/events
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid with ONLY mandatory: {"name":"Event","venue":"Venue","status":"DRAFT","ticketTypes":[{"name":"Type","price":10.0}]}
- ✅ Valid with ALL: {"name":"Event","venue":"Venue","status":"DRAFT","start":"2023-01-01T10:00","end":"2023-01-01T12:00","salesStart":"2023-01-01T08:00","salesEnd":"2023-01-01T09:00","ticketTypes":[{"name":"Type","price":10.0,"description":"Desc","totalAvailable":100}]}
- ❌ Missing mandatory: {"venue":"Venue","status":"DRAFT","ticketTypes":[{"name":"Type","price":10.0}]} → 400
- ❌ Invalid status: {"name":"Event","venue":"Venue","status":"INVALID","ticketTypes":[{"name":"Type","price":10.0}]} → 400
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403

#### PUT /api/v1/events/{eventId}
- URL: /api/v1/events/{eventId}
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid with ONLY mandatory: {"name":"Event","venue":"Venue","status":"DRAFT","ticketTypes":[{"name":"Type","price":10.0}]}
- ✅ Valid with ALL: same as create
- ❌ Missing mandatory: same as create → 400
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/events
- URL: /api/v1/events
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403

#### GET /api/v1/events/{eventId}
- URL: /api/v1/events/{eventId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### DELETE /api/v1/events/{eventId}
- URL: /api/v1/events/{eventId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/events/{eventId}/sales-dashboard
- URL: /api/v1/events/{eventId}/sales-dashboard
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/events/{eventId}/attendees-report
- URL: /api/v1/events/{eventId}/attendees-report
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/events/{eventId}/sales-report.xlsx
- URL: /api/v1/events/{eventId}/sales-report.xlsx
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

### TicketTypeController

#### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets
- URL: /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid with ONLY mandatory: {}
- ✅ Valid with ALL: {"quantity":2}
- ❌ Invalid quantity: {"quantity":0} → 400
- ❌ Wrong role: Use STAFF token → 403
- ❌ Not approved: Use PENDING user → 403

#### POST /api/v1/events/{eventId}/ticket-types
- URL: /api/v1/events/{eventId}/ticket-types
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid with ONLY mandatory: {"name":"Type","price":10.0}
- ✅ Valid with ALL: {"name":"Type","price":10.0,"description":"Desc","totalAvailable":100}
- ❌ Missing mandatory: {"price":10.0} → 400
- ❌ Negative price: {"name":"Type","price":-1.0} → 400
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/events/{eventId}/ticket-types
- URL: /api/v1/events/{eventId}/ticket-types
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- URL: /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- URL: /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid with ONLY mandatory: {"name":"Type","price":10.0}
- ✅ Valid with ALL: {"name":"Type","price":10.0,"description":"Desc","totalAvailable":100}
- ❌ Missing mandatory: {"price":10.0} → 400
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- URL: /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

### PublishedEventController

#### GET /api/v1/published-events
- URL: /api/v1/published-events
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ADMIN token → 403
- ❌ Not approved: Use PENDING user → 403

#### GET /api/v1/published-events/{eventId}
- URL: /api/v1/published-events/{eventId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ADMIN token → 403
- ❌ Not approved: Use PENDING user → 403

### TicketController

#### GET /api/v1/tickets
- URL: /api/v1/tickets
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use STAFF token → 403
- ❌ Not approved: Use PENDING user → 403

#### GET /api/v1/tickets/{ticketId}
- URL: /api/v1/tickets/{ticketId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use STAFF token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: Not own ticket → 403

#### GET /api/v1/tickets/{ticketId}/qr-codes
- URL: /api/v1/tickets/{ticketId}/qr-codes
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use STAFF token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: Not own ticket → 403

#### GET /api/v1/tickets/{ticketId}/qr-codes/view
- URL: /api/v1/tickets/{ticketId}/qr-codes/view
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use STAFF token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: Not own ticket → 403

#### GET /api/v1/tickets/{ticketId}/qr-codes/png
- URL: /api/v1/tickets/{ticketId}/qr-codes/png
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use STAFF token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: Not own ticket → 403

#### GET /api/v1/tickets/{ticketId}/qr-codes/pdf
- URL: /api/v1/tickets/{ticketId}/qr-codes/pdf
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use STAFF token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: Not own ticket → 403

### TicketValidationController

#### POST /api/v1/ticket-validations
- URL: /api/v1/ticket-validations
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: {"id":"uuid","method":"MANUAL"}
- ✅ Valid with ALL: same
- ❌ Missing id: {"method":"MANUAL"} → 400
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403

#### GET /api/v1/ticket-validations/events/{eventId}
- URL: /api/v1/ticket-validations/events/{eventId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: STAFF not assigned to event → 403

#### GET /api/v1/ticket-validations/tickets/{ticketId}
- URL: /api/v1/ticket-validations/tickets/{ticketId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: STAFF not assigned to event → 403

### EventStaffController

#### POST /api/v1/events/{eventId}/staff
- URL: /api/v1/events/{eventId}/staff
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: {"userId":"uuid"}
- ✅ Valid with ALL: same
- ❌ Missing userId: {} → 400
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### DELETE /api/v1/events/{eventId}/staff/{userId}
- URL: /api/v1/events/{eventId}/staff/{userId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/events/{eventId}/staff
- URL: /api/v1/events/{eventId}/staff
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

### AdminGovernanceController

#### POST /api/v1/admin/users/{userId}/roles
- URL: /api/v1/admin/users/{userId}/roles
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: {"roleName":"ATTENDEE"}
- ✅ Valid with ALL: same
- ❌ Missing roleName: {} → 400
- ❌ Invalid role: {"roleName":"INVALID"} → 400
- ❌ Wrong role: Use ORGANIZER token → 403
- ❌ Not approved: Use PENDING user → 403

#### DELETE /api/v1/admin/users/{userId}/roles/{roleName}
- URL: /api/v1/admin/users/{userId}/roles/{roleName}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ORGANIZER token → 403
- ❌ Not approved: Use PENDING user → 403

#### GET /api/v1/admin/users/{userId}/roles
- URL: /api/v1/admin/users/{userId}/roles
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ORGANIZER token → 403
- ❌ Not approved: Use PENDING user → 403

#### GET /api/v1/admin/roles
- URL: /api/v1/admin/roles
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ORGANIZER token → 403
- ❌ Not approved: Use PENDING user → 403

### ApprovalController

#### GET /api/v1/admin/approvals/pending
- URL: /api/v1/admin/approvals/pending
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ORGANIZER token → 403
- ❌ Not approved: Use PENDING user → 403

#### POST /api/v1/admin/approvals/{userId}/approve
- URL: /api/v1/admin/approvals/{userId}/approve
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ORGANIZER token → 403
- ❌ Not approved: Use PENDING user → 403

#### POST /api/v1/admin/approvals/{userId}/reject
- URL: /api/v1/admin/approvals/{userId}/reject
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: {"reason":"Reason for rejection"}
- ✅ Valid with ALL: same
- ❌ Missing reason: {} → 400
- ❌ Short reason: {"reason":"Short"} → 400
- ❌ Wrong role: Use ORGANIZER token → 403
- ❌ Not approved: Use PENDING user → 403

#### GET /api/v1/admin/approvals
- URL: /api/v1/admin/approvals
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ORGANIZER token → 403
- ❌ Not approved: Use PENDING user → 403

### AuditController

#### GET /api/v1/audit
- URL: /api/v1/audit
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ORGANIZER token → 403
- ❌ Not approved: Use PENDING user → 403

#### GET /api/v1/audit/events/{eventId}
- URL: /api/v1/audit/events/{eventId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/audit/me
- URL: /api/v1/audit/me
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Not approved: Use PENDING user → 403

### DiscountController

#### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- URL: /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid with ONLY mandatory: {"discountType":"PERCENTAGE","value":10.0,"validFrom":"2023-01-01T00:00","validTo":"2023-01-02T00:00"}
- ✅ Valid with ALL: {"discountType":"PERCENTAGE","value":10.0,"validFrom":"2023-01-01T00:00","validTo":"2023-01-02T00:00","active":true,"description":"Desc"}
- ❌ Missing mandatory: {"value":10.0,"validFrom":"2023-01-01T00:00","validTo":"2023-01-02T00:00"} → 400
- ❌ Invalid value: {"discountType":"PERCENTAGE","value":150.0,"validFrom":"2023-01-01T00:00","validTo":"2023-01-02T00:00"} → 400
- ❌ Past validFrom: {"discountType":"PERCENTAGE","value":10.0,"validFrom":"2020-01-01T00:00","validTo":"2023-01-02T00:00"} → 400
- ❌ validTo before validFrom: {"discountType":"PERCENTAGE","value":10.0,"validFrom":"2023-01-02T00:00","validTo":"2023-01-01T00:00"} → 400
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- URL: /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- Headers: Content-Type: application/json, Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid with ONLY mandatory: same as create
- ✅ Valid with ALL: same as create
- ❌ Same as create
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- URL: /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- URL: /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- URL: /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- Headers: Authorization: Bearer <token>
- Auth: JWT
- ✅ Valid: No body
- ❌ Wrong role: Use ATTENDEE token → 403
- ❌ Not approved: Use PENDING user → 403
- ❌ Ownership: ORGANIZER not owning event → 403

## Common Testing Mistakes & Why They Happened

- Why adding grant_type caused failures: The backend is a pure OAuth2 Resource Server that validates JWT tokens issued externally by Keycloak. It does not handle OAuth grants or token issuance, so including grant_type in requests leads to 400 errors as it's not expected.

- Why sending roles in request body is wrong: Roles are extracted from the JWT token claims (realm_access.roles or resource_access), not from the request body. Sending roles in the body is ignored and leads to authorization failures based on actual JWT roles.

- Why backend never issues tokens: The backend is designed as a resource server, not an authorization server. Token issuance is handled by Keycloak, and the backend only validates incoming tokens.

- Why earlier docs were misleading: Previous documentation likely included assumptions about OAuth flows (e.g., grant types, token endpoints) that the backend does not implement, leading to incorrect testing expectations.
