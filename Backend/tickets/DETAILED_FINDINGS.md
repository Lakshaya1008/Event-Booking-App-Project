# DETAILED AUDIT FINDINGS WITH CODE EXAMPLES

## Critical Issue #1: Hardcoded Database Credentials

### Current Code
**File:** `application-local.properties`
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/eventdb
spring.datasource.username=eventuser
spring.datasource.password=Lakshaya@1008  # ❌ EXPOSED
spring.datasource.driver-class-name=org.postgresql.Driver
```

### Root Cause
Developer created a local properties file and accidentally committed it to Git with real credentials.

### Security Impact
- **Confidentiality:** Anyone cloning repo gets DB access
- **Integrity:** Someone could modify event data, delete users, manipulate tickets
- **Availability:** Could drop all tables or lock out legitimate users
- **Compliance:** Violates OWASP, PCI-DSS, SOC2 requirements

### Exploitation Path
```bash
# Attacker clones repo
git clone <repo-url>

# Reads credentials
cat application-local.properties
# Output: spring.datasource.password=Lakshaya@1008

# Connects to database
psql -h localhost -U eventuser -d eventdb -W
Enter password: Lakshaya@1008

# Now has full database access
postgres=# SELECT * FROM users WHERE role='ADMIN';
postgres=# UPDATE tickets SET status='CANCELLED' WHERE id='...';
postgres=# DROP TABLE events; -- 💥
```

### Fix
```properties
# application.properties
spring.datasource.password=${DB_PASSWORD}

# No fallback - fail fast if env var missing
```

```bash
# On deployment server, set env var
export DB_PASSWORD="<strong-random-password>"

# Or in docker-compose.yml
environment:
  DB_PASSWORD: ${POSTGRES_PASSWORD}
  
# Or in Kubernetes secret
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
type: Opaque
stringData:
  password: <strong-random-password>
```

### Post-Fix Actions
1. **Immediate:** Remove from Git history
   ```bash
   git filter-repo --path application-local.properties
   git push --force-with-lease origin main
   ```

2. **Rotate credentials:** Change password in PostgreSQL
   ```sql
   ALTER USER eventuser WITH PASSWORD 'new_very_strong_password_here';
   ```

3. **Audit logs:** Check who accessed the database in the past week
   ```sql
   SELECT * FROM pg_stat_statements WHERE query LIKE '%ALTER%' OR query LIKE '%DROP%';
   ```

4. **Notify team:** Tell all developers to update local DB password

---

## Critical Issue #2: Unlimited ADMIN Role Escalation

### Current Code
**File:** `InviteCodeServiceImpl.java` Lines 185-192
```java
if ("ADMIN".equals(inviteCode.getRoleName())) {
    log.warn("HIGH-SEVERITY: ADMIN role granted to user '{}' via invite code '{}'",
            userId, inviteCode.getCode());
    emitAdminRoleGrantedAudit(user, inviteCode);
}
```

### The Problem
1. **No duplicate check** - User can redeem multiple ADMIN codes
2. **No rate limiting** - User can redeem 100 codes in a minute
3. **No approval process** - Single redeem grants permanent ADMIN
4. **Audit logs misleading** - Shows "ADMIN granted" 5 times for same user getting 1 role

### Exploitation Scenario

**Step 1:** Admin A creates 5 ADMIN invite codes
```bash
for i in {1..5}; do
  curl -X POST /api/v1/admin/invite-codes \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"roleName":"ADMIN","expirationHours":720}' \
    | jq -r '.code' > code_$i.txt
done
```

**Step 2:** Admin A shares 4 codes with attacker
- Code A: ADMIN2A4K-87FG-98HJ
- Code B: ADMIN3J4K-87FG-98HJ
- Code C: ADMIN4J4K-87FG-98HJ
- Code D: ADMIN5J4K-87FG-98HJ

**Step 3:** Attacker creates account and redeems all 4 codes
```bash
# Register
curl -X POST /api/v1/auth/register \
  -d '{"email":"attacker@evil.com","password":"...","name":"Attacker"}'

