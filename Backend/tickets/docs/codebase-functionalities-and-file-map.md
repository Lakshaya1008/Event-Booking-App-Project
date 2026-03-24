# Backend Functionalities and File Map (Code Audit)

Generated from source code inspection (controllers/services/repositories/entities/tests), not from old API docs.

## Totals

- Major functional modules: **13**
- Controllers: **13**
- Service interfaces: **17**
- Service implementations: **14**
- Repositories: **9**
- Entities/enums: **18**
- DTOs: **39**
- Mappers: **5**
- Main test files: **19**

---

## 1) Authentication and Registration

**What it does**
- Public user registration endpoint
- Supports invite-based and no-invite registration paths
- Keycloak + DB consistency/rollback handling in service logic

**Primary files**
- `src/main/java/com/event/tickets/controllers/AuthController.java`
- `src/main/java/com/event/tickets/services/RegistrationService.java`
- `src/main/java/com/event/tickets/services/impl/RegistrationServiceImpl.java`
- `src/main/java/com/event/tickets/domain/dtos/RegisterRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/RegisterResponseDto.java`

**Related tests**
- `src/test/java/com/event/tickets/services/RegistrationServiceImplTest.java`

---

## 2) User Approval Workflow (Admin)

**What it does**
- List pending users
- Approve/reject users
- List all users with approval status

**Primary files**
- `src/main/java/com/event/tickets/controllers/ApprovalController.java`
- `src/main/java/com/event/tickets/services/ApprovalService.java`
- `src/main/java/com/event/tickets/services/impl/ApprovalServiceImpl.java`
- `src/main/java/com/event/tickets/domain/dtos/UserApprovalDto.java`
- `src/main/java/com/event/tickets/domain/dtos/RejectReasonDto.java`
- `src/main/java/com/event/tickets/domain/entities/ApprovalStatus.java`

**Related tests**
- `src/test/java/com/event/tickets/services/ApprovalServiceImplTest.java`

---

## 3) Approval Gate Filter (runtime access blocking)

**What it does**
- Blocks business endpoint access for users in `PENDING`/`REJECTED` states
- Bypasses allowed routes (register/redeem, actuator, swagger, docs)

**Primary files**
- `src/main/java/com/event/tickets/filters/ApprovalGateFilter.java`

**Related tests**
- `src/test/java/com/event/tickets/filters/ApprovalGateFilterTest.java`

---

## 4) Admin Governance (role management)

**What it does**
- Assign role to user
- Revoke role from user
- Get user roles
- Get available roles

**Primary files**
- `src/main/java/com/event/tickets/controllers/AdminGovernanceController.java`
- `src/main/java/com/event/tickets/services/KeycloakAdminService.java`
- `src/main/java/com/event/tickets/services/impl/KeycloakAdminServiceImpl.java`
- `src/main/java/com/event/tickets/domain/dtos/AssignRoleRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UserRolesResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/AvailableRolesResponseDto.java`

---

## 5) Invite Code Lifecycle

**What it does**
- Generate invite codes
- Redeem invite codes
- Revoke invite codes
- List invite codes (all/by creator/by event)

**Primary files**
- `src/main/java/com/event/tickets/controllers/InviteCodeController.java`
- `src/main/java/com/event/tickets/services/InviteCodeService.java`
- `src/main/java/com/event/tickets/services/impl/InviteCodeServiceImpl.java`
- `src/main/java/com/event/tickets/domain/entities/InviteCode.java`
- `src/main/java/com/event/tickets/domain/entities/InviteCodeStatus.java`
- `src/main/java/com/event/tickets/repositories/InviteCodeRepository.java`

**Related DTOs**
- `src/main/java/com/event/tickets/domain/dtos/GenerateInviteCodeRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/InviteCodeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/RedeemInviteCodeRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/RedeemInviteCodeResponseDto.java`

**Related tests**
- `src/test/java/com/event/tickets/services/InviteCodeServiceImplTest.java`

---

## 6) Invite Expiry Scheduler

**What it does**
- Periodically marks stale pending invite codes as expired

