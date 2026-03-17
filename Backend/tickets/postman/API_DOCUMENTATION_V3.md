# API Documentation V4

Generated from the fixed codebase after applying all 65 bug fixes.

**Changes from V3:**
- H-10: `roleName` now accepts `ADMIN` — pattern is `^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$`
- H-08: `GET /api/v1/invites` — ADMIN now sees ALL invite codes, not just own
- C-04: `PUT /api/v1/events/{eventId}` — `maxCapacity` field added to update DTO
- M-01: `POST` and `PUT /api/v1/events` — date ordering validated (end > start, salesEnd > salesStart)
- NEW-02: `PUT /api/v1/events/{eventId}` — CANCELLED event cannot be re-published
- NEW-01: `POST /api/v1/events/{eventId}/ticket-types` — `totalAvailable` is now mandatory
- H-03: `POST /api/v1/ticket-validations` — both `id` and `method` are `@NotNull`
- H-02: `POST /api/v1/ticket-validations` — CANCELLED ticket returns 400
- H-04: `POST /api/v1/ticket-validations` — response now includes `validatedById`, `validatedByName`, `validatedAt`
- H-13: QR Code endpoints — `QrCodeNotFoundException` now returns 404 (was 500)
- L-25: `GET /api/v1/tickets/{ticketId}` — response now includes `pricePaid`, `originalPrice`, `discountApplied`
- L-18/L-19: All `GET /api/v1/audit*` — response now includes `userAgent`
- H-05: Sales dashboard and attendees report exclude CANCELLED tickets from all counts and revenue
- Enum fix: ticket validation method value is `QR_SCAN` (not `QR_CODE`)

---

## Security Notes

- All endpoints except `/api/v1/auth/register` require authentication via `Authorization: Bearer <jwt-token>`.
- Roles are derived from JWT `realm_access.roles` claims — never from the request body.
- All authenticated endpoints are blocked for PENDING and REJECTED users via ApprovalGateFilter.
- Bypass paths (no approval check): `/api/v1/auth/register`, `/api/v1/invites/redeem`, `/actuator/health`, `/actuator/info`.

---

## Endpoints

### AuthController

#### POST /api/v1/auth/register
- Authentication required: No
- Required roles: None
- Approval requirement: No (bypass)
- Request body:
    - Mandatory fields:
        - `email`: String, @NotBlank, @Email, @Size(max=255)
        - `password`: String, @NotBlank, @Size(min=8, max=128), @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")
        - `name`: String, @NotBlank, @Size(min=2, max=100)
    - Optional fields:
        - `inviteCode`: String, @Pattern(regexp="^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")
- Success response: 201, `RegisterResponseDto`
  ```json
  {
    "message": "Registration successful! Your account is pending admin approval.",
    "email": "user@example.com",
    "requiresApproval": true,
    "assignedRole": "ATTENDEE",
    "instructions": "You will receive an email once your account has been reviewed."
  }
  ```
- Error responses:
    - 400: Validation errors
    - 400: Invalid invite code (`InvalidInviteCodeException`)
    - 409: Email already in use (`EmailAlreadyInUseException`)
    - 500: Keycloak operation failure

---

### InviteCodeController

#### POST /api/v1/invites
- Authentication required: Yes
- Required roles: ADMIN or ORGANIZER
- Approval requirement: Yes
- Request body:
    - Mandatory fields:
        - `roleName`: String, @NotBlank, **@Pattern(regexp="^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$")** ← V4 includes ADMIN
        - `expirationHours`: Integer, @NotNull, @Positive
    - Optional fields:
        - `eventId`: UUID (required when `roleName=STAFF`)
- Business rules:
    - ADMIN can create invites for any role including ADMIN
    - ORGANIZER can only create STAFF invites for events they own
    - STAFF role requires `eventId`; non-STAFF roles must NOT have `eventId`
- Success response: 201, `InviteCodeResponseDto`
  ```json
  {
    "id": "uuid",
    "code": "ABCD-1234-EFGH-5678",
    "roleName": "STAFF",
    "eventId": "uuid-or-null",
    "status": "PENDING",
    "createdAt": "2025-01-01T10:00:00",
    "expiresAt": "2025-01-03T10:00:00"
  }
  ```