# Redeem code 1
curl -X POST /api/v1/invites/redeem \
  -d '{"code":"ADMIN2A4K-87FG-98HJ"}' -H "Authorization: Bearer $TOKEN1"

# Redeem code 2
curl -X POST /api/v1/invites/redeem \
  -d '{"code":"ADMIN3J4K-87FG-98HJ"}' -H "Authorization: Bearer $TOKEN1"

# Redeem code 3
curl -X POST /api/v1/invites/redeem \
  -d '{"code":"ADMIN4J4K-87FG-98HJ"}' -H "Authorization: Bearer $TOKEN1"

# Redeem code 4
curl -X POST /api/v1/invites/redeem \
  -d '{"code":"ADMIN5J4K-87FG-98HJ"}' -H "Authorization: Bearer $TOKEN1"

# Result: Now attacker has ADMIN role
# (Keycloak doesn't duplicate roles, but audit shows 4 grants)
```

**Step 4:** Attacker uses ADMIN access
```bash
# List all users
curl /api/v1/admin/users -H "Authorization: Bearer $ADMIN_TOKEN"

# Approve pending malicious accounts
curl -X POST /api/v1/admin/users/{id}/approve \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Access all event financial data
curl /api/v1/admin/events/{eventId}/sales-dashboard \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Create backdoor accounts
curl -X POST /api/v1/admin/invite-codes \
  -d '{"roleName":"ADMIN",...}' \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Why Audit Logs Don't Help
```
Audit Log Entry 1: ADMIN_ROLE_GRANTED_VIA_INVITE code=ADMIN2A4K userId=attacker
Audit Log Entry 2: ADMIN_ROLE_GRANTED_VIA_INVITE code=ADMIN3J4K userId=attacker
Audit Log Entry 3: ADMIN_ROLE_GRANTED_VIA_INVITE code=ADMIN4J4K userId=attacker
Audit Log Entry 4: ADMIN_ROLE_GRANTED_VIA_INVITE code=ADMIN5J4K userId=attacker
```

Even though Keycloak only assigned 1 role (subsequent assignments are idempotent), the audit logs show 4 separate grants. A compliance auditor sees "4 ADMIN roles assigned" and thinks there are 4 different roles, or suspects log tampering.

### Recommended Fix

**Option A: One Role Per User**
```java
@Override
@Transactional
public RedeemInviteCodeResponseDto redeemInviteCode(UUID userId, String code) {
    // ... existing code ...
    
    if ("ADMIN".equals(inviteCode.getRoleName())) {
        // Check if user already has ADMIN role
        List<String> currentRoles = keycloakAdminService.getUserRoles(userId);
        if (currentRoles.contains("ADMIN")) {
            throw new InvalidBusinessStateException(
                "User already has ADMIN role. Cannot assign again.");
        }
        
        log.warn("ADMIN role granted to user '{}' via invite code '{}'",
                userId, inviteCode.getCode());
        emitAdminRoleGrantedAudit(user, inviteCode);
    }
    
    // ... rest of method ...
}
```

**Option B: Rate Limit Redemptions**
```java
@Override
@Transactional
public RedeemInviteCodeResponseDto redeemInviteCode(UUID userId, String code) {
    // Count recent redemptions by this user (last 24 hours)
    int recentRedemptions = inviteCodeRepository.countByRedeemedByIdAndRedeemedAtAfter(
        userId, 
        LocalDateTime.now().minusHours(24)
    );
    
    if (recentRedemptions >= 5) {
        throw new InvalidBusinessStateException(
            "You have redeemed too many invite codes recently. Try again tomorrow.");
    }
    
    // ... rest of method ...
}
```

**Option C: Require Manual Approval for ADMIN (Recommended)**
```java
if ("ADMIN".equals(inviteCode.getRoleName())) {
    // Don't assign immediately - mark for approval
    inviteCode.setRequiresAdminApproval(true);
    inviteCode.setStatus(InviteCodeStatus.PENDING_ADMIN_REVIEW);
    inviteCodeRepository.save(inviteCode);
    
    // Send notification to current admins
    emailService.notifyAdminsOfPendingRoleGrant(user.getEmail(), "ADMIN");
    
    throw new InvalidBusinessStateException(
        "ADMIN role grants require manual approval. Your request has been sent to administrators.");
}
```