**Primary files**
- `src/main/java/com/event/tickets/scheduler/InviteCodeExpiryScheduler.java`

---

## 7) Event Management (Organizer)

**What it does**
- Create/update/list/get/delete events
- Sales dashboard and attendees report
- Excel sales report export

**Primary files**
- `src/main/java/com/event/tickets/controllers/EventController.java`
- `src/main/java/com/event/tickets/services/EventService.java`
- `src/main/java/com/event/tickets/services/impl/EventServiceImpl.java`
- `src/main/java/com/event/tickets/services/ExportService.java`
- `src/main/java/com/event/tickets/services/impl/ExportServiceImpl.java`
- `src/main/java/com/event/tickets/mappers/EventMapper.java`
- `src/main/java/com/event/tickets/repositories/EventRepository.java`

**Related DTOs**
- `src/main/java/com/event/tickets/domain/dtos/CreateEventRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/CreateEventResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UpdateEventRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UpdateEventResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ListEventResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GetEventDetailsResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ListEventTicketTypeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GetEventDetailsTicketTypesResponseDto.java`

**Related entities**
- `src/main/java/com/event/tickets/domain/entities/Event.java`
- `src/main/java/com/event/tickets/domain/entities/EventStatusEnum.java`

**Related tests**
- `src/test/java/com/event/tickets/services/EventServiceImplTest.java`
- `src/test/java/com/event/tickets/controllers/EventControllerUpdateTest.java`

---

## 8) Published Event Discovery

**What it does**
- List/search published events
- Get published event details

**Primary files**
- `src/main/java/com/event/tickets/controllers/PublishedEventController.java`
- `src/main/java/com/event/tickets/services/EventService.java`

**Related DTOs**
- `src/main/java/com/event/tickets/domain/dtos/ListPublishedEventResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GetPublishedEventDetailsResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GetPublishedEventDetailsTicketTypesResponseDto.java`

**Related tests**
- `src/test/java/com/event/tickets/controllers/PublishedEventControllerPageableSortIntegrationTest.java`

---

## 9) Event Staff Management

**What it does**
- Assign staff to event
- Remove staff from event
- List event staff

**Primary files**
- `src/main/java/com/event/tickets/controllers/EventStaffController.java`
- `src/main/java/com/event/tickets/services/EventStaffService.java`
- `src/main/java/com/event/tickets/services/impl/EventStaffServiceImpl.java`

**Related DTOs**
- `src/main/java/com/event/tickets/domain/dtos/AssignStaffRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/EventStaffResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/StaffMemberDto.java`

**Related tests**
- `src/test/java/com/event/tickets/services/EventStaffServiceImplTest.java`

---

## 10) Ticket Type Management + Ticket Purchase

**What it does**
- Organizer CRUD for ticket types
- Ticket purchase with event ownership/capacity/time checks

**Primary files**
- `src/main/java/com/event/tickets/controllers/TicketTypeController.java`
- `src/main/java/com/event/tickets/services/TicketTypeService.java`
- `src/main/java/com/event/tickets/services/impl/TicketTypeServiceImpl.java`
- `src/main/java/com/event/tickets/mappers/TicketTypeMapper.java`
- `src/main/java/com/event/tickets/mappers/TicketMapper.java`
- `src/main/java/com/event/tickets/repositories/TicketTypeRepository.java`
- `src/main/java/com/event/tickets/repositories/TicketRepository.java`

**Related DTOs/entities**
- `src/main/java/com/event/tickets/domain/dtos/CreateTicketTypeRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/CreateTicketTypeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UpdateTicketTypeRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UpdateTicketTypeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/PurchaseTicketRequestDto.java`
- `src/main/java/com/event/tickets/domain/entities/TicketType.java`
- `src/main/java/com/event/tickets/domain/entities/Ticket.java`
- `src/main/java/com/event/tickets/domain/entities/TicketStatusEnum.java`

**Related tests**
- `src/test/java/com/event/tickets/services/TicketTypeServiceImplTest.java`
- `src/test/java/com/event/tickets/repositories/TicketRepositoryTest.java`

