# Event Booking App - API Testing Guide (Codebase Derived)

This guide is generated from controller and DTO code under `src/main/java/com/event/tickets`.

## 1) Environment Setup

- Base URL: `http://localhost:8081`
- Auth: `Bearer <access_token>` for all endpoints except register.
- Content-Type for JSON APIs: `application/json`

### Recommended role tokens
- ADMIN token
- ORGANIZER token
- ATTENDEE token
- STAFF token

### Common headers
```http
Authorization: Bearer <token>
Content-Type: application/json
Accept: application/json
```

## 2) Common Error Response Shape

Most failures are returned as `ErrorDto` from `GlobalExceptionHandler`:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Validation failed on 2 field(s). See validationErrors for details.",
  "statusCode": 400,
  "statusDescription": "BAD REQUEST - Validation failed",
  "timestamp": "2026-03-18T10:30:20",
  "path": "/api/v1/auth/register",
  "validationErrors": [
    "email: Email is required"
  ]
}
```

Typical status codes you can get:
- `200` OK
- `201` Created
- `204` No Content
- `400` Bad Request (validation/illegal argument)
- `401` Unauthorized (missing/invalid token)
- `403` Forbidden (role or ownership restriction)
- `404` Not Found
- `405` Method Not Allowed
- `409` Conflict
- `422` Unprocessable Entity (registration flow failures)
- `500` Internal Server Error

---

## 3) Endpoint Matrix (Method, URL, Payload, Responses)

## Auth

### 3.1 Register User
- Method: `POST`
- URL: `/api/v1/auth/register`
- Auth: Public
- Payload:
```json
{
  "inviteCode": "ABCD-1234-EFGH-5678",
  "email": "user@example.com",
  "password": "StrongPass1!",
  "name": "John Doe"
}
```
- Success response:
  - `201 Created`
  - Body: `RegisterResponseDto` (email, assigned role, approval-required flags)
- Possible errors:
  - `400 VALIDATION_ERROR`
  - `404 INVITE_CODE_NOT_FOUND`
  - `409 EMAIL_ALREADY_REGISTERED`
  - `422 REGISTRATION_FAILED`

## Admin Governance

### 3.2 Assign Role to User
- Method: `POST`
- URL: `/api/v1/admin/users/{userId}/roles`
- Auth: `ADMIN`
- Payload:
```json
{
  "roleName": "STAFF"
}
```
- Success response: `200 OK`, body `UserRolesResponseDto`
- Possible errors: `400`, `403`, `404 USER_NOT_FOUND`, `500 KEYCLOAK_OPERATION_FAILED`

### 3.3 Revoke Role from User
- Method: `DELETE`
- URL: `/api/v1/admin/users/{userId}/roles/{roleName}`
- Auth: `ADMIN`
- Payload: none
- Success response: `200 OK`, body `UserRolesResponseDto`
- Possible errors: `403`, `404 USER_NOT_FOUND`, `500 KEYCLOAK_OPERATION_FAILED`

### 3.4 Get User Roles
- Method: `GET`
- URL: `/api/v1/admin/users/{userId}/roles`
- Auth: `ADMIN`
- Payload: none
- Success response: `200 OK`, body `UserRolesResponseDto`
- Possible errors: `403`, `404 USER_NOT_FOUND`

### 3.5 Get Available Roles
- Method: `GET`
- URL: `/api/v1/admin/roles`
- Auth: `ADMIN`
- Payload: none
- Success response: `200 OK`, body `AvailableRolesResponseDto`
- Possible errors: `403`, `500`

## Approval Workflow (Admin)

### 3.6 Get Pending Approvals
- Method: `GET`
- URL: `/api/v1/admin/approvals/pending?page=0&size=20`
- Auth: `ADMIN`
- Payload: none
- Success response: `200 OK`, body `Page<UserApprovalDto>`
- Possible errors: `403`

### 3.7 Approve User
- Method: `POST`
- URL: `/api/v1/admin/approvals/{userId}/approve`
- Auth: `ADMIN`
- Payload: none
- Success response:
```json
{
  "message": "User approved successfully",
  "userId": "uuid",
  "status": "APPROVED"
}
```
- Possible errors: `403`, `404 USER_NOT_FOUND`, `409 INVALID_APPROVAL_STATE`

### 3.8 Reject User
- Method: `POST`
- URL: `/api/v1/admin/approvals/{userId}/reject`
- Auth: `ADMIN`
- Payload:
```json
{
  "reason": "Incomplete onboarding details provided by applicant"
}
```
- Success response:
```json
{
  "message": "User rejected successfully",
  "userId": "uuid",
  "status": "REJECTED",
  "reason": "..."
}
```
- Possible errors: `400 VALIDATION_ERROR`, `403`, `404 USER_NOT_FOUND`, `409 INVALID_APPROVAL_STATE`

### 3.9 Get All Users with Approval Status
- Method: `GET`
- URL: `/api/v1/admin/approvals?page=0&size=20`
- Auth: `ADMIN`
- Payload: none
- Success response: `200 OK`, body `Page<UserApprovalDto>`
- Possible errors: `403`

## Events (Organizer)

### 3.10 Create Event
- Method: `POST`
- URL: `/api/v1/events`
- Auth: `ORGANIZER`
- Payload:
```json
{
  "name": "Tech Summit 2026",
  "start": "2026-04-25T10:00:00",
  "end": "2026-04-25T18:00:00",
  "venue": "Convention Center Hall A",
  "salesStart": "2026-03-20T00:00:00",
  "salesEnd": "2026-04-25T12:00:00",
  "status": "DRAFT",
  "maxCapacity": 500,
  "ticketTypes": [
    {
      "name": "General",
      "price": 99.99,
      "description": "General entry",
      "totalAvailable": 300
    },
    {
      "name": "VIP",
      "price": 249.99,
      "description": "VIP seat + perks",
      "totalAvailable": 100
    }
  ]
}
```
- Success response: `201 Created`, body `CreateEventResponseDto`
- Possible errors: `400 VALIDATION_ERROR`, `403`, `409 BUSINESS_RULE_VIOLATION`

### 3.11 Update Event
- Method: `PUT`
- URL: `/api/v1/events/{eventId}`
- Auth: `ORGANIZER`
- Payload:
```json
{
  "id": "event-uuid-optional-but-if-present-must-match-path",
  "name": "Tech Summit 2026 Updated",
  "start": "2026-04-25T10:00:00",
  "end": "2026-04-25T19:00:00",
  "venue": "Convention Center Hall B",
  "salesStart": "2026-03-20T00:00:00",
  "salesEnd": "2026-04-25T13:00:00",
  "status": "PUBLISHED",
  "maxCapacity": 600,
  "ticketTypes": [
    {
      "id": "ticket-type-uuid",
      "name": "General",
      "price": 109.99,
      "description": "Updated",
      "totalAvailable": 320
    }
  ]
}
```
- Success response: `200 OK`, body `UpdateEventResponseDto`
- Possible errors: `400 INVALID_ARGUMENT/VALIDATION_ERROR/EVENT_UPDATE_ERROR`, `403`, `404 EVENT_NOT_FOUND`

### 3.12 List Organizer Events
- Method: `GET`
- URL: `/api/v1/events?page=0&size=20`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `Page<ListEventResponseDto>`
- Possible errors: `403`

### 3.13 Get Organizer Event
- Method: `GET`
- URL: `/api/v1/events/{eventId}`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `GetEventDetailsResponseDto`
- Alternate success: `404 Not Found` if event not visible/does not exist

### 3.14 Delete Event
- Method: `DELETE`
- URL: `/api/v1/events/{eventId}`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `204 No Content`
- Possible errors: `403`, `404 EVENT_NOT_FOUND`, `409 BUSINESS_RULE_VIOLATION`

### 3.15 Sales Dashboard
- Method: `GET`
- URL: `/api/v1/events/{eventId}/sales-dashboard`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `Map<String,Object>` metrics
- Possible errors: `403`, `404 EVENT_NOT_FOUND`

### 3.16 Attendees Report
- Method: `GET`
- URL: `/api/v1/events/{eventId}/attendees-report`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `Map<String,Object>`
- Possible errors: `403`, `404 EVENT_NOT_FOUND`

### 3.17 Export Sales Report Excel
- Method: `GET`
- URL: `/api/v1/events/{eventId}/sales-report.xlsx`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, binary `.xlsx` file
- Headers: `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Possible errors: `403`, `404`, `500 REPORT_GENERATION_FAILED`

