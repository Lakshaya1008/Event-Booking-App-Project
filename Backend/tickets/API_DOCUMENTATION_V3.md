# API Documentation V3

This documentation is generated strictly from the backend codebase. It includes all endpoints, their security requirements, request/response structures, and validations.

## Security Notes

- All endpoints except /api/v1/auth/register require authentication via JWT token in Authorization header.
- Roles are derived from JWT claims, not request body.
- grant_type is NOT used here.
- Approval is required for all authenticated endpoints except bypass paths.

## Endpoints

### AuthController

#### POST /api/v1/auth/register
- Authentication required: No
- Required roles: None
- Approval requirement: No (bypass)
- Controller method: AuthController.register
- Request body:
  - Mandatory fields:
    - email: String, @NotBlank, @Email, @Size(max=255)
    - password: String, @NotBlank, @Size(min=8, max=128), @Pattern(regex="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")
    - name: String, @NotBlank, @Size(min=2, max=100)
  - Optional fields:
    - inviteCode: String, @Pattern(regex="^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")
- Success response: 201, RegisterResponseDto
- Error responses:
  - Validation errors: 400
  - Email already in use: 409, EmailAlreadyInUseException
  - Invalid invite code: 400, InvalidInviteCodeException
  - Keycloak operation failure: 500, KeycloakOperationException

### InviteCodeController

#### POST /api/v1/invites
- Authentication required: Yes
- Required roles: ADMIN or ORGANIZER
- Approval requirement: Yes
- Controller method: InviteCodeController.generateInviteCode
- Request body:
  - Mandatory fields:
    - roleName: String, @NotBlank, @Pattern(regex="^(ORGANIZER|ATTENDEE|STAFF)$")
    - expirationHours: Integer, @NotNull, @Positive
  - Optional fields:
    - eventId: UUID (required if roleName=STAFF)
- Success response: 201, InviteCodeResponseDto
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - Business rule errors: 400, if ORGANIZER tries non-STAFF or STAFF without eventId, or if not owner of event

#### POST /api/v1/invites/redeem
- Authentication required: Yes
- Required roles: None (authenticated)
- Approval requirement: No (bypass)
- Controller method: InviteCodeController.redeemInviteCode
- Request body:
  - Mandatory fields:
    - code: String, @NotBlank
- Success response: 200, RedeemInviteCodeResponseDto
- Error responses:
  - Validation errors: 400
  - Invalid invite code: 400, InvalidInviteCodeException
  - Invite code used or expired: 400

#### DELETE /api/v1/invites/{codeId}
- Authentication required: Yes
- Required roles: ADMIN or ORGANIZER
- Approval requirement: Yes
- Controller method: InviteCodeController.revokeInviteCode
- Request body: None
- Success response: 204
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not found: 404
  - Business rule: 400, if not creator or ADMIN

#### GET /api/v1/invites
- Authentication required: Yes
- Required roles: ADMIN or ORGANIZER
- Approval requirement: Yes
- Controller method: InviteCodeController.listInviteCodes
- Request body: None
- Success response: 200, Page<InviteCodeResponseDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403

#### GET /api/v1/invites/events/{eventId}
- Authentication required: Yes
- Required roles: ADMIN or ORGANIZER
- Approval requirement: Yes
- Controller method: InviteCodeController.listEventInviteCodes
- Request body: None
- Success response: 200, Page<InviteCodeResponseDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403

### EventController

#### POST /api/v1/events
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventController.createEvent
- Request body:
  - Mandatory fields:
    - name: String, @NotBlank
    - venue: String, @NotBlank
    - status: EventStatusEnum, @NotNull
    - ticketTypes: List<CreateTicketTypeRequestDto>, @NotEmpty, @Valid
  - Optional fields:
    - start: LocalDateTime
    - end: LocalDateTime
    - salesStart: LocalDateTime
    - salesEnd: LocalDateTime
- Success response: 201, CreateEventResponseDto
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403

#### PUT /api/v1/events/{eventId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventController.updateEvent
- Request body:
  - Mandatory fields:
    - name: String, @NotBlank
    - venue: String, @NotBlank
    - status: EventStatusEnum, @NotNull
    - ticketTypes: List<UpdateTicketTypeRequestDto>, @NotEmpty, @Valid
  - Optional fields:
    - id: UUID (ignored, from path)
    - start: LocalDateTime
    - end: LocalDateTime
    - salesStart: LocalDateTime
    - salesEnd: LocalDateTime
- Success response: 200, UpdateEventResponseDto
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/events
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventController.listEvents
- Request body: None
- Success response: 200, Page<ListEventResponseDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403

#### GET /api/v1/events/{eventId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventController.getEvent
- Request body: None
- Success response: 200, GetEventDetailsResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### DELETE /api/v1/events/{eventId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventController.deleteEvent
- Request body: None
- Success response: 204
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/events/{eventId}/sales-dashboard
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventController.getSalesDashboard
- Request body: None
- Success response: 200, Map<String, Object>
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/events/{eventId}/attendees-report
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventController.getAttendeesReport
- Request body: None
- Success response: 200, Map<String, Object>
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/events/{eventId}/sales-report.xlsx
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventController.exportSalesReportExcel
- Request body: None
- Success response: 200, byte[] (Excel file)
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

### TicketTypeController

#### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketTypeController.purchaseTicket
- Request body:
  - Mandatory fields:
    - quantity: Integer, @Min(1), @Max(10), default 1
- Success response: 201, List<GetTicketResponseDto>
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - Not found: 404
  - Business rule: 400, if sold out, TicketsSoldOutException

#### POST /api/v1/events/{eventId}/ticket-types
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: TicketTypeController.createTicketType
- Request body:
  - Mandatory fields:
    - name: String, @NotBlank
    - price: Double, @NotNull, @PositiveOrZero
  - Optional fields:
    - description: String
    - totalAvailable: Integer
- Success response: 201, CreateTicketTypeResponseDto
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403

#### GET /api/v1/events/{eventId}/ticket-types
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: TicketTypeController.listTicketTypes
- Request body: None
- Success response: 200, List<CreateTicketTypeResponseDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: TicketTypeController.getTicketType
- Request body: None
- Success response: 200, CreateTicketTypeResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: TicketTypeController.updateTicketType
- Request body:
  - Mandatory fields:
    - name: String, @NotBlank
    - price: Double, @NotNull, @PositiveOrZero
  - Optional fields:
    - id: UUID
    - description: String
    - totalAvailable: Integer
- Success response: 200, UpdateTicketTypeResponseDto
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: TicketTypeController.deleteTicketType
- Request body: None
- Success response: 204
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

### PublishedEventController

#### GET /api/v1/published-events
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Approval requirement: Yes
- Controller method: PublishedEventController.listPublishedEvents
- Request body: None
- Query params: q (optional), pageable
- Success response: 200, Page<ListPublishedEventResponseDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403

#### GET /api/v1/published-events/{eventId}
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Approval requirement: Yes
- Controller method: PublishedEventController.getPublishedEventDetails
- Request body: None
- Success response: 200, GetPublishedEventDetailsResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not found: 404

### TicketController

#### GET /api/v1/tickets
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketController.listTickets
- Request body: None
- Success response: 200, Page<ListTicketResponseDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403

#### GET /api/v1/tickets/{ticketId}
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketController.getTicket
- Request body: None
- Success response: 200, GetTicketResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/tickets/{ticketId}/qr-codes
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketController.getTicketQrCode
- Request body: None
- Success response: 200, byte[] (PNG)
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/tickets/{ticketId}/qr-codes/view
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketController.viewQrCode
- Request body: None
- Success response: 200, byte[] (PNG)
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/tickets/{ticketId}/qr-codes/png
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketController.downloadQrCodePng
- Request body: None
- Success response: 200, byte[] (PNG)
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/tickets/{ticketId}/qr-codes/pdf
- Authentication required: Yes
- Required roles: ATTENDEE or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketController.downloadQrCodePdf
- Request body: None
- Success response: 200, byte[] (PDF)
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

### TicketValidationController

#### POST /api/v1/ticket-validations
- Authentication required: Yes
- Required roles: STAFF or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketValidationController.validateTicket
- Request body:
  - Mandatory fields:
    - id: UUID
    - method: TicketValidationMethod
- Success response: 200, TicketValidationResponseDto
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - Not found: 404
  - Business rule: 400, if already validated

#### GET /api/v1/ticket-validations/events/{eventId}
- Authentication required: Yes
- Required roles: STAFF or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketValidationController.listValidationsForEvent
- Request body: None
- Success response: 200, Page<TicketValidationResponseDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner or not staff for event: 403

#### GET /api/v1/ticket-validations/tickets/{ticketId}
- Authentication required: Yes
- Required roles: STAFF or ORGANIZER
- Approval requirement: Yes
- Controller method: TicketValidationController.getValidationsByTicket
- Request body: None
- Success response: 200, List<TicketValidationResponseDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner or not staff for event: 403

### EventStaffController

#### POST /api/v1/events/{eventId}/staff
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventStaffController.assignStaffToEvent
- Request body:
  - Mandatory fields:
    - userId: UUID, @NotNull
- Success response: 201, EventStaffResponseDto
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - User not STAFF: 400

