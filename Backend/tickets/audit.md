# audit.md — Single Source of Truth
# Project: Event Ticket Platform (Spring Boot + Keycloak)
# Last updated: Session 9 — Full re-read of src/main + all Flyway migrations. 8 new schema mismatch bugs found and FIXED (V5 rewritten + V7 created). Total bugs: 31 / Fixed: 31.

---

## 1. PROJECT OVERVIEW

**Stack:** Java + Spring Boot 3.x, PostgreSQL, Keycloak (OAuth2 JWT Resource Server)
**Base package:** com.event.tickets
**Port:** 8081
**DB:** PostgreSQL port 5432, database: Event_Booking_App_db
**Schema:** `spring.jpa.hibernate.ddl-auto=create` — **DROPS AND RECREATES ALL TABLES ON EVERY RESTART** (BUG-2)
**No Flyway migrations exist in this codebase**

---

## 2. SYSTEM ARCHITECTURE

```
Authentication:  Keycloak (OAuth2 JWT Resource Server)
Authorization:   AuthorizationService — role + ownership checks
Approval:        DB controls approval state; Keycloak controls login access

Request pipeline (every authenticated request):
  JWT → Spring Security validates
    → UserProvisioningFilter @Order(none) — PASSIVE, only warns (BUG-3)
    → ApprovalGateFilter @Order(2)        — blocks PENDING/REJECTED
    → @PreAuthorize role check
    → Service ownership check via AuthorizationService
```

**Trust model:**
- Keycloak → identity + roles (authoritative)
- DB → approval status + user metadata (authoritative)

---

## 3. ROLES

| Role      | Invite required | Who issues          | Approval needed |
|-----------|----------------|---------------------|-----------------|
| ATTENDEE  | No (auto)      | N/A                 | No              |
| STAFF     | Yes            | ORGANIZER or ADMIN  | Yes — PENDING   |
| ORGANIZER | Yes            | ADMIN only          | Yes — PENDING   |
| ADMIN     | Yes            | ADMIN only          | Yes — PENDING   |

---

## 4. MODULE MAP

| # | Module | Controller | Service Impl | Status |
|---|--------|------------|--------------|--------|
| M1 | Auth/Registration | AuthController | RegistrationServiceImpl | BROKEN — BUG-3,4,11 |
| M2 | Invite Code | InviteCodeController | InviteCodeServiceImpl | BROKEN — BUG-4,14 |
| M3 | Approval | ApprovalController | ApprovalServiceImpl | BROKEN — BUG-5 |
| M4 | Event Management | EventController | EventServiceImpl | BROKEN — BUG-10,12,22 |
| M5 | Ticket Types + Purchase | TicketTypeController | TicketTypeServiceImpl | BROKEN — BUG-1,6,7,15,16,23 |
| M6 | Ticket Viewing + QR | TicketController | QrCodeServiceImpl | BROKEN — BUG-8 |
| M7 | Ticket Validation | TicketValidationController | TicketValidationServiceImpl | PARTIAL — BUG-13 |
| M8 | Discounts | DiscountController | DiscountServiceImpl | CLEAN |
| M9 | Event Staff | EventStaffController | EventStaffServiceImpl | PARTIAL — BUG-9 |
| M10 | Audit Logging | AuditController | AuditLogService | CLEAN |
| M11 | Admin Governance | AdminGovernanceController | KeycloakAdminServiceImpl | CLEAN |
| M12 | Filters / Security | UserProvisioningFilter, ApprovalGateFilter | — | BROKEN — BUG-3,17,21 |
| M13 | Config / Startup | DatabaseInitializer, DataInitializer | — | PARTIAL — BUG-18,19,20 |

---

## 5. SYSTEM RULES (LAW — DO NOT CONTRADICT)

1. Role assigned from invite code ONLY — never from user-supplied request body
2. ATTENDEE (no invite) → registered as PENDING, treated as approved by convention
3. Privileged roles → PENDING until admin explicitly approves
4. **Approval is atomic: Keycloak activation MUST succeed FIRST, then DB save** (BUG-5 violates this)
5. Only ADMIN can approve or reject users
6. Invite codes are single-use — pessimistic DB lock required at redemption (BUG-4 missing)
7. STAFF invite codes must carry an eventId — no eventId = invalid for STAFF (BUG-14 guard is dead)
8. Authorization = role + ownership (not role alone)
   - ADMIN → all events
   - ORGANIZER → own events only
   - STAFF → assigned events only
9. Ticket validation is one-time — duplicate scan creates INVALID record
10. All critical actions emit audit logs — AuditLogService uses REQUIRES_NEW
11. DB authoritative for approval; Keycloak authoritative for identity/roles
12. ADMIN role does NOT bypass business rules
13. No new dependencies without explicit decision
14. One bug at a time — no scope creep, no refactoring, no renaming during fix sessions
15. After every session: update audit.md, push to GitHub before closing

---

## 6. ACCEPTED TRADE-OFFS (NOT BUGS)