## Event Staff (Organizer)

### 3.18 Assign Staff to Event
- Method: `POST`
- URL: `/api/v1/events/{eventId}/staff`
- Auth: `ORGANIZER`
- Payload:
```json
{
  "userId": "staff-user-uuid"
}
```
- Success response: `201 Created`, body `EventStaffResponseDto`
- Possible errors: `400`, `403`, `404 USER_NOT_FOUND/EVENT_NOT_FOUND`, `409 BUSINESS_RULE_VIOLATION`

### 3.19 Remove Staff from Event
- Method: `DELETE`
- URL: `/api/v1/events/{eventId}/staff/{userId}`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `EventStaffResponseDto`
- Possible errors: `403`, `404`

### 3.20 List Event Staff
- Method: `GET`
- URL: `/api/v1/events/{eventId}/staff`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `EventStaffResponseDto`
- Possible errors: `403`, `404 EVENT_NOT_FOUND`

## Ticket Types and Ticket Purchase

### 3.21 Purchase Tickets
- Method: `POST`
- URL: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets`
- Auth: `ATTENDEE` or `ORGANIZER`
- Payload:
```json
{
  "quantity": 2
}
```
- Success response: `201 Created`, body `List<GetTicketResponseDto>`
- Possible errors: `400 VALIDATION_ERROR/TICKETS_SOLD_OUT`, `403`, `404 TICKET_TYPE_NOT_FOUND/EVENT_NOT_FOUND`, `409 BUSINESS_RULE_VIOLATION`

### 3.22 Create Ticket Type
- Method: `POST`
- URL: `/api/v1/events/{eventId}/ticket-types`
- Auth: `ORGANIZER`
- Payload:
```json
{
  "name": "Early Bird",
  "price": 49.99,
  "description": "Limited early bird tickets",
  "totalAvailable": 200
}
```
- Success response: `201 Created`, body `CreateTicketTypeResponseDto`
- Possible errors: `400`, `403`, `404 EVENT_NOT_FOUND`, `409 DATA_CONFLICT`

### 3.23 List Ticket Types
- Method: `GET`
- URL: `/api/v1/events/{eventId}/ticket-types`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `List<CreateTicketTypeResponseDto>`
- Possible errors: `403`, `404 EVENT_NOT_FOUND`

### 3.24 Get Ticket Type
- Method: `GET`
- URL: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `CreateTicketTypeResponseDto`
- Alternate success: `404 Not Found`

### 3.25 Update Ticket Type
- Method: `PUT`
- URL: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
- Auth: `ORGANIZER`
- Payload:
```json
{
  "id": "ticket-type-uuid",
  "name": "Early Bird Updated",
  "price": 59.99,
  "description": "Updated description",
  "totalAvailable": 180
}
```
- Success response: `200 OK`, body `UpdateTicketTypeResponseDto`
- Possible errors: `400`, `403`, `404 TICKET_TYPE_NOT_FOUND/EVENT_NOT_FOUND`, `409 BUSINESS_RULE_VIOLATION`

### 3.26 Delete Ticket Type
- Method: `DELETE`
- URL: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `204 No Content`
- Possible errors: `403`, `404`, `409 TICKET_TYPE_DELETE_NOT_ALLOWED`

## Discounts (Organizer)

### 3.27 Create Discount
- Method: `POST`
- URL: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts`
- Auth: `ORGANIZER`
- Payload:
```json
{
  "discountType": "PERCENTAGE",
  "value": 15.00,
  "validFrom": "2026-03-20T00:00:00",
  "validTo": "2026-04-01T23:59:59",
  "active": true,
  "description": "Spring promo"
}
```
- Success response: `201 Created`, body `DiscountResponseDto`
- Possible errors: `400`, `403`, `404`, `409 DISCOUNT_ALREADY_EXISTS`

