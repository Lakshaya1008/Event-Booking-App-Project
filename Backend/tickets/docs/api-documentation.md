# Event Booking Backend - API Documentation (Controller + DTO Audit)

Last audited: 2026-03-20  
Audited files:
- Controllers in `src/main/java/com/event/tickets/controllers`
- Security in `src/main/java/com/event/tickets/config/SecurityConfig.java`
- Approval gate in `src/main/java/com/event/tickets/filters/ApprovalGateFilter.java`
- DTOs in `src/main/java/com/event/tickets/domain/dtos`
- Error mappings in `src/main/java/com/event/tickets/controllers/GlobalExceptionHandler.java`

---

## 1) Base API Info

- Base URL (default): `http://localhost:8081`
- OpenAPI JSON: `/api-docs`
- Swagger UI: `/swagger-ui.html`
- Auth: Bearer JWT (Keycloak issuer configured by `spring.security.oauth2.resourceserver.jwt.issuer-uri`)

Public route:
- `POST /api/v1/auth/register`

Everything else requires authentication and may also require approval-gate pass.

---

## 2) Controller Audit (All Endpoints)

## 2.1 `AuthController` (`/api/v1/auth`)

- `POST /register`
  - Auth: Public
  - Request: `RegisterRequestDto`
  - Response: `201 RegisterResponseDto`

## 2.2 `ApprovalController` (`/api/v1/admin/approvals`)

- `GET /pending`
  - Auth: `ADMIN`
  - Response: `Page<UserApprovalDto>`
- `POST /{userId}/approve`
  - Auth: `ADMIN`
  - Response: `Map<String,String>`
- `POST /{userId}/reject`
  - Auth: `ADMIN`
  - Request: `RejectReasonDto`
  - Response: `Map<String,String>`
- `GET /`
  - Auth: `ADMIN`
  - Response: `Page<UserApprovalDto>`

## 2.3 `AdminGovernanceController` (`/api/v1/admin`)

- `POST /users/{userId}/roles`
  - Auth: `ADMIN`
  - Request: `AssignRoleRequestDto`
  - Response: `UserRolesResponseDto`
- `DELETE /users/{userId}/roles/{roleName}`
  - Auth: `ADMIN`
  - Response: `UserRolesResponseDto`
- `GET /users/{userId}/roles`
  - Auth: `ADMIN`
  - Response: `UserRolesResponseDto`
- `GET /roles`
  - Auth: `ADMIN`
  - Response: `AvailableRolesResponseDto`

## 2.4 `AuditController` (`/api/v1/audit`)

- `GET /`
  - Auth: `ADMIN`
  - Response: `Page<AuditLogDto>`
- `GET /events/{eventId}`
  - Auth: `ORGANIZER` (ownership enforced)
  - Response: `Page<AuditLogDto>`
- `GET /me`
  - Auth: any authenticated
  - Response: `Page<AuditLogDto>`

## 2.5 `DiscountController` (`/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts`)

- `POST /`
  - Auth: `ORGANIZER` (ownership enforced)
  - Request: `CreateDiscountRequestDto`
  - Response: `201 DiscountResponseDto`
- `PUT /{discountId}`
  - Auth: `ORGANIZER`
  - Request: `CreateDiscountRequestDto`
  - Response: `DiscountResponseDto`
- `DELETE /{discountId}`
  - Auth: `ORGANIZER`
  - Response: `204`
- `GET /{discountId}`
  - Auth: `ORGANIZER`
  - Response: `DiscountResponseDto`
- `GET /`
  - Auth: `ORGANIZER`
  - Response: `List<DiscountResponseDto>`

## 2.6 `EventController` (`/api/v1/events`)

- `POST /`
  - Auth: `ORGANIZER`
  - Request: `CreateEventRequestDto`
  - Response: `201 CreateEventResponseDto`
- `PUT /{eventId}`
  - Auth: `ORGANIZER`
  - Request: `UpdateEventRequestDto`
  - Response: `UpdateEventResponseDto`
- `GET /`
  - Auth: `ORGANIZER`
  - Response: `Page<ListEventResponseDto>`