---

## 11) Discount Management

**What it does**
- Create/update/delete/get/list discounts per ticket type
- Resolve active discount and compute final price

**Primary files**
- `src/main/java/com/event/tickets/controllers/DiscountController.java`
- `src/main/java/com/event/tickets/services/DiscountService.java`
- `src/main/java/com/event/tickets/services/impl/DiscountServiceImpl.java`
- `src/main/java/com/event/tickets/mappers/DiscountMapper.java`
- `src/main/java/com/event/tickets/repositories/DiscountRepository.java`

**Related DTOs/entities**
- `src/main/java/com/event/tickets/domain/dtos/CreateDiscountRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/DiscountResponseDto.java`
- `src/main/java/com/event/tickets/domain/entities/Discount.java`
- `src/main/java/com/event/tickets/domain/entities/DiscountType.java`

**Related tests**
- `src/test/java/com/event/tickets/services/DiscountServiceImplTest.java`
- `src/test/java/com/event/tickets/repositories/DiscountRepositoryTest.java`

---

## 12) Ticket Read APIs + QR Code Operations

**What it does**
- List/get user tickets
- QR image retrieval/view/download (PNG/PDF)
- QR filename generation

**Primary files**
- `src/main/java/com/event/tickets/controllers/TicketController.java`
- `src/main/java/com/event/tickets/services/TicketService.java`
- `src/main/java/com/event/tickets/services/impl/TicketServiceImpl.java`
- `src/main/java/com/event/tickets/services/QrCodeService.java`
- `src/main/java/com/event/tickets/services/impl/QrCodeServiceImpl.java`
- `src/main/java/com/event/tickets/repositories/QrCodeRepository.java`

**Related DTOs/entities**
- `src/main/java/com/event/tickets/domain/dtos/GetTicketResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ListTicketResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ListTicketTicketTypeResponseDto.java`
- `src/main/java/com/event/tickets/domain/entities/QrCode.java`
- `src/main/java/com/event/tickets/domain/entities/QrCodeStatusEnum.java`

**Related tests**
- `src/test/java/com/event/tickets/services/QrCodeServiceImplTest.java`

---

## 13) Ticket Validation (Check-in)

**What it does**
- Validate tickets manually or by QR
- List validations for event and ticket

**Primary files**
- `src/main/java/com/event/tickets/controllers/TicketValidationController.java`
- `src/main/java/com/event/tickets/services/TicketValidationService.java`
- `src/main/java/com/event/tickets/services/impl/TicketValidationServiceImpl.java`
- `src/main/java/com/event/tickets/mappers/TicketValidationMapper.java`
- `src/main/java/com/event/tickets/repositories/TicketValidationRepository.java`

**Related DTOs/entities**
- `src/main/java/com/event/tickets/domain/dtos/TicketValidationRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/TicketValidationResponseDto.java`
- `src/main/java/com/event/tickets/domain/entities/TicketValidation.java`
- `src/main/java/com/event/tickets/domain/entities/TicketValidationMethod.java`
- `src/main/java/com/event/tickets/domain/entities/TicketValidationStatusEnum.java`

**Related tests**
- `src/test/java/com/event/tickets/services/TicketValidationServiceImplTest.java`

---

## Cross-Cutting Functionalities

### Security and Access Control
- `src/main/java/com/event/tickets/config/SecurityConfig.java`
- `src/main/java/com/event/tickets/config/CustomSecurityErrorHandler.java`
- `src/main/java/com/event/tickets/filters/UserProvisioningFilter.java`
- `src/main/java/com/event/tickets/config/RateLimitingFilter.java`
- `src/main/java/com/event/tickets/config/PageableSizeValidator.java`
- `src/main/java/com/event/tickets/config/WebMvcConfig.java`
- Test: `src/test/java/com/event/tickets/filters/UserProvisioningFilterTest.java`