### 3.28 Update Discount
- Method: `PUT`
- URL: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}`
- Auth: `ORGANIZER`
- Payload: same schema as create
- Success response: `200 OK`, body `DiscountResponseDto`
- Possible errors: `400`, `403`, `404 DISCOUNT_NOT_FOUND`, `409`

### 3.29 Delete Discount
- Method: `DELETE`
- URL: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `204 No Content`
- Possible errors: `403`, `404 DISCOUNT_NOT_FOUND`

### 3.30 Get Discount
- Method: `GET`
- URL: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `DiscountResponseDto`
- Possible errors: `403`, `404 DISCOUNT_NOT_FOUND`

### 3.31 List Discounts
- Method: `GET`
- URL: `/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts`
- Auth: `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `List<DiscountResponseDto>`
- Possible errors: `403`, `404`

## Tickets

### 3.32 List My Tickets
- Method: `GET`
- URL: `/api/v1/tickets?page=0&size=20`
- Auth: `ATTENDEE` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `Page<ListTicketResponseDto>`
- Possible errors: `403`

### 3.33 Get Ticket
- Method: `GET`
- URL: `/api/v1/tickets/{ticketId}`
- Auth: `ATTENDEE` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `GetTicketResponseDto`
- Alternate success: `404 Not Found`