- `GET /{eventId}`
  - Auth: `ORGANIZER`
  - Response: `GetEventDetailsResponseDto | 404`
- `DELETE /{eventId}`
  - Auth: `ORGANIZER`
  - Response: `204`
- `GET /{eventId}/sales-dashboard`
  - Auth: `ORGANIZER`
  - Response: `Map<String,Object>`
- `GET /{eventId}/attendees-report`
  - Auth: `ORGANIZER`
  - Response: `Map<String,Object>`
- `GET /{eventId}/sales-report.xlsx`
  - Auth: `ORGANIZER`
  - Response: `byte[]` (XLSX attachment)

## 2.7 `EventStaffController` (`/api/v1/events/{eventId}/staff`)

- `POST /`
  - Auth: `ORGANIZER`
  - Request: `AssignStaffRequestDto`
  - Response: `201 EventStaffResponseDto`
- `DELETE /{userId}`
  - Auth: `ORGANIZER`
  - Response: `EventStaffResponseDto`
- `GET /`
  - Auth: `ORGANIZER`
  - Response: `EventStaffResponseDto`

## 2.8 `InviteCodeController` (`/api/v1/invites`)

- `POST /`
  - Auth: `ADMIN` or `ORGANIZER`
  - Request: `GenerateInviteCodeRequestDto`
  - Response: `201 InviteCodeResponseDto`
- `POST /redeem`
  - Auth: authenticated (`isAuthenticated`)
  - Request: `RedeemInviteCodeRequestDto`
  - Response: `RedeemInviteCodeResponseDto`
- `DELETE /{codeId}`
  - Auth: `ADMIN` or `ORGANIZER`
  - Query param: `reason` optional
  - Response: `204`
- `GET /`
  - Auth: `ADMIN` or `ORGANIZER`
  - Response: `Page<InviteCodeResponseDto>`
- `GET /events/{eventId}`
  - Auth: `ADMIN` or `ORGANIZER` (ownership check for organizer)
  - Response: `Page<InviteCodeResponseDto>`

## 2.9 `PublishedEventController` (`/api/v1/published-events`)

- `GET /`
  - Auth: `ATTENDEE` or `ORGANIZER` or `STAFF`
  - Query param: `q` optional
  - Response: `Page<ListPublishedEventResponseDto>`
- `GET /{eventId}`
  - Auth: `ATTENDEE` or `ORGANIZER` or `STAFF`
  - Response: `GetPublishedEventDetailsResponseDto | 404`

## 2.10 `TicketController` (`/api/v1/tickets`)

- `GET /`
  - Auth: `ATTENDEE` or `ORGANIZER`
  - Response: `Page<ListTicketResponseDto>`
- `GET /{ticketId}`
  - Auth: `ATTENDEE` or `ORGANIZER`
  - Response: `GetTicketResponseDto | 404`
- `GET /{ticketId}/qr-codes`
  - Auth: `ATTENDEE` or `ORGANIZER`
  - Response: `image/png`
- `GET /{ticketId}/qr-codes/view`
  - Auth: `ATTENDEE` or `ORGANIZER`
  - Response: inline `image/png`
- `GET /{ticketId}/qr-codes/png`
  - Auth: `ATTENDEE` or `ORGANIZER`
  - Response: `image/png` attachment
- `GET /{ticketId}/qr-codes/pdf`
  - Auth: `ATTENDEE` or `ORGANIZER`
  - Response: `application/pdf` attachment

## 2.11 `TicketTypeController` (`/api/v1/events/{eventId}/ticket-types`)

- `POST /{ticketTypeId}/tickets`
  - Auth: `ATTENDEE` or `ORGANIZER`
  - Request: `PurchaseTicketRequestDto`
  - Response: `201 List<GetTicketResponseDto>`
- `POST /`
  - Auth: `ORGANIZER`
  - Request: `CreateTicketTypeRequestDto`
  - Response: `201 CreateTicketTypeResponseDto`
- `GET /`
  - Auth: `ORGANIZER`
  - Response: `List<CreateTicketTypeResponseDto>`
- `GET /{ticketTypeId}`
  - Auth: `ORGANIZER`
  - Response: `CreateTicketTypeResponseDto | 404`