### Additional Mitigations
- Implement `@RateLimit(5 per hour per user)` on `/invites/redeem`
- Require dual-factor approval for ADMIN role assignments
- Add webhook notification to Slack when ADMIN role is assigned
- Implement time window: ADMIN codes only valid for 1 hour (not 30 days)

---

## Critical Issue #3: Missing Audit on Failed Operations

### Current Problem
**File:** `TicketTypeServiceImpl.purchaseTickets()` Lines 88-115

```java
@Override
@Transactional
public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(...)); // ❌ No audit

    TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
            .orElseThrow(() -> new TicketTypeNotFoundException(...)); // ❌ No audit

    Event event = ticketType.getEvent();

    if (!EventStatusEnum.PUBLISHED.equals(event.getStatus())) {
        throw new InvalidBusinessStateException(reason); // ❌ NO AUDIT LOGGED
    }
    
    // ❌ Lots of validations with no audit on failure
    if (event.getSalesStart() != null && now.isBefore(event.getSalesStart())) {
        throw new InvalidBusinessStateException(...); // ❌ NO AUDIT
    }
    
    if (event.getSalesEnd() != null && now.isAfter(event.getSalesEnd())) {
        throw new InvalidBusinessStateException(...); // ❌ NO AUDIT
    }
    
    // Eventually ticket creation succeeds
    // Only then is audit emitted ✅
    emitTicketPurchasedAudit(user, event, ticketType, quantity);
    return createdTickets;
}
```

### Security Impact

**Attack Scenario: Account Enumeration**
```bash
# Attacker tries to find if user UUIDs exist
for uuid in 00000000-0000-0000-0000-00000000{0001..9999}; do
  response=$(curl -s -X POST /api/v1/tickets/purchase \
    -d "{\"userId\":\"$uuid\",\"ticketTypeId\":\"$KNOWN_TYPE\"}" \
    -w "%{http_code}")
    
  if response contains "User not found"; then
    echo "UUID $uuid does NOT exist"
  else
    echo "UUID $uuid might exist"
  fi
done

# Result: Can map out all valid user UUIDs without any audit trail
```

No audit record is created, so admin cannot detect:
- How many enumeration attempts
- Which UUIDs were being targeted
- Which IP addresses performed the attacks
- When the attacks occurred

**Scenario 2: Fraud Detection**
```
Attacker buys 100 tickets in 1 minute:
- Request 1: Succeeds → audit logged ✓
- Request 2: Fails (sold out) → NO AUDIT ✗
- Request 3: Fails (sold out) → NO AUDIT ✗
- Request 4: Fails (sold out) → NO AUDIT ✗
... (repeated 100 times) ...

Audit log shows:
- 1 successful ticket purchase
- 0 failed attempts

Reality: 100 attempted purchases (99 failed)

Fraud analyst cannot detect enumeration or attack pattern!
```

### How RegistrationServiceImpl Does It Correctly ✅
**File:** `RegistrationServiceImpl.java` Lines 75-93

```java
@Override
@Transactional
public RegisterResponseDto register(RegisterRequestDto request) {
    // ✅ Audit attempt at start
    emitAuditEvent(null, null, null, AuditAction.REGISTRATION_ATTEMPT,
            "email=" + request.getEmail() + ",inviteCode=" + ...,
            clientIp, userAgent);

    // ❌ Email uniqueness check
    if (userRepository.existsByEmail(request.getEmail())) {
        // ✅ Audit failure before throwing
        emitAuditEvent(null, null, null, AuditAction.REGISTRATION_FAILED,
                "email=" + request.getEmail() + ",reason=EMAIL_ALREADY_EXISTS", 
                clientIp, userAgent);
        throw new EmailAlreadyInUseException("Email already in use: " + request.getEmail());
    }

    // ❌ Invite code validation
    if (request.getInviteCode() != null) {
        inviteCode = validateAndGetInviteCode(request.getInviteCode());
    }

    // ❌ Keycloak check
    UUID existingKeycloakUserId = keycloakAdminService.getUserIdByEmail(request.getEmail());
    if (existingKeycloakUserId != null && userRepository.existsById(existingKeycloakUserId)) {
        // ✅ Audit failure
        throw new RegistrationException("User already registered");
    }
    
    // ... more steps, each with audit on failure ...
}
```