### Audit Logging
- `src/main/java/com/event/tickets/controllers/AuditController.java`
- `src/main/java/com/event/tickets/services/AuditLogService.java`
- `src/main/java/com/event/tickets/repositories/AuditLogRepository.java`
- DTO/entity: `src/main/java/com/event/tickets/domain/dtos/AuditLogDto.java`, `src/main/java/com/event/tickets/domain/entities/AuditLog.java`, `src/main/java/com/event/tickets/domain/entities/AuditAction.java`

### Exception Handling
- `src/main/java/com/event/tickets/controllers/GlobalExceptionHandler.java`
- `src/main/java/com/event/tickets/domain/dtos/ErrorDto.java`
- `src/main/java/com/event/tickets/exceptions/*.java`

### Startup/Health/Initialization
- `src/main/java/com/event/tickets/config/StartupDiagnosticsRunner.java`
- `src/main/java/com/event/tickets/config/KeycloakHealthIndicator.java`
- `src/main/java/com/event/tickets/config/KeycloakAdminConfig.java`
- `src/main/java/com/event/tickets/config/DatabaseInitializer.java`
- `src/main/java/com/event/tickets/config/DataInitializer.java`
- `src/main/java/com/event/tickets/services/SystemUserProvider.java`
- Tests:
  - `src/test/java/com/event/tickets/config/StartupDiagnosticsRunnerTest.java`
  - `src/test/java/com/event/tickets/config/KeycloakAdminConfigWiringTest.java`
  - `src/test/java/com/event/tickets/EventBookingAppApplicationTests.java`