- `PUT /{ticketTypeId}`
  - Auth: `ORGANIZER`
  - Request: `UpdateTicketTypeRequestDto`
  - Response: `UpdateTicketTypeResponseDto`
- `DELETE /{ticketTypeId}`
  - Auth: `ORGANIZER`
  - Response: `204`

## 2.12 `TicketValidationController` (`/api/v1/ticket-validations`)

- `POST /`
  - Auth: `STAFF` or `ORGANIZER`
  - Request: `TicketValidationRequestDto`
  - Response: `TicketValidationResponseDto`
- `GET /events/{eventId}`
  - Auth: `STAFF` or `ORGANIZER`
  - Response: `Page<TicketValidationResponseDto>`
- `GET /tickets/{ticketId}`
  - Auth: `STAFF` or `ORGANIZER`
  - Response: `List<TicketValidationResponseDto>`

---

## 3) DTO Audit (All DTOs)

## 3.1 Request DTOs (input)

- `RegisterRequestDto`
  - `inviteCode` (optional, regex `XXXX-XXXX-XXXX-XXXX`)
  - `email` (`@NotBlank`, `@Email`, max 255)
  - `password` (`@NotBlank`, size 8..128, complexity regex)
  - `name` (`@NotBlank`, size 2..100)

- `RejectReasonDto`
  - `reason` (`@NotBlank`, size 3..500)

- `AssignRoleRequestDto`
  - `roleName` (`@NotBlank`, pattern `ADMIN|ORGANIZER|ATTENDEE|STAFF`)

- `CreateEventRequestDto`
  - `name` required max 200
  - `start`, `end` required
  - `venue` required max 500
  - `salesStart`, `salesEnd`
  - `status` required
  - `maxCapacity` optional min 1
  - `ticketTypes` required non-empty + `@Valid`

- `UpdateEventRequestDto`
  - `id` optional (path is source of truth)
  - `name` required max 200
  - `start`, `end` required
  - `venue` required max 500
  - `salesStart`, `salesEnd`
  - `status` required
  - `maxCapacity` optional min 1
  - `ticketTypes` required non-empty + `@Valid`

- `CreateTicketTypeRequestDto`
  - `name` required
  - `price` required, decimal >= 0.00
  - `description`
  - `totalAvailable` optional, min 1 (null means unlimited)

- `UpdateTicketTypeRequestDto`
  - `id` optional
  - `name` required
  - `price` required, decimal >= 0.00
  - `description`
  - `totalAvailable` optional

- `PurchaseTicketRequestDto`
  - `quantity` min 1, max 10 (default 1)

- `CreateDiscountRequestDto`
  - `discountType` required
  - `value` required, decimal >= 0.01
  - `validFrom` required
  - `validTo` required, future
  - `active` optional
  - `description` optional

- `AssignStaffRequestDto`
  - `userId` required UUID

- `GenerateInviteCodeRequestDto`
  - `roleName` required, pattern `ADMIN|ORGANIZER|ATTENDEE|STAFF`
  - `eventId` optional (business rule dependent on role)
  - `expirationHours` required positive, max 8760

- `RedeemInviteCodeRequestDto`
  - `code` required non-blank

- `TicketValidationRequestDto`
  - `id` required UUID
  - `method` required enum (`MANUAL` / `QR_SCAN`)

## 3.2 Response DTOs (output)

- `RegisterResponseDto`
  - `message`, `email`, `requiresApproval`, `assignedRole`, `instructions`

- `UserApprovalDto`
  - user identity, `approvalStatus`, roles, timestamps (`createdAt`, `approvedAt`, `rejectedAt`), `rejectionReason`, `approvedByName`

- `AvailableRolesResponseDto`
  - `roles`, `message`

- `UserRolesResponseDto`
  - `userId`, `userName`, `email`, `roles`

- `AuditLogDto`
  - audit identity/action/actor/target/event/resource/details + `ipAddress`, `userAgent`, `createdAt`

- `CreateEventResponseDto`, `UpdateEventResponseDto`
  - event core fields + ticket type list + `createdAt`, `updatedAt`