### Recommended Fix
Apply the pattern from RegistrationServiceImpl to all critical operations:

```java
@Override
@Transactional
public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
    HttpServletRequest request = getCurrentRequest();
    String clientIp = extractClientIp(request);
    String userAgent = extractUserAgent(request);

    // ✅ Audit attempt
    auditLogService.saveAuditLog(AuditLog.builder()
            .action(AuditAction.TICKET_PURCHASE_ATTEMPT)
            .details("userId=" + userId + ",ticketTypeId=" + ticketTypeId + ",quantity=" + quantity)
            .ipAddress(clientIp)
            .userAgent(userAgent)
            .build());

    try {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(...));

        TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(...));

        Event event = ticketType.getEvent();

        // ✅ Audit all failures
        if (!EventStatusEnum.PUBLISHED.equals(event.getStatus())) {
            auditLogService.saveAuditLog(AuditLog.builder()
                    .action(AuditAction.TICKET_PURCHASE_FAILED)
                    .actor(user)
                    .event(event)
                    .details("reason=EVENT_NOT_PUBLISHED,status=" + event.getStatus())
                    .ipAddress(clientIp)
                    .userAgent(userAgent)
                    .build());
            throw new InvalidBusinessStateException("Event not published");
        }

        if (event.getSalesEnd() != null && now.isAfter(event.getSalesEnd())) {
            auditLogService.saveAuditLog(AuditLog.builder()
                    .action(AuditAction.TICKET_PURCHASE_FAILED)
                    .actor(user)
                    .event(event)
                    .details("reason=SALES_CLOSED,salesEnd=" + event.getSalesEnd())
                    .ipAddress(clientIp)
                    .userAgent(userAgent)
                    .build());
            throw new InvalidBusinessStateException("Sales have closed");
        }

        // ... more validations with audit on each failure ...

        // ✅ Existing success audit
        emitTicketPurchasedAudit(user, event, ticketType, quantity);
        return createdTickets;
        
    } catch (Exception e) {
        // ✅ Catch-all for unexpected errors
        auditLogService.saveAuditLog(AuditLog.builder()
                .action(AuditAction.TICKET_PURCHASE_FAILED)
                .actor(userRepository.findById(userId).orElse(null))
                .details("reason=UNEXPECTED_ERROR,message=" + e.getMessage())
                .ipAddress(clientIp)
                .userAgent(userAgent)
                .build());
        throw e;
    }
}
```

### Audit Enum Additions Needed
```java
public enum AuditAction {
    // ... existing ...
    TICKET_PURCHASE_ATTEMPT,
    TICKET_PURCHASE_FAILED,
    TICKET_PURCHASE_SUCCEEDED,
}
```

---

## Critical Issue #4: Ticket Overselling Under High Load

### The Problem
**File:** `TicketTypeServiceImpl.purchaseTickets()` Lines 104-115

```java
// Pessimistic lock acquired on TicketType ✅
TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
        .orElseThrow(...);

// But availability check reads from separate table without lock ❌
int activeForType = ticketRepository.countActiveByTicketTypeId(
        ticketType.getId(), TicketStatusEnum.CANCELLED);

// Check passes
if (ticketType.getTotalAvailable() != null
        && activeForType + quantity > ticketType.getTotalAvailable()) {
    // Does NOT throw ✅ (assuming 5 sold, 5 requested, 10 available)
}

// Between check and insert, another thread can create tickets
// No atomic compare-and-swap operation

// Now create tickets (within transaction, but not under lock)
List<Ticket> createdTickets = new ArrayList<>();
for (int i = 0; i < quantity; i++) {
    Ticket ticket = new Ticket();
    ticket.setStatus(TicketStatusEnum.PURCHASED);
    ticket.setTicketType(ticketType);
    // ... set other fields ...
    Ticket savedTicket = ticketRepository.save(ticket);
    createdTickets.add(savedTicket);
}
```