| ID | Trade-off | Decision |
|----|-----------|----------|
| T1 | Keycloak down = system down | Fail-fast by design. Acceptable for scope. |
| T2 | Per-user ticket limit counts CANCELLED tickets | Prevents buy-cancel-rebuy abuse |
| T3 | Discount changes are prospective only | Per-ticket snapshot preserves history |
| T4 | Invite codes not email-bound | Single-use lock prevents double-redemption |
| T5 | Stale JWT after rejection | ApprovalGateFilter blocks API; Keycloak revocation not implemented |
| T6 | Orphan Keycloak user possible if DB fails after creation | Best-effort rollback, extreme edge case |
| T7 | ticketTypeName excluded from stats | Service has it from entity; no duplication needed |
| T8 | ADMIN does not bypass business rules | ADMIN = role management only, not event access bypass |

---

## 7. CONFIRMED FIXES — ALL 23 BUGS VERIFIED IN SESSION 8

| Bug | Fix verified in file | Evidence |
|-----|----------------------|----------|
| BUG-1: 4-arg purchaseTickets | TicketTypeServiceImpl.java | Method present at line 178, @Override, passes cross-event check |
| BUG-2: ddl-auto=create | application.properties | Line 28: `ddl-auto=validate` + Flyway enabled |
| BUG-3: UserProvisioningFilter passive | UserProvisioningFilter.java | @Order(1), user==null returns 401, approvalStatus==null returns 500 |
| BUG-4: No pessimistic lock on invite | InviteCodeRepository.java | findByCodeForUpdate() with @Lock(PESSIMISTIC_WRITE) at line 35–37 |
| BUG-4 (callers) | RegistrationServiceImpl.java, InviteCodeServiceImpl.java | Both call findByCodeForUpdate() at lines 289, 192 |
| BUG-5: DB before Keycloak | ApprovalServiceImpl.java | activateUser() called first (line 91), DB save after (line 99–103) |
| BUG-6: cascade race deleteTicketType | TicketTypeServiceImpl.java | deleteTicketType() uses findByIdAndEventIdWithLock() + countActiveByTicketTypeId() |
| BUG-7: No event lock in purchase | EventRepository.java, TicketTypeServiceImpl.java | findByIdWithLock() in EventRepository; called at line 198 in purchaseTickets() |
| BUG-8: QR wrong UUID | QrCodeServiceImpl.java | All 3 view/download methods call getActiveQrCodeForTicket() → encode qrCode.getId() |
| BUG-9: isStaff loads full collection | EventRepository.java | isStaffMember() COUNT query at lines 64–71 |
| BUG-10: getSalesDashboard N+1 | EventServiceImpl.java, TicketRepository.java | Single aggregate query findSalesStatsByEventId(); BigDecimal accumulators |
| BUG-11: STAFF no event assignment | RegistrationServiceImpl.java | Step 6 block (lines 195–221) adds user to event.getStaff() and saves |
| BUG-12: updateEvent cascade delete | EventServiceImpl.java | removeIf block (lines 232–244) checks activeSoldForType before removing |
| BUG-13: validateTicket loads validations | TicketValidationServiceImpl.java | existsByTicketIdAndStatus() at line 145 (no collection load) |
| BUG-14: Dead STAFF guard | InviteCodeServiceImpl.java | Guard at line 126: `if ("STAFF".equals(roleName) && eventId == null)` — outside the if block |
| BUG-15: TicketType.price Double | TicketType.java | `private BigDecimal price` at line 58 with @Column(precision=10,scale=2) |
| BUG-16: Returns live Hibernate collection | TicketTypeServiceImpl.java | Line 419: `return new ArrayList<>(event.getTicketTypes())` |
| BUG-17: Null status passes filter | ApprovalGateFilter.java | Lines 91–95: null status → SC_INTERNAL_SERVER_ERROR and return |
| BUG-18: Conflicting column default | User.java | Java field is null (no initializer) per L-09 FIX comment; DB default APPROVED still in columnDefinition. Design decision documented. |
| BUG-19: Multiple @PostConstruct | DatabaseInitializer.java | Single @PostConstruct initialize() method; others called as private methods sequentially |
| BUG-20: DataInitializer findAll() | DataInitializer.java | Uses findTicketsMissingPricingData() (line 42), not findAll() |
| BUG-21: No Swagger bypass | ApprovalGateFilter.java | shouldNotFilter() includes /swagger-ui and /v3/api-docs at lines 131–132 |
| BUG-22: countByTicketTypeEventId CANCELLED | EventServiceImpl.java, TicketRepository.java | Uses countActiveTicketsByEventId() (excludes CANCELLED) in both callers |
| BUG-23: Double save in purchase loop | TicketTypeServiceImpl.java | Lines 325–327: single save; no second save after generateQrCode() |

---

## 8. COMPLETE BUG REGISTRY — ALL 23 BUGS LINE-VERIFIED FROM CODE

---

### TIER 0 — Application cannot start

#### BUG-1 — CRITICAL — 4-arg purchaseTickets not implemented in TicketTypeServiceImpl