- `ListEventResponseDto`
  - event list view with nested `ListEventTicketTypeResponseDto`

- `GetEventDetailsResponseDto`
  - event detail view with nested `GetEventDetailsTicketTypesResponseDto`

- `ListPublishedEventResponseDto`
  - `id`, `name`, `start`, `end`, `venue`

- `GetPublishedEventDetailsResponseDto`
  - published event detail + ticket types (`GetPublishedEventDetailsTicketTypesResponseDto`)

- `CreateTicketTypeResponseDto`, `UpdateTicketTypeResponseDto`
  - ticket type fields + timestamps

- `ListTicketResponseDto`
  - `id`, `status`, nested `ListTicketTicketTypeResponseDto`

- `GetTicketResponseDto`
  - ticket + pricing transparency fields:
    - `price` (base)
    - `pricePaid`
    - `originalPrice`
    - `discountApplied`
  - plus event detail fields

- `DiscountResponseDto`
  - discount metadata, validity window, state, timestamps

- `StaffMemberDto`
  - `userId`, `userName`, `email`

- `EventStaffResponseDto`
  - `eventId`, `eventName`, `staffMembers`, `totalStaffCount`

- `InviteCodeResponseDto`
  - invite metadata + status + creator/redeemer and timestamps

- `RedeemInviteCodeResponseDto`
  - `message`, `roleAssigned`, `eventName`, `currentRoles`

- `TicketValidationResponseDto`
  - `ticketId`, `status`, `validatedById`, `validatedByName`, `validatedAt`

- `ErrorDto`
  - standard error format fields:
    - `error`, `message`, `statusCode`, `statusDescription`, `timestamp`, `path`
    - `validationErrors` (list for validation failures)
    - `possibleCauses`, `solutions`

---

## 4) Error/Status Mapping (Audited)

From `GlobalExceptionHandler` + security handlers + controller behavior.

- `400 BAD_REQUEST`
  - validation errors (`MethodArgumentNotValidException`, `ConstraintViolationException`)
  - `IllegalArgumentException`, `InvalidInputException`, `TicketsSoldOutException`, `EventUpdateException`, `InvalidInviteCodeException`
- `401 UNAUTHORIZED`
  - auth failures (`AuthenticationException`, security entry point)
- `403 FORBIDDEN`
  - role/ownership denied (`AccessDeniedException`)
  - approval gate blocked: `APPROVAL_PENDING` / `APPROVAL_REJECTED` (filter-specific JSON)
- `404 NOT_FOUND`
  - event/ticket/ticket-type/user/invite/QR/discount not found
  - controller notFound responses for optional lookups
  - unknown endpoint (`NoHandlerFoundException`)
- `405 METHOD_NOT_ALLOWED`
  - wrong HTTP method
- `409 CONFLICT`
  - business-state violations, invalid approval transition, delete-not-allowed, discount exists, data integrity, duplicate email, keycloak creation conflict, concurrent modification (optimistic locking)
- `422 UNPROCESSABLE_ENTITY`
  - registration flow failure (`RegistrationException`)
- `500 INTERNAL_SERVER_ERROR`
  - QR generation, Keycloak operation, system configuration, report generation, catch-all

---

## 5) Security Notes (Important for Consumers)

- Role extraction supports Keycloak `realm_access.roles` and also `resource_access.event-ticket-platform-app.roles`.
- Passing role checks does not guarantee access; service layer also enforces ownership/business authorization.
- `ApprovalGateFilter` can deny authenticated users even if roles are correct.
- Swagger and actuator are explicitly excluded from approval-gate checks.

---

## 6) Known Integration Dependencies

- Keycloak (issuer + realm roles)
- PostgreSQL (local user approvals and domain data)
- For some registration/role flows, Keycloak Admin API connectivity is mandatory

---

## 7) Suggested Maintenance Workflow

When controllers/DTOs change:
1. Re-audit controller mappings and `@PreAuthorize` conditions.
2. Re-audit request DTO constraints.
3. Re-audit exception handlers and status mappings.
4. Update both docs:
   - `docs/api-testing-guide.md`
   - `docs/api-documentation.md`