### 3.34 Get Ticket QR (Legacy PNG)
- Method: `GET`
- URL: `/api/v1/tickets/{ticketId}/qr-codes`
- Auth: `ATTENDEE` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, binary PNG
- Possible errors: `403`, `404 QR_CODE_NOT_FOUND`

### 3.35 View Ticket QR Inline
- Method: `GET`
- URL: `/api/v1/tickets/{ticketId}/qr-codes/view`
- Auth: `ATTENDEE` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, binary PNG with inline disposition
- Possible errors: `403`, `404 QR_CODE_NOT_FOUND`

### 3.36 Download Ticket QR PNG
- Method: `GET`
- URL: `/api/v1/tickets/{ticketId}/qr-codes/png`
- Auth: `ATTENDEE` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, binary PNG attachment
- Possible errors: `403`, `404 QR_CODE_NOT_FOUND`

### 3.37 Download Ticket QR PDF
- Method: `GET`
- URL: `/api/v1/tickets/{ticketId}/qr-codes/pdf`
- Auth: `ATTENDEE` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, binary PDF attachment
- Possible errors: `403`, `404 QR_CODE_NOT_FOUND`

## Ticket Validation (Staff/Organizer)

### 3.38 Validate Ticket
- Method: `POST`
- URL: `/api/v1/ticket-validations`
- Auth: `STAFF` or `ORGANIZER`
- Payload:
```json
{
  "id": "ticket-or-qr-uuid",
  "method": "QR_SCAN"
}
```
- `method` values: `QR_SCAN`, `MANUAL`
- Success response: `200 OK`, body `TicketValidationResponseDto`
- Possible errors: `400 VALIDATION_ERROR`, `403`, `404`

### 3.39 List Validations for Event
- Method: `GET`
- URL: `/api/v1/ticket-validations/events/{eventId}?page=0&size=20`
- Auth: `STAFF` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `Page<TicketValidationResponseDto>`
- Possible errors: `403`, `404 EVENT_NOT_FOUND`