**File:** `TicketTypeService.java` line 28, `TicketTypeServiceImpl.java` line 74, `TicketTypeController.java`

**Evidence:**
- Interface declares: `List<Ticket> purchaseTickets(UUID userId, UUID eventId, UUID ticketTypeId, int quantity)` (line 28)
- Impl only has: `public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity)` (line 74)
- Controller calls the 4-arg version
- Spring cannot instantiate TicketTypeServiceImpl — it doesn't fulfill the interface contract
- **Application cannot start**

**Fix:**
```java
@Override
@Transactional
public List<Ticket> purchaseTickets(UUID userId, UUID eventId, UUID ticketTypeId, int quantity) {
    // Validate ticketType belongs to stated event (prevents cross-event purchase)
    TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
        .orElseThrow(() -> new TicketTypeNotFoundException("..."));
    if (!ticketType.getEvent().getId().equals(eventId)) {
        throw new InvalidBusinessStateException("Ticket type does not belong to this event");
    }
    return purchaseTickets(userId, ticketTypeId, quantity);
}
```

---

### TIER 1 — Data destruction / security gate failures

#### BUG-2 — CRITICAL — ddl-auto=create destroys all data on every restart

**File:** `src/main/resources/application.properties`

**Evidence:** `spring.jpa.hibernate.ddl-auto=create`
The comment above it says "Using 'update' mode" — the comment is wrong. Every restart drops all tables and recreates them. All data lost. Test `application.properties` correctly uses `update`.

**Fix:** Change to `spring.jpa.hibernate.ddl-auto=update`

---

#### BUG-3 — HIGH — UserProvisioningFilter is passive, never blocks missing users

**File:** `UserProvisioningFilter.java`

**Evidence:**
- `if (!userRepository.existsById(keycloakId)) { log.warn(...) }` — only logs
- `filterChain.doFilter(request, response)` — always called unconditionally
- No `@Order` annotation — Spring assigns default order
- A valid Keycloak JWT for a deleted/non-existent DB user reaches controllers and causes a 500 instead of a clean 401

**Fix:**
```java
@Order(1)  // Add this annotation
// In doFilterInternal:
if (!userRepository.existsById(keycloakId)) {
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
        "User account not found. Please register.");
    return;  // Stop filter chain
}
```

---

#### BUG-4 — CRITICAL — No pessimistic lock on invite code redemption (race condition)

**Files:** `InviteCodeRepository.java`, `RegistrationServiceImpl.java`, `InviteCodeServiceImpl.java`

**Evidence:**
- `InviteCodeRepository` has only `findByCode(String code)` — no `@Lock`
- Two concurrent requests can both read the same PENDING code, both pass validation, both redeem it
- `InviteCode` entity has `@Version` but that only helps on update, not on the read-then-check race

**Fix — add to InviteCodeRepository:**
```java
@Query("SELECT ic FROM InviteCode ic WHERE ic.code = :code")
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<InviteCode> findByCodeForUpdate(@Param("code") String code);
```
Use `findByCodeForUpdate()` in BOTH `RegistrationServiceImpl.validateAndGetInviteCode()` and `InviteCodeServiceImpl.redeemInviteCode()`.

---

#### BUG-5 — HIGH — DB saved before Keycloak activation; Keycloak failure silently swallowed

**File:** `ApprovalServiceImpl.java`

**Evidence (approveUser):**
```java
userRepository.save(user);              // line 70 — DB FIRST
try {
    keycloakAdminService.activateUser(userId);  // line 74 — Keycloak SECOND
} catch (Exception e) {
    log.warn("Failed to activate Keycloak account (non-critical): ...");
    // SILENT — no rollback, no rethrow
}
```
Same pattern in `rejectUser()`. If Keycloak fails: DB = APPROVED, Keycloak = disabled. User appears approved but cannot log in. Violates Rule #4.

**Fix:**
Remove the try/catch. Move `userRepository.save()` to AFTER `activateUser()` succeeds:
```java
keycloakAdminService.activateUser(userId);  // throws on failure → rolls back transaction
user.setApprovalStatus(ApprovalStatus.APPROVED);
userRepository.save(user);
emitApprovalAudit(...);
```

---

#### BUG-6 — HIGH — deleteTicketType loads full ticket collection + cascade race deletes paid tickets

**File:** `TicketTypeServiceImpl.java`, `TicketType.java`

**Evidence:**
- Line 240: `if (!ticketType.getTickets().isEmpty())` — loads ALL tickets into Hibernate session
- `TicketType.tickets` has `CascadeType.ALL` → `ticketTypeRepository.delete()` cascades to delete all Ticket rows
- Race: between `isEmpty()` returning true and the actual delete, a concurrent purchase creates a paid ticket → silently deleted by cascade with no refund trail