### DB Migrations and Config
- `src/main/resources/db/migration/V1__initial_schema.sql`
- `src/main/resources/db/migration/V2__admin_bootstrap.sql`
- `src/main/resources/db/migration/V3__add_keycloak_sync_pending.sql`
- `src/main/resources/db/migration/V5__fix_qr_code_value_column.sql`
- `src/main/resources/db/migration/V6__fix_schema_entity_mismatches.sql`
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`

---

## Complete Package-Level File Lists

### Controllers (13)
- `src/main/java/com/event/tickets/controllers/AdminGovernanceController.java`
- `src/main/java/com/event/tickets/controllers/ApprovalController.java`
- `src/main/java/com/event/tickets/controllers/AuditController.java`
- `src/main/java/com/event/tickets/controllers/AuthController.java`
- `src/main/java/com/event/tickets/controllers/DiscountController.java`
- `src/main/java/com/event/tickets/controllers/EventController.java`
- `src/main/java/com/event/tickets/controllers/EventStaffController.java`
- `src/main/java/com/event/tickets/controllers/GlobalExceptionHandler.java`
- `src/main/java/com/event/tickets/controllers/InviteCodeController.java`
- `src/main/java/com/event/tickets/controllers/PublishedEventController.java`
- `src/main/java/com/event/tickets/controllers/TicketController.java`
- `src/main/java/com/event/tickets/controllers/TicketTypeController.java`
- `src/main/java/com/event/tickets/controllers/TicketValidationController.java`

### Service Interfaces (17)
- `src/main/java/com/event/tickets/services/ApprovalService.java`
- `src/main/java/com/event/tickets/services/AuditLogService.java`
- `src/main/java/com/event/tickets/services/AuthorizationService.java`
- `src/main/java/com/event/tickets/services/DiscountService.java`
- `src/main/java/com/event/tickets/services/EmailService.java`
- `src/main/java/com/event/tickets/services/EventService.java`
- `src/main/java/com/event/tickets/services/EventStaffService.java`
- `src/main/java/com/event/tickets/services/ExportService.java`
- `src/main/java/com/event/tickets/services/InviteCodeService.java`
- `src/main/java/com/event/tickets/services/KeycloakAdminService.java`
- `src/main/java/com/event/tickets/services/KeycloakUserService.java`
- `src/main/java/com/event/tickets/services/QrCodeService.java`
- `src/main/java/com/event/tickets/services/RegistrationService.java`
- `src/main/java/com/event/tickets/services/SystemUserProvider.java`
- `src/main/java/com/event/tickets/services/TicketService.java`
- `src/main/java/com/event/tickets/services/TicketTypeService.java`
- `src/main/java/com/event/tickets/services/TicketValidationService.java`

### Service Implementations (14)
- `src/main/java/com/event/tickets/services/impl/ApprovalServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/AuthorizationServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/DiscountServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/EmailServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/EventServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/EventStaffServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/ExportServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/InviteCodeServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/KeycloakAdminServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/QrCodeServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/RegistrationServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/TicketServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/TicketTypeServiceImpl.java`
- `src/main/java/com/event/tickets/services/impl/TicketValidationServiceImpl.java`

### Repositories (9)
- `src/main/java/com/event/tickets/repositories/AuditLogRepository.java`
- `src/main/java/com/event/tickets/repositories/DiscountRepository.java`
- `src/main/java/com/event/tickets/repositories/EventRepository.java`
- `src/main/java/com/event/tickets/repositories/InviteCodeRepository.java`
- `src/main/java/com/event/tickets/repositories/QrCodeRepository.java`
- `src/main/java/com/event/tickets/repositories/TicketRepository.java`
- `src/main/java/com/event/tickets/repositories/TicketTypeRepository.java`
- `src/main/java/com/event/tickets/repositories/TicketValidationRepository.java`
- `src/main/java/com/event/tickets/repositories/UserRepository.java`

### Entities and Enums (18)
- `src/main/java/com/event/tickets/domain/entities/ApprovalStatus.java`
- `src/main/java/com/event/tickets/domain/entities/AuditAction.java`
- `src/main/java/com/event/tickets/domain/entities/AuditLog.java`
- `src/main/java/com/event/tickets/domain/entities/Discount.java`
- `src/main/java/com/event/tickets/domain/entities/DiscountType.java`
- `src/main/java/com/event/tickets/domain/entities/Event.java`
- `src/main/java/com/event/tickets/domain/entities/EventStatusEnum.java`
- `src/main/java/com/event/tickets/domain/entities/InviteCode.java`
- `src/main/java/com/event/tickets/domain/entities/InviteCodeStatus.java`
- `src/main/java/com/event/tickets/domain/entities/QrCode.java`
- `src/main/java/com/event/tickets/domain/entities/QrCodeStatusEnum.java`
- `src/main/java/com/event/tickets/domain/entities/Ticket.java`
- `src/main/java/com/event/tickets/domain/entities/TicketStatusEnum.java`
- `src/main/java/com/event/tickets/domain/entities/TicketType.java`
- `src/main/java/com/event/tickets/domain/entities/TicketValidation.java`
- `src/main/java/com/event/tickets/domain/entities/TicketValidationMethod.java`
- `src/main/java/com/event/tickets/domain/entities/TicketValidationStatusEnum.java`
- `src/main/java/com/event/tickets/domain/entities/User.java`

### Domain Request Models (4)
- `src/main/java/com/event/tickets/domain/CreateEventRequest.java`
- `src/main/java/com/event/tickets/domain/UpdateEventRequest.java`
- `src/main/java/com/event/tickets/domain/CreateTicketTypeRequest.java`
- `src/main/java/com/event/tickets/domain/UpdateTicketTypeRequest.java`

### DTOs (39)
- `src/main/java/com/event/tickets/domain/dtos/AssignRoleRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/AssignStaffRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/AvailableRolesResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/AuditLogDto.java`
- `src/main/java/com/event/tickets/domain/dtos/CreateDiscountRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/CreateEventRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/CreateEventResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/CreateTicketTypeRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/CreateTicketTypeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/DiscountResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ErrorDto.java`
- `src/main/java/com/event/tickets/domain/dtos/EventStaffResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GenerateInviteCodeRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GetEventDetailsResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GetEventDetailsTicketTypesResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GetPublishedEventDetailsResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GetPublishedEventDetailsTicketTypesResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/GetTicketResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/InviteCodeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ListEventResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ListEventTicketTypeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ListPublishedEventResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ListTicketResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/ListTicketTicketTypeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/PurchaseTicketRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/RedeemInviteCodeRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/RedeemInviteCodeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/RegisterRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/RegisterResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/RejectReasonDto.java`
- `src/main/java/com/event/tickets/domain/dtos/StaffMemberDto.java`
- `src/main/java/com/event/tickets/domain/dtos/TicketValidationRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/TicketValidationResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UpdateEventRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UpdateEventResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UpdateTicketTypeRequestDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UpdateTicketTypeResponseDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UserApprovalDto.java`
- `src/main/java/com/event/tickets/domain/dtos/UserRolesResponseDto.java`

### Mappers (5)
- `src/main/java/com/event/tickets/mappers/DiscountMapper.java`
- `src/main/java/com/event/tickets/mappers/EventMapper.java`
- `src/main/java/com/event/tickets/mappers/TicketMapper.java`
- `src/main/java/com/event/tickets/mappers/TicketTypeMapper.java`
- `src/main/java/com/event/tickets/mappers/TicketValidationMapper.java`

### Filters (2)
- `src/main/java/com/event/tickets/filters/ApprovalGateFilter.java`
- `src/main/java/com/event/tickets/filters/UserProvisioningFilter.java`

### Config (12)
- `src/main/java/com/event/tickets/config/CustomSecurityErrorHandler.java`
- `src/main/java/com/event/tickets/config/DataInitializer.java`
- `src/main/java/com/event/tickets/config/DatabaseInitializer.java`
- `src/main/java/com/event/tickets/config/KeycloakAdminConfig.java`
- `src/main/java/com/event/tickets/config/KeycloakHealthIndicator.java`
- `src/main/java/com/event/tickets/config/PageableSizeValidator.java`
- `src/main/java/com/event/tickets/config/PostgreSQLEnumDialect.java`
- `src/main/java/com/event/tickets/config/RateLimitingFilter.java`
- `src/main/java/com/event/tickets/config/RestTemplateConfig.java`
- `src/main/java/com/event/tickets/config/SecurityConfig.java`
- `src/main/java/com/event/tickets/config/StartupDiagnosticsRunner.java`
- `src/main/java/com/event/tickets/config/WebMvcConfig.java`

### Utilities (4)
- `src/main/java/com/event/tickets/util/JwtUtil.java`
- `src/main/java/com/event/tickets/util/PriceUtil.java`
- `src/main/java/com/event/tickets/util/RequestUtil.java`
- `src/main/java/com/event/tickets/util/SystemUser.java`

### Test Files (19)
- `src/test/java/com/event/tickets/EventBookingAppApplicationTests.java`
- `src/test/java/com/event/tickets/config/KeycloakAdminConfigWiringTest.java`
- `src/test/java/com/event/tickets/config/StartupDiagnosticsRunnerTest.java`
- `src/test/java/com/event/tickets/config/TestSecurityConfig.java`
- `src/test/java/com/event/tickets/controllers/EventControllerUpdateTest.java`
- `src/test/java/com/event/tickets/controllers/PublishedEventControllerPageableSortIntegrationTest.java`
- `src/test/java/com/event/tickets/filters/ApprovalGateFilterTest.java`
- `src/test/java/com/event/tickets/filters/UserProvisioningFilterTest.java`
- `src/test/java/com/event/tickets/repositories/DiscountRepositoryTest.java`
- `src/test/java/com/event/tickets/repositories/TicketRepositoryTest.java`
- `src/test/java/com/event/tickets/services/ApprovalServiceImplTest.java`
- `src/test/java/com/event/tickets/services/DiscountServiceImplTest.java`
- `src/test/java/com/event/tickets/services/EventServiceImplTest.java`
- `src/test/java/com/event/tickets/services/EventStaffServiceImplTest.java`
- `src/test/java/com/event/tickets/services/InviteCodeServiceImplTest.java`
- `src/test/java/com/event/tickets/services/QrCodeServiceImplTest.java`
- `src/test/java/com/event/tickets/services/RegistrationServiceImplTest.java`
- `src/test/java/com/event/tickets/services/TicketTypeServiceImplTest.java`
- `src/test/java/com/event/tickets/services/TicketValidationServiceImplTest.java`

