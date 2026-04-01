# Event Booking App - Comprehensive Codebase Report
This document provides an exhaustive file-by-file breakdown of the entire application, detailing the purpose, dependencies, fields, behavior, and relevant code snippets for each component, along with all infrastructure configuration files.

## Folder: `src\main\java\com\event\tickets`
---

### File: `EventBookingAppApplication.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@EnableJpaAuditing, @EnableScheduling, @SpringBootApplication`
**Package**: `com.event.tickets`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class EventBookingAppApplication`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EventBookingAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventBookingAppApplication.class, args);
    }
}
```

<br>

## Folder: `src\main\java\com\event\tickets\config`
---

### File: `CustomSecurityErrorHandler.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component, @Slf4j, @lombok`
**Package**: `com.event.tickets.config`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.ErrorDto`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class CustomSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /**
     * L-15 FIX: ObjectMapper injected by Spring, not created with new.
     * new ObjectMapper() ignores Jackson auto-configuration — custom serializers,
     * date formats, and module registrations (Java time, Kotlin, etc.) set up
     * by Spring Boot's JacksonAutoConfiguration are bypassed. This caused
     * timestamp fields in error responses to serialize differently from the
     * main application JSON responses.
     */
    private final ObjectMapper objectMapper;

    @Overrid
```

<br>

### File: `DataInitializer.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component, @RequiredArgsConstructor, @Slf4j, @Order(1), @Transactional`
**Package**: `com.event.tickets.config`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.Ticket`
- `com.event.tickets.repositories.TicketRepository`

**File Code Structure:**
- **State/Properties (Fields):**
  - `TicketRepository ticketRepository`
- **Behavior/Capabilities (Methods):**
  - `void run(ApplicationArguments args)`

**Relevant Code Snippet:**
```java
public class DataInitializer implements ApplicationRunner {

    private final TicketRepository ticketRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Starting ticket pricing data initialization...");