- Error responses:
    - 400: Validation errors, business rule violations
    - 403: Wrong role, not approved

#### POST /api/v1/invites/redeem
- Authentication required: Yes
- Required roles: None (any authenticated user)
- Approval requirement: No (bypass)
- Request body:
    - Mandatory fields:
        - `code`: String, @NotBlank
- Success response: 200, `RedeemInviteCodeResponseDto`
- Error responses:
    - 400: Validation errors, invalid/expired/used code

#### DELETE /api/v1/invites/{codeId}
- Authentication required: Yes
- Required roles: ADMIN or ORGANIZER
- Approval requirement: Yes
- Request body: None
- Query params: `reason` (optional, default "Revoked by creator")
- Success response: 204
- Error responses:
    - 403: Not creator and not ADMIN
    - 404: Code not found

#### GET /api/v1/invites
- Authentication required: Yes
- Required roles: ADMIN or ORGANIZER
- Approval requirement: Yes
- **V4 CHANGE — ADMIN sees ALL codes, ORGANIZER sees only own codes**
- Query params: `page`, `size`, `sort` (Pageable)
- Success response: 200, `Page<InviteCodeResponseDto>`

#### GET /api/v1/invites/events/{eventId}
- Authentication required: Yes
- Required roles: ADMIN or ORGANIZER
- Approval requirement: Yes
- ADMIN bypass: ADMIN can see any event's invite codes without ownership check
- Success response: 200, `Page<InviteCodeResponseDto>`
- Error responses:
    - 403: ORGANIZER not owning event

---

### EventController

#### POST /api/v1/events
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Request body:
    - Mandatory fields:
        - `name`: String, @NotBlank
        - `venue`: String, @NotBlank
        - `status`: EventStatusEnum, @NotNull (`DRAFT` | `PUBLISHED` | `CANCELLED`)
        - `ticketTypes`: List<CreateTicketTypeRequestDto>, @NotEmpty, @Valid
    - Optional fields:
        - `start`: LocalDateTime
        - `end`: LocalDateTime
        - `salesStart`: LocalDateTime
        - `salesEnd`: LocalDateTime
        - `maxCapacity`: Integer
- **V4 VALIDATION: `end` must be after `start`; `salesEnd` must be after `salesStart` when both provided**
- **V4 TICKET TYPE: `totalAvailable` is now mandatory (@NotNull @Min(1)) in each ticket type**
- Success response: 201, `CreateEventResponseDto`
- Audits: `EVENT_CREATED` is now emitted on every successful create
- Error responses:
    - 400: Validation errors, date ordering violated

#### PUT /api/v1/events/{eventId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Request body:
    - Mandatory fields:
        - `id`: UUID (must match path `eventId`)
        - `name`: String, @NotBlank
        - `venue`: String, @NotBlank
        - `status`: EventStatusEnum, @NotNull
        - `ticketTypes`: List<UpdateTicketTypeRequestDto>, @NotEmpty, @Valid
    - Optional fields:
        - `start`: LocalDateTime
        - `end`: LocalDateTime
        - `salesStart`: LocalDateTime
        - `salesEnd`: LocalDateTime
        - `maxCapacity`: Integer ← **V4: now included (was silently dropped before)**
- **V4 VALIDATION: `end` must be after `start`; `salesEnd` must be after `salesStart`**
- **V4 RULE: Cannot set status away from CANCELLED (event is permanently cancelled)**
- **V4 RULE: `maxCapacity` cannot be set below currently active (non-CANCELLED) ticket count**
- Success response: 200, `UpdateEventResponseDto`
- Audits: `EVENT_UPDATED` is now emitted on every successful update
- Error responses:
    - 400: Date ordering violated, CANCELLED re-publish attempted, maxCapacity too low
    - 403: Not organizer of this event
    - 404: Event not found

#### GET /api/v1/events
- Authentication required: Yes
- Required roles: ORGANIZER
- Success response: 200, `Page<ListEventResponseDto>`