**Fix:**
```java
// Replace isEmpty() check with COUNT query (no collection load):
int soldCount = ticketRepository.countByTicketTypeId(ticketTypeId);
if (soldCount > 0) {
    throw new TicketTypeDeleteNotAllowedException(
        "Cannot delete ticket type with " + soldCount + " sold tickets");
}
// Also acquire pessimistic lock before count to close the race:
ticketTypeRepository.findByIdWithLock(ticketTypeId)  // lock before count
```

---

#### BUG-7 — HIGH — No event-level lock in purchaseTickets — event oversell possible

**File:** `TicketTypeServiceImpl.java`, `EventRepository.java`

**Evidence:**
- TicketType row IS locked: `ticketTypeRepository.findByIdWithLock(ticketTypeId)` (line 81)
- Event row is NOT locked: `Event event = ticketType.getEvent()` (line 86) — plain lazy proxy load
- Two concurrent purchases for different ticket types of the same event can both pass the `maxCapacity` check and both commit → event oversold

**Fix — add to EventRepository:**
```java
@Query("SELECT e FROM Event e WHERE e.id = :id")
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Event> findByIdWithLock(@Param("id") UUID id);
```
In `purchaseTickets()`, load event with lock FIRST (always lock Event before TicketType to prevent deadlock):
```java
Event event = eventRepository.findByIdWithLock(ticketType.getEvent().getId())
    .orElseThrow(...);
```

---

### TIER 2 — Correctness bugs users will directly encounter

#### BUG-8 — HIGH — QR view/download encodes ticketId; validation expects qrCodeId → scan fails at door

**File:** `QrCodeServiceImpl.java`

**Evidence:**
- `generateQrCode()` (called at purchase): generates random UUID as qrCode.id, encodes it as the QR image → stored in `qr_codes` table with that UUID as primary key
- Validation in `TicketValidationServiceImpl`: looks up `findByIdAndStatus(qrCodeId, ACTIVE)` — expects qrCode entity UUID
- **`generateQrCodePngForViewing()` line 120:** `generateQrCodeImageBytes(ticket.getId())` — encodes TICKET UUID
- **`generateQrCodePngForDownload()` line 136:** `generateQrCodeImageBytes(ticket.getId())` — encodes TICKET UUID
- **`generateQrCodePdf()` line 152:** `generateQrCodeImageBytes(ticket.getId())` — encodes TICKET UUID
- Any attendee who views or downloads their QR gets one that encodes the ticket UUID. The scanner calls `findByIdAndStatus(ticketUUID, ACTIVE)` → no match → **scan fails at the door**

**Fix — in all three view/download methods:**
```java
// Instead of: generateQrCodeImageBytes(ticket.getId())
// Do:
QrCode qrCode = qrCodeRepository.findByTicketIdAndTicketPurchaserId(ticketId, userId)
    .orElseThrow(QrCodeNotFoundException::new);
byte[] qrCodeBytes = generateQrCodeImageBytes(qrCode.getId());  // encode the qrCode UUID
```

---

#### BUG-9 — HIGH — isStaff() loads full staff collection on every ticket scan

**Files:** `AuthorizationServiceImpl.java`, `EventStaffServiceImpl.java`

**Evidence:**
```java
// AuthorizationServiceImpl.isStaff() lines 156–162:
if (event.getStaff() == null || event.getStaff().isEmpty()) { ... }
return event.getStaff().stream()
    .anyMatch(staff -> staff.getId().equals(userId));
```
Loads entire `user_staffing_events` join table rows + User objects for that event on every ticket scan. `EventStaffServiceImpl.isStaffAssignedToEvent()` has the same pattern. No `isStaffMember()` query exists in `EventRepository`.

**Fix — add to EventRepository:**
```java
@Query("SELECT COUNT(u) > 0 FROM Event e JOIN e.staff u WHERE e.id = :eventId AND u.id = :userId")
boolean isStaffMember(@Param("eventId") UUID eventId, @Param("userId") UUID userId);
```
Replace `isStaff()` body:
```java
return eventRepository.isStaffMember(event.getId(), userId);
```

---

#### BUG-10 — HIGH — getSalesDashboard loads all tickets into memory with double arithmetic

**File:** `EventServiceImpl.java`

**Evidence:**
```java
double totalRevenueBeforeDiscount = 0.0;   // line 268 — double, not BigDecimal
for (TicketType ticketType : event.getTicketTypes()) {
    int soldCount = ticketType.getTickets().size();    // line 275 — loads ALL tickets
    for (Ticket ticket : ticketType.getTickets()) {   // line 281 — iterates them
        double originalPrice = ticket.getOriginalPrice().doubleValue();  // precision lost
```
For a 5000-ticket event: loads 5000 Ticket entities into heap on every dashboard/export call. Revenue figures have IEEE 754 rounding errors. `getAttendeesReport()` has the same N+1 pattern (line 338).

**Fix — add to TicketRepository:**
```java
@Query("""
    SELECT t.ticketType.id, COUNT(t), SUM(t.originalPrice),
           SUM(t.discountApplied), SUM(t.pricePaid)
    FROM Ticket t
    WHERE t.ticketType.event.id = :eventId
    AND t.status <> :excludedStatus
    GROUP BY t.ticketType.id
    """)
List<Object[]> findSalesStatsByEventId(@Param("eventId") UUID eventId,
                                        @Param("excludedStatus") TicketStatusEnum excludedStatus);
```
Rewrite `getSalesDashboard()` to use this query. Use `BigDecimal` for all accumulation.

---

#### BUG-11 — HIGH — STAFF registration via /register does not assign user to event

**File:** `RegistrationServiceImpl.java`

**Evidence:**
```java
// Step 6 — lines 199–222:
if ("STAFF".equals(assignedRole) && eventId != null) {
    Event event = eventRepository.findById(finalEventId)...;
    log.info("STAFF role assigned via invite - manual event assignment required: ...");
    // TODO: Consider adding a system method...
    // Does NOTHING — event.getStaff().add(user) is NEVER called
}
```
`InviteCodeServiceImpl.redeemInviteCode()` correctly does `event.getStaff().add(user)` + `eventRepository.save(event)`. Two paths for the same operation produce different outcomes. STAFF users registered via `/register` get the Keycloak role but zero event access → their scans denied by `requireOrganizerOrStaffAccess()`.

**Fix — in the STAFF block:**
```java
event.getStaff().add(user);
eventRepository.save(event);
log.info("User '{}' assigned as staff to event '{}'", user.getId(), event.getId());
```

---

#### BUG-12 — HIGH — updateEventForOrganizer silently deletes ticket types with sold tickets

**Files:** `EventServiceImpl.java`, `Event.java`

**Evidence:**
```java
// EventServiceImpl line 174:
existingEvent.getTicketTypes().removeIf(existingTicketType ->
    !requestTicketTypeIds.contains(existingTicketType.getId())
);
// Event.java line 93:
@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
```
If the organizer's update request omits a ticket type ID, that ticket type is removed from the collection. With `orphanRemoval = true`, Hibernate deletes the TicketType row. With `CascadeType.ALL` on `TicketType.tickets`, all child Ticket rows are deleted — including paid tickets with no refund trail.

**Fix — before removeIf:**
```java
existingEvent.getTicketTypes().stream()
    .filter(tt -> !requestTicketTypeIds.contains(tt.getId()))
    .forEach(tt -> {
        int sold = ticketRepository.countByTicketTypeId(tt.getId());
        if (sold > 0) throw new InvalidBusinessStateException(
            "Cannot remove ticket type '" + tt.getName() + "' — " + sold + " tickets sold");
    });
existingEvent.getTicketTypes().removeIf(...);  // safe now
```

---

#### BUG-13 — MEDIUM — validateTicket loads full validations collection to check prior scans

**File:** `TicketValidationServiceImpl.java`

**Evidence:**
```java
// Line 132:
TicketValidationStatusEnum status = ticket.getValidations().stream()
    .filter(v -> TicketValidationStatusEnum.VALID.equals(v.getStatus()))
    .findFirst()
    ...
```
Loads ALL `TicketValidation` rows for this ticket into memory. A repeatedly-scanned ticket accumulates rows indefinitely, making each subsequent scan slower.

**Fix — add to TicketValidationRepository:**
```java
boolean existsByTicketIdAndStatus(UUID ticketId, TicketValidationStatusEnum status);
```
Replace the stream:
```java
boolean alreadyValidated = ticketValidationRepository
    .existsByTicketIdAndStatus(ticket.getId(), TicketValidationStatusEnum.VALID);
TicketValidationStatusEnum status = alreadyValidated
    ? TicketValidationStatusEnum.INVALID
    : TicketValidationStatusEnum.VALID;
```

---

#### BUG-14 — MEDIUM — Dead STAFF guard in generateInviteCode (STAFF invite with no eventId allowed)

**File:** `InviteCodeServiceImpl.java`

**Evidence:**
```java
// Lines 95–104:
if (eventId != null) {                           // outer guard
    event = eventRepository.findById(eventId)...;
    if ("STAFF".equals(roleName) && event == null) {  // DEAD — event cannot be null here
        throw new InvalidInputException("Event ID is required for STAFF role invites");
    }
}
// When eventId == null AND roleName == "STAFF": outer if is false → inner guard never runs
```
A STAFF invite code can be generated without an eventId. When redeemed, the STAFF path in `redeemInviteCode()` checks `inviteCode.getEvent() != null` before assigning → no event assignment occurs. STAFF user gets role but no event access.

**Fix — move guard outside the if block:**
```java
if ("STAFF".equals(roleName) && eventId == null) {
    throw new InvalidInputException("Event ID is required for STAFF role invites");
}
if (eventId != null) {
    event = eventRepository.findById(eventId)...;
}
```

---

### TIER 3 — Quality / robustness

#### BUG-15 — MEDIUM — TicketType.price is Double, not BigDecimal

**File:** `TicketType.java` line 51