        try {
            List<Ticket> ticketsNeedingUpdate = ticketRepository.findTicketsMissingPricingData();

            if (ticketsNeedingUpdate.isEmpty()) {
                log.info("No tickets require pricing backfill. All data is up-to-date.");
                return;
            }

            log.info("Found {} tickets req
```

<br>

### File: `DatabaseInitializer.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component("databaseInitializer"), @RequiredArgsConstructor, @Slf4j, @PostConstruct, @Transactional`
**Package**: `com.event.tickets.config`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.ApprovalStatus`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.KeycloakAdminService`
- `com.event.tickets.util.SystemUser`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class DatabaseInitializer`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class DatabaseInitializer {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

    /**
     * Runs synchronously at startup. Performs only DB operations — no Keycloak calls.
     * Fast and reliable even if Keycloak is temporarily unavailable.
     */
    @PostConstruct
    @Transactional
    public void initialize() {
        migrateExistingUsers();
        createSystemUser();
        validateDatabaseState();
        // FIX-DI2: Keycloak normalization is async — does not block startup
        normalizeKeycloakStateAsync();
  
```

<br>

### File: `KeycloakHealthIndicator.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component("keycloakHealthIndicator"), @RequiredArgsConstructor, @Slf4j`
**Package**: `com.event.tickets.config`

**Internal Dependencies (What it uses):**
- `com.event.tickets.services.KeycloakAdminService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class KeycloakHealthIndicator extends AbstractHealthIndicator {

    private final KeycloakAdminService keycloakAdminService;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            // Try a simple Keycloak API call to verify connectivity
            // This calls the admin API which is always available
            keycloakAdminService.getUserCount();

            builder
                .up()
                .withDetail("service", "Keycloak Admin API")
                .withDetail("status", "reachable");

            log.debug("Keycloak health
```

<br>

### File: `PageableSizeValidator.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component, @Slf4j, @Value("${spring.data.web.pageable.max-page-size:50}"), @Value("${spring.data.web.pageable.default-page-size:20}")`
**Package**: `com.event.tickets.config`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class PageableSizeValidator implements HandlerMethodArgumentResolver {

    @Value("${spring.data.web.pageable.max-page-size:50}")
    private int maxPageSize;

    @Value("${spring.data.web.pageable.default-page-size:20}")
    private int defaultPageSize;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Pageable.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
   
```

<br>

### File: `PostgreSQLEnumDialect.java`
**Description / What it does**: Application class component.
**Package**: `com.event.tickets.config`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class PostgreSQLEnumDialect extends PostgreSQLDialect {

  @Override
  public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
    super.contributeTypes(typeContributions, serviceRegistry);

    // Register VARCHAR as the JDBC type for enums instead of PostgreSQL ENUM
    JdbcTypeRegistry jdbcTypeRegistry = typeContributions.getTypeConfiguration()
        .getJdbcTypeRegistry();

    // This tells Hibernate to use VARCHAR(255) instead of creating custom ENUM types
    // The CHECK constraint in @Column annotation provides DB-level validation
 
```

<br>

### File: `RateLimitingFilter.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component, @Slf4j`
**Package**: `com.event.tickets.config`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class RateLimitingFilter implements Filter {

    // FIX #13: Caffeine cache with 1-hour expiry replaces unbounded ConcurrentHashMap
    private final Cache<String, Bucket> ipBuckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private final Cache<String, Bucket> authBuckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private final Cache<String, Bucket> userBuckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    @Override
 
```

<br>

### File: `SecurityConfig.java`
**Description / What it does**: Application configuration (Security, Beans, Web).
**Key Annotations**: `@Configuration, @EnableWebSecurity, @EnableMethodSecurity, @RequiredArgsConstructor, @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")`
**Package**: `com.event.tickets.config`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class SecurityConfig`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class SecurityConfig {

    private final CustomSecurityErrorHandler customSecurityErrorHandler;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    /**
     * FIX-SC1: Read client ID from config, not hardcoded.
     * Add keycloak.client-id to application.properties / environment variables.
     */
    @Value("${keycloak.client-id:ev
```

<br>

### File: `StartupDiagnosticsRunner.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component, @Order(2), @RequiredArgsConstructor, @Slf4j`
**Package**: `com.event.tickets.config`

**Internal Dependencies (What it uses):**
- `com.event.tickets.services.KeycloakAdminService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class StartupDiagnosticsRunner implements ApplicationRunner {

    private final KeycloakAdminService keycloakAdminService;
    private final JdbcTemplate jdbcTemplate;

    /** All tables this application requires to be present in the DB. */
    private static final List<String> REQUIRED_TABLES = List.of(
            "users",
            "events",
            "ticket_types",
            "tickets",
            "discounts",
            "qr_codes",
            "ticket_validations",
            "invite_codes",
            "audit_logs",
            "user_attending_events",
            "user
```

<br>

### File: `WebMvcConfig.java`
**Description / What it does**: Application configuration (Security, Beans, Web).
**Key Annotations**: `@Configuration, @RequiredArgsConstructor`
**Package**: `com.event.tickets.config`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class WebMvcConfig implements WebMvcConfigurer {

    private final PageableSizeValidator pageableSizeValidator;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(pageableSizeValidator);
    }
}


```

<br>

### File: `KeycloakAdminConfig.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Configuration, @ConfigurationProperties(prefix = "keycloak.admin"), @Getter, @Setter, @Bean`
**Package**: `com.event.tickets.config`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class KeycloakAdminConfig`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
 class entirely.
 *
 *   Migration steps for existing deployments:
 *   1. In Keycloak Admin UI → Clients → Create a new client (e.g. "event-ticket-backend")
 *   2. Set Access Type = confidential, Service Accounts Enabled = ON
 *   3. Under Service Account Roles, assign only the realm-management roles needed
 *      (manage-users, view-users, manage-realm)
 *   4. Copy the client secret from the Credentials tab
 *   5. Set keycloak.admin.client-id=event-ticket-backend
 *      and keycloak.admin.client-secret=<copied secret> in your env
 *   6. Remove KEYCLOAK_ADMIN_USERNAME / KEYCLOAK_ADMIN_P
```

<br>

### File: `RestTemplateConfig.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Configuration, @Bean`
**Package**: `com.event.tickets.config`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class RestTemplateConfig`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
 class exposes a properly configured RestTemplate that:
 * - Has 10-second connect + read timeouts (prevents hanging email calls)
 * - Can be replaced with @MockBean in tests
 * - Is consistent with any RestTemplateCustomizer beans in the context
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}
```

<br>

## Folder: `src\main\java\com\event\tickets\controllers`
---

### File: `AdminGovernanceController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping("/api/v1/admin"), @RequiredArgsConstructor, @Slf4j, @PostMapping("/users/{userId}/roles")`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.AssignRoleRequestDto`
- `com.event.tickets.domain.dtos.AvailableRolesResponseDto`
- `com.event.tickets.domain.dtos.UserRolesResponseDto`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.KeycloakAdminService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class AdminGovernanceController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AdminGovernanceController {

    private final KeycloakAdminService keycloakAdminService;
    // FIX A-2 note: read-only use only (findById for response DTO). No writes here.
    private final UserRepository userRepository;

    /**
     * Assign a role to a user.
     *
     * Validates DB user exists first, then delegates to Keycloak Admin API.
     * Audit log is written by KeycloakAdminServiceImpl.assignRoleToUser().
     */
    @PostMapping("/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserRolesResponseDto> assignRoleToUser(
        
```

<br>

### File: `ApprovalController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping("/api/v1/admin/approvals"), @RequiredArgsConstructor, @Slf4j, @GetMapping("/pending")`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.RejectReasonDto`
- `com.event.tickets.domain.dtos.UserApprovalDto`
- `com.event.tickets.services.ApprovalService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class ApprovalController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * Gets all users with PENDING approval status.
     * Response includes each user's Keycloak roles (e.g. ORGANIZER, STAFF)
     * so the admin knows what they registered for before approving.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserApprovalDto>> getPendingApprovals(Pageable pageable) {
        log.info("Admin fetching pending approvals, page: {}", pageable.getPageNumber());
        Page<UserApprovalDto> pendingApprovals = appro
```

<br>

### File: `AuditController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping("/api/v1/audit"), @RequiredArgsConstructor, @Slf4j, @GetMapping`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.AuditLogDto`
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.AuthorizationService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class AuditController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AuditController {

    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;

    /**
     * All audit logs — ADMIN only.
     *
     * FIX A-6: Optional ?action= query parameter filters by AuditAction type.
     * When action is null, returns all logs (existing behaviour).
     * When action is provided (e.g. ?action=ROLE_ASSIGNED), filters in the DB.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDto>> getAllAuditLogs(
            Pageable pageable,
            @RequestPar
```

<br>

### File: `AuthController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping("/api/v1/auth"), @RequiredArgsConstructor, @Slf4j, @PostMapping("/register")`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.RegisterRequestDto`
- `com.event.tickets.domain.dtos.RegisterResponseDto`
- `com.event.tickets.services.RegistrationService`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class AuthController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AuthController {

  private final RegistrationService registrationService;

  /**
   * Registers a new user via invite code or as ATTENDEE (no invite).
   *
   * This endpoint is PUBLIC and does not require authentication.
   * It creates a Keycloak user and local database record with PENDING approval status.
   *
   * Registration Flow:
   * - If inviteCode provided: Validate invite and assign role from invite
   * - If no inviteCode: Assign ATTENDEE role
   * - Always creates user with approval_status = PENDING
   * - No JWT token issued (user must wait for approval)
   *
   * @
```

<br>

### File: `DiscountController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts"), @RequiredArgsConstructor, @Slf4j, @PostMapping`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.CreateDiscountRequestDto`
- `com.event.tickets.domain.dtos.DiscountResponseDto`
- `com.event.tickets.domain.entities.Discount`
- `com.event.tickets.exceptions.DiscountNotFoundException`
- `com.event.tickets.mappers.DiscountMapper`
- `com.event.tickets.services.DiscountService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class DiscountController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class DiscountController {

    private final DiscountService discountService;
    private final DiscountMapper discountMapper;

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<DiscountResponseDto> createDiscount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @Valid @RequestBody CreateDiscountRequestDto request
    ) {
        UUID organizerId = parseUserId(jwt);
        log.info("Organizer {} creating discount for ticket type {} in event {}",
           
```

<br>

### File: `EventController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping(path = "/api/v1/events"), @RequiredArgsConstructor, @Slf4j, @PostMapping`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.CreateEventRequest`
- `com.event.tickets.domain.UpdateEventRequest`
- `com.event.tickets.domain.dtos.CreateEventRequestDto`
- `com.event.tickets.domain.dtos.CreateEventResponseDto`
- `com.event.tickets.domain.dtos.GetEventDetailsResponseDto`
- `com.event.tickets.domain.dtos.ListEventResponseDto`
- `com.event.tickets.domain.dtos.UpdateEventRequestDto`
- `com.event.tickets.domain.dtos.UpdateEventResponseDto`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.mappers.EventMapper`
- `com.event.tickets.services.EventService`
- `com.event.tickets.services.ExportService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class EventController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EventController {

    private final EventMapper eventMapper;
    private final EventService eventService;
    private final ExportService exportService;

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CreateEventResponseDto> createEvent(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateEventRequestDto createEventRequestDto) {
        CreateEventRequest createEventRequest = eventMapper.fromDto(createEventRequestDto);
        UUID userId = parseUserId(jwt);

        Event createdEvent = eventService.createEven
```

<br>

### File: `EventStaffController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping("/api/v1/events/{eventId}/staff"), @RequiredArgsConstructor, @Slf4j, @PostMapping`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.AssignStaffRequestDto`
- `com.event.tickets.domain.dtos.EventStaffResponseDto`
- `com.event.tickets.domain.dtos.StaffMemberDto`
- `com.event.tickets.services.EventStaffService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class EventStaffController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EventStaffController {

    private final EventStaffService eventStaffService;

    /**
     * FIX S-6: assignStaffToEvent() returns EventStaffResponseDto directly.
     * No extra listEventStaff() or getEventName() calls needed.
     */
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventStaffResponseDto> assignStaffToEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody AssignStaffRequestDto request
    ) {
        UUID organizerId = parseUserId(jwt);
        UUID userId 
```

<br>

### File: `GlobalExceptionHandler.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestControllerAdvice, @Slf4j, @Value("${spring.profiles.active:prod}"), @ExceptionHandler(MethodArgumentNotValidException.class), @ExceptionHandler(ConstraintViolationException.class)`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.ErrorDto`
- `com.event.tickets.exceptions.EmailAlreadyInUseException`
- `com.event.tickets.exceptions.EventNotFoundException`
- `com.event.tickets.exceptions.EventUpdateException`
- `com.event.tickets.exceptions.InvalidBusinessStateException`
- `com.event.tickets.exceptions.InvalidInputException`
- `com.event.tickets.exceptions.InvalidInviteCodeException`
- `com.event.tickets.exceptions.InviteCodeNotFoundException`
- `com.event.tickets.exceptions.KeycloakOperationException`
- `com.event.tickets.exceptions.KeycloakUserCreationException`
- `com.event.tickets.exceptions.QrCodeGenerationException`
- `com.event.tickets.exceptions.QrCodeNotFoundException`
- `com.event.tickets.exceptions.RegistrationException`
- `com.event.tickets.exceptions.SystemUserNotFoundException`
- `com.event.tickets.exceptions.TicketNotFoundException`
- `com.event.tickets.exceptions.TicketTypeNotFoundException`
- `com.event.tickets.exceptions.TicketsSoldOutException`
- `com.event.tickets.exceptions.UserNotFoundException`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class GlobalExceptionHandler`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    // ============= 400 BAD REQUEST — VALIDATION =============

    /**
     * FIX: Now returns ALL field validation errors, not just the first one.
     *
     * BEFORE: fieldErrors.get(0).getDefaultMessage() — only showed the first failing field.
     * A request with 3 invalid fields forced 3 round-trips to discover all the problems.
     *
     * AFTER: All field errors are collected into errorDto.validationErrors as a list.
     * The message summarises how many fields f
```

<br>

### File: `InviteCodeController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping("/api/v1/invites"), @RequiredArgsConstructor, @Slf4j, @PostMapping`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.GenerateInviteCodeRequestDto`
- `com.event.tickets.domain.dtos.InviteCodeResponseDto`
- `com.event.tickets.domain.dtos.RedeemInviteCodeRequestDto`
- `com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto`
- `com.event.tickets.services.AuthorizationService`
- `com.event.tickets.services.InviteCodeService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class InviteCodeController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class InviteCodeController {

    private final InviteCodeService inviteCodeService;
    private final AuthorizationService authorizationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<InviteCodeResponseDto> generateInviteCode(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GenerateInviteCodeRequestDto request
    ) {
        UUID creatorId = parseUserId(jwt);
        String roleName = request.getRoleName();
        UUID eventId = request.getEventId();

        log.info("User '{}' generatin
```

<br>

### File: `PublishedEventController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping(path = "/api/v1/published-events"), @RequiredArgsConstructor, @GetMapping, @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER') or hasRole('STAFF')")`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.GetPublishedEventDetailsResponseDto`
- `com.event.tickets.domain.dtos.ListPublishedEventResponseDto`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.mappers.EventMapper`
- `com.event.tickets.services.EventService`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class PublishedEventController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class PublishedEventController {

    private final EventService eventService;
    private final EventMapper eventMapper;

    @GetMapping
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER') or hasRole('STAFF')")
    public ResponseEntity<Page<ListPublishedEventResponseDto>> listPublishedEvents(
            @RequestParam(required = false) String q,
            Pageable pageable) {

        Page<Event> events;
        if (null != q && !q.trim().isEmpty()) {
            events = eventService.searchPublishedEvents(q, pageable);
        } else {
            events = eventService.
```

<br>

### File: `TicketController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping(path = "/api/v1/tickets"), @RequiredArgsConstructor, @Slf4j, @GetMapping`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.GetTicketResponseDto`
- `com.event.tickets.domain.dtos.ListTicketResponseDto`
- `com.event.tickets.mappers.TicketMapper`
- `com.event.tickets.services.QrCodeService`
- `com.event.tickets.services.TicketService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class TicketController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketController {

    private final TicketService ticketService;
    private final TicketMapper ticketMapper;
    private final QrCodeService qrCodeService;

    @GetMapping
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
    public Page<ListTicketResponseDto> listTickets(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable
    ) {
        return ticketService.listTicketsForUser(
                parseUserId(jwt),
                pageable
        ).map(ticketMapper::toListTicketResponseDto);
    }

    @GetMapping(path = "/{ticketId}")
   
```

<br>

### File: `TicketTypeController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequiredArgsConstructor, @RequestMapping(path = "/api/v1/events/{eventId}/ticket-types"), @PostMapping(path = "/{ticketTypeId}/tickets"), @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.CreateTicketTypeRequestDto`
- `com.event.tickets.domain.dtos.CreateTicketTypeResponseDto`
- `com.event.tickets.domain.dtos.GetTicketResponseDto`
- `com.event.tickets.domain.dtos.PurchaseTicketRequestDto`
- `com.event.tickets.domain.dtos.UpdateTicketTypeRequestDto`
- `com.event.tickets.domain.dtos.UpdateTicketTypeResponseDto`
- `com.event.tickets.domain.entities.Ticket`
- `com.event.tickets.mappers.TicketMapper`
- `com.event.tickets.mappers.TicketTypeMapper`
- `com.event.tickets.services.TicketTypeService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class TicketTypeController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;
    private final TicketTypeMapper ticketTypeMapper;
    private final TicketMapper ticketMapper;

    /**
     * Purchase tickets.
     *
     * FIX: now passes eventId from the URL path into purchaseTickets().
     * The service validates that the ticketTypeId actually belongs to that event,
     * preventing a crafted request from buying tickets across unrelated events.
     * The service also enforces PUBLISHED status and the sales window.
     */
    @PostMapping(path = "/{ticketTypeId}/tickets")
 
```

<br>

### File: `TicketValidationController.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@RestController, @RequestMapping(path = "/api/v1/ticket-validations"), @RequiredArgsConstructor, @PostMapping, @PreAuthorize("hasRole('STAFF') or hasRole('ORGANIZER')")`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.TicketValidationRequestDto`
- `com.event.tickets.domain.dtos.TicketValidationResponseDto`
- `com.event.tickets.domain.entities.TicketValidation`
- `com.event.tickets.domain.entities.TicketValidationMethod`
- `com.event.tickets.mappers.TicketValidationMapper`
- `com.event.tickets.services.TicketValidationService`
- `com.event.tickets.util.JwtUtil.parseUserId`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class TicketValidationController`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketValidationController {

    private final TicketValidationService ticketValidationService;
    private final TicketValidationMapper ticketValidationMapper;

    /**
     * H-03 FIX: @Valid added to @RequestBody.
     *
     * Previously had no @Valid, so TicketValidationRequestDto.method could be null.
     * The routing logic TicketValidationMethod.MANUAL.equals(method) would NPE when
     * method was null (the enum constant is non-null, but equals(null) on an enum
     * does not NPE — however the else-branch would call validateTicketByQrCode with
     * a null id, causin
```

<br>

## Folder: `src\main\java\com\event\tickets\domain`
---

### File: `CreateEventRequest.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class CreateEventRequest`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class CreateEventRequest {

    private String name;
    private LocalDateTime start;
    private LocalDateTime end;
    private String venue;
    private LocalDateTime salesStart;
    private LocalDateTime salesEnd;
    private EventStatusEnum status;
    private Integer maxCapacity;
    private List<CreateTicketTypeRequest> ticketTypes = new ArrayList<>();
}

```

<br>

### File: `CreateTicketTypeRequest.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class CreateTicketTypeRequest`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class CreateTicketTypeRequest {
    private String name;
    private BigDecimal price;
    private String description;
    private Integer totalAvailable;
}
```

<br>

### File: `UpdateEventRequest.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class UpdateEventRequest`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UpdateEventRequest {

    private UUID id;
    private String name;
    private LocalDateTime start;
    private LocalDateTime end;
    private String venue;
    private LocalDateTime salesStart;
    private LocalDateTime salesEnd;
    private EventStatusEnum status;
    private Integer maxCapacity;
    private List<UpdateTicketTypeRequest> ticketTypes = new ArrayList<>();
}

```

<br>

### File: `UpdateTicketTypeRequest.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class UpdateTicketTypeRequest`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UpdateTicketTypeRequest {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer totalAvailable;
}
```

<br>

## Folder: `src\main\java\com\event\tickets\domain\dtos`
---

### File: `AssignRoleRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @NotBlank(message = "Role name is required"), @Pattern`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class AssignRoleRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AssignRoleRequestDto {

  @NotBlank(message = "Role name is required")
  @Pattern(
      regexp = "^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$",
      message = "Role must be one of: ADMIN, ORGANIZER, ATTENDEE, STAFF"
  )
  private String roleName;
}

```

<br>

### File: `AssignStaffRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @NotNull(message = "User ID is required")`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class AssignStaffRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AssignStaffRequestDto {

  @NotNull(message = "User ID is required")
  private UUID userId;
}

```

<br>

### File: `AuditLogDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @Builder`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class AuditLogDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AuditLogDto {

    private UUID id;
    private String action;
    private String actorName;
    private UUID actorId;
    private String targetUserName;
    private UUID targetUserId;
    private String eventName;
    private UUID eventId;
    private String resourceType;
    private UUID resourceId;
    private String details;
    private String ipAddress;
    /**
     * L-18 FIX: userAgent field added. AuditLog entity stores userAgent
     * but it was dropped here — invisible to every admin API response.
     */
    private String userAgent;
    private LocalDateTime createdAt
```

<br>

### File: `AvailableRolesResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class AvailableRolesResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AvailableRolesResponseDto {

  private List<String> roles;
  private String message;
}

```

<br>

### File: `CreateDiscountRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @Builder, @NotNull(message = "Discount type is required")`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.DiscountType`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class CreateDiscountRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class CreateDiscountRequestDto {

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be positive")
    private BigDecimal value;

    /**
     * FIX D-1: @FutureOrPresent removed.
     *
     * On CREATE the service enforces future-only via validateDiscountRequest().
     * On UPDATE a past validFrom is valid (the discount period may have started).
     * Removing the annotation here prevents Spring from rejecting update requests
  
```

<br>

### File: `CreateEventRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor, @NotBlank(message = "Event name is required"), @Size(max = 200, message = "Event name must not exceed 200 characters")`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class CreateEventRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class CreateEventRequestDto {

    @NotBlank(message = "Event name is required")
    @Size(max = 200, message = "Event name must not exceed 200 characters")
    private String name;

    /**
     * FIX-E8-DTO2: Now required. An event must have a start date.
     */
    @NotNull(message = "Event start date is required")
    private LocalDateTime start;

    /**
     * FIX-E8-DTO2: Now required. An event must have an end date.
     */
    @NotNull(message = "Event end date is required")
    private LocalDateTime end;

    @NotBlank(message = "Venue information is required")
    @Size(max 
```

<br>

### File: `CreateEventResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class CreateEventResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class CreateEventResponseDto {

  private UUID id;
  private String name;
  private LocalDateTime start;
  private LocalDateTime end;
  private String venue;
  private LocalDateTime salesStart;
  private LocalDateTime salesEnd;
  private EventStatusEnum status;
  private List<CreateTicketTypeResponseDto> ticketTypes;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}

```

<br>

### File: `CreateTicketTypeRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor, @NotBlank(message = "Ticket type name is required"), @NotNull(message = "Price is required")`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class CreateTicketTypeRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class CreateTicketTypeRequestDto {

    @NotBlank(message = "Ticket type name is required")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be zero or greater")
    private BigDecimal price;

    private String description;

    /**
     * FIX-TT5: No longer @NotNull. Null means unlimited capacity.
     * If provided, must be at least 1.
     */
    @Min(value = 1, message = "Total available must be at least 1 if provided")
    private Integer totalAvailable;
}
```

<br>

### File: `CreateTicketTypeResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class CreateTicketTypeResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class CreateTicketTypeResponseDto {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer totalAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

<br>

### File: `DiscountResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @Builder`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.DiscountType`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class DiscountResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class DiscountResponseDto {

    private UUID id;
    private UUID ticketTypeId;
    private String ticketTypeName;
    private DiscountType discountType;
    private BigDecimal value;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Boolean active;
    private String description;

    /**
     * FIX D-5: UUID of the organizer who created this discount.
     * Useful for audit trails and admin views showing which organizer set up each discount.
     */
    private UUID createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt
```

<br>

### File: `ErrorDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class ErrorDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ErrorDto {
    private String error;
    private String message;
    private int statusCode;
    private String statusDescription;
    private String timestamp;
    private String path;

    /**
     * FIX: List of ALL field-level validation errors.
     *
     * Previously GlobalExceptionHandler only returned the FIRST field error.
     * If a register request had 3 missing/invalid fields, the client had to
     * fix one → resubmit → see next error → fix → resubmit → etc.
     *
     * Now ALL validation errors are returned at once in this list.
     * Format: ["email: Email is 
```

<br>

### File: `EventStaffResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class EventStaffResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EventStaffResponseDto {

  private UUID eventId;
  private String eventName;
  private List<StaffMemberDto> staffMembers;
  private int totalStaffCount;
}

```

<br>

### File: `GenerateInviteCodeRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @NotBlank(message = "Role name is required"), @Pattern`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class GenerateInviteCodeRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class GenerateInviteCodeRequestDto {

    @NotBlank(message = "Role name is required")
    @Pattern(
            regexp = "^(ADMIN|ORGANIZER|ATTENDEE|STAFF)$",
            message = "Role must be one of: ADMIN, ORGANIZER, ATTENDEE, STAFF"
    )
    private String roleName;

    private UUID eventId; // Required only for STAFF role

    @NotNull(message = "Expiration hours is required")
    @Positive(message = "Expiration hours must be positive")
    @Max(value = 8760, message = "Expiration hours cannot exceed 8760 (1 year). Regenerate the code if you need longer access.")
    private In
```

<br>

### File: `GetEventDetailsResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class GetEventDetailsResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class GetEventDetailsResponseDto {

  private UUID id;
  private String name;
  private LocalDateTime start;
  private LocalDateTime end;
  private String venue;
  private LocalDateTime salesStart;
  private LocalDateTime salesEnd;
  private EventStatusEnum status;
  private List<GetEventDetailsTicketTypesResponseDto> ticketTypes = new ArrayList<>();
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}

```

<br>

### File: `GetEventDetailsTicketTypesResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class GetEventDetailsTicketTypesResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class GetEventDetailsTicketTypesResponseDto {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer totalAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

<br>

### File: `GetPublishedEventDetailsResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class GetPublishedEventDetailsResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class GetPublishedEventDetailsResponseDto {

    private UUID id;
    private String name;
    private LocalDateTime start;
    private LocalDateTime end;
    private String venue;

    /** FIX-E7-DTO: When ticket sales open. Null = open from now. */
    private LocalDateTime salesStart;

    /** FIX-E7-DTO: When ticket sales close. Null = open until event starts. */
    private LocalDateTime salesEnd;

    /** FIX-E7-DTO: Always PUBLISHED for this endpoint — included for client display. */
    private EventStatusEnum status;

    /** FIX-E7-DTO: Whether sales are currently open (comput
```

<br>

### File: `GetPublishedEventDetailsTicketTypesResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class GetPublishedEventDetailsTicketTypesResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class GetPublishedEventDetailsTicketTypesResponseDto {

    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;

    /**
     * FIX-E7-DTO: How many tickets are available for purchase.
     * Null means unlimited (no cap set by organizer).
     * When 0, this ticket type is sold out.
     *
     * Note: this is totalAvailable from the ticket type, NOT remaining slots.
     * Remaining = totalAvailable - soldCount, but soldCount is organizer-only.
     * Exposing totalAvailable gives attendees enough signal to know the tier exists
     
```

<br>

### File: `GetTicketResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.TicketStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class GetTicketResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class GetTicketResponseDto {
    private UUID id;
    private TicketStatusEnum status;
    private BigDecimal price;          // ticket type base price
    private BigDecimal pricePaid;      // actual amount charged (after discount)
    private BigDecimal originalPrice;  // base price at time of purchase (snapshot)
    private BigDecimal discountApplied; // discount amount (0 if none)
    private String description;
    private String eventName;
    private String eventVenue;
    private LocalDateTime eventStart;
    private LocalDateTime eventEnd;
}
```

<br>

### File: `InviteCodeResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @Builder`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class InviteCodeResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class InviteCodeResponseDto {

    private UUID id;
    private String code;
    private String roleName;
    private UUID eventId;
    private String eventName;
    private String status;

    /** Display name of the user who generated this code. */
    private String createdBy;

    /**
     * FIX I-7: UUID of the user who generated this code.
     * Stable identifier — does not change if the user's display name changes.
     */
    private UUID createdByUserId;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    // ... (truncated)
```

<br>

### File: `ListEventResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class ListEventResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ListEventResponseDto {

  private UUID id;
  private String name;
  private LocalDateTime start;
  private LocalDateTime end;
  private String venue;
  private LocalDateTime salesStart;
  private LocalDateTime salesEnd;
  private EventStatusEnum status;
  private List<ListEventTicketTypeResponseDto> ticketTypes = new ArrayList<>();
}

```

<br>

### File: `ListEventTicketTypeResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class ListEventTicketTypeResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ListEventTicketTypeResponseDto {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer totalAvailable;
}
```

<br>

### File: `ListPublishedEventResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class ListPublishedEventResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ListPublishedEventResponseDto {

    private UUID id;
    private String name;
    private LocalDateTime start;
    private LocalDateTime end;
    private String venue;

    /** FIX-E7-DTO: When ticket sales open. Null = open from now. */
    private LocalDateTime salesStart;

    /** FIX-E7-DTO: When ticket sales close. Null = open until event starts. */
    private LocalDateTime salesEnd;

    /** FIX-E7-DTO: True if sales are currently open (server-computed for client convenience). */
    private boolean salesOpen;
}
```

<br>

### File: `ListTicketResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.TicketStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class ListTicketResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ListTicketResponseDto {
  private UUID id;
  private TicketStatusEnum status;
  private ListTicketTicketTypeResponseDto ticketType;
}

```

<br>

### File: `ListTicketTicketTypeResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class ListTicketTicketTypeResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ListTicketTicketTypeResponseDto {
    private UUID id;
    private String name;
    private BigDecimal price;
}
```

<br>

### File: `PurchaseTicketRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor, @Min(value = 1, message = "Quantity must be at least 1"), @Max(value = 10, message = "Quantity cannot exceed 10")`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class PurchaseTicketRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class PurchaseTicketRequestDto {
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 10, message = "Quantity cannot exceed 10")
    private Integer quantity = 1; // Default to 1 for backward compatibility
}

```

<br>

### File: `RedeemInviteCodeRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @NotBlank(message = "Invite code is required")`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class RedeemInviteCodeRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class RedeemInviteCodeRequestDto {

  @NotBlank(message = "Invite code is required")
  private String code;
}

```

<br>

### File: `RedeemInviteCodeResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class RedeemInviteCodeResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class RedeemInviteCodeResponseDto {

    /** Human-readable success message. */
    private String message;

    /** The role that was assigned via the invite code. */
    private String roleAssigned;

    /** Event name if this was a STAFF code scoped to a specific event. Null otherwise. */
    private String eventName;

    /**
     * FIX-DTO7: Whether the user now needs admin approval before using the system.
     * Currently always false (only approved users can redeem codes).
     * Included for client clarity and future extensibility.
     */
    private boolean requiresApproval;

```

<br>

### File: `RegisterRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @Pattern, @NotBlank(message = "Email is required")`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class RegisterRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class RegisterRequestDto {

  /**
   * Invite code provided to the user (OPTIONAL).
   * If provided: User gets role from invite code
   * If not provided: User gets default ATTENDEE role
   * Format: XXXX-XXXX-XXXX-XXXX (16 characters + 3 hyphens)
   */
  @Pattern(
      regexp = "^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$",
      message = "Invalid invite code format. Expected: XXXX-XXXX-XXXX-XXXX"
  )
  private String inviteCode;

  /**
   * User's email address.
   * Will be used as both email and username in Keycloak.
   */
  @NotBlank(message = "Email is required")
  @Email
```

<br>

### File: `RegisterResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class RegisterResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class RegisterResponseDto {

    /** Success message describing what the user should do next. */
    private String message;

    /** The normalized (lowercase) email the account was created with. */
    private String email;

    /**
     * Whether the user must wait for admin approval before logging in.
     *
     * true  → ORGANIZER / STAFF / ADMIN — account is PENDING, login blocked until admin approves.
     * false → ATTENDEE — account is APPROVED immediately, user can log in right now.
     */
    private boolean requiresApproval;

    /**
     * The role assigned to the user.
 
```

<br>

### File: `RejectReasonDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @NotBlank(message = "Rejection reason is required"), @Size(min = 3, max = 500, message = "Rejection reason must be between 3 and 500 characters")`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class RejectReasonDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class RejectReasonDto {

    /**
     * Reason for rejecting the user account.
     * Required for transparency and audit purposes.
     * Short reasons like "Spam" or "Bot" are valid.
     */
    @NotBlank(message = "Rejection reason is required")
    @Size(min = 3, max = 500, message = "Rejection reason must be between 3 and 500 characters")
    private String reason;
}
```

<br>

### File: `StaffMemberDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class StaffMemberDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class StaffMemberDto {

  private UUID userId;
  private String userName;
  private String email;
}

```

<br>

### File: `TicketValidationRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor, @NotNull(message = "Ticket or QR code ID is required"), @NotNull(message = "Validation method is required (MANUAL or QR_SCAN)")`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.TicketValidationMethod`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class TicketValidationRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketValidationRequestDto {

    @NotNull(message = "Ticket or QR code ID is required")
    private UUID id;

    @NotNull(message = "Validation method is required (MANUAL or QR_SCAN)")
    private TicketValidationMethod method;
}
```

<br>

### File: `TicketValidationResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.TicketValidationStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class TicketValidationResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketValidationResponseDto {
    private UUID ticketId;
    private TicketValidationStatusEnum status;

    /** ID of the staff member / organizer who scanned this ticket. */
    private UUID validatedById;

    /** Display name of the scanner — useful for the frontend without a second request. */
    private String validatedByName;

    /** When this validation record was created. */
    private LocalDateTime validatedAt;
}

```

<br>

### File: `UpdateEventRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor, @JsonInclude(JsonInclude.Include.NON_NULL), @NotBlank(message = "Event name is required")`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class UpdateEventRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UpdateEventRequestDto {

    // ID sourced from URL path parameter — optional in body
    private UUID id;

    @NotBlank(message = "Event name is required")
    @Size(max = 200, message = "Event name must not exceed 200 characters")
    private String name;

    private LocalDateTime start;

    private LocalDateTime end;

    @NotBlank(message = "Venue information is required")
    @Size(max = 500, message = "Venue must not exceed 500 characters")
    private String venue;

    private LocalDateTime salesStart;

    private LocalDateTime salesEnd;
    // ... (truncated)
```

<br>

### File: `UpdateEventResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class UpdateEventResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UpdateEventResponseDto {

  private UUID id;
  private String name;
  private LocalDateTime start;
  private LocalDateTime end;
  private String venue;
  private LocalDateTime salesStart;
  private LocalDateTime salesEnd;
  private EventStatusEnum status;
  private List<UpdateTicketTypeResponseDto> ticketTypes;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}

```

<br>

### File: `UpdateTicketTypeRequestDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor, @NotBlank(message = "Ticket type name is required"), @NotNull(message = "Price is required")`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class UpdateTicketTypeRequestDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UpdateTicketTypeRequestDto {

    private UUID id;

    @NotBlank(message = "Ticket type name is required")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be zero or greater")
    private BigDecimal price;

    private String description;

    private Integer totalAvailable;
}
```

<br>

### File: `UpdateTicketTypeResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @AllArgsConstructor, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class UpdateTicketTypeResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UpdateTicketTypeResponseDto {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer totalAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

<br>

### File: `UserApprovalDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class UserApprovalDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UserApprovalDto {

    /** Keycloak user ID (UUID as string). */
    private String userId;

    /** User's display name. */
    private String name;

    /** User's email address (normalized lowercase). */
    private String email;

    /**
     * Current approval status: PENDING, APPROVED, REJECTED, or UNKNOWN (legacy null).
     */
    private String approvalStatus;

    /**
     * FIX-DTO3: Keycloak roles assigned to this user.
     * For PENDING users this shows what role they registered for
     * (e.g. ["ORGANIZER"] or ["STAFF"]) — the admin uses this to make
    // ... (truncated)
```

<br>

### File: `UserRolesResponseDto.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@Data, @NoArgsConstructor, @AllArgsConstructor`
**Package**: `com.event.tickets.domain.dtos`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class UserRolesResponseDto`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UserRolesResponseDto {

    private UUID userId;
    private String userName;
    private String email;
    private List<String> roles;
}
```

<br>

## Folder: `src\main\java\com\event\tickets\domain\entities`
---

### File: `ApprovalStatus.java`
**Description / What it does**: Application enum component.
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `enum ApprovalStatus`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public enum ApprovalStatus {
    /**
     * User account is pending admin approval.
     * User can authenticate with Keycloak but cannot access backend endpoints.
     */
    PENDING,

    /**
     * User account has been approved by an admin.
     * User has full access according to their assigned roles.
     */
    APPROVED,

    /**
     * User account has been rejected by an admin.
     * User cannot access backend endpoints even with valid JWT.
     */
    REJECTED
}

```

<br>

### File: `AuditAction.java`
**Description / What it does**: Application enum component.
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `enum AuditAction`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public enum AuditAction {
    // Registration Operations
    REGISTRATION_ATTEMPT,
    REGISTRATION_SUCCESS,
    REGISTRATION_FAILED,

    // Approval Gate Operations
    USER_APPROVED,
    USER_REJECTED,
    APPROVAL_GATE_VIOLATION,

    // Role Management
    ROLE_ASSIGNED,
    ROLE_REVOKED,

    // Admin Promotion (high-severity — ADMIN granted via invite code)
    ADMIN_ROLE_GRANTED_VIA_INVITE,

    // Event Staff Management
    STAFF_ASSIGNED,
    // ... (truncated)
```

<br>

### File: `AuditLog.java`
**Description / What it does**: Database domain model representing a persistent table.
**Key Annotations**: `@Entity, @Table(name = "audit_logs"), @EntityListeners(AuditingEntityListener.class), @Getter, @NoArgsConstructor`
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class AuditLog`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "action", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  private AuditAction action;

  @ManyToOne
  @JoinColumn(name = "actor_id", nullable = false)
  private User actor;

  @ManyToOne
  @JoinColumn(name = "target_user_id")
  private User targetUser;

  @ManyToOne
    // ... (truncated)
```

<br>

### File: `DiscountType.java`
**Description / What it does**: Application enum component.
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `enum DiscountType`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public enum DiscountType {
  /**
   * Percentage-based discount (0-100).
   * Final price = basePrice * (1 - value/100)
   */
  PERCENTAGE,

  /**
   * Fixed amount discount in currency.
   * Final price = basePrice - value
   */
  FIXED_AMOUNT
}

```

<br>

### File: `Event.java`
**Description / What it does**: Database domain model representing a persistent table.
**Key Annotations**: `@Entity, @Table(name = "events"), @EntityListeners(AuditingEntityListener.class), @Getter, @Setter`
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class Event`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class Event {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "event_start")
    private LocalDateTime start;

    @Column(name = "event_end")
    private LocalDateTime end;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Column(name = "sales_start")
    // ... (truncated)
```

<br>

### File: `EventStatusEnum.java`
**Description / What it does**: Application enum component.
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `enum EventStatusEnum`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public enum EventStatusEnum {
  DRAFT, PUBLISHED, CANCELLED, COMPLETED
}

```

<br>

### File: `InviteCode.java`
**Description / What it does**: Database domain model representing a persistent table.
**Key Annotations**: `@Entity, @Table(name = "invite_codes"), @EntityListeners(AuditingEntityListener.class), @Getter, @Setter`
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class InviteCode`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class InviteCode {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, unique = true, length = 32)
  private String code;

  @Column(name = "role_name", nullable = false, length = 50)
  private String roleName;

  @ManyToOne
  @JoinColumn(name = "event_id")
  private Event event; // Null for global roles, set for event-staff invites

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private InviteCodeStatus status;
    // ... (truncated)
```

<br>

### File: `InviteCodeStatus.java`
**Description / What it does**: Application enum component.
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `enum InviteCodeStatus`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public enum InviteCodeStatus {
  /**
   * Code is active and can be redeemed.
   */
  PENDING,

  /**
   * Code has been successfully redeemed.
   */
  REDEEMED,

  /**
   * Code has expired (past expiration time).
   */
  EXPIRED,

  /**
   * Code was manually revoked before use.
   */
  REVOKED
    // ... (truncated)
```

<br>

### File: `QrCode.java`
**Description / What it does**: Database domain model representing a persistent table.
**Key Annotations**: `@Entity, @Table(name = "qr_codes"), @EntityListeners(AuditingEntityListener.class), @Getter, @Setter`
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class QrCode`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class QrCode {

    /**
     * ID is set manually by QrCodeServiceImpl before save.
     * This UUID is encoded into the QR image — it is what the scanner reads back.
     * Never save a QrCode without calling qrCode.setId() first.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private QrCodeStatusEnum status;

    /**
     * FIX-QR1: @Lob maps to PostgreSQL TEXT — unbounded.
     *
     * Stores a base64-encoded PNG of the QR code image.
     * A 300×
```

<br>

### File: `QrCodeStatusEnum.java`
**Description / What it does**: Application enum component.
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `enum QrCodeStatusEnum`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public enum QrCodeStatusEnum {
  ACTIVE, EXPIRED
}

```

<br>

### File: `Ticket.java`
**Description / What it does**: Database domain model representing a persistent table.
**Key Annotations**: `@Entity, @Table(name = "tickets"), @EntityListeners(AuditingEntityListener.class), @Getter, @Setter`
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class Ticket`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class Ticket {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TicketStatusEnum status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchaser_id")
    private User purchaser;

    /**
    // ... (truncated)
```

<br>

### File: `TicketStatusEnum.java`
**Description / What it does**: Application enum component.
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `enum TicketStatusEnum`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public enum TicketStatusEnum {
  PURCHASED, VALIDATED, CANCELLED
}

```

<br>

### File: `TicketType.java`
**Description / What it does**: Database domain model representing a persistent table.
**Key Annotations**: `@Entity, @Table, @EntityListeners(AuditingEntityListener.class), @Getter, @Setter`
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class TicketType`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketType {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * FIX #15: Changed from Double to BigDecimal.
     * Double causes floating-point precision errors in financial calculations
     * (e.g. 0.1 + 0.2 != 0.3 in IEEE 754). BigDecimal is the correct type
     * for all monetary values. Column precision 10,2 supports up to 99999999.99.
     */
    @Column(name = "price", nullable = false, precision
```

<br>

### File: `TicketValidation.java`
**Description / What it does**: Database domain model representing a persistent table.
**Key Annotations**: `@Entity, @Table(name = "ticket_validations"), @EntityListeners(AuditingEntityListener.class), @Getter, @Setter`
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class TicketValidation`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketValidation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TicketValidationStatusEnum status;

    @Column(name = "validation_method", nullable = false)
    @Enumerated(EnumType.STRING)
    private TicketValidationMethod validationMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    /**
    // ... (truncated)
```

<br>

### File: `TicketValidationMethod.java`
**Description / What it does**: Application enum component.
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `enum TicketValidationMethod`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public enum TicketValidationMethod {
  QR_SCAN, MANUAL
}

```

<br>

### File: `TicketValidationStatusEnum.java`
**Description / What it does**: Application enum component.
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `enum TicketValidationStatusEnum`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public enum TicketValidationStatusEnum {
  VALID, INVALID, EXPIRED
}

```

<br>

### File: `User.java`
**Description / What it does**: Database domain model representing a persistent table.
**Key Annotations**: `@Entity, @Table(name = "users"), @EntityListeners(AuditingEntityListener.class), @Getter, @Setter`
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class User`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class User {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * L-09 FIX: Java default is now null, not ApprovalStatus.PENDING.
     *
     * Previously the Java field initializer set PENDING while the DB column
     * defaulted to APPROVED. This created an inconsistency:
     * - New objects created in Java started as PENDING (correct for new registrations)
     * - But exi
```

<br>

### File: `Discount.java`
**Description / What it does**: Database domain model representing a persistent table.
**Key Annotations**: `@Entity, @Table(name = "discounts"), @EntityListeners(AuditingEntityListener.class), @Getter, @Setter`
**Package**: `com.event.tickets.domain.entities`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class Discount`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
 enum to avoid invalid states (better than nullable fields)</li>
 *   <li>Validity period enforced by CHECK constraint (valid_to > valid_from)</li>
 *   <li>Percentage range enforced by CHECK constraint (0 < value <= 100)</li>
 *   <li>Cascade DELETE when ticket type is deleted (cleanup)</li>
 * </ul>
 */
@Entity
@Table(name = "discounts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Discount {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  @GeneratedValue(strategy = GenerationType.UUID)
  priv
```

<br>

## Folder: `src\main\java\com\event\tickets\exceptions`
---

### File: `DiscountAlreadyExistsException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class DiscountAlreadyExistsException extends EventTicketException {

  public DiscountAlreadyExistsException(String message) {
    super(message);
  }

  public DiscountAlreadyExistsException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `DiscountNotFoundException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class DiscountNotFoundException extends EventTicketException {

  public DiscountNotFoundException(String message) {
    super(message);
  }

  public DiscountNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `EmailAlreadyInUseException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EmailAlreadyInUseException extends RuntimeException {
  public EmailAlreadyInUseException(String message) {
    super(message);
  }

  public EmailAlreadyInUseException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `EventNotFoundException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EventNotFoundException extends EventTicketException {

  public EventNotFoundException() {
  }

  public EventNotFoundException(String message) {
    super(message);
  }

  public EventNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public EventNotFoundException(Throwable cause) {
    super(cause);
  }

  public EventNotFoundException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // ... (truncated)
```

<br>

### File: `EventTicketException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EventTicketException extends RuntimeException {

  public EventTicketException() {
    super();
  }

  public EventTicketException(String message) {
    super(message);
  }

  public EventTicketException(String message, Throwable cause) {
    super(message, cause);
  }

  public EventTicketException(Throwable cause) {
    super(cause);
  }

  public EventTicketException(String message, Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    // ... (truncated)
```

<br>

### File: `EventUpdateException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EventUpdateException extends EventTicketException {

  public EventUpdateException() {
  }

  public EventUpdateException(String message) {
    super(message);
  }

  public EventUpdateException(String message, Throwable cause) {
    super(message, cause);
  }

  public EventUpdateException(Throwable cause) {
    super(cause);
  }

  public EventUpdateException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // ... (truncated)
```

<br>

### File: `InvalidApprovalStateException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class InvalidApprovalStateException extends RuntimeException {
  public InvalidApprovalStateException(String message) {
    super(message);
  }

  public InvalidApprovalStateException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `InvalidBusinessStateException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class InvalidBusinessStateException extends EventTicketException {
    public InvalidBusinessStateException(String message) {
        super(message);
    }
    public InvalidBusinessStateException(String message, Throwable cause) {
        super(message, cause);
    }
}

```

<br>

### File: `InvalidInputException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class InvalidInputException extends EventTicketException {
    public InvalidInputException(String message) {
        super(message);
    }
    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}

```

<br>

### File: `InvalidInviteCodeException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class InvalidInviteCodeException extends RuntimeException {

  public InvalidInviteCodeException(String message) {
    super(message);
  }
}

```

<br>

### File: `InviteCodeNotFoundException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class InviteCodeNotFoundException extends RuntimeException {

  public InviteCodeNotFoundException(String message) {
    super(message);
  }
}

```

<br>

### File: `KeycloakOperationException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class KeycloakOperationException extends RuntimeException {

  public KeycloakOperationException(String message) {
    super(message);
  }

  public KeycloakOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `KeycloakRoleAssignmentException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class KeycloakRoleAssignmentException extends RuntimeException {
  public KeycloakRoleAssignmentException(String message) {
    super(message);
  }

  public KeycloakRoleAssignmentException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `KeycloakUserCreationException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class KeycloakUserCreationException extends RuntimeException {
  public KeycloakUserCreationException(String message) {
    super(message);
  }

  public KeycloakUserCreationException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `KeycloakUserDeletionException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class KeycloakUserDeletionException extends RuntimeException {
  public KeycloakUserDeletionException(String message) {
    super(message);
  }

  public KeycloakUserDeletionException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `KeycloakUserUpdateException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class KeycloakUserUpdateException extends RuntimeException {
  public KeycloakUserUpdateException(String message) {
    super(message);
  }

  public KeycloakUserUpdateException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `QrCodeGenerationException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class QrCodeGenerationException extends EventTicketException {

  public QrCodeGenerationException() {
  }

  public QrCodeGenerationException(String message) {
    super(message);
  }

  public QrCodeGenerationException(String message, Throwable cause) {
    super(message, cause);
  }

  public QrCodeGenerationException(Throwable cause) {
    super(cause);
  }

  public QrCodeGenerationException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // ... (truncated)
```

<br>

### File: `QrCodeNotFoundException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class QrCodeNotFoundException extends EventTicketException {

  public QrCodeNotFoundException() {
  }

  public QrCodeNotFoundException(String message) {
    super(message);
  }

  public QrCodeNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public QrCodeNotFoundException(Throwable cause) {
    super(cause);
  }

  public QrCodeNotFoundException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // ... (truncated)
```

<br>

### File: `RegistrationException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class RegistrationException extends RuntimeException {
  public RegistrationException(String message) {
    super(message);
  }

  public RegistrationException(String message, Throwable cause) {
    super(message, cause);
  }
}

```

<br>

### File: `ReportGenerationException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ReportGenerationException extends EventTicketException {
    public ReportGenerationException(String message) {
        super(message);
    }
    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

```

<br>

### File: `SystemUserNotFoundException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class SystemUserNotFoundException extends EventTicketException {
    public SystemUserNotFoundException(String message) {
        super(message);
    }
    public SystemUserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

```

<br>

### File: `TicketNotFoundException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketNotFoundException extends EventTicketException {

  public TicketNotFoundException() {
  }

  public TicketNotFoundException(String message) {
    super(message);
  }

  public TicketNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public TicketNotFoundException(Throwable cause) {
    super(cause);
  }

  public TicketNotFoundException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // ... (truncated)
```

<br>

### File: `TicketTypeDeleteNotAllowedException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketTypeDeleteNotAllowedException extends EventTicketException {
    public TicketTypeDeleteNotAllowedException(String message) {
        super(message);
    }
}

```

<br>

### File: `TicketTypeNotFoundException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketTypeNotFoundException extends EventTicketException {

  public TicketTypeNotFoundException() {
  }

  public TicketTypeNotFoundException(String message) {
    super(message);
  }

  public TicketTypeNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public TicketTypeNotFoundException(Throwable cause) {
    super(cause);
  }

  public TicketTypeNotFoundException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // ... (truncated)
```

<br>

### File: `TicketsSoldOutException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketsSoldOutException extends EventTicketException {

  public TicketsSoldOutException() {
  }

  public TicketsSoldOutException(String message) {
    super(message);
  }

  public TicketsSoldOutException(String message, Throwable cause) {
    super(message, cause);
  }

  public TicketsSoldOutException(Throwable cause) {
    super(cause);
  }

  public TicketsSoldOutException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // ... (truncated)
```

<br>

### File: `UserNotFoundException.java`
**Description / What it does**: Custom exception for domain-specific error handling.
**Package**: `com.event.tickets.exceptions`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UserNotFoundException extends EventTicketException {

  public UserNotFoundException() {
  }

  public UserNotFoundException(String message) {
    super(message);
  }

  public UserNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public UserNotFoundException(Throwable cause) {
    super(cause);
  }

  public UserNotFoundException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    // ... (truncated)
```

<br>

## Folder: `src\main\java\com\event\tickets\filters`
---

### File: `ApprovalGateFilter.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component, @Order(2), @RequiredArgsConstructor, @Slf4j`
**Package**: `com.event.tickets.filters`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.ApprovalStatus`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.impl.KeycloakAdminServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ApprovalGateFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final KeycloakAdminServiceImpl keycloakAdminService;
    private final ObjectMapper objectMapper;

    /**
     * FIX ISSUE 9: Added Swagger UI and actuator paths to the bypass list.
     *
     * BEFORE: A PENDING user hitting /swagger-ui.html would get 403 APPROVAL_PENDING
     * instead of the documentation page. Same for Kubernetes/Render health probes
     * that authenticate with a JWT but hit /actuator/health.
     *
     * AFTER: Swagger and all actuator endpoint
```

<br>

### File: `UserProvisioningFilter.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component, @Order(1)   // FIX-UPF1: Must run BEFORE ApprovalGateFilter (@Order(2)), @RequiredArgsConstructor, @Slf4j, @Autowired(required = false)`
**Package**: `com.event.tickets.filters`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.KeycloakAdminService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class UserProvisioningFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    @Autowired(required = false)
    private KeycloakAdminService keycloakAdminService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            
```

<br>

## Folder: `src\main\java\com\event\tickets\mappers`
---

### File: `DiscountMapper.java`
**Description / What it does**: Application interface component.
**Key Annotations**: `@Mapper(componentModel = "spring"), @Mapping(source = "ticketType.id",   target = "ticketTypeId"), @Mapping(source = "ticketType.name", target = "ticketTypeName"), @Mapping(source = "createdBy",       target = "createdBy")`
**Package**: `com.event.tickets.mappers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.DiscountResponseDto`
- `com.event.tickets.domain.entities.Discount`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface DiscountMapper`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface DiscountMapper {

    @Mapping(source = "ticketType.id",   target = "ticketTypeId")
    @Mapping(source = "ticketType.name", target = "ticketTypeName")
    @Mapping(source = "createdBy",       target = "createdBy")    // FIX D-5
    DiscountResponseDto toResponseDto(Discount discount);
}
```

<br>

### File: `EventMapper.java`
**Description / What it does**: Application interface component.
**Key Annotations**: `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE), @Mapping(target = "status", ignore = true), @AfterMapping, @MappingTarget, @AfterMapping`
**Package**: `com.event.tickets.mappers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.CreateEventRequest`
- `com.event.tickets.domain.CreateTicketTypeRequest`
- `com.event.tickets.domain.UpdateEventRequest`
- `com.event.tickets.domain.UpdateTicketTypeRequest`
- `com.event.tickets.domain.dtos.CreateEventRequestDto`
- `com.event.tickets.domain.dtos.CreateEventResponseDto`
- `com.event.tickets.domain.dtos.CreateTicketTypeRequestDto`
- `com.event.tickets.domain.dtos.GetEventDetailsResponseDto`
- `com.event.tickets.domain.dtos.GetEventDetailsTicketTypesResponseDto`
- `com.event.tickets.domain.dtos.GetPublishedEventDetailsResponseDto`
- `com.event.tickets.domain.dtos.GetPublishedEventDetailsTicketTypesResponseDto`
- `com.event.tickets.domain.dtos.ListEventResponseDto`
- `com.event.tickets.domain.dtos.ListEventTicketTypeResponseDto`
- `com.event.tickets.domain.dtos.ListPublishedEventResponseDto`
- `com.event.tickets.domain.dtos.UpdateEventRequestDto`
- `com.event.tickets.domain.dtos.UpdateEventResponseDto`
- `com.event.tickets.domain.dtos.UpdateTicketTypeRequestDto`
- `com.event.tickets.domain.dtos.UpdateTicketTypeResponseDto`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.domain.entities.TicketType`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface EventMapper`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface EventMapper {

    CreateTicketTypeRequest fromDto(CreateTicketTypeRequestDto dto);

    // FIX-E1-MAP: status ignored — service always sets DRAFT
    @Mapping(target = "status", ignore = true)
    CreateEventRequest fromDto(CreateEventRequestDto dto);

    CreateEventResponseDto toDto(Event event);

    ListEventTicketTypeResponseDto toDto(TicketType ticketType);

    ListEventResponseDto toListEventResponseDto(Event event);

    GetEventDetailsTicketTypesResponseDto toGetEventDetailsTicketTypesResponseDto(TicketType ticketType);

    GetEventDetailsResponseDto toGetEventDeta
```

<br>

### File: `TicketMapper.java`
**Description / What it does**: Application interface component.
**Key Annotations**: `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE), @Mapping(target = "price", source = "ticket.ticketType.price"), @Mapping(target = "pricePaid", source = "ticket.pricePaid"), @Mapping(target = "originalPrice", source = "ticket.originalPrice"), @Mapping(target = "discountApplied", source = "ticket.discountApplied")`
**Package**: `com.event.tickets.mappers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.GetTicketResponseDto`
- `com.event.tickets.domain.dtos.ListTicketResponseDto`
- `com.event.tickets.domain.dtos.ListTicketTicketTypeResponseDto`
- `com.event.tickets.domain.entities.Ticket`
- `com.event.tickets.domain.entities.TicketType`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface TicketMapper`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface TicketMapper {

    ListTicketTicketTypeResponseDto toListTicketTicketTypeResponseDto(TicketType ticketType);

    ListTicketResponseDto toListTicketResponseDto(Ticket ticket);

    @Mapping(target = "price", source = "ticket.ticketType.price")
    @Mapping(target = "pricePaid", source = "ticket.pricePaid")         // L-25 FIX
    @Mapping(target = "originalPrice", source = "ticket.originalPrice") // L-25 FIX
    @Mapping(target = "discountApplied", source = "ticket.discountApplied") // L-25 FIX
    @Mapping(target = "description", source = "ticket.ticketType.description")
   
```

<br>

### File: `TicketTypeMapper.java`
**Description / What it does**: Application interface component.
**Key Annotations**: `@Mapper(componentModel = "spring")`
**Package**: `com.event.tickets.mappers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.CreateTicketTypeRequest`
- `com.event.tickets.domain.UpdateTicketTypeRequest`
- `com.event.tickets.domain.dtos.CreateTicketTypeRequestDto`
- `com.event.tickets.domain.dtos.CreateTicketTypeResponseDto`
- `com.event.tickets.domain.dtos.UpdateTicketTypeRequestDto`
- `com.event.tickets.domain.dtos.UpdateTicketTypeResponseDto`
- `com.event.tickets.domain.entities.TicketType`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface TicketTypeMapper`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface TicketTypeMapper {

  TicketTypeMapper INSTANCE = Mappers.getMapper(TicketTypeMapper.class);

  // Request DTO to Domain mappings
  CreateTicketTypeRequest fromDto(CreateTicketTypeRequestDto dto);
  UpdateTicketTypeRequest fromUpdateDto(UpdateTicketTypeRequestDto dto);

  // Entity to Response DTO mappings
  CreateTicketTypeResponseDto toCreateResponseDto(TicketType ticketType);
  UpdateTicketTypeResponseDto toUpdateResponseDto(TicketType ticketType);
}

```

<br>

### File: `TicketValidationMapper.java`
**Description / What it does**: Application record component.
**Key Annotations**: `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE), @Mapping(target = "ticketId",       source = "ticket.id"), @Mapping(target = "validatedById",  source = "validatedBy.id"), @Mapping(target = "validatedByName",source = "validatedBy.name"), @Mapping(target = "validatedAt",    source = "createdAt")`
**Package**: `com.event.tickets.mappers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.TicketValidationResponseDto`
- `com.event.tickets.domain.entities.TicketValidation`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface TicketValidationMapper`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
 record was created)
 *
 * validatedBy is nullable (legacy rows pre-fix), so MapStruct correctly emits
 * null-safe property access — the generated code checks validatedBy != null.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TicketValidationMapper {

    @Mapping(target = "ticketId",       source = "ticket.id")
    @Mapping(target = "validatedById",  source = "validatedBy.id")
    @Mapping(target = "validatedByName",source = "validatedBy.name")
    @Mapping(target = "validatedAt",    source = "createdAt")
    TicketValidationResponseDt
```

<br>

## Folder: `src\main\java\com\event\tickets\repositories`
---

### File: `AuditLogRepository.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@Repository`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** All audit logs — delegated from JpaRepository.findAll(Pageable). */
    Page<AuditLog> findAll(Pageable pageable);

    /**
     * Audit logs for a specific event.
     * Spring Data derives: WHERE event.id = :eventId
     */
    Page<AuditLog> findByEventId(UUID eventId, Pageable pageable);

    /**
     * Audit logs by actor (user who performed the action).
     * Spring Data derives: WHERE actor.id = :actorId
     */
    Page<AuditLog> findByActorId(UUID actorId, Pageable pageable);

    /**
     * FIX A-7:
```

<br>

### File: `DiscountRepository.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@Repository, @Query, @Param("ticketTypeId"), @Param("now"), @Query`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.Discount`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface DiscountRepository extends JpaRepository<Discount, UUID> {

    @Query("""
      SELECT d FROM Discount d
      WHERE d.ticketType.id = :ticketTypeId
      AND d.active = true
      AND d.validFrom <= :now
      AND d.validTo > :now
      """)
    Optional<Discount> findActiveDiscount(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("now") LocalDateTime now
    );

    @Query("""
      SELECT d FROM Discount d
      WHERE d.ticketType.id = :ticketTypeId
      ORDER BY d.createdAt DESC
      """)
    List<Discount> findAllByTicketTypeId(@Param("ticketTy
```

<br>

### File: `EventRepository.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@Repository, @Lock(LockModeType.PESSIMISTIC_WRITE), @Query("SELECT e FROM Event e WHERE e.id = :id"), @Query, @Query("SELECT e FROM Event e WHERE e.status = :status AND e.end < :before")`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.StaffMemberDto`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface EventRepository extends JpaRepository<Event, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdWithLock(@Param("id") UUID id);

    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    Optional<Event> findByIdAndOrganizerId(UUID id, UUID organizerId);

    Page<Event> findByStatus(EventStatusEnum status, Pageable pageable);

    @Query(value = "SELECT * FROM events WHERE " +
            "status = 'PUBLISHED' AND " +
            "to_tsvector('english', COALESCE(name, '') || ' '
```

<br>

### File: `InviteCodeRepository.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@Repository, @Lock(LockModeType.PESSIMISTIC_WRITE), @Query("SELECT ic FROM InviteCode ic WHERE ic.code = :code"), @Query("SELECT ic FROM InviteCode ic WHERE ic.status = 'PENDING' AND ic.expiresAt < :now"), @Modifying`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.InviteCode`
- `com.event.tickets.domain.entities.InviteCodeStatus`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

  /**
   * Finds an invite code by its code string.
   *
   * @param code The invite code string
   * @return Optional containing the invite code if found
   */
  Optional<InviteCode> findByCode(String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ic FROM InviteCode ic WHERE ic.code = :code")
  Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);

  /**
   * Finds all invite codes created by a specific user.
   *
   * @param createdById The ID of the creator
   * @param pageable
```

<br>

### File: `QrCodeRepository.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@Repository`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.QrCode`
- `com.event.tickets.domain.entities.QrCodeStatusEnum`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

    Optional<QrCode> findByTicketIdAndTicketPurchaserId(UUID ticketId, UUID ticketPurchaseId);

    Optional<QrCode> findByIdAndStatus(UUID id, QrCodeStatusEnum status);

    /**
     * FIX #4: New query to look up the active QrCode record for a given ticket.
     *
     * ROOT CAUSE of the original bug:
     * generateQrCodePngForViewing/Download/Pdf() generated a new QR image encoding
     * ticket.getId() — but the QrCode entity stored a different random UUID as its
     * own ID. The validator received ticket.getId() 
```

<br>

### File: `TicketRepository.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@Repository, @Query("SELECT COUNT(t) FROM Ticket t WHERE t.ticketType.id = :ticketTypeId AND t.status <> :excludedStatus"), @Param("ticketTypeId"), @Param("excludedStatus"), @Query`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.Ticket`
- `com.event.tickets.domain.entities.TicketStatusEnum`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    int countByTicketTypeId(UUID ticketTypeId);

    /**
     * H-06 / H-07 FIX: Counts only non-CANCELLED tickets for a ticket type.
     * Used by purchaseTickets() and updateTicketType() to check capacity.
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.ticketType.id = :ticketTypeId AND t.status <> :excludedStatus")
    int countActiveByTicketTypeId(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    /**
     * FIX D-3 (BUG D-
```

<br>

### File: `TicketTypeRepository.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@Repository, @Query("SELECT tt FROM TicketType tt WHERE tt.id = :id"), @Lock(LockModeType.PESSIMISTIC_WRITE), @Lock(LockModeType.PESSIMISTIC_WRITE), @Query("SELECT tt FROM TicketType tt WHERE tt.id = :id AND tt.event.id = :eventId")`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.TicketType`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

  @Query("SELECT tt FROM TicketType tt WHERE tt.id = :id")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<TicketType> findByIdWithLock(@Param("id") UUID id);

  // Find ticket type by ID and event ID (for authorization checks)
  Optional<TicketType> findByIdAndEventId(UUID ticketTypeId, UUID eventId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT tt FROM TicketType tt WHERE tt.id = :id AND tt.event.id = :eventId")
  Optional<TicketType> findByIdAndEventIdWithLock(
          @Param("id") UUID id,
  
```

<br>

### File: `TicketValidationRepository.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@Repository`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.TicketValidation`
- `com.event.tickets.domain.entities.TicketValidationStatusEnum`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface TicketValidationRepository extends JpaRepository<TicketValidation, UUID> {

    Page<TicketValidation> findByTicketTicketTypeEventId(UUID eventId, Pageable pageable);

    List<TicketValidation> findByTicketId(UUID ticketId);

    /**
     * FIX-TV1 (BUG 6-2): EXISTS-style check for prior VALID scan.
     *
     * BEFORE: TicketValidationServiceImpl.validateTicket() called
     * ticket.getValidations().stream().filter(VALID).findFirst() — loading ALL
     * TicketValidation records for the ticket into memory just to check for a prior scan.
     *
     * AFTER: This single EXI
```

<br>

### File: `UserRepository.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@Repository`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.ApprovalStatus`
- `com.event.tickets.domain.entities.User`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Finds all users with a specific approval status.
   *
   * @param approvalStatus The approval status to filter by
   * @param pageable Pagination parameters
   * @return Page of users with the specified status
   */
  Page<User> findByApprovalStatus(ApprovalStatus approvalStatus, Pageable pageable);

  /**
   * Checks if a user with the given email already exists.
   *
   * @param email The email to check
   * @return true if user exists, false otherwise
   */
  boolean existsByEmail(String email);

  /**
    // ... (truncated)
```

<br>

## Folder: `src\main\java\com\event\tickets\scheduler`
---

### File: `InviteCodeExpiryScheduler.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@Component, @RequiredArgsConstructor, @Slf4j, @Scheduled, @Transactional`
**Package**: `com.event.tickets.scheduler`

**Internal Dependencies (What it uses):**
- `com.event.tickets.repositories.InviteCodeRepository`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class InviteCodeExpiryScheduler`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class InviteCodeExpiryScheduler {

    private final InviteCodeRepository inviteCodeRepository;

    /**
     * Marks all PENDING invite codes whose expiresAt is in the past as EXPIRED.
     * Uses a single bulk UPDATE query — no per-row round trips.
     *
     * Runs every 15 minutes. First execution 5 minutes after startup
     * (initialDelay gives the app time to fully start before the first run).
     */
    @Scheduled(
            fixedRateString  = "${app.scheduler.invite-expiry-rate-ms:900000}",   // 15 min
            initialDelayString = "${app.scheduler.invite-expiry-delay-m
```

<br>

## Folder: `src\main\java\com\event\tickets\services`
---

### File: `ApprovalService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.UserApprovalDto`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface ApprovalService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface ApprovalService {

  /**
   * Gets all users with PENDING approval status.
   *
   * @param pageable Pagination parameters
   * @return Page of users pending approval
   */
  Page<UserApprovalDto> getPendingApprovals(Pageable pageable);

  /**
   * Approves a user account.
   * Sets approval status to APPROVED and records admin who approved.
   *
   * @param userId The user ID to approve
   * @param adminId The admin user ID performing the approval
   * @throws com.event.tickets.exceptions.UserNotFoundException if user doesn't exist
   * @throws com.event.tickets.exceptions.In
```

<br>

### File: `AuditLogService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @Transactional(propagation = Propagation.REQUIRES_NEW), @Transactional(readOnly = true), @Transactional(readOnly = true), @Transactional(readOnly = true)`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.repositories.AuditLogRepository`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class AuditLogService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Saves an audit log in a NEW transaction so that audit failures never
     * roll back the business operation that triggered them.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAuditLog(AuditLog auditLog) {
        auditLogRepository.save(auditLog);
    }

    /** Returns all audit logs — ADMIN use only. */
    @Transactiona
```

<br>

### File: `AuthorizationService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.Event`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface AuthorizationService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface AuthorizationService {

  /**
   * Verifies that a user is the organizer of an event.
   *
   * @param userId The ID of the user attempting the operation
   * @param eventId The ID of the event being accessed
   * @throws org.springframework.security.access.AccessDeniedException if user is not the organizer
   * @throws com.event.tickets.exceptions.EventNotFoundException if event does not exist
   * @throws com.event.tickets.exceptions.UserNotFoundException if user does not exist
   */
  void requireOrganizerAccess(UUID userId, UUID eventId);

  /**
   * Verifies that a user i
```

<br>

### File: `DiscountService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.CreateDiscountRequestDto`
- `com.event.tickets.domain.dtos.DiscountResponseDto`
- `com.event.tickets.domain.entities.Discount`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface DiscountService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface DiscountService {

  /**
   * Creates a new discount for a ticket type.
   *
   * <p>Enforces:
   * <ul>
   *   <li>Organizer owns the event (via ticket type)</li>
   *   <li>Only one active discount per ticket type</li>
   *   <li>Valid date range (validTo > validFrom)</li>
   *   <li>Percentage range (0 < value <= 100) if PERCENTAGE type</li>
   * </ul>
   *
   * @param organizerId ID of the organizer creating the discount
   * @param eventId ID of the event (for ownership verification)
   * @param ticketTypeId ID of the ticket type
   * @param request Discount configuration
```

<br>

### File: `EmailService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface EmailService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface EmailService {

    /** Sent to user after successful registration (account pending approval). */
    void sendRegistrationEmail(String toEmail, String userName);

    /** Sent to user when admin approves their account. */
    void sendApprovalEmail(String toEmail, String userName);

    /** Sent to user when admin rejects their account. */
    void sendRejectionEmail(String toEmail, String userName, String rejectionReason);

    /** Sent to purchaser after successful ticket purchase. */
    void sendTicketConfirmationEmail(String toEmail, String userName,
                    
```

<br>

### File: `EventService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.CreateEventRequest`
- `com.event.tickets.domain.UpdateEventRequest`
- `com.event.tickets.domain.entities.Event`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface EventService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface EventService {

  Event createEvent(UUID organizerId, CreateEventRequest event);

  Page<Event> listEventsForOrganizer(UUID organizerId, Pageable pageable);

  Optional<Event> getEventForOrganizer(UUID organizerId, UUID id);

  Event updateEventForOrganizer(UUID organizerId, UUID id, UpdateEventRequest event);

  void deleteEventForOrganizer(UUID organizerId, UUID id);

  Page<Event> listPublishedEvents(Pageable pageable);

  Page<Event> searchPublishedEvents(String query, Pageable pageable);

  Optional<Event> getPublishedEvent(UUID id);

  // Sales dashboard operations
  Map
```

<br>

### File: `ExportService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface ExportService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface ExportService {

  /**
   * Generates sales report as Excel (.xlsx) file.
   *
   * REUSES: EventService.getSalesDashboard() for data
   * IDEMPOTENT: Same content every time for same event state
   * READ-ONLY: No state changes
   *
   * ACCESS CONTROL:
   * - Organizer must own the event (checked via AuthorizationService)
   * - ADMIN does NOT bypass ownership check
   *
   * @param organizerId The organizer requesting the report
   * @param eventId The event ID
   * @return Excel file bytes (.xlsx format)
   */
  byte[] generateSalesReportExcel(UUID organizerId, UUID eventI
```

<br>

### File: `InviteCodeService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.InviteCodeResponseDto`
- `com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface InviteCodeService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface InviteCodeService {

    InviteCodeResponseDto generateInviteCode(UUID creatorId, String roleName,
                                             UUID eventId, int expirationHours);

    RedeemInviteCodeResponseDto redeemInviteCode(UUID userId, String code);

    /**
     * FIX I-2: isAdmin parameter added.
     *
     * BEFORE: revokeInviteCode() called keycloakAdminService.userHasRole(revokerId, "ADMIN")
     * internally — a live Keycloak API call on every revoke request just to determine
     * whether the revoker is an admin. Spring Security has already verified this via
  
```

<br>

### File: `KeycloakAdminService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface KeycloakAdminService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface KeycloakAdminService {

    /**
     * Assigns a role to a user in Keycloak.
     *
     * @param userId The UUID of the user (Keycloak user ID)
     * @param roleName The name of the role to assign (ORGANIZER, ATTENDEE, STAFF, ADMIN)
     * @throws com.event.tickets.exceptions.UserNotFoundException if user doesn't exist
     * @throws com.event.tickets.exceptions.KeycloakOperationException if Keycloak operation fails
     */
    void assignRoleToUser(UUID userId, String roleName);

    /**
     * Revokes a role from a user in Keycloak.
     *
     * @param userId The UUID of 
```

<br>

### File: `KeycloakUserService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface KeycloakUserService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface KeycloakUserService {

  /**
   * Creates a new user in Keycloak.
   *
   * @param email User's email (also used as username)
   * @param password User's password (will be hashed by Keycloak)
   * @param name User's display name
   * @return The Keycloak user ID (UUID) of the created user
   * @throws com.event.tickets.exceptions.KeycloakUserCreationException if creation fails
   */
  UUID createUser(String email, String password, String name);

  /**
   * Assigns a realm-level role to a user in Keycloak.
   *
   * @param userId The Keycloak user ID
   * @param roleName The ro
```

<br>

### File: `QrCodeService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.QrCode`
- `com.event.tickets.domain.entities.Ticket`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface QrCodeService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface QrCodeService {

  /**
   * Generates and persists QR code for a ticket.
   * Called during ticket purchase.
   */
  QrCode generateQrCode(Ticket ticket);

  /**
   * Gets QR code image for inline viewing (legacy method).
   * Authorization: User must own ticket or own the event.
   */
  byte[] getQrCodeImageForUserAndTicket(UUID userId, UUID ticketId);

  /**
   * Generates QR code PNG image for viewing (inline display).
   * IDEMPOTENT: Same content every time for same ticket.
   * NO side effects, read-only operation.
   *
   * @param userId User requesting the QR code
    // ... (truncated)
```

<br>

### File: `RegistrationService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.RegisterRequestDto`
- `com.event.tickets.domain.dtos.RegisterResponseDto`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface RegistrationService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface RegistrationService {

  /**
   * Registers a new user via invite code.
   *
   * TRANSACTION FLOW:
   * 1. Validate invite code (exists, PENDING status, not expired)
   * 2. Check email not already registered
   * 3. Create user in Keycloak
   * 4. Assign role from invite code in Keycloak
   * 5. Create user record in database (status=PENDING)
   * 6. If STAFF role: Assign to event
   * 7. Mark invite code as REDEEMED
   *
   * ROLLBACK ON ANY FAILURE:
   * - Delete Keycloak user if created
   * - Do NOT mark invite as used
   * - Log failure for investigation
   *
   * @para
```

<br>

### File: `SystemUserProvider.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @DependsOn("databaseInitializer"), @RequiredArgsConstructor, @Slf4j, @PostConstruct`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.SystemUserNotFoundException`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.util.SystemUser`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class SystemUserProvider`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class SystemUserProvider {

    private final UserRepository userRepository;
    private User systemUser;

    @PostConstruct
    public void loadSystemUser() {
        // FIX-SU1: Reference the single constant, not a local copy
        systemUser = userRepository.findById(SystemUser.SYSTEM_USER_UUID)
                .orElseThrow(() -> new SystemUserNotFoundException(
                        "SYSTEM user not found. DatabaseInitializer may have failed."));
        log.info("SYSTEM user loaded: {}", systemUser.getId());
    }

    public User getSystemUser() {
        return systemUser;
 
```

<br>

### File: `TicketService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.Ticket`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface TicketService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface TicketService {
  Page<Ticket> listTicketsForUser(UUID userId, Pageable pageable);
  Optional<Ticket> getTicketForUser(UUID userId, UUID ticketId);
}

```

<br>

### File: `TicketTypeService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.CreateTicketTypeRequest`
- `com.event.tickets.domain.UpdateTicketTypeRequest`
- `com.event.tickets.domain.entities.Ticket`
- `com.event.tickets.domain.entities.TicketType`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface TicketTypeService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface TicketTypeService {

    /**
     * Purchase tickets with mandatory event ownership validation.
     *
     * Enforces:
     * - ticketTypeId belongs to eventId (cross-event purchase prevention)
     * - Event status must be PUBLISHED
     * - Current time within salesStart/salesEnd window
     * - Per-type capacity not exceeded (active tickets only)
     * - Event maxCapacity not exceeded (active tickets only)
     * - Per-user limit not exceeded (max 10 per user per type, including cancelled)
     *
     * Uses PESSIMISTIC_WRITE lock on the ticket type row to prevent oversel
```

<br>

### File: `TicketValidationService.java`
**Description / What it does**: Contains core business logic and transaction management.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.TicketValidation`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface TicketValidationService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public interface TicketValidationService {
  TicketValidation validateTicketByQrCode(UUID userId, UUID qrCodeId);
  TicketValidation validateTicketManually(UUID userId, UUID ticketId);

  // Staff listing operations - now with authorization
  Page<TicketValidation> listValidationsForEvent(UUID userId, UUID eventId, Pageable pageable);
  List<TicketValidation> getValidationsByTicket(UUID userId, UUID ticketId);
}

```

<br>

### File: `EventStaffService.java`
**Description / What it does**: Application interface component.
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.EventStaffResponseDto`
- `com.event.tickets.domain.dtos.StaffMemberDto`

**File Code Structure:**
- **State/Properties (Fields):**
  - `interface EventStaffService`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
 interface for any other callers, but is no longer
 * called by EventStaffController after assign/remove.
 */
public interface EventStaffService {

    /**
     * Assigns a staff member to an event and returns the updated staff list.
     *
     * Requirements:
     * - User must have STAFF role in Keycloak
     * - Organizer must own the event
     *
     * @return Complete EventStaffResponseDto with updated staff list — no extra queries needed.
     */
    EventStaffResponseDto assignStaffToEvent(UUID organizerId, UUID eventId, UUID userId);

    /**
     * Removes a staff member from an eve
```

<br>

## Folder: `src\main\java\com\event\tickets\services\impl`
---

### File: `ApprovalServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @Transactional(readOnly = true), @Transactional`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.UserApprovalDto`
- `com.event.tickets.domain.entities.ApprovalStatus`
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.InvalidApprovalStateException`
- `com.event.tickets.exceptions.InvalidBusinessStateException`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.ApprovalService`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.EmailService`
- `com.event.tickets.services.KeycloakAdminService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ApprovalServiceImpl implements ApprovalService {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public Page<UserApprovalDto> getPendingApprovals(Pageable pageable) {
        log.debug("Fetching pending approvals, page: {}", pageable.getPageNumber());
        // FIX-A1: Map with full role data so admin sees what role each user registered for.
        return userRepositor
```

<br>

### File: `AuthorizationServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.EventNotFoundException`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.EventRepository`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuthorizationService`
- `com.event.tickets.services.KeycloakAdminService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class AuthorizationServiceImpl implements AuthorizationService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Override
    public void requireOrganizerAccess(UUID userId, UUID eventId) {
        log.debug("Checking organizer access: userId={}, eventId={}", userId, eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventI
```

<br>

### File: `DiscountServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @Transactional, @Transactional`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.CreateDiscountRequestDto`
- `com.event.tickets.domain.entities.Discount`
- `com.event.tickets.domain.entities.DiscountType`
- `com.event.tickets.domain.entities.TicketStatusEnum`
- `com.event.tickets.domain.entities.TicketType`
- `com.event.tickets.exceptions.DiscountAlreadyExistsException`
- `com.event.tickets.exceptions.DiscountNotFoundException`
- `com.event.tickets.exceptions.InvalidInputException`
- `com.event.tickets.exceptions.TicketTypeNotFoundException`
- `com.event.tickets.repositories.DiscountRepository`
- `com.event.tickets.repositories.TicketRepository`
- `com.event.tickets.repositories.TicketTypeRepository`
- `com.event.tickets.services.AuthorizationService`
- `com.event.tickets.services.DiscountService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discountRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;
    private final AuthorizationService authorizationService;

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Discount createDiscount(UUID organizerId, UUID eventId, UUID ticketTypeId,
                                   CreateDiscountRequestDto request) {
        log.info("Creating disc
```

<br>

### File: `EmailServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @Slf4j, @lombok, @Value("${app.brevo.api-key}"), @Value("${app.mail.from-email}")`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.services.EmailService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EmailServiceImpl implements EmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    /**
     * L-13 FIX: RestTemplate injected via constructor, not created with new.
     * new RestTemplate() bypasses Spring auto-configuration — connection timeouts,
     * message converters, and interceptors configured via RestTemplateBuilder are ignored.
     * More importantly, new RestTemplate() cannot be replaced by @MockBean in tests,
     * making it impossible to unit-test email sending without live HTTP calls.
     *
     * A RestTemplat
```

<br>

### File: `EventServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @Transactional, @Transactional(readOnly = true)`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.CreateEventRequest`
- `com.event.tickets.domain.UpdateEventRequest`
- `com.event.tickets.domain.UpdateTicketTypeRequest`
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.domain.entities.EventStatusEnum`
- `com.event.tickets.domain.entities.TicketStatusEnum`
- `com.event.tickets.domain.entities.TicketType`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.EventNotFoundException`
- `com.event.tickets.exceptions.EventUpdateException`
- `com.event.tickets.exceptions.InvalidBusinessStateException`
- `com.event.tickets.exceptions.TicketTypeNotFoundException`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.EventRepository`
- `com.event.tickets.repositories.TicketRepository`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.AuthorizationService`
- `com.event.tickets.services.EmailService`
- `com.event.tickets.services.EventService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.util.RequestUtil.extractClientIp`
- `com.event.tickets.util.RequestUtil.extractUserAgent`
- `com.event.tickets.util.RequestUtil.getCurrentRequest`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EventServiceImpl implements EventService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final AuthorizationService authorizationService;
    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;
    private final SystemUserProvider systemUserProvider;
    private final EmailService emailService;

    // ── Valid status transitions ──────────────────────────────────────────────

    private static final Map<EventStatusEnum, Set<EventStatusEnum>> VALID_TRANSITIONS = Map.
```

<br>

### File: `EventStaffServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @Transactional, @Transactional`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.EventStaffResponseDto`
- `com.event.tickets.domain.dtos.StaffMemberDto`
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.EventNotFoundException`
- `com.event.tickets.exceptions.InvalidBusinessStateException`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.EventRepository`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.AuthorizationService`
- `com.event.tickets.services.EventStaffService`
- `com.event.tickets.services.KeycloakAdminService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.util.RequestUtil.extractClientIp`
- `com.event.tickets.util.RequestUtil.extractUserAgent`
- `com.event.tickets.util.RequestUtil.getCurrentRequest`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class EventStaffServiceImpl implements EventStaffService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final KeycloakAdminService keycloakAdminService;
    private final SystemUserProvider systemUserProvider;
    private final AuditLogService auditLogService;

    // ── ASSIGN ────────────────────────────────────────────────────────────────

    /**
     * FIX S-6: Returns EventStaffResponseDto — controller no longer needs extra calls.
     * FIX S-3: Organiz
```

<br>

### File: `ExportServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @SuppressWarnings("unchecked")`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.ReportGenerationException`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.EventService`
- `com.event.tickets.services.ExportService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.util.RequestUtil.extractClientIp`
- `com.event.tickets.util.RequestUtil.extractUserAgent`
- `com.event.tickets.util.RequestUtil.getCurrentRequest`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class ExportServiceImpl implements ExportService {

    private final EventService eventService;
    private final UserRepository userRepository;
    private final SystemUserProvider systemUserProvider;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter FILENAME_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public byte[] generateSalesReportExcel(UUID organizerId, UUID eventId) {
        log.info("Generating sales report Excel: organizerId={}, eventId={}", organizerId, eventId);
        Map<String,
```

<br>

### File: `InviteCodeServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @Transactional, @Transactional`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.InviteCodeResponseDto`
- `com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto`
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.domain.entities.InviteCode`
- `com.event.tickets.domain.entities.InviteCodeStatus`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.EventNotFoundException`
- `com.event.tickets.exceptions.InvalidBusinessStateException`
- `com.event.tickets.exceptions.InvalidInputException`
- `com.event.tickets.exceptions.InvalidInviteCodeException`
- `com.event.tickets.exceptions.InviteCodeNotFoundException`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.EventRepository`
- `com.event.tickets.repositories.InviteCodeRepository`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.InviteCodeService`
- `com.event.tickets.services.KeycloakAdminService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.util.RequestUtil.extractClientIp`
- `com.event.tickets.util.RequestUtil.extractUserAgent`
- `com.event.tickets.util.RequestUtil.getCurrentRequest`

**File Code Structure:**
- **State/Properties (Fields):**
  - `AuditLogService auditLogService`
  - `EventRepository eventRepository`
  - `InviteCodeRepository inviteCodeRepository`
  - `KeycloakAdminService keycloakAdminService`
  - `SecureRandom SECURE_RANDOM`
  - `String CODE_CHARACTERS`
  - `SystemUserProvider systemUserProvider`
  - `UserRepository userRepository`
  - `int CODE_LENGTH`
  - `int MAX_INVITES_PER_EVENT`
  - `int MAX_INVITES_PER_ORGANIZER`
- **Behavior/Capabilities (Methods):**
  - `InviteCodeResponseDto getInviteCode(UUID codeId)`
  - `InviteCodeResponseDto mapToResponseDto(InviteCode inviteCode)`
  - `Page<InviteCodeResponseDto> listAllInviteCodes(Pageable pageable)`
  - `Page<InviteCodeResponseDto> listInviteCodesByCreator(UUID creatorId, Pageable pageable)`
  - `Page<InviteCodeResponseDto> listInviteCodesByEvent(UUID eventId, Pageable pageable)`
  - `RedeemInviteCodeResponseDto redeemInviteCode(UUID userId, String code)`
  - `String generateRandomCode()`
  - `int markExpiredCodes()`
  - `void emitAdminRoleGrantedAudit(User newAdmin, InviteCode inviteCode)`
  - `void emitFailedInviteRedemption(User user, InviteCode inviteCode, String reason)`
  - `void emitInviteRedeemedAudit(User user, InviteCode inviteCode)`
  - `void revokeInviteCode(UUID revokerId, UUID codeId, String reason, boolean isAdmin)`
  - `void validateCodeForRedemption(InviteCode inviteCode)`

**Relevant Code Snippet:**
```java
public class InviteCodeServiceImpl implements InviteCodeService {

    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final AuditLogService auditLogService;
    private final SystemUserProvider systemUserProvider;

    private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRando
```

<br>

### File: `KeycloakAdminServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @Value("${keycloak.admin.target-realm}"), @Transactional`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.KeycloakOperationException`
- `com.event.tickets.exceptions.KeycloakUserCreationException`
- `com.event.tickets.exceptions.KeycloakUserDeletionException`
- `com.event.tickets.exceptions.KeycloakUserUpdateException`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.KeycloakAdminService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.util.RequestUtil.extractClientIp`
- `com.event.tickets.util.RequestUtil.extractUserAgent`
- `com.event.tickets.util.RequestUtil.getCurrentRequest`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class KeycloakAdminServiceImpl implements KeycloakAdminService {

    private final Keycloak keycloakAdminClient;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectProvider<SystemUserProvider> systemUserProviderProvider;

    @Value("${keycloak.admin.target-realm}")
    private String realm;

    @Override
    @Transactional
    @Retry(name = "keycloak")
    public void assignRoleToUser(UUID userId, String roleName) {
        log.info("Assigning role '{}' to user '{}'", roleName, userId);

        // FIX #12-1: Va
```

<br>

### File: `QrCodeServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.QrCode`
- `com.event.tickets.domain.entities.QrCodeStatusEnum`
- `com.event.tickets.domain.entities.Ticket`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.QrCodeGenerationException`
- `com.event.tickets.exceptions.QrCodeNotFoundException`
- `com.event.tickets.exceptions.TicketNotFoundException`
- `com.event.tickets.repositories.QrCodeRepository`
- `com.event.tickets.repositories.TicketRepository`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.AuthorizationService`
- `com.event.tickets.services.QrCodeService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.util.RequestUtil.extractClientIp`
- `com.event.tickets.util.RequestUtil.extractUserAgent`
- `com.event.tickets.util.RequestUtil.getCurrentRequest`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class QrCodeServiceImpl implements QrCodeService {

    private static final int QR_HEIGHT = 300;
    private static final int QR_WIDTH = 300;

    private final QRCodeWriter qrCodeWriter = new QRCodeWriter();
    private final QrCodeRepository qrCodeRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final SystemUserProvider systemUserProvider;

    @Override
    public QrCode generateQrCode(Ticket tick
```

<br>

### File: `RegistrationServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @Transactional(rollbackFor = Exception.class)`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.RegisterRequestDto`
- `com.event.tickets.domain.dtos.RegisterResponseDto`
- `com.event.tickets.domain.entities.ApprovalStatus`
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.domain.entities.InviteCode`
- `com.event.tickets.domain.entities.InviteCodeStatus`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.EmailAlreadyInUseException`
- `com.event.tickets.exceptions.InvalidInviteCodeException`
- `com.event.tickets.exceptions.InviteCodeNotFoundException`
- `com.event.tickets.exceptions.KeycloakUserCreationException`
- `com.event.tickets.exceptions.RegistrationException`
- `com.event.tickets.repositories.EventRepository`
- `com.event.tickets.repositories.InviteCodeRepository`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.EmailService`
- `com.event.tickets.services.KeycloakAdminService`
- `com.event.tickets.services.RegistrationService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.util.RequestUtil.extractClientIp`
- `com.event.tickets.util.RequestUtil.extractUserAgent`
- `com.event.tickets.util.RequestUtil.getCurrentRequest`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class RegistrationServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final EventRepository eventRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final SystemUserProvider systemUserProvider;
    private final AuditLogService auditLogService;
    private final EmailService emailService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterResponseDto register(RegisterRequestDto request) {
        // FIX-R2: 
```

<br>

### File: `TicketServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Transactional(readOnly = true), @Transactional(readOnly = true)`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.Ticket`
- `com.event.tickets.repositories.TicketRepository`
- `com.event.tickets.services.TicketService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketServiceImpl implements TicketService {

  private final TicketRepository ticketRepository;

  @Override
  @Transactional(readOnly = true)
  public Page<Ticket> listTicketsForUser(UUID userId, Pageable pageable) {
    return ticketRepository.findByPurchaserId(userId, pageable);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Ticket> getTicketForUser(UUID userId, UUID ticketId) {
    return ticketRepository.findByIdAndPurchaserId(ticketId, userId);
  }
}

```

<br>

### File: `TicketTypeServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @Transactional, @Transactional`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.CreateTicketTypeRequest`
- `com.event.tickets.domain.UpdateTicketTypeRequest`
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.Discount`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.domain.entities.EventStatusEnum`
- `com.event.tickets.domain.entities.Ticket`
- `com.event.tickets.domain.entities.TicketStatusEnum`
- `com.event.tickets.domain.entities.TicketType`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.EventNotFoundException`
- `com.event.tickets.exceptions.InvalidBusinessStateException`
- `com.event.tickets.exceptions.TicketTypeDeleteNotAllowedException`
- `com.event.tickets.exceptions.TicketTypeNotFoundException`
- `com.event.tickets.exceptions.TicketsSoldOutException`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.EventRepository`
- `com.event.tickets.repositories.TicketRepository`
- `com.event.tickets.repositories.TicketTypeRepository`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.AuthorizationService`
- `com.event.tickets.services.DiscountService`
- `com.event.tickets.services.EmailService`
- `com.event.tickets.services.QrCodeService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.services.TicketTypeService`
- `com.event.tickets.util.RequestUtil.extractClientIp`
- `com.event.tickets.util.RequestUtil.extractUserAgent`
- `com.event.tickets.util.RequestUtil.getCurrentRequest`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketTypeServiceImpl implements TicketTypeService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;
    private final AuthorizationService authorizationService;
    private final DiscountService discountService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final SystemUserProvider systemUserProvider;

    /*
```

<br>

### File: `TicketValidationServiceImpl.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@Service, @RequiredArgsConstructor, @Slf4j, @Transactional, @Transactional(readOnly = true)`
**Package**: `com.event.tickets.services.impl`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.Event`
- `com.event.tickets.domain.entities.QrCode`
- `com.event.tickets.domain.entities.QrCodeStatusEnum`
- `com.event.tickets.domain.entities.Ticket`
- `com.event.tickets.domain.entities.TicketStatusEnum`
- `com.event.tickets.domain.entities.TicketValidation`
- `com.event.tickets.domain.entities.TicketValidationMethod`
- `com.event.tickets.domain.entities.TicketValidationStatusEnum`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.EventNotFoundException`
- `com.event.tickets.exceptions.InvalidBusinessStateException`
- `com.event.tickets.exceptions.QrCodeNotFoundException`
- `com.event.tickets.exceptions.TicketNotFoundException`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.EventRepository`
- `com.event.tickets.repositories.QrCodeRepository`
- `com.event.tickets.repositories.TicketRepository`
- `com.event.tickets.repositories.TicketValidationRepository`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.AuthorizationService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.services.TicketValidationService`
- `com.event.tickets.util.RequestUtil.extractClientIp`
- `com.event.tickets.util.RequestUtil.extractUserAgent`
- `com.event.tickets.util.RequestUtil.getCurrentRequest`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TicketValidationServiceImpl implements TicketValidationService {

    private final QrCodeRepository qrCodeRepository;
    private final TicketValidationRepository ticketValidationRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final SystemUserProvider systemUserProvider;
    private final AuditLogService auditLogService;

    // ── VALIDATE BY QR ───────────────────────────────────────────────
```

<br>

## Folder: `src\main\java\com\event\tickets\util`
---

### File: `JwtUtil.java`
**Description / What it does**: Application class component.
**Package**: `com.event.tickets.util`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class JwtUtil`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public final class JwtUtil {
  private JwtUtil(){
  }

  public static UUID parseUserId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }


}

```

<br>

### File: `PriceUtil.java`
**Description / What it does**: Application class component.
**Package**: `com.event.tickets.util`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class PriceUtil`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class PriceUtil {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Round a price to 2 decimal places using HALF_UP mode.
     * Standard for financial calculations in most jurisdictions.
     *
     * Examples:
     * - 10.125 → 10.13
     * - 10.124 → 10.12
     *
     * @param value The price to round
     * @return Rounded price, or null if input is null
     */
    public static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return null;
        }
    // ... (truncated)
```

<br>

### File: `RequestUtil.java`
**Description / What it does**: Data Transfer Object for API request/response payload.
**Key Annotations**: `@UtilityClass`
**Package**: `com.event.tickets.util`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class RequestUtil`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class RequestUtil {

  /**
   * Returns the current HTTP request, or null if called outside a request context
   * (e.g. from a scheduler or async thread).
   *
   * @return current HttpServletRequest, or null if not in a web request scope
   */
  public static HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }

  /**
   * Extracts client IP address from HTTP request.
   * Returns "unknown" if request is null.
```

<br>

### File: `SystemUser.java`
**Description / What it does**: Application class component.
**Package**: `com.event.tickets.util`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class SystemUser`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public final class SystemUser {

    /** The well-known UUID for the SYSTEM audit actor. All-zeros by convention. */
    public static final UUID SYSTEM_USER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private SystemUser() {}
}
```

<br>

## Folder: `src\test\java\com\event\tickets`
---

### File: `EventBookingAppApplicationTests.java`
**Description / What it does**: Unit/Integration Test suite.
**Key Annotations**: `@ActiveProfiles("test")`
**Package**: `com.event.tickets`

**Internal Dependencies (What it uses):**
- `com.event.tickets.config.TestSecurityConfig`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class EventBookingAppApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the application context loads successfully
    }
}

```

<br>

## Folder: `src\test\java\com\event\tickets\config`
---

### File: `StartupDiagnosticsRunnerTest.java`
**Description / What it does**: Unit/Integration Test suite.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @DisplayName("StartupDiagnosticsRunner"), @Mock, @Mock, @InjectMocks`
**Package**: `com.event.tickets.config`

**Internal Dependencies (What it uses):**
- `com.event.tickets.services.KeycloakAdminService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class StartupDiagnosticsRunnerTest {

    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private StartupDiagnosticsRunner runner;

    @Test
    @DisplayName("does not throw when Keycloak is reachable and schema is complete")
    void happyPath_nothingThrows() {
        when(keycloakAdminService.getAvailableRoles()).thenReturn(List.of("ADMIN","ORGANIZER","STAFF","ATTENDEE"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(
                        "users","ev
```

<br>

### File: `TestSecurityConfig.java`
**Description / What it does**: Application configuration (Security, Beans, Web).
**Key Annotations**: `@Bean, @Primary, @Bean, @Primary`
**Package**: `com.event.tickets.config`

**File Code Structure:**
- **State/Properties (Fields):**
  - `class TestSecurityConfig`
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
public class TestSecurityConfig {

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("sub", "test-user")
                // T-01 FIX: Keycloak puts roles in realm_access.roles, NOT a top-level "roles" claim.
                // The old .claim("roles", ...) mirrored the production C-08 bug — tests were passing
                // against a JWT structure that does not match real Keycloak tokens.
                // SecurityConfig.extractAuthorities() reads realm_access.roles — t
```

<br>

### File: `KeycloakAdminConfigWiringTest.java`
**Description / What it does**: Application class component.
**Key Annotations**: `@DisplayName("KeycloakAdminConfig wiring"), @DisplayName("creates keycloakAdminClient bean from CLIENT_CREDENTIALS properties"), @DisplayName("does not fail when clientSecret is blank (public client fallback)")`
**Package**: `com.event.tickets.config`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
 class had username/password fields that were used in KeycloakBuilder.
 *
 * AFTER: Config only needs serverUrl, realm, clientId, clientSecret, targetRealm.
 * username and password fields are removed — they were for the PASSWORD grant which
 * is now replaced by CLIENT_CREDENTIALS. Providing them would cause Spring to reject
 * unknown properties (or silently ignore them, masking the migration).
 *
 * Also added: targetRealm property which was missing from the old test but is required
 * by KeycloakAdminServiceImpl via @Value("${keycloak.admin.target-realm}").
 */
@DisplayName("KeycloakAdminC
```

<br>

## Folder: `src\test\java\com\event\tickets\controllers`
---

### File: `EventControllerUpdateTest.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@BeforeEach, @DisplayName("UpdateEventRequestDto — null id field is excluded from JSON (@JsonInclude NON_NULL)"), @DisplayName("UpdateEventRequestDto — explicit id field is preserved in serialization"), @DisplayName("C-04 FIX — null maxCapacity excluded from JSON (prevents silent cap wipe on PUT)"), @DisplayName("Controller defensive check — detects mismatch between URL id and body id")`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.UpdateEventRequestDto`
- `com.event.tickets.domain.entities.EventStatusEnum`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class EventControllerUpdateTest {

    // FIX: no @SpringBootTest — just create ObjectMapper directly
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Register JavaTimeModule if needed for LocalDateTime serialization
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("UpdateEventRequestDto — null id field is excluded from JSON (@JsonInclude NON_NULL)")
    void validateDtoContract_NullIdExcludedFromJson() throws Exception {
        UpdateEventRequestDto dto = new UpdateEventRequestDto
```

<br>

### File: `PublishedEventControllerPageableSortIntegrationTest.java`
**Description / What it does**: Handles incoming HTTP requests and routing.
**Key Annotations**: `@AutoConfigureMockMvc(addFilters = false), @ActiveProfiles("test"), @DisplayName("PublishedEventController pageable integration"), @Autowired, @MockitoBean`
**Package**: `com.event.tickets.controllers`

**Internal Dependencies (What it uses):**
- `com.event.tickets.config.TestSecurityConfig`
- `com.event.tickets.mappers.EventMapper`
- `com.event.tickets.services.EventService`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class PublishedEventControllerPageableSortIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private EventMapper eventMapper;

    @Test
    @DisplayName("preserves sort parameter in pageable for list endpoint")
    @WithMockUser(roles = "ATTENDEE")
    void preservesSortForPublishedEventsList() throws Exception {
        when(eventService.listPublishedEvents(any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/published-events")
    // ... (truncated)
```

<br>

## Folder: `src\test\java\com\event\tickets\filters`
---

### File: `ApprovalGateFilterTest.java`
**Description / What it does**: Unit/Integration Test suite.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @DisplayName("ApprovalGateFilter"), @Mock, @Mock, @Mock`
**Package**: `com.event.tickets.filters`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.ApprovalStatus`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.AuditLogService`
- `com.event.tickets.services.SystemUserProvider`
- `com.event.tickets.services.impl.KeycloakAdminServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class ApprovalGateFilterTest {

    @Mock private UserRepository userRepository;
    @Mock private KeycloakAdminServiceImpl keycloakAdminService;
    @Mock private AuditLogService auditLogService;
    @Mock private SystemUserProvider systemUserProvider;

    // ObjectMapper needs to be real — it serializes the JSON error body
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ApprovalGateFilter filter;

    private UUID userId;
    private User pendingUser;
    private User approvedUser;
    private User rejectedUser;

    @BeforeEach
    // ... (truncated)
```

<br>

### File: `UserProvisioningFilterTest.java`
**Description / What it does**: Application record component.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @DisplayName("UserProvisioningFilter"), @Mock, @InjectMocks, @BeforeEach`
**Package**: `com.event.tickets.filters`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.ApprovalStatus`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.repositories.UserRepository`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
 record is missing (desync = untrusted state).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserProvisioningFilter")
class UserProvisioningFilterTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserProvisioningFilter filter;

    private UUID userId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userId = UUID.randomUUID();
    }

    private void setUpJwtAuthentication(UUID subjectId) {
    // ... (truncated)
```

<br>

## Folder: `src\test\java\com\event\tickets\repositories`
---

### File: `TicketRepositoryTest.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@ActiveProfiles("test"), @DisplayName("TicketRepository"), @Autowired, @Autowired, @BeforeEach`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.*`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class TicketRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TicketRepository ticketRepository;

    // FIX: store only IDs, not entity instances — entities become detached between tests
    private UUID eventId;
    private UUID ticketTypeId;
    private UUID purchaserId;

    @BeforeEach
    void setUp() {
        User organizer = new User();
        organizer.setId(UUID.randomUUID());
        organizer.setName("Org");
        organizer.setEmail("org@test.com");
    // ... (truncated)
```

<br>

### File: `DiscountRepositoryTest.java`
**Description / What it does**: Handles database access and projection interfaces.
**Key Annotations**: `@ActiveProfiles("test"), @DisplayName("DiscountRepository"), @Autowired, @Autowired, @BeforeEach`
**Package**: `com.event.tickets.repositories`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.*`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java
 class holds a stale detached reference.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("DiscountRepository")
class DiscountRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DiscountRepository discountRepository;

    // Store IDs only — never store entity instances as fields in @DataJpaTest
    // because they become detached between tests
    private UUID ticketTypeId;

    @BeforeEach
    void setUp() {
        User organizer = new User();
    // ... (truncated)
```

<br>

## Folder: `src\test\java\com\event\tickets\services`
---

### File: `ApprovalServiceImplTest.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @DisplayName("ApprovalServiceImpl"), @Mock, @Mock, @Mock`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.UserApprovalDto`
- `com.event.tickets.domain.entities.ApprovalStatus`
- `com.event.tickets.domain.entities.AuditAction`
- `com.event.tickets.domain.entities.AuditLog`
- `com.event.tickets.domain.entities.User`
- `com.event.tickets.exceptions.InvalidApprovalStateException`
- `com.event.tickets.exceptions.UserNotFoundException`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.impl.ApprovalServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class ApprovalServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ApprovalServiceImpl service;

    private UUID userId;
    private UUID adminId;
    private User pendingUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        userId  = UUID.randomUUID();
        adminId = UUID.randomUUID();
    // ... (truncated)
```

<br>

### File: `DiscountServiceImplTest.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @MockitoSettings(strictness = Strictness.LENIENT), @DisplayName("DiscountServiceImpl"), @Mock, @Mock`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.CreateDiscountRequestDto`
- `com.event.tickets.domain.entities.*`
- `com.event.tickets.exceptions.*`
- `com.event.tickets.repositories.*`
- `com.event.tickets.services.impl.DiscountServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class DiscountServiceImplTest {

    @Mock private DiscountRepository discountRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks
    private DiscountServiceImpl service;

    private UUID organizerId;
    private UUID eventId;
    private UUID ticketTypeId;
    private UUID discountId;
    private TicketType ticketType;
    private Event event;
    private Discount existingDiscount;

    @BeforeEach
    // ... (truncated)
```

<br>

### File: `EventServiceImplTest.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @MockitoSettings(strictness = Strictness.LENIENT), @DisplayName("EventServiceImpl"), @Mock, @Mock`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.CreateEventRequest`
- `com.event.tickets.domain.CreateTicketTypeRequest`
- `com.event.tickets.domain.UpdateEventRequest`
- `com.event.tickets.domain.UpdateTicketTypeRequest`
- `com.event.tickets.domain.entities.*`
- `com.event.tickets.exceptions.*`
- `com.event.tickets.repositories.*`
- `com.event.tickets.services.impl.EventServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class EventServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private TicketRepository ticketRepository;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;
    @Mock private SystemUserProvider systemUserProvider;

    @InjectMocks
    private EventServiceImpl service;

    private UUID organizerId;
    private UUID eventId;
    private User organizer;
    private Event event;
    private TicketType ticketType;

    // ... (truncated)
```

<br>

### File: `EventStaffServiceImplTest.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @MockitoSettings(strictness = Strictness.LENIENT), @DisplayName("EventStaffServiceImpl"), @Mock, @Mock`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.EventStaffResponseDto`
- `com.event.tickets.domain.dtos.StaffMemberDto`
- `com.event.tickets.domain.entities.*`
- `com.event.tickets.exceptions.*`
- `com.event.tickets.repositories.*`
- `com.event.tickets.services.impl.EventStaffServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class EventStaffServiceImplTest {

    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private SystemUserProvider systemUserProvider;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private EventStaffServiceImpl service;

    private UUID organizerId;
    private UUID eventId;
    private UUID staffUserId;
    private User organizer;
    private User staffUser;
    private Event event;

    // ... (truncated)
```

<br>

### File: `InviteCodeServiceImplTest.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @MockitoSettings(strictness = Strictness.LENIENT), @DisplayName("InviteCodeServiceImpl"), @Mock, @Mock`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.InviteCodeResponseDto`
- `com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto`
- `com.event.tickets.domain.entities.*`
- `com.event.tickets.exceptions.*`
- `com.event.tickets.repositories.*`
- `com.event.tickets.services.impl.InviteCodeServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class InviteCodeServiceImplTest {

    @Mock private InviteCodeRepository inviteCodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private AuditLogService auditLogService;
    @Mock private SystemUserProvider systemUserProvider;

    @InjectMocks
    private InviteCodeServiceImpl service;

    private UUID creatorId;
    private UUID eventId;
    private UUID userId;
    private User creator;
    private User redeemer;
    private Event event;

    // ... (truncated)
```

<br>

### File: `QrCodeServiceImplTest.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @MockitoSettings(strictness = Strictness.LENIENT), @DisplayName("QrCodeServiceImpl"), @Mock, @Mock`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.*`
- `com.event.tickets.exceptions.QrCodeNotFoundException`
- `com.event.tickets.exceptions.TicketNotFoundException`
- `com.event.tickets.repositories.QrCodeRepository`
- `com.event.tickets.repositories.TicketRepository`
- `com.event.tickets.repositories.UserRepository`
- `com.event.tickets.services.impl.QrCodeServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class QrCodeServiceImplTest {

    @Mock private QrCodeRepository qrCodeRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditLogService auditLogService;
    @Mock private SystemUserProvider systemUserProvider;

    @InjectMocks
    private QrCodeServiceImpl service;

    private UUID userId;
    private UUID ticketId;
    private UUID qrCodeId;
    private UUID organizerId;
    private User purchaser;
    private User organizer;
    private Event eve
```

<br>

### File: `RegistrationServiceImplTest.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @MockitoSettings(strictness = Strictness.LENIENT), @DisplayName("RegistrationServiceImpl"), @Mock, @Mock`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.dtos.RegisterRequestDto`
- `com.event.tickets.domain.dtos.RegisterResponseDto`
- `com.event.tickets.domain.entities.*`
- `com.event.tickets.exceptions.*`
- `com.event.tickets.repositories.*`
- `com.event.tickets.services.impl.RegistrationServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class RegistrationServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private InviteCodeRepository inviteCodeRepository;
    @Mock private EventRepository eventRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private SystemUserProvider systemUserProvider;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private RegistrationServiceImpl service;

    private RegisterRequestDto request;
    private UUID keycloakUserId;

    @BeforeEach
    void setUp() {
        keyclo
```

<br>

### File: `TicketTypeServiceImplTest.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @MockitoSettings(strictness = Strictness.LENIENT), @DisplayName("TicketTypeServiceImpl"), @Mock, @Mock`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.*`
- `com.event.tickets.exceptions.*`
- `com.event.tickets.repositories.*`
- `com.event.tickets.services.impl.TicketTypeServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class TicketTypeServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;    // TEST-TT1: new — needed by 4-arg overload
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private AuthorizationService authorizationService;
    @Mock private DiscountService discountService;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;
    @Mock private SystemUserProvider systemUserP
```

<br>

### File: `TicketValidationServiceImplTest.java`
**Description / What it does**: Contains core business logic and transaction management.
**Key Annotations**: `@ExtendWith(MockitoExtension.class), @MockitoSettings(strictness = Strictness.LENIENT), @DisplayName("TicketValidationServiceImpl"), @Mock, @Mock`
**Package**: `com.event.tickets.services`

**Internal Dependencies (What it uses):**
- `com.event.tickets.domain.entities.*`
- `com.event.tickets.exceptions.*`
- `com.event.tickets.repositories.*`
- `com.event.tickets.services.impl.TicketValidationServiceImpl`

**File Code Structure:**
- **State/Properties**: None (Stateless/Interface)
- **Behavior/Capabilities**: None explicitly visible/extracted.

**Relevant Code Snippet:**
```java

class TicketValidationServiceImplTest {

    @Mock private QrCodeRepository qrCodeRepository;
    @Mock private TicketValidationRepository ticketValidationRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private EventRepository eventRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private UserRepository userRepository;
    @Mock private SystemUserProvider systemUserProvider;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private TicketValidationServiceImpl service;

    private UUID validatorId;
    private 
```

<br>

## Infrastructure and Configuration Files
---

### `Docker Compose`
Path: `docker-compose.yml`
```yml
services:
  # Our PostgreSQL database
  db:
    # Using the latest PostgreSQL image
    image: postgres:16
    ports:
      - "5433:5432"
    restart: always
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres123
      POSTGRES_DB: Event_Booking_App_db
    volumes:
      - postgres-data:/var/lib/postgresql/data

  # Database management interface
  adminer:
    image: adminer:latest
    restart: always
    ports:
      - 8888:8080

  keycloak:
    # Pinned to a specific version - never use :latest for Keycloak.
    # The Admin REST API changes between major versions. Using :latest risks
    # pulling a breaking change that changes realm behavior or API contracts.
    # To upgrade: test in a branch, update this version, and re-run Keycloak setup.
    image: quay.io/keycloak/keycloak:26.1.4
    ports:
      - "9090:8080"
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    volumes:
      - keycloak-data:/opt/keycloak/data
    command:
      - start-dev
      - --db=dev-file

volumes:
  keycloak-data:
    driver: local
  postgres-data:
    driver: local

```

<br>

### `POM (Maven)`
Path: `pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.5</version>
        <relativePath/>
    </parent>
    <groupId>com.event</groupId>
    <artifactId>tickets</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>Event Booking App</name>
    <description>Event Booking App</description>
    <properties>
        <java.version>21</java.version>
        <org.mapstruct.version>1.6.3</org.mapstruct.version>
        <zxing.version>3.5.2</zxing.version>
        <lombok.version>1.18.36</lombok.version>
        <keycloak.version>26.0.0</keycloak.version>
        <mockito.version>5.14.2</mockito.version>
    </properties>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
        <!-- Flyway: manages all DB schema migrations. ddl-auto=validate only. -->
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>${lombok.version}</version><optional>true</optional></dependency>
        <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId><version>${org.mapstruct.version}</version></dependency>
        <dependency><groupId>com.google.zxing</groupId><artifactId>core</artifactId><version>${zxing.version}</version></dependency>
        <dependency><groupId>com.google.zxing</groupId><artifactId>javase</artifactId><version>${zxing.version}</version></dependency>
        <dependency><groupId>org.keycloak</groupId><artifactId>keycloak-admin-client</artifactId><version>${keycloak.version}</version></dependency>
        <dependency><groupId>com.bucket4j</groupId><artifactId>bucket4j-core</artifactId><version>8.7.0</version></dependency>
        <!-- FIX #13: Caffeine replaces unbounded ConcurrentHashMap in RateLimitingFilter -->
        <dependency><groupId>com.github.ben-manes.caffeine</groupId><artifactId>caffeine</artifactId></dependency>
        <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId><version>5.2.5</version></dependency>
        <dependency><groupId>com.itextpdf</groupId><artifactId>itext7-core</artifactId><version>8.0.2</version><type>pom</type></dependency>
        <dependency><groupId>com.itextpdf</groupId><artifactId>kernel</artifactId><version>8.0.2</version></dependency>
        <dependency><groupId>com.itextpdf</groupId><artifactId>layout</artifactId><version>8.0.2</version></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
        <!-- FIX #13-1: Resilience4j for Keycloak retry logic -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
            <version>2.1.0</version>
        </dependency>
        <!-- Swagger: auto-generates /swagger-ui.html from your @RestController annotations -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId><version>${mockito.version}</version><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><version>${lombok.version}</version></path>
                        <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId><version>${org.mapstruct.version}</version></path>
                        <path><groupId>org.projectlombok</groupId><artifactId>lombok-mapstruct-binding</artifactId><version>0.2.0</version></path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.12</version>
                <executions>
                    <execution><goals><goal>prepare-agent</goal></goals></execution>
                    <execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.3</version>
                <configuration>

... (truncated)
```

<br>

### `Application Properties`
Path: `src\main\resources\application.properties`
```properties
spring.application.name=tickets

# ===============================
# Database Configuration
# ===============================
spring.datasource.url=jdbc:postgresql://localhost:5433/Event_Booking_App_db
spring.datasource.username=postgres
# L-22 FIX: use environment variable - never hardcode credentials in source control
# For local Docker Compose, set DB_PASSWORD to match POSTGRES_PASSWORD (postgres123).
spring.datasource.password=${DB_PASSWORD:postgres123}

# ===============================
# HikariCP Connection Pool
# ===============================
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000

# ===============================
# JPA / Hibernate
# ===============================
# ddl-auto=validate: Hibernate checks schema matches entities but never modifies it.
# Schema changes are managed exclusively by Flyway migrations in db/migration/.
# Never use ddl-auto=update in any environment - it can silently drop columns,
# fail on NOT NULL additions, and cause data loss on schema drift.
spring.jpa.hibernate.ddl-auto=validate
# open-in-view=false: Prevents lazy loading during HTTP response serialization.
# With open-in-view=true (default), a DB connection is held open for the entire
# HTTP request lifecycle, causing N+1 queries and connection pool exhaustion.
spring.jpa.open-in-view=false
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
spring.jpa.properties.hibernate.dialect=com.event.tickets.config.PostgreSQLEnumDialect
spring.jpa.properties.hibernate.hbm2ddl.jdbc_metadata_extraction_strategy=individually

# ===============================
# Flyway - Database Migrations
# ===============================
# Flyway manages all schema changes. Never use ddl-auto=create/update.
# Migration files live in src/main/resources/db/migration/
# Naming: V{version}__{description}.sql  e.g. V1__initial_schema.sql
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0

# ===============================
# Server
# ===============================
server.port=8081

# ===============================
# Pagination Safety
# Prevents ?size=999999 from dumping entire database
# ===============================
spring.data.web.pageable.max-page-size=50
spring.data.web.pageable.default-page-size=20

# ===============================
# Keycloak Configuration
# ===============================
spring.security.oauth2.resourceserver.jwt.issuer-uri=${KEYCLOAK_ISSUER_URI:http://localhost:9090/realms/event-ticket-platform}
keycloak.admin.server-url=${KEYCLOAK_SERVER_URL:http://localhost:9090}
# realm=master: The Keycloak Admin REST API requires a token from the master realm.
# Authenticate here (master) -> manage users there (target-realm below).
keycloak.admin.realm=master
# admin-cli is the built-in master realm client for admin API access. No secret needed.
keycloak.admin.client-id=admin-cli
# Leave blank - admin-cli is a public client and does not use a client secret.
keycloak.admin.client-secret=
keycloak.admin.username=${KEYCLOAK_ADMIN_USERNAME:admin}
keycloak.admin.password=${KEYCLOAK_ADMIN_PASSWORD:admin}
# target-realm: The realm where your application users actually live.
keycloak.admin.target-realm=${KEYCLOAK_REALM:event-ticket-platform}

# ===============================
# Email - Brevo Transactional API
# ===============================
app.brevo.api-key=${BREVO_API_KEY:your_brevo_api_key_here}
app.mail.from-email=${MAIL_FROM_EMAIL:noreply@eventbooking.com}
app.mail.from-name=${MAIL_FROM_NAME:Event Booking Platform}
app.mail.enabled=${MAIL_ENABLED:true}

# ===============================
# Swagger / OpenAPI
# ===============================
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method

# ===============================
# Actuator
# ===============================
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
management.endpoint.health.probes.enabled=true
management.metrics.tags.application=${spring.application.name}

... (truncated)
```

<br>

### `Application Prod Properties`
Path: `src\main\resources\application-prod.properties`
```properties

# Database Configuration
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5433/Event_Booking_App_db}
spring.datasource.username=${DATABASE_USERNAME:postgres}
spring.datasource.password=${DATABASE_PASSWORD:postgres}

# JPA Configuration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false

# Keycloak Configuration
spring.security.oauth2.resourceserver.jwt.issuer-uri=${KEYCLOAK_ISSUER_URI:http://localhost:9090/realms/event-ticket-platform}

# Keycloak Admin API Configuration
keycloak.admin.server-url=${KEYCLOAK_SERVER_URL:http://localhost:9090}
keycloak.admin.realm=${KEYCLOAK_REALM:event-ticket-platform}
keycloak.admin.client-id=${KEYCLOAK_CLIENT_ID:event-ticket-platform-app}
keycloak.admin.client-secret=${KEYCLOAK_CLIENT_SECRET}
keycloak.admin.username=${KEYCLOAK_ADMIN_USERNAME}
keycloak.admin.password=${KEYCLOAK_ADMIN_PASSWORD}

# Server Configuration
server.port=${PORT:8081}
server.error.include-message=never
server.error.include-stacktrace=never
server.error.include-binding-errors=never

# Logging
logging.level.root=WARN
logging.level.com.event.tickets=INFO
logging.file.name=logs/event-booking-app-prod.log
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n

# CORS - Production
cors.allowed-origins=${CORS_ALLOWED_ORIGINS:https://yourdomain.com}
cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
cors.allowed-headers=*
cors.allow-credentials=true
cors.max-age=3600

# Security Headers
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.same-site=strict

# Rate Limiting
rate-limiting.enabled=true
rate-limiting.requests-per-minute=1000
rate-limiting.auth-requests-per-minute=10

```

<br>

### `Migration: V1__initial_schema.sql`
Path: `src\main\resources\db\migration\V1__initial_schema.sql`
```sql
-- V1__initial_schema.sql
-- Initial schema baseline.
-- baseline-on-migrate=true means Flyway marks this as already applied on
-- an existing database without running it again.
-- On a fresh database, Flyway runs this to create everything from scratch.

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    approval_status VARCHAR(255) DEFAULT 'APPROVED',
    approved_at TIMESTAMP,
    rejected_at TIMESTAMP,
    approved_by UUID REFERENCES users(id),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    venue VARCHAR(500) NOT NULL,
    event_start TIMESTAMP,
    event_end TIMESTAMP,
    sales_start TIMESTAMP,
    sales_end TIMESTAMP,
    status VARCHAR(255) NOT NULL,
    max_capacity INTEGER,
    organizer_id UUID REFERENCES users(id),
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    price NUMERIC(19,2) NOT NULL,
    total_available INTEGER,
    event_id UUID REFERENCES events(id),
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(255) NOT NULL,
    original_price NUMERIC(19,2),
    price_paid NUMERIC(19,2),
    discount_applied NUMERIC(19,2),
    purchaser_id UUID REFERENCES users(id),
    ticket_type_id UUID REFERENCES ticket_types(id),
    event_id UUID REFERENCES events(id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS discounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    discount_type VARCHAR(255) NOT NULL,
    value NUMERIC(19,2) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    description VARCHAR(1000),
    ticket_type_id UUID REFERENCES ticket_types(id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS qr_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tickets(id),
    qr_code_data TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket_validations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tickets(id),
    validated_by UUID REFERENCES users(id),
    method VARCHAR(255),
    status VARCHAR(255),
    validated_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invite_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(255) NOT NULL UNIQUE,
    role_name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP,
    created_by UUID REFERENCES users(id),

... (truncated)
```

<br>

### `Migration: V2__admin_bootstrap.sql`
Path: `src\main\resources\db\migration\V2__admin_bootstrap.sql`
```sql
-- V2__admin_bootstrap.sql
-- Bootstrap SYSTEM user (audit logging) and admin DB record.

-- SYSTEM user - all-zeros UUID, used as audit actor when no real user context exists.
INSERT INTO users (id, email, name, approval_status, created_at, updated_at)
SELECT
    '00000000-0000-0000-0000-000000000000',
    'system@system.local',
    'SYSTEM',
    'APPROVED',
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE id = '00000000-0000-0000-0000-000000000000'
);

-- ─────────────────────────────────────────────────────────────────────────────
-- ADMIN USER BOOTSTRAP
--
-- FIX: Removed hardcoded Keycloak UUID.
--
-- PROBLEM WITH THE OLD APPROACH:
-- The previous version had a hardcoded UUID ('7612a810-c65c-4047-96e3-3e1c1b425e87').
-- That UUID was the Keycloak-generated ID for admin@test.com in ONE developer's local env.
-- On any other machine (colleague, CI, staging, production), Keycloak generates a
-- completely different UUID for the same user. The result:
--   - Admin record inserted with wrong UUID
--   - Admin JWT carries the real Keycloak UUID
--   - ApprovalGateFilter does findById(jwt.subject) → not found → blocks admin
--   - The admin CANNOT LOG IN on any fresh deployment
--
-- CORRECT APPROACH:
-- The admin user record must be inserted AFTER the Keycloak user is created, using
-- the UUID that Keycloak actually assigned. This migration cannot know that UUID
-- at authoring time. Use the setup script below instead.
--
-- SETUP STEPS FOR A NEW ENVIRONMENT:
-- 1. Create admin@<yourdomain.com> in Keycloak Admin UI manually
--    (Realm → Users → Add user → set email, enable account, set password)
-- 2. Copy the User ID from Keycloak (Users → admin → ID field, looks like a UUID)
-- 3. Run this one-time SQL against your database, replacing <KEYCLOAK_UUID_HERE>:
--
--    INSERT INTO users (id, email, name, approval_status, created_at, updated_at)
--    VALUES (
--        '<KEYCLOAK_UUID_HERE>',
--        'admin@<yourdomain.com>',
--        'Admin User',
--        'APPROVED',
--        now(),
--        now()
--    )
--    ON CONFLICT (id) DO NOTHING;
--
-- 4. Assign the ADMIN role to the user in Keycloak
--    (Users → admin → Role Mappings → Assign ADMIN realm role)
--
-- ALTERNATIVE — environment-variable-driven bootstrap:
-- If your deployment pipeline supports it, inject the admin UUID as an env var
-- and run the INSERT via a startup script (e.g. Flyway placeholder substitution):
--
--    keycloak.admin-user-id=${ADMIN_KEYCLOAK_UUID}
--
-- This migration intentionally does NOT insert an admin record. DatabaseInitializer
-- and the manual setup above handle it. The Flyway migration only bootstraps the
-- SYSTEM user, which has a well-known fixed UUID.
-- ─────────────────────────────────────────────────────────────────────────────
```

<br>

### `Migration: V3__add_keycloak_sync_pending.sql`
Path: `src\main\resources\db\migration\V3__add_keycloak_sync_pending.sql`
```sql
-- V3__add_keycloak_sync_pending.sql
-- Adds keycloak_sync_pending flag to users table.
-- When true, a @Scheduled job retries the Keycloak activation/disable call
-- that failed during approval/rejection (e.g. Keycloak was temporarily down).
-- This prevents the DB and Keycloak falling out of sync silently.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS keycloak_sync_pending BOOLEAN NOT NULL DEFAULT FALSE;


```

<br>

### `Migration: V5__fix_qr_code_value_column.sql`
Path: `src\main\resources\db\migration\V5__fix_qr_code_value_column.sql`
```sql
-- V5__fix_qr_code_value_column.sql
--
-- FIX-QR1 (BUG 5-3): Change qr_codes.qr_value from VARCHAR(1000) to TEXT.
--
-- ROOT CAUSE:
-- QrCodeServiceImpl stores a base64-encoded PNG (~11,000-20,000 chars) in qr_value.
-- The VARCHAR(1000) column silently truncated every stored image.
-- When the truncated base64 was decoded for download, Base64.getDecoder().decode()
-- threw IllegalArgumentException -- caught and re-thrown as QrCodeNotFoundException.
-- Every QR code download silently failed.
--
-- FIX STEPS:
-- 1. Rename qr_code_data to qr_value if necessary.
-- 2. Alter the column type to TEXT (unbounded in PostgreSQL).
-- 3. Mark all existing QrCode rows as EXPIRED if the status column exists.
--
-- ROLLBACK:
-- Reverting to VARCHAR(1000) would re-introduce the truncation bug.
-- There is no safe rollback -- keep TEXT.

DO $$
BEGIN
    -- Rename column if it exists as qr_code_data
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='qr_codes' AND column_name='qr_code_data') THEN
        ALTER TABLE qr_codes RENAME COLUMN qr_code_data TO qr_value;
    END IF;

    -- Alter column type to TEXT
    ALTER TABLE qr_codes ALTER COLUMN qr_value TYPE TEXT;

    -- Mark existing rows as EXPIRED if status column exists
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='qr_codes' AND column_name='status') THEN
        UPDATE qr_codes SET status = 'EXPIRED' WHERE status = 'ACTIVE';
    END IF;
END $$;

COMMENT ON COLUMN qr_codes.qr_value IS
    'Base64-encoded PNG of the QR code image. '
    'Stores ~11,000-20,000 characters for a 300x300 PNG. '
    'Changed from VARCHAR(1000) to TEXT in V5 migration.';
```

<br>

### `Migration: V6__fix_schema_entity_mismatches.sql`
Path: `src\main\resources\db\migration\V6__fix_schema_entity_mismatches.sql`
```sql
-- V6__fix_schema_entity_mismatches.sql
-- Fixes three columns where the JPA entity @Column(name=...) differs from
-- the actual DB column name. With spring.jpa.hibernate.ddl-auto=validate,
-- Hibernate checks the schema on startup and throws SchemaManagementException
-- if any mapped column is missing -- preventing the application from starting.
--
-- All changes are idempotent (IF NOT EXISTS / IF EXISTS guards).
-- Safe to run against both fresh and existing databases.

-- ============================================================
-- FIX 1: discounts.value -> discounts.discount_value
-- ============================================================
-- Discount entity maps: @Column(name = "discount_value")
-- V1 schema created:    value NUMERIC(19,2)
-- Hibernate validate:   FAIL -- column "discount_value" not found
--
-- Rename the existing column to match the entity mapping.
-- Uses DO block to guard against double-execution.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'discounts' AND column_name = 'value'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'discounts' AND column_name = 'discount_value'
    ) THEN
        ALTER TABLE discounts RENAME COLUMN value TO discount_value;
    END IF;
END$$;

-- ============================================================
-- FIX 2: audit_logs missing resource_id column
-- ============================================================
-- AuditLog entity maps: @Column(name = "resource_id") UUID resourceId
-- V1 schema:            resource_type VARCHAR(255) -- but NO resource_id column
-- Hibernate validate:   FAIL -- column "resource_id" not found
--
-- Add the missing column. NULL allowed: not all audit events have a resource.
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS resource_id UUID;

-- ============================================================
-- FIX 3: discounts missing created_by column
-- ============================================================
-- Discount entity maps: @Column(name = "created_by") UUID createdBy
-- V1 schema:            no created_by column on discounts table
-- Hibernate validate:   FAIL -- column "created_by" not found
--
-- Add the missing column. NULL allowed: existing discount rows have no creator recorded.
ALTER TABLE discounts
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);

-- ============================================================
-- Verification queries (informational -- do not remove)
-- ============================================================
-- After running, confirm:
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'discounts' ORDER BY ordinal_position;
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'audit_logs'  ORDER BY ordinal_position;


```

<br>

### `Migration: V7__fix_remaining_schema_mismatches.sql`
Path: `src\main\resources\db\migration\V7__fix_remaining_schema_mismatches.sql`
```sql
-- V7__fix_remaining_schema_mismatches.sql
-- Fixes 7 column mismatches between JPA entity @Column annotations and the
-- actual DB columns created by V1__initial_schema.sql.
-- With spring.jpa.hibernate.ddl-auto=validate, every mismatch causes a
-- SchemaManagementException on startup — application exits immediately.
--
-- All changes are idempotent: DO $$ ... END$$ blocks check before acting.
-- Safe to run on both fresh databases and existing ones.

-- ============================================================
-- FIX S-2: ticket_validations.validated_by  →  validated_by_id
-- ============================================================
-- TicketValidation entity: @JoinColumn(name = "validated_by_id")
-- V1 schema created:       validated_by UUID REFERENCES users(id)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket_validations' AND column_name = 'validated_by'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket_validations' AND column_name = 'validated_by_id'
    ) THEN
        ALTER TABLE ticket_validations RENAME COLUMN validated_by TO validated_by_id;
    END IF;
END$$;

-- ============================================================
-- FIX S-3: ticket_validations.method  →  validation_method
-- ============================================================
-- TicketValidation entity: @Column(name = "validation_method")
-- V1 schema created:       method VARCHAR(255)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket_validations' AND column_name = 'method'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket_validations' AND column_name = 'validation_method'
    ) THEN
        ALTER TABLE ticket_validations RENAME COLUMN method TO validation_method;
    END IF;
END$$;

-- ============================================================
-- FIX S-4: invite_codes.revoke_reason  →  revoked_reason
-- ============================================================
-- InviteCode entity: @Column(name = "revoked_reason")
-- V1 schema created: revoke_reason VARCHAR(500)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'invite_codes' AND column_name = 'revoke_reason'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'invite_codes' AND column_name = 'revoked_reason'
    ) THEN
        ALTER TABLE invite_codes RENAME COLUMN revoke_reason TO revoked_reason;
    END IF;
END$$;

-- ============================================================
-- FIX S-5: invite_codes missing version column
-- ============================================================
-- InviteCode entity: @Version @Column(name = "version") Long version
-- V1 schema:         no version column on invite_codes
ALTER TABLE invite_codes
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- ============================================================
-- FIX S-6: invite_codes missing revoked_at column
-- ============================================================
-- InviteCode entity: @Column(name = "revoked_at") LocalDateTime revokedAt
-- V1 schema:         no revoked_at column
ALTER TABLE invite_codes
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP;

-- ============================================================
-- FIX S-7: qr_codes missing status column
-- ============================================================
-- QrCode entity: @Enumerated(STRING) @Column(name = "status") QrCodeStatusEnum status
-- V1 schema:     no status column on qr_codes (only had 'active BOOLEAN')
ALTER TABLE qr_codes
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

-- ============================================================
-- Verification queries (informational — do not remove)
-- ============================================================
-- After running, confirm columns exist:
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'ticket_validations' ORDER BY ordinal_position;
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'invite_codes'        ORDER BY ordinal_position;
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'qr_codes'            ORDER BY ordinal_position;

```

<br>