#### GET /api/v1/events/{eventId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Success response: 200, `GetEventDetailsResponseDto`
- Error responses: 403 not owner, 404 not found

#### DELETE /api/v1/events/{eventId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Business rule: Cannot delete if active (non-CANCELLED) tickets exist. Cancel the event first.
- Success response: 204
- Audits: `EVENT_DELETED` is now emitted before deletion

#### GET /api/v1/events/{eventId}/sales-dashboard
- Authentication required: Yes
- Required roles: ORGANIZER
- **V4 CHANGE: CANCELLED tickets are excluded from all counts and revenue totals**
- Success response: 200
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

#### GET /api/v1/events/{eventId}/attendees-report
- Authentication required: Yes
- Required roles: ORGANIZER
- **V4 CHANGE: CANCELLED tickets are excluded from attendees list**
- Success response: 200, attendees list with ticket status and validation count

#### GET /api/v1/events/{eventId}/sales-report.xlsx
- Authentication required: Yes
- Required roles: ORGANIZER
- Success response: 200, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

---

### TicketTypeController

#### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets (Purchase)
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Request body:
    - Optional fields:
        - `quantity`: Integer, @Min(1), @Max(10), default 1
- Business rules:
    - Event must be PUBLISHED
    - Current time must be within salesStart/salesEnd window
    - Cancelled ticket slots are freed back — previously sold+cancelled tickets do not permanently block capacity
- Success response: 201, `List<GetTicketResponseDto>`
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
- Error responses:
    - 400: Sold out (`TicketsSoldOutException`), event not published, outside sales window

#### POST /api/v1/events/{eventId}/ticket-types
- Authentication required: Yes
- Required roles: ORGANIZER
- Request body:
    - Mandatory fields:
        - `name`: String, @NotBlank
        - `price`: BigDecimal, @NotNull, @DecimalMin("0.00")
        - `totalAvailable`: Integer, **@NotNull, @Min(1)** ← **V4: now mandatory**
    - Optional fields:
        - `description`: String
- Success response: 201

#### GET /api/v1/events/{eventId}/ticket-types
- Authentication required: Yes
- Required roles: ORGANIZER
- Success response: 200, `List<CreateTicketTypeResponseDto>`

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Success response: 200

#### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Request body: same as create
- Business rule: `totalAvailable` cannot be set below count of active (non-CANCELLED) tickets already sold
- Success response: 200

#### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Business rule: Cannot delete if any tickets have been sold
- Success response: 204

---

### PublishedEventController

#### GET /api/v1/published-events
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER or STAFF
- Query params: `q` (full-text search, optional), `page`, `size`, `sort`
- Success response: 200, `Page<ListPublishedEventResponseDto>`

#### GET /api/v1/published-events/{eventId}
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER or STAFF
- Success response: 200, `GetPublishedEventDetailsResponseDto`
- Error responses: 404 not found or not published

---

### TicketController

#### GET /api/v1/tickets
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Success response: 200, `Page<ListTicketResponseDto>`

#### GET /api/v1/tickets/{ticketId}
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- **V4 RESPONSE: now includes `pricePaid`, `originalPrice`, `discountApplied`**
- Success response: 200, `GetTicketResponseDto`
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
- Error responses: 403 not owner, 404 not found

#### GET /api/v1/tickets/{ticketId}/qr-codes
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Success response: 200, `image/png` bytes
- **V4 ERROR: 404 (not 500) when QR code not found**

#### GET /api/v1/tickets/{ticketId}/qr-codes/view
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Success response: 200, `image/png` (inline display)
- **V4 ERROR: 404 for not found**

#### GET /api/v1/tickets/{ticketId}/qr-codes/png
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Success response: 200, `image/png` (download attachment)
- **V4 ERROR: 404 for not found**

#### GET /api/v1/tickets/{ticketId}/qr-codes/pdf
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Success response: 200, `application/pdf`
- **V4 ERROR: 404 for not found**

---

### TicketValidationController