### 3.40 List Validations by Ticket
- Method: `GET`
- URL: `/api/v1/ticket-validations/tickets/{ticketId}`
- Auth: `STAFF` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `List<TicketValidationResponseDto>`
- Possible errors: `403`, `404 TICKET_NOT_FOUND`

## Published Events

### 3.41 List Published Events
- Method: `GET`
- URL: `/api/v1/published-events?q=music&page=0&size=20`
- Auth: `ATTENDEE`, `ORGANIZER`, or `STAFF`
- Payload: none
- Query params:
  - `q` optional text search
  - `page`, `size`, `sort` pageable params
- Success response: `200 OK`, body `Page<ListPublishedEventResponseDto>`

### 3.42 Get Published Event Details
- Method: `GET`
- URL: `/api/v1/published-events/{eventId}`
- Auth: `ATTENDEE`, `ORGANIZER`, or `STAFF`
- Payload: none
- Success response: `200 OK`, body `GetPublishedEventDetailsResponseDto`
- Alternate success: `404 Not Found`

## Invite Codes

### 3.43 Generate Invite Code
- Method: `POST`
- URL: `/api/v1/invites`
- Auth: `ADMIN` or `ORGANIZER`
- Payload:
```json
{
  "roleName": "STAFF",
  "eventId": "event-uuid-required-for-staff",
  "expirationHours": 24
}
```
- `roleName` allowed: `ADMIN`, `ORGANIZER`, `ATTENDEE`, `STAFF`
- Success response: `201 Created`, body `InviteCodeResponseDto`
- Possible errors: `400 INVALID_ARGUMENT/VALIDATION_ERROR`, `403`, `404 EVENT_NOT_FOUND`

### 3.44 Redeem Invite Code
- Method: `POST`
- URL: `/api/v1/invites/redeem`
- Auth: any authenticated user
- Payload:
```json
{
  "code": "ABCD-1234-EFGH-5678"
}
```
- Success response: `200 OK`, body `RedeemInviteCodeResponseDto`
- Possible errors: `400 INVALID_INVITE_CODE`, `404 INVITE_CODE_NOT_FOUND`, `403`

### 3.45 Revoke Invite Code
- Method: `DELETE`
- URL: `/api/v1/invites/{codeId}?reason=Revoked+by+admin`
- Auth: `ADMIN` or `ORGANIZER`
- Payload: none
- Success response: `204 No Content`
- Possible errors: `403`, `404 INVITE_CODE_NOT_FOUND`

### 3.46 List Invite Codes
- Method: `GET`
- URL: `/api/v1/invites?page=0&size=20`
- Auth: `ADMIN` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `Page<InviteCodeResponseDto>`

### 3.47 List Invite Codes by Event
- Method: `GET`
- URL: `/api/v1/invites/events/{eventId}?page=0&size=20`
- Auth: `ADMIN` or `ORGANIZER`
- Payload: none
- Success response: `200 OK`, body `Page<InviteCodeResponseDto>`
- Possible errors: `403`, `404 EVENT_NOT_FOUND`

---

## 4) Quick Test Flow (End-to-End)

1. Register user: `POST /api/v1/auth/register`
2. Admin approve user: `POST /api/v1/admin/approvals/{userId}/approve`
3. Organizer create event: `POST /api/v1/events`
4. Organizer create/publish ticket types and optional discount
5. Attendee list published events and purchase ticket
6. Attendee fetch QR (`/png` or `/pdf`)
7. Staff validate ticket via `POST /api/v1/ticket-validations`

---

## 5) Notes

- This guide reflects controller mappings and DTO validation annotations in code.
- Some business-rule errors are service-layer dependent and can vary by data state.
- For exhaustive payload/response examples, pair this guide with Swagger UI: `http://localhost:8081/swagger-ui.html`.