### Visualization of Race Condition

```
Timeline:
--------

T0:  [Thread A] countActiveByTicketTypeId() = 5
     [TicketType holds lock, but only for this thread]

T1:  [Thread B] countActiveByTicketTypeId() = 5
     [Also acquired lock, no wait]

T2:  [Thread A] 5 + 5 = 10 ≤ 10 ✓ Pass check, continue

T3:  [Thread B] 5 + 5 = 10 ≤ 10 ✓ Pass check, continue

T4:  [Thread A] INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     [Total: 5 new tickets]

T5:  [Thread B] INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     INSERT INTO tickets (ticket_type_id=XXX, status='PURCHASED') VALUES...
     [Total: 5 new tickets]

T6:  [Both transactions commit]

Result:
- Database now has: 5 (original) + 5 (Thread A) + 5 (Thread B) = 15 tickets
- But capacity is only 10 ❌ OVERSOLD BY 5 TICKETS
```

### Why Pessimistic Lock Doesn't Help
The lock is held on TicketType row, which only prevents concurrent updates to **that row**. It does NOT prevent concurrent inserts into the Ticket table! Two threads can hold the same lock and still both insert.

### Real-World Scenario
100 tickets available for concert. Demand surge at 8 PM:
- 100 users simultaneously request 1 ticket each
- 50 users get through successfully
- 50 users get "sold out" error
- Actually 100 tickets were sold (101st user gets error)

But if the window is longer:
- User A: requests 50 tickets, passes check (0+50=50 < 100), starts inserting
- User B: requests 50 tickets, passes check (0+50=50 < 100), starts inserting
- User C: requests 20 tickets, passes check (0+20=20 < 100), starts inserting

After all commit:
- 50 + 50 + 20 = 120 tickets sold when only 100 available
- **Real oversale: 20 tickets**
- Next 20 customers who show up have invalid tickets
- Venue is overcrowded, fire code violation
- Class action lawsuit possible

### Root Cause Analysis
1. Optimistic assumption: "count() then insert()" is atomic
2. Database doesn't enforce constraint: No CHECK constraint on total sold
3. Distributed system problem: Multiple app instances = multiple threads

### Recommended Fix #1: Database Constraint
```sql
-- Add CHECK constraint
ALTER TABLE tickets ADD CONSTRAINT check_capacity
  CHECK ((SELECT COUNT(*) FROM tickets t2 
          WHERE t2.ticket_type_id = ticket_type_id 
          AND t2.status != 'CANCELLED') 
         <= (SELECT total_available FROM ticket_types tt 
             WHERE tt.id = ticket_type_id));
```

**Problem:** Check constraints don't work well across tables in PostgreSQL (they're evaluated per-row, not for entire result set).

### Recommended Fix #2: Atomic Counter Update
```java
// Instead of counting then inserting:
@Modifying
@Query("UPDATE TicketType tt SET tt.ticketsSold = tt.ticketsSold + :quantity " +
       "WHERE tt.id = :ticketTypeId " +
       "AND (SELECT COUNT(*) FROM Ticket t WHERE t.ticketType.id = :ticketTypeId " +
            "AND t.status != 'CANCELLED') + :quantity <= tt.totalAvailable")
int incrementSoldCount(@Param("ticketTypeId") UUID ticketTypeId, 
                       @Param("quantity") int quantity);
```

Then in service:
```java
public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
    TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
            .orElseThrow(...);

    // Atomic operation: increment counter only if room available
    int updated = ticketRepository.incrementSoldCount(ticketTypeId, quantity);
    
    if (updated == 0) {
        throw new TicketsSoldOutException();
    }

    // Now safe to insert actual tickets
    List<Ticket> createdTickets = new ArrayList<>();
    for (int i = 0; i < quantity; i++) {
        Ticket ticket = new Ticket();
        // ... set fields ...
        createdTickets.add(ticketRepository.save(ticket));
    }

    return createdTickets;
}
```