#### POST /api/v1/ticket-validations
- Authentication required: Yes
- Required roles: STAFF or ORGANIZER
- Approval requirement: Yes
- Request body:
    - Mandatory fields:
        - `id`: UUID, **@NotNull** ← V4: explicit not-null constraint
        - `method`: TicketValidationMethod, **@NotNull** ← V4: explicit not-null constraint
            - Valid values: `MANUAL`, `QR_SCAN` ← **V4 FIX: value is `QR_SCAN`, not `QR_CODE`**
            - `MANUAL`: `id` is the ticket UUID
            - `QR_SCAN`: `id` is the QR code UUID (scanned from image)
- **V4 RULE: CANCELLED tickets are rejected with 400 — cannot validate a cancelled ticket**
- Success response: 200, `TicketValidationResponseDto`
  ```json
  {
    "ticketId": "uuid",
    "status": "VALID",
    "validatedById": "uuid",
    "validatedByName": "John Staff",
    "validatedAt": "2025-12-15T10:23:45"
  }
  ```
    - `status`: `VALID` on first scan, `INVALID` on subsequent scans
    - **V4: `validatedById`, `validatedByName`, `validatedAt` now populated (were always null before)**
- Error responses:
    - 400: Validation errors, `id` or `method` is null, CANCELLED ticket (H-02)
    - 403: Not staff/organizer for this event
    - 404: Ticket not found, QR code not found (H-13: now 404, was 500)

#### GET /api/v1/ticket-validations/events/{eventId}
- Authentication required: Yes
- Required roles: STAFF or ORGANIZER
- Success response: 200, `Page<TicketValidationResponseDto>`

#### GET /api/v1/ticket-validations/tickets/{ticketId}
- Authentication required: Yes
- Required roles: STAFF or ORGANIZER
- Success response: 200, `List<TicketValidationResponseDto>`

---

### EventStaffController

#### POST /api/v1/events/{eventId}/staff
- Authentication required: Yes
- Required roles: ORGANIZER
- Request body:
    - Mandatory fields:
        - `userId`: UUID, @NotNull
- Success response: 201
- Error responses: 400 user not STAFF role

#### DELETE /api/v1/events/{eventId}/staff/{userId}
- Authentication required: Yes
- Required roles: ORGANIZER
- **V4 RULE: Returns 400 if user is not currently assigned to staff (was silent no-op before)**
- Success response: 200
- Error responses: 400 user not assigned to this event's staff

#### GET /api/v1/events/{eventId}/staff
- Authentication required: Yes
- Required roles: ORGANIZER
- Success response: 200, `List<StaffMemberDto>`

---

### AdminGovernanceController

#### POST /api/v1/admin/users/{userId}/roles
- Authentication required: Yes
- Required roles: ADMIN
- Request body:
    - Mandatory fields:
        - `roleName`: String, @NotBlank, @Pattern(regexp="^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$")
- Success response: 200, `UserRolesResponseDto`

#### DELETE /api/v1/admin/users/{userId}/roles/{roleName}
- Authentication required: Yes
- Required roles: ADMIN
- Success response: 200

#### GET /api/v1/admin/users/{userId}/roles
- Authentication required: Yes
- Required roles: ADMIN
- Success response: 200, `UserRolesResponseDto`
- Note: This is now the recommended way to get a specific user's roles. The approval list endpoints no longer make individual Keycloak calls per user (M-03 fix).

#### GET /api/v1/admin/roles
- Authentication required: Yes
- Required roles: ADMIN
- Success response: 200, `AvailableRolesResponseDto`

---

### ApprovalController

#### GET /api/v1/admin/approvals/pending
- Authentication required: Yes
- Required roles: ADMIN
- **V4 CHANGE: No longer makes one Keycloak API call per user. `roles` field is empty in list responses — use GET /api/v1/admin/users/{userId}/roles for role details on a specific user.**
- Success response: 200, `Page<UserApprovalDto>`

#### POST /api/v1/admin/approvals/{userId}/approve
- Authentication required: Yes
- Required roles: ADMIN
- Success response: 200