#### DELETE /api/v1/events/{eventId}/staff/{userId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventStaffController.removeStaffFromEvent
- Request body: None
- Success response: 200, EventStaffResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/events/{eventId}/staff
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: EventStaffController.listEventStaff
- Request body: None
- Success response: 200, EventStaffResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403

### AdminGovernanceController

#### POST /api/v1/admin/users/{userId}/roles
- Authentication required: Yes
- Required roles: ADMIN
- Approval requirement: Yes
- Controller method: AdminGovernanceController.assignRoleToUser
- Request body:
  - Mandatory fields:
    - roleName: String, @NotBlank, @Pattern(regex="^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$")
- Success response: 200, UserRolesResponseDto
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - User not found: 404
  - Keycloak error: 500

#### DELETE /api/v1/admin/users/{userId}/roles/{roleName}
- Authentication required: Yes
- Required roles: ADMIN
- Approval requirement: Yes
- Controller method: AdminGovernanceController.revokeRoleFromUser
- Request body: None
- Success response: 200, UserRolesResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - User not found: 404
  - Keycloak error: 500

#### GET /api/v1/admin/users/{userId}/roles
- Authentication required: Yes
- Required roles: ADMIN
- Approval requirement: Yes
- Controller method: AdminGovernanceController.getUserRoles
- Request body: None
- Success response: 200, UserRolesResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - User not found: 404

#### GET /api/v1/admin/roles
- Authentication required: Yes
- Required roles: ADMIN
- Approval requirement: Yes
- Controller method: AdminGovernanceController.getAvailableRoles
- Request body: None
- Success response: 200, AvailableRolesResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403

### ApprovalController

#### GET /api/v1/admin/approvals/pending
- Authentication required: Yes
- Required roles: ADMIN
- Approval requirement: Yes
- Controller method: ApprovalController.getPendingApprovals
- Request body: None
- Success response: 200, Page<UserApprovalDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403

#### POST /api/v1/admin/approvals/{userId}/approve
- Authentication required: Yes
- Required roles: ADMIN
- Approval requirement: Yes
- Controller method: ApprovalController.approveUser
- Request body: None
- Success response: 200, Map<String, String>
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - User not found: 404

#### POST /api/v1/admin/approvals/{userId}/reject
- Authentication required: Yes
- Required roles: ADMIN
- Approval requirement: Yes
- Controller method: ApprovalController.rejectUser
- Request body:
  - Mandatory fields:
    - reason: String, @NotBlank, @Size(min=10, max=500)
- Success response: 200, Map<String, String>
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - User not found: 404

#### GET /api/v1/admin/approvals
- Authentication required: Yes
- Required roles: ADMIN
- Approval requirement: Yes
- Controller method: ApprovalController.getAllUsersWithApprovalStatus
- Request body: None
- Success response: 200, Page<UserApprovalDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403

### AuditController

#### GET /api/v1/audit
- Authentication required: Yes
- Required roles: ADMIN
- Approval requirement: Yes
- Controller method: AuditController.getAllAuditLogs
- Request body: None
- Success response: 200, Page<AuditLogDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403

#### GET /api/v1/audit/events/{eventId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: AuditController.getEventAuditLogs
- Request body: None
- Success response: 200, Page<AuditLogDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403

#### GET /api/v1/audit/me
- Authentication required: Yes
- Required roles: None (authenticated)
- Approval requirement: Yes
- Controller method: AuditController.getMyAuditLogs
- Request body: None
- Success response: 200, Page<AuditLogDto>
- Error responses:
  - Approval errors: 403

### DiscountController

#### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: DiscountController.createDiscount
- Request body:
  - Mandatory fields:
    - discountType: DiscountType, @NotNull
    - value: BigDecimal, @NotNull, @DecimalMin("0.01")
    - validFrom: LocalDateTime, @NotNull, @FutureOrPresent
    - validTo: LocalDateTime, @NotNull, @Future
  - Optional fields:
    - active: Boolean
    - description: String
- Success response: 201, DiscountResponseDto
- Error responses:
  - Validation errors: 400
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Business rule: 409, if active discount exists, DiscountAlreadyExistsException
  - Business rule: 400, if validTo <= validFrom, or percentage out of range, or fixed negative

#### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: DiscountController.updateDiscount
- Request body: same as create
- Success response: 200, DiscountResponseDto
- Error responses: same as create, plus not found

#### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: DiscountController.deleteDiscount
- Request body: None
- Success response: 204
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: DiscountController.getDiscount
- Request body: None
- Success response: 200, DiscountResponseDto
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
  - Not found: 404

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- Authentication required: Yes
- Required roles: ORGANIZER
- Approval requirement: Yes
- Controller method: DiscountController.listDiscounts
- Request body: None
- Success response: 200, List<DiscountResponseDto>
- Error responses:
  - Role errors: 403
  - Approval errors: 403
  - Not owner: 403