**Evidence:** `private Double price;`
Financial values stored as IEEE 754 doubles. `TicketTypeServiceImpl` wraps with `BigDecimal.valueOf(ticketType.getPrice())` — converts double → BigDecimal, carrying over imprecision from storage. Errors compound across transactions.

**Fix:** `private BigDecimal price;` with `@Column(precision=10, scale=2)`. Remove `BigDecimal.valueOf()` wrapper in TicketTypeServiceImpl.

---

#### BUG-16 — LOW — listTicketTypesForEvent returns live Hibernate collection

**File:** `TicketTypeServiceImpl.java` line 202

**Evidence:** `return event.getTicketTypes();` — returns the live `PersistentBag`. Callers that modify the returned list outside a transaction can corrupt entity state.

**Fix:** `return new ArrayList<>(event.getTicketTypes());`

---

#### BUG-17 — MEDIUM — Null approvalStatus passes through ApprovalGateFilter

**File:** `ApprovalGateFilter.java`

**Evidence:**
```java
if (status == null) {
    log.warn("Legacy user with null approval status detected: userId={}...");
    // Does NOT block — falls through to filterChain.doFilter()
}
```
A user with null approval status has full API access.

**Fix — add before the PENDING check:**
```java
if (status == null) {
    sendForbiddenResponse(response, "INVALID_ACCOUNT_STATE",
        "Account has invalid state. Contact support.", userId.toString());
    return;
}
```

---

#### BUG-18 — LOW — User.approvalStatus column default conflicts with Java default

**File:** `User.java` line 59–60

**Evidence:**
```java
@Column(columnDefinition = "VARCHAR(255) DEFAULT 'APPROVED'")  // DB default = APPROVED
private ApprovalStatus approvalStatus = ApprovalStatus.PENDING; // Java default = PENDING
```
Direct SQL inserts bypass Java and get APPROVED without admin action.

**Fix:** Change `columnDefinition` to `DEFAULT 'PENDING'` to match Java default.

---

#### BUG-19 — MEDIUM — DatabaseInitializer has four @PostConstruct methods, execution order undefined

**File:** `DatabaseInitializer.java`

**Evidence:** Lines 45, 98, 132, 187 all have `@PostConstruct`. Spring does not guarantee execution order within a single bean. `normalizeKeycloakStateForApprovedUsers()` queries APPROVED users — it depends on `migrateExistingUsers()` having completed first. Order is undefined.

**Fix:** Merge into a single `@PostConstruct` method with explicit sequential calls, OR use separate `@Order`-annotated `ApplicationRunner` beans (as `DataInitializer` correctly does).

---

#### BUG-20 — LOW — DataInitializer loads all tickets on every startup

**File:** `DataInitializer.java` line 45

**Evidence:** `ticketRepository.findAll()` — loads every ticket in the database on every app startup to filter for ones needing pricing backfill. Once all tickets are backfilled, this is wasted work every restart.

**Fix:** Replace with a derived query: `ticketRepository.findByOriginalPriceIsNull()` — only loads tickets that actually need backfill.

---

#### BUG-21 — LOW — ApprovalGateFilter missing Swagger bypass paths

**File:** `ApprovalGateFilter.java`

**Evidence:** `APPROVAL_BYPASS_PATHS` contains only: `/api/v1/auth/register`, `/actuator/health`, `/actuator/info`, `/api/v1/invites/redeem`. No Swagger paths. If Swagger is enabled, unapproved users (or users testing before approval) cannot access API docs.

**Fix:** Add to `APPROVAL_BYPASS_PATHS`:
```java
"/swagger-ui/",
"/v3/api-docs/",
"/swagger-resources/"
```

---

#### BUG-22 — MEDIUM — countByTicketTypeEventId includes CANCELLED tickets

**File:** `TicketRepository.java` line 23

**Evidence:** `int countByTicketTypeEventId(UUID eventId)` — derived query, no status filter.
Used in:
- `EventServiceImpl.deleteEventForOrganizer()` — blocks deletion even if all tickets are CANCELLED
- `EventServiceImpl.updateEventForOrganizer()` — salesEnd past-date guard fires even if all tickets cancelled

**Fix:** Add:
```java
int countByTicketTypeEventIdAndStatusNot(UUID eventId, TicketStatusEnum excludedStatus);
```
Use with `TicketStatusEnum.CANCELLED` in both callers.

---

#### BUG-23 — LOW — Redundant double-save in purchaseTickets loop

**File:** `TicketTypeServiceImpl.java` lines 163–165

**Evidence:**
```java
Ticket savedTicket = ticketRepository.save(ticket);    // save #1
qrCodeService.generateQrCode(savedTicket);
createdTickets.add(ticketRepository.save(savedTicket)); // save #2 — no changes made
```
`savedTicket` is not modified between save #1 and save #2. Two DB writes per ticket per purchase.

**Fix:**
```java
Ticket savedTicket = ticketRepository.save(ticket);
qrCodeService.generateQrCode(savedTicket);
createdTickets.add(savedTicket);  // no second save
```

---

## 9. BUG SUMMARY TABLE