#### POST /api/v1/admin/approvals/{userId}/reject
- Authentication required: Yes
- Required roles: ADMIN
- Request body:
    - Mandatory fields:
        - `reason`: String, @NotBlank, @Size(min=10, max=500)
- Success response: 200

#### GET /api/v1/admin/approvals
- Authentication required: Yes
- Required roles: ADMIN
- **V4 CHANGE: Same as pending — `roles` field is empty in list responses**
- Success response: 200, `Page<UserApprovalDto>`

---

### AuditController

#### GET /api/v1/audit
- Authentication required: Yes
- Required roles: ADMIN
- **V4 CHANGE: Response now includes `userAgent` field**
- Success response: 200, `Page<AuditLogDto>`
  ```json
  {
    "content": [
      {
        "id": "uuid",
        "action": "TICKET_PURCHASED",
        "actorId": "uuid",
        "actorName": "Jane Doe",
        "targetUserId": "uuid",
        "eventId": "uuid",
        "resourceType": "TICKET",
        "resourceId": "uuid",
        "details": "ticketType=Early Bird,quantity=2",
        "ipAddress": "192.168.1.100",
        "userAgent": "Mozilla/5.0 ...",
        "createdAt": "2025-12-15T10:30:00"
      }
    ]
  }
  ```

#### GET /api/v1/audit/events/{eventId}
- Authentication required: Yes
- Required roles: ORGANIZER (must own the event)
- Success response: 200, `Page<AuditLogDto>` with `userAgent` field

#### GET /api/v1/audit/me
- Authentication required: Yes
- Required roles: None (authenticated)
- Success response: 200, `Page<AuditLogDto>` with `userAgent` field

---

### DiscountController

#### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- Authentication required: Yes
- Required roles: ORGANIZER
- Request body:
    - Mandatory fields:
        - `discountType`: DiscountType, @NotNull (`PERCENTAGE` | `FIXED_AMOUNT`)
        - `value`: BigDecimal, @NotNull, @DecimalMin("0.01")
        - `validFrom`: LocalDateTime, @NotNull, @FutureOrPresent
        - `validTo`: LocalDateTime, @NotNull, @Future
    - Optional fields:
        - `active`: Boolean
        - `description`: String
- Business rules:
    - `validTo` must be after `validFrom`
    - PERCENTAGE: value must be 0–100
    - FIXED_AMOUNT: value must be positive; if greater than ticket price, final price is clamped to 0
- Success response: 201, `DiscountResponseDto`
- Error responses:
    - 400: Validation errors, business rule violations
    - 409: Active discount already exists (`DiscountAlreadyExistsException`)

#### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- Same as POST
- Success response: 200

#### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- Success response: 204

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- Success response: 200, `DiscountResponseDto`

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- Success response: 200, `List<DiscountResponseDto>`

---

## Common Testing Mistakes

**Why `roleName: "ADMIN"` previously returned 400:**
The `@Pattern` on `GenerateInviteCodeRequestDto` excluded ADMIN. Fixed in V4 — ADMIN is now a valid `roleName`.

**Why ORGANIZER couldn't see all invite codes as ADMIN:**
`GET /api/v1/invites` called `listInviteCodesByCreator()` for both roles. ADMINs now call `listAllInviteCodes()`.

**Why QR scans were always failing:**
The old collection used `"method": "QR_CODE"` which is not a valid enum value. The correct value is `"method": "QR_SCAN"`.

**Why ticket validation always returned null for `validatedById`, `validatedByName`, `validatedAt`:**
The mapper only mapped `ticketId`. Fixed in V4 — all fields are now mapped.

**Why the sales dashboard showed inflated revenue:**
CANCELLED tickets were included in sold counts and revenue sums. Fixed in V4 — only PURCHASED status tickets count.

**Why updating an event silently lost the venue capacity:**
`UpdateEventRequestDto` was missing the `maxCapacity` field. Fixed in V4 — always include `maxCapacity` in update requests.

**Why a CANCELLED ticket could be scanned as VALID:**
The validation logic only checked prior scan records, not the ticket's own status. Fixed in V4 — CANCELLED tickets return 400.