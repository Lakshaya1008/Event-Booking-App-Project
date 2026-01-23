# API Documentation V2

This document provides a complete, code-backed specification of all API endpoints in the Event Booking App backend. All information is derived directly from the codebase audit.

## Security & Authentication Overview

### Backend is a PURE OAuth2 Resource Server
- Backend NEVER issues tokens
- Backend NEVER uses password / authorization_code grants
- All tokens are issued ONLY by Keycloak
- Backend ONLY validates JWTs via issuer-uri

### Authentication Flow
- Frontend handles login/register UI and obtains JWTs from Keycloak
- Backend validates JWTs and extracts roles
- Roles are NOT sent during login (roles are embedded in JWT)
- Grant_type is NOT part of backend APIs

### Approval Gate
- All authenticated endpoints require admin approval by default
- ApprovalGateFilter enforces approval status (PENDING/APPROVED/REJECTED)
- Bypass paths: /api/v1/auth/register, /actuator/health, /actuator/info, /api/v1/invites/redeem

## Endpoints

### AuthController

#### POST /api/v1/auth/register
- **Required Authentication**: No
- **Required Role(s)**: None
- **Approval Requirement**: No (bypassed)
- **Controller Method**: AuthController.register()

**Request Body** (RegisterRequestDto):
- inviteCode (String, Optional): Invite code format XXXX-XXXX-XXXX-XXXX (DTO-level validation: @Pattern)
- email (String, Mandatory): @NotBlank, @Email, @Size(max=255)
- password (String, Mandatory): @NotBlank, @Size(min=8,max=128), @Pattern(uppercase, lowercase, digit)
- name (String, Mandatory): @NotBlank, @Size(min=2,max=100)

**Success Response** (201):
- RegisterResponseDto: message, email, requiresApproval, assignedRole, instructions

**Error Responses**:
- 400: Validation errors (DTO-level)
- 409: Email already in use (service-level)
- 400: Invalid invite code (service-level)

### ApprovalController

#### GET /api/v1/admin/approvals/pending
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN
- **Approval Requirement**: No (admin bypass)
- **Controller Method**: ApprovalController.getPendingApprovals()

**Request Body**: None

**Success Response** (200):
- Page<UserApprovalDto>

**Error Responses**:
- 403: Insufficient role (Spring Security)

#### POST /api/v1/admin/approvals/{userId}/approve
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN
- **Approval Requirement**: No (admin bypass)
- **Controller Method**: ApprovalController.approveUser()

**Request Body**: None

**Success Response** (200):
- Map<String, String>: message, userId, status

**Error Responses**:
- 403: Insufficient role
- 404: User not found (service-level)
- 400: User not in PENDING state (service-level)

#### POST /api/v1/admin/approvals/{userId}/reject
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN
- **Approval Requirement**: No (admin bypass)
- **Controller Method**: ApprovalController.rejectUser()

**Request Body** (RejectReasonDto):
- reason (String, Mandatory): @NotBlank, @Size(min=10,max=500)

**Success Response** (200):
- Map<String, String>: message, userId, status, reason

**Error Responses**:
- 403: Insufficient role
- 404: User not found
- 400: User not in PENDING state

#### GET /api/v1/admin/approvals
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN
- **Approval Requirement**: No (admin bypass)
- **Controller Method**: ApprovalController.getAllUsersWithApprovalStatus()

**Request Body**: None

**Success Response** (200):
- Page<UserApprovalDto>

**Error Responses**:
- 403: Insufficient role

### AuditController

#### GET /api/v1/audit
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN
- **Approval Requirement**: Yes
- **Controller Method**: AuditController.getAllAuditLogs()

**Request Body**: None

**Success Response** (200):
- Page<AuditLogDto>

**Error Responses**:
- 403: Insufficient role or approval

#### GET /api/v1/audit/events/{eventId}
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: AuditController.getEventAuditLogs()

**Request Body**: None

**Success Response** (200):
- Page<AuditLogDto>

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer (AuthorizationService)

#### GET /api/v1/audit/me
- **Required Authentication**: Yes
- **Required Role(s)**: None
- **Approval Requirement**: Yes
- **Controller Method**: AuditController.getMyAuditLogs()

**Request Body**: None

**Success Response** (200):
- Page<AuditLogDto>

**Error Responses**:
- 403: No approval

### DiscountController

#### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: DiscountController.createDiscount()

**Request Body** (CreateDiscountRequestDto):
- discountType (DiscountType, Mandatory): @NotNull
- value (BigDecimal, Mandatory): @NotNull, @DecimalMin(0.01)
- validFrom (LocalDateTime, Mandatory): @NotNull, @FutureOrPresent
- validTo (LocalDateTime, Mandatory): @NotNull, @Future
- active (Boolean, Optional): No backend-enforced constraint
- description (String, Optional): No backend-enforced constraint

**Success Response** (201):
- DiscountResponseDto

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer
- 400: Validation errors (DTO-level)
- 400: validTo <= validFrom (service-level)
- 400: Percentage value not 0-100 or fixed amount <=0 (service-level)
- 409: Active discount already exists for ticket type (service-level)

#### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: DiscountController.updateDiscount()

**Request Body**: Same as create

**Success Response** (200):
- DiscountResponseDto

**Error Responses**: Same as create, plus discount not found

#### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: DiscountController.deleteDiscount()

**Request Body**: None

**Success Response** (204)

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer
- 404: Discount not found

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: DiscountController.getDiscount()

**Request Body**: None

**Success Response** (200):
- DiscountResponseDto

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer
- 404: Discount not found

#### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: DiscountController.listDiscounts()

**Request Body**: None

**Success Response** (200):
- List<DiscountResponseDto>

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer

### EventController

#### POST /api/v1/events
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventController.createEvent()

**Request Body** (CreateEventRequestDto):
- name (String, Mandatory): @NotBlank
- venue (String, Mandatory): @NotBlank
- status (EventStatusEnum, Mandatory): @NotNull
- ticketTypes (List<CreateTicketTypeRequestDto>, Mandatory): @NotEmpty, @Valid
  - name (String, Mandatory): @NotBlank
  - price (Double, Mandatory): @NotNull, @PositiveOrZero
  - description (String, Optional): No backend-enforced constraint
  - totalAvailable (Integer, Optional): No backend-enforced constraint
- start (LocalDateTime, Optional): No backend-enforced constraint
- end (LocalDateTime, Optional): No backend-enforced constraint
- salesStart (LocalDateTime, Optional): No backend-enforced constraint
- salesEnd (LocalDateTime, Optional): No backend-enforced constraint

**Success Response** (201):
- CreateEventResponseDto

**Error Responses**:
- 403: Insufficient role or approval
- 400: Validation errors

#### PUT /api/v1/events/{eventId}
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventController.updateEvent()

**Request Body** (UpdateEventRequestDto):
- name (String, Mandatory): @NotBlank
- venue (String, Mandatory): @NotBlank
- status (EventStatusEnum, Mandatory): @NotNull
- ticketTypes (List<UpdateTicketTypeRequestDto>, Mandatory): @NotEmpty, @Valid
  - name (String, Mandatory): @NotBlank
  - price (Double, Mandatory): @NotNull, @PositiveOrZero
  - description (String, Optional): No backend-enforced constraint
  - totalAvailable (Integer, Optional): No backend-enforced constraint
  - id (UUID, Optional): No backend-enforced constraint
- start (LocalDateTime, Optional): No backend-enforced constraint
- end (LocalDateTime, Optional): No backend-enforced constraint
- salesStart (LocalDateTime, Optional): No backend-enforced constraint
- salesEnd (LocalDateTime, Optional): No backend-enforced constraint
- id (UUID, Optional): Ignored, sourced from URL

**Success Response** (200):
- UpdateEventResponseDto

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer
- 400: Validation errors

#### GET /api/v1/events
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventController.listEvents()

**Request Body**: None

**Success Response** (200):
- Page<ListEventResponseDto>

**Error Responses**:
- 403: Insufficient role or approval

#### GET /api/v1/events/{eventId}
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventController.getEvent()

**Request Body**: None

**Success Response** (200):
- GetEventDetailsResponseDto

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer
- 404: Event not found

#### DELETE /api/v1/events/{eventId}
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventController.deleteEvent()

**Request Body**: None

**Success Response** (204)

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer
- 404: Event not found

#### GET /api/v1/events/{eventId}/sales-dashboard
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventController.getSalesDashboard()

**Request Body**: None

**Success Response** (200):
- Map<String, Object>

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer

#### GET /api/v1/events/{eventId}/attendees-report
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventController.getAttendeesReport()

**Request Body**: None

**Success Response** (200):
- Map<String, Object>

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer

#### GET /api/v1/events/{eventId}/sales-report.xlsx
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventController.exportSalesReportExcel()

**Request Body**: None

**Success Response** (200):
- Excel file (application/vnd.openxmlformats-officedocument.spreadsheetml.sheet)

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer

### EventStaffController

#### POST /api/v1/events/{eventId}/staff
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventStaffController.assignStaffToEvent()

**Request Body** (AssignStaffRequestDto):
- userId (UUID, Mandatory): @NotNull

**Success Response** (200):
- EventStaffResponseDto

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer
- 400: Validation errors
- 404: User not found or no STAFF role (service-level)

#### DELETE /api/v1/events/{eventId}/staff/{userId}
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventStaffController.removeStaffFromEvent()

**Request Body**: None

**Success Response** (200):
- EventStaffResponseDto

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer
- 404: Staff assignment not found

#### GET /api/v1/events/{eventId}/staff
- **Required Authentication**: Yes
- **Required Role(s)**: ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: EventStaffController.listEventStaff()

**Request Body**: None

**Success Response** (200):
- List<StaffMemberDto>

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer

### InviteCodeController

#### POST /api/v1/invites
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN or ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: InviteCodeController.generateInviteCode()

**Request Body** (GenerateInviteCodeRequestDto):
- roleName (String, Mandatory): @NotBlank
- eventId (UUID, Optional): Required for STAFF role
- expirationHours (Integer, Optional): No backend-enforced constraint

**Success Response** (201):
- InviteCodeResponseDto

**Error Responses**:
- 403: Insufficient role, approval, or not event organizer (for STAFF invites)
- 400: Validation errors
- 400: Invalid role or missing eventId for STAFF

#### POST /api/v1/invites/redeem
- **Required Authentication**: Yes
- **Required Role(s)**: None
- **Approval Requirement**: No (bypassed)
- **Controller Method**: InviteCodeController.redeemInviteCode()

**Request Body** (RedeemInviteCodeRequestDto):
- code (String, Mandatory): @NotBlank

**Success Response** (200):
- RedeemInviteCodeResponseDto

**Error Responses**:
- 400: Invalid invite code (service-level)

#### DELETE /api/v1/invites/{codeId}
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN or ORGANIZER (creator)
- **Approval Requirement**: Yes
- **Controller Method**: InviteCodeController.revokeInviteCode()

**Request Body**: None

**Success Response** (204)

**Error Responses**:
- 403: Insufficient role or approval
- 404: Invite code not found

#### GET /api/v1/invites
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN or ORGANIZER
- **Approval Requirement**: Yes
- **Controller Method**: InviteCodeController.listInviteCodes()

**Request Body**: None

**Success Response** (200):
- Page<InviteCodeResponseDto>

**Error Responses**:
- 403: Insufficient role or approval

### AdminGovernanceController

#### POST /api/v1/admin/users/{userId}/roles
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN
- **Approval Requirement**: No (admin bypass)
- **Controller Method**: AdminGovernanceController.assignRoleToUser()

**Request Body** (AssignRoleRequestDto):
- roleName (String, Mandatory): @NotBlank

**Success Response** (200):
- UserRolesResponseDto

**Error Responses**:
- 403: Insufficient role
- 404: User not found

#### DELETE /api/v1/admin/users/{userId}/roles/{roleName}
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN
- **Approval Requirement**: No (admin bypass)
- **Controller Method**: AdminGovernanceController.revokeRoleFromUser()

**Request Body**: None

**Success Response** (200):
- UserRolesResponseDto

**Error Responses**:
- 403: Insufficient role
- 404: User not found

#### GET /api/v1/admin/users/{userId}/roles
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN
- **Approval Requirement**: No (admin bypass)
- **Controller Method**: AdminGovernanceController.getUserRoles()

**Request Body**: None

**Success Response** (200):
- UserRolesResponseDto

**Error Responses**:
- 403: Insufficient role
- 404: User not found

#### GET /api/v1/admin/roles
- **Required Authentication**: Yes
- **Required Role(s)**: ADMIN
- **Approval Requirement**: No (admin bypass)
- **Controller Method**: AdminGovernanceController.getAvailableRoles()

**Request Body**: None

**Success Response** (200):
- AvailableRolesResponseDto

**Error Responses**:
- 403: Insufficient role

## Why authentication issues occurred earlier

The backend logic was correct. Issues came from:
- Outdated/incorrect documentation expecting backend to handle OAuth2 grants
- Testing assumptions that backend issues tokens
- Expecting roles to be sent during login
- No backend auth logic change was required</content>
<parameter name="filePath">C:\Users\LAKSHAYA\Desktop\CODING\java\Projects\project 2 Event booking App\Event-Booking-App-Project\Backend\tickets\API_DOCUMENTATION_V2.md