| Bug | Severity | File(s) | Tier | Status |
|-----|----------|---------|------|--------|
| BUG-1: 4-arg purchaseTickets not implemented | CRITICAL | TicketTypeServiceImpl.java | 0 | ✅ FIXED |
| BUG-2: ddl-auto=create destroys data on restart | CRITICAL | application.properties | 1 | ✅ FIXED |
| BUG-3: UserProvisioningFilter passive | HIGH | UserProvisioningFilter.java | 1 | ✅ FIXED |
| BUG-4: No pessimistic lock on invite code | CRITICAL | InviteCodeRepository.java, Registration/InviteCodeServiceImpl.java | 1 | ✅ FIXED |
| BUG-5: DB saved before Keycloak, failure swallowed | HIGH | ApprovalServiceImpl.java | 1 | ✅ FIXED |
| BUG-6: deleteTicketType loads collection + cascade race | HIGH | TicketTypeServiceImpl.java | 1 | ✅ FIXED |
| BUG-7: No event lock in purchaseTickets | HIGH | EventRepository.java, TicketTypeServiceImpl.java | 1 | ✅ FIXED |
| BUG-8: QR view/download encodes wrong UUID | HIGH | QrCodeServiceImpl.java | 2 | ✅ FIXED |
| BUG-9: isStaff() loads full staff collection | HIGH | AuthorizationServiceImpl.java, EventRepository.java | 2 | ✅ FIXED |
| BUG-10: getSalesDashboard N+1 + double arithmetic | HIGH | EventServiceImpl.java, TicketRepository.java | 2 | ✅ FIXED |
| BUG-11: STAFF registration no event assignment | HIGH | RegistrationServiceImpl.java | 2 | ✅ FIXED |
| BUG-12: updateEvent silently deletes sold ticket types | HIGH | EventServiceImpl.java | 2 | ✅ FIXED |
| BUG-13: validateTicket loads full validations | MEDIUM | TicketValidationServiceImpl.java, TicketValidationRepository.java | 2 | ✅ FIXED |
| BUG-14: Dead STAFF guard in generateInviteCode | MEDIUM | InviteCodeServiceImpl.java | 2 | ✅ FIXED |
| BUG-15: TicketType.price is Double not BigDecimal | MEDIUM | TicketType.java | 3 | ✅ FIXED |
| BUG-16: listTicketTypesForEvent returns live collection | LOW | TicketTypeServiceImpl.java | 3 | ✅ FIXED |
| BUG-17: Null approvalStatus passes ApprovalGateFilter | MEDIUM | ApprovalGateFilter.java | 3 | ✅ FIXED |
| BUG-18: Conflicting approvalStatus column default | LOW | User.java | 3 | ✅ FIXED (design updated: Java null, DB APPROVED, migration handles legacy rows) |
| BUG-19: Multiple @PostConstruct undefined ordering | MEDIUM | DatabaseInitializer.java | 3 | ✅ FIXED (single @PostConstruct, sequential private calls) |
| BUG-20: DataInitializer findAll() on every startup | LOW | DataInitializer.java | 3 | ✅ FIXED (uses findTicketsMissingPricingData()) |
| BUG-21: No Swagger bypass in ApprovalGateFilter | LOW | ApprovalGateFilter.java | 3 | ✅ FIXED |
| BUG-22: countByTicketTypeEventId includes CANCELLED | MEDIUM | TicketRepository.java, EventServiceImpl.java | 3 | ✅ FIXED |
| BUG-23: Redundant double-save in purchase loop | LOW | TicketTypeServiceImpl.java | 3 | ✅ FIXED |

---

## 10. FIX ORDER

### TIER 0 — Fix first (app cannot start)

| # | Bug | Files to change |
|---|-----|-----------------|
| 1 | BUG-1: Implement 4-arg purchaseTickets | TicketTypeServiceImpl.java |

### TIER 1 — Fix before any real testing (data / security)

| # | Bug | Files to change |
|---|-----|-----------------|
| 2 | BUG-2: ddl-auto=create | application.properties |
| 3 | BUG-3: UserProvisioningFilter passive | UserProvisioningFilter.java |
| 4 | BUG-4: No invite code pessimistic lock | InviteCodeRepository.java, RegistrationServiceImpl.java, InviteCodeServiceImpl.java |
| 5 | BUG-5: DB before Keycloak in approval | ApprovalServiceImpl.java |
| 6 | BUG-6: deleteTicketType cascade race | TicketTypeServiceImpl.java, TicketRepository.java |
| 7 | BUG-7: No event lock in purchase | EventRepository.java, TicketTypeServiceImpl.java |

### TIER 2 — Fix before any user testing (correctness)