### Recommended Fix #3: Optimistic Locking with @Version
```java
@Entity
@Table(name = "ticket_types")
public class TicketType {
    @Id
    private UUID id;
    
    @Version  // ✅ Add this
    private Long version;
    
    // ... other fields ...
    
    private Integer totalAvailable;
}
```

Then purchases will automatically fail if ticket type was modified:
```java
public List<Ticket> purchaseTickets(...) {
    TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
            .orElseThrow(...);
    
    int activeForType = ticketRepository.countActiveByTicketTypeId(...);
    
    if (activeForType + quantity > ticketType.getTotalAvailable()) {
        throw new TicketsSoldOutException();
    }

    // Save will throw OptimisticLockingFailureException if version changed
    // Retry logic would re-check and potentially fail purchase
    ticketTypeRepository.save(ticketType);
    
    // Insert tickets...
}
```

---

## Critical Issue #5: Incomplete Cancelled Event Guard

### Current Code
**File:** `EventServiceImpl.updateEventForOrganizer()` Lines 160-167

```java
// NEW FIX: Block re-publishing a cancelled event.
if (EventStatusEnum.CANCELLED.equals(existingEvent.getStatus())
        && !EventStatusEnum.CANCELLED.equals(event.getStatus())) {
    throw new InvalidBusinessStateException(
            "Cannot change status of a cancelled event. " +
            "All tickets were cancelled with the event. " +
            "Please create a new event instead.");
}
```

### The Weakness
The guard only prevents status changes FROM CANCELLED TO something else. But what if organizer tries:

```bash
# Request 1: Try to change status
curl -X PUT /api/v1/events/{id} \
  -d '{"status":"PUBLISHED", ...other fields...}'
# Blocked ✅ by the guard

# Request 2: Change other fields AND status (in same request)
curl -X PUT /api/v1/events/{id} \
  -d '{"name":"New Name", "status":"PUBLISHED", ...}'
# Still blocked ✅ (guard fires before field update)

# Request 3: Can we soft-update via multiple requests?
curl -X PUT /api/v1/events/{id} \
  -d '{"name":"New Name"}' # Only update name, not status
# Is this allowed? ❌ UNCLEAR
```

### The Real Issue
What if someone finds a way to bypass the status check? Or what if there's an API endpoint that updates fields without status?

The comment correctly explains the reasoning:
> "When an event is cancelled, all tickets are bulk-cancelled. Re-publishing would allow new purchases but those old cancelled tickets would NOT be restored — creating a confusing state where prior attendees can't enter while new buyers can."

But the code should make it **impossible** to modify a cancelled event AT ALL, not just impossible to change status.

### Recommended Stricter Guard
```java
@Override
@Transactional
public Event updateEventForOrganizer(UUID organizerId, UUID id, UpdateEventRequest event) {
    if (event.getId() == null) throw new EventUpdateException("Event ID cannot be null");
    if (!id.equals(event.getId())) throw new EventUpdateException("Cannot update the ID of an event");

    authorizationService.requireOrganizerAccess(organizerId, id);

    Event existingEvent = eventRepository.findById(id)
            .orElseThrow(() -> new EventNotFoundException(...));

    // ✅ NEW: Block ANY modification to cancelled events
    if (EventStatusEnum.CANCELLED.equals(existingEvent.getStatus())) {
        throw new InvalidBusinessStateException(
                "Cannot modify a cancelled event. " +
                "All tickets for this event have been permanently cancelled. " +
                "To run a new event, please create a new event instead.");
    }

    // ... rest of validation ...
}
```

### Rationale
- Once an event is cancelled, **all tickets are cancelled**
- Those cancelled tickets cannot be restored
- Allowing updates would be confusing to attendees ("Wait, did my ticket get cancelled?")
- Clean separation: cancelled events are immutable
- Users who want to re-run the event should create a fresh event

---

## Complete Findings Summary

This detailed audit provides the technical foundations for the issues listed in the main AUDIT_REPORT.md. Each critical issue has:
- Root cause analysis
- Exploitation scenarios
- Code examples
- Recommended fixes with implementation details
- Security/business impact assessment