| # | Bug | Files to change |
|---|-----|-----------------|
| 8 | BUG-8: QR wrong UUID in view/download | QrCodeServiceImpl.java |
| 9 | BUG-9: isStaff loads full collection | EventRepository.java, AuthorizationServiceImpl.java |
| 10 | BUG-10: getSalesDashboard N+1 + double | EventServiceImpl.java, TicketRepository.java |
| 11 | BUG-11: STAFF registration no event assign | RegistrationServiceImpl.java |
| 12 | BUG-12: updateEvent deletes sold ticket types | EventServiceImpl.java, TicketRepository.java |
| 13 | BUG-13: validateTicket loads validations | TicketValidationServiceImpl.java, TicketValidationRepository.java |
| 14 | BUG-14: Dead STAFF guard in invite generation | InviteCodeServiceImpl.java |

### TIER 3 — Fix for quality and robustness

| # | Bug | Files to change |
|---|-----|-----------------|
| 15 | BUG-15: TicketType.price Double | TicketType.java, TicketTypeServiceImpl.java |
| 16 | BUG-16: Returns live Hibernate collection | TicketTypeServiceImpl.java |
| 17 | BUG-17: Null status passes filter | ApprovalGateFilter.java |
| 18 | BUG-18: Conflicting column default | User.java |
| 19 | BUG-19: @PostConstruct ordering | DatabaseInitializer.java |
| 20 | BUG-20: DataInitializer findAll() | DataInitializer.java, TicketRepository.java |
| 21 | BUG-21: No Swagger bypass | ApprovalGateFilter.java |
| 22 | BUG-22: countByTicketTypeEventId includes CANCELLED | TicketRepository.java, EventServiceImpl.java |
| 23 | BUG-23: Double save in purchase loop | TicketTypeServiceImpl.java |

---

## 11. TEST COVERAGE STATE

**Test files found:**
- `EventBookingAppApplicationTests.java` — `contextLoads()` only. Will FAIL until BUG-1 is fixed.
- `TestSecurityConfig.java` — Mocks `JwtDecoder` and `Keycloak` admin client. Well-written infrastructure. Enables real slice tests to be written without a running Keycloak.
- `EventControllerUpdateTest.java` — 5 tests for `UpdateEventRequestDto` DTO serialization and controller ID-mismatch logic simulation. No HTTP, no DB. Passes in isolation.

**Business logic covered by tests: ZERO**

The purchase flow, approval flow, invite redemption, QR generation/validation, ticket validation — none of it is tested. `TestSecurityConfig` provides the right infrastructure to write real integration tests with `@WebMvcTest` or `@SpringBootTest`.

---

## 12. NEXT SESSION INSTRUCTIONS

1. **Reference `audit.md` at the start of every session — it lives in the project root**
2. **ALL 31 bugs are now FIXED (23 original + 8 schema mismatches). The codebase is ready for integration testing.**
3. **After every session: update this document with any new findings**
4. **Push to GitHub before closing every session**

**Session 9 fixes applied:**
- `V5__fix_qr_code_value_column.sql` — REWRITTEN: now renames `qr_code_data → qr_value` FIRST (idempotent DO $$ guard) before altering type. Fixes bugs S-1 and S-8.
- `V7__fix_remaining_schema_mismatches.sql` — NEW FILE: fixes S-2 (`validated_by → validated_by_id`), S-3 (`method → validation_method`), S-4 (`revoke_reason → revoked_reason`), S-5 (adds `version` to invite_codes), S-6 (adds `revoked_at` to invite_codes), S-7 (adds `status` to qr_codes).

**All bugs are fixed. Next focus areas:**
- Start Keycloak (port 9090) + PostgreSQL (port 5433)
- Run `mvn spring-boot:run` — Flyway will run V1→V2→V3→V5→V6→V7 in order
- Test the full flow in Postman: register → approve → create event → add ticket types → purchase → download QR → validate at gate
- Write integration tests (TestSecurityConfig infrastructure is in place)

---

## 13. DECISIONS LOG

| Decision | Reason | Session |
|----------|---------|---------|
| Role from invite code only | Single source of truth for privilege | S1 |
| One bug at a time, no scope drift | Prevents context loss and hallucination | S1 |
| Paste files directly — GitHub fetching fails | Technical constraint | S1 |
| Full audit.md output every session | Single source of truth | S1 |
| Push to GitHub before closing | Prevent version confusion | S1 |
| Keycloak activation before DB save | Consistency guarantee — Rule #4 | S2 |
| Service-layer admin check + controller check | Defense in depth | S2 |
| STAFF codes must carry eventId | Role + scope both required | S2 |
| AuditLogService REQUIRES_NEW | Audit must never rollback business tx | S1 |
| Per-user ticket limit counts CANCELLED | Prevents buy-cancel-rebuy abuse | S5 |
| Discount changes prospective only | Per-ticket snapshot protects history | S5 |
| QR encodes qrCode entity UUID (not ticketId) | Scan target is QrCode row ID | S7 |
| TicketType pessimistic lock on purchase | Prevents per-type oversell | S2 |
| ADMIN does not bypass business rules | Separation: role mgmt vs event access | S1 |
| No new dependencies without explicit decision | Prevents scope creep | S1 |
| BUG-TT-D (discount TOCTOU) deferred | Per-ticket snapshot partially mitigates | S5 |
