# REMEDIATION GUIDE - HOW TO FIX EACH ISSUE

## CRITICAL ISSUES - Fix Immediately

---

### 1. Hardcoded Database Password

#### Current State
```properties
# application-local.properties
spring.datasource.password=Lakshaya@1008
```

#### Step 1: Remove from Git History
```bash
cd /path/to/repo

# Remove file from entire git history
git filter-repo --path application-local.properties --invert-paths

# Force push to all remotes (WARNING: This rewrite history)
git push --force-with-lease origin --all
git push --force-with-lease origin --tags

# Notify all developers to re-clone
echo "SECURITY ALERT: Run 'git clone <repo-url>' to get clean history"
```

#### Step 2: Update application.properties
```properties
# application.properties
spring.datasource.password=${DB_PASSWORD}
```

**Important:** Remove the fallback `:postgres123` default - fail fast if env var is missing!

#### Step 3: Rotate Database Password
```bash
# SSH into database server
ssh postgres@db-server

# Connect as postgres user
psql -U postgres

# Rotate the password
postgres=# ALTER USER eventuser WITH PASSWORD 'NEW_VERY_STRONG_PASSWORD_48_CHARS_LONG_HERE';

# Verify
postgres=# SELECT * FROM pg_user WHERE usename='eventuser';
```

#### Step 4: Update All Deployments
```bash
# Development machines
export DB_PASSWORD="<strong-password>"

# Docker Compose
cat > .env << EOF
POSTGRES_PASSWORD=<strong-password>
DB_PASSWORD=<strong-password>
KEYCLOAK_PASSWORD=<strong-password>
EOF

# Kubernetes
kubectl create secret generic db-credentials \
  --from-literal=password='<strong-password>'

# GitHub Actions / CI/CD
# Go to Settings → Secrets → Actions
# Add secret: DB_PASSWORD = <strong-password>
```

#### Verification
```bash
# Test application startup
mvn clean spring-boot:run
# Should start successfully with $DB_PASSWORD env var

# Verify git history is clean
git log --all --full-history -- application-local.properties
# Should return: "fatal: revision walk setup failed"
```

---

### 2. Unlimited ADMIN Role Escalation

#### File: InviteCodeServiceImpl.java

#### Current Problem
```java
// Line 185-192: No check for duplicate ADMIN roles
if ("ADMIN".equals(inviteCode.getRoleName())) {
    log.warn("HIGH-SEVERITY: ADMIN role granted to user '{}' via invite code '{}'",
            userId, inviteCode.getCode());
    emitAdminRoleGrantedAudit(user, inviteCode);
}
```

#### Fix Option A: Prevent Duplicate ADMIN Roles (RECOMMENDED)
```java
// In InviteCodeServiceImpl.redeemInviteCode() - ADD THIS CHECK:

if ("ADMIN".equals(inviteCode.getRoleName())) {
    // Check if user already has ADMIN role
    try {
        List<String> currentRoles = keycloakAdminService.getUserRoles(userId);
        if (currentRoles != null && currentRoles.contains("ADMIN")) {
            log.warn("User {} already has ADMIN role, denying duplicate assignment", userId);
            emitAuditEvent(AuditAction.ADMIN_DUPLICATE_GRANT_BLOCKED, userId, inviteCode);
            throw new InvalidBusinessStateException(
                "User already has ADMIN role. Cannot assign again.");
        }
    } catch (Exception e) {
        log.error("Failed to check user roles before ADMIN grant", e);
        throw new InvalidBusinessStateException(
            "Could not verify user permissions. Please try again.", e);
    }
    
    log.warn("HIGH-SEVERITY: ADMIN role granted to user '{}' via invite code '{}'",
            userId, inviteCode.getCode());
    emitAdminRoleGrantedAudit(user, inviteCode);
}
```

#### Fix Option B: Add Rate Limiting on Redemptions
```java
// Add new query method to InviteCodeRepository:

@Query("""
    SELECT COUNT(ic) FROM InviteCode ic
    WHERE ic.redeemedBy.id = :userId
    AND ic.redeemedAt > :since
""")
int countRedeemedInvitesByUserSince(
    @Param("userId") UUID userId, 
    @Param("since") LocalDateTime since);

// Then in redeemInviteCode():
int redeemedToday = inviteCodeRepository.countRedeemedInvitesByUserSince(
    userId, 
    LocalDateTime.now().minusHours(24));

if (redeemedToday >= 5) {
    throw new InvalidBusinessStateException(
        "You have redeemed too many invite codes today. Please try again tomorrow.");
}
```

#### Fix Option C: Require Manual Approval (MOST SECURE)
```java
// Add new status to InviteCodeStatus enum:
public enum InviteCodeStatus {
    PENDING,
    REDEEMED,
    REVOKED,
    EXPIRED,
    PENDING_ADMIN_APPROVAL  // NEW
}

// Modify redeemInviteCode():
if ("ADMIN".equals(inviteCode.getRoleName())) {
    // Don't assign immediately
    inviteCode.setStatus(InviteCodeStatus.PENDING_ADMIN_APPROVAL);
    inviteCodeRepository.save(inviteCode);
    
    // Notify current admins
    try {
        List<User> admins = userRepository.findByApprovalStatusAndRole(
            ApprovalStatus.APPROVED, "ADMIN");
        admins.forEach(admin -> {
            emailService.sendAdminRoleApprovalRequest(
                admin.getEmail(),
                user.getEmail(),
                user.getName(),
                user.getId().toString());
        });
    } catch (Exception e) {
        log.error("Failed to notify admins of pending role grant", e);
    }
    
    // Return pending status to user
    throw new InvalidBusinessStateException(
        "ADMIN role grants require manual approval from existing admins. " +
        "Your request has been submitted for review.");
}
```

#### Testing
```java
// Add unit test for duplicate prevention
@Test
public void testRedeemAdminCodeWhenUserAlreadyAdmin() {
    // Create user
    UUID userId = UUID.randomUUID();
    User user = createTestUser(userId);
    userRepository.save(user);
    
    // User already has ADMIN role in Keycloak
    when(keycloakAdminService.getUserRoles(userId))
        .thenReturn(List.of("ADMIN"));
    
    // Create invite code
    InviteCode code = createTestInviteCode("ADMIN");
    inviteCodeRepository.save(code);
    
    // Attempt redemption
    assertThrows(InvalidBusinessStateException.class, 
        () -> inviteCodeService.redeemInviteCode(userId, code.getCode()));
    
    // Verify audit was emitted
    verify(auditLogService).saveAuditLog(argThat(log -> 
        log.getAction() == AuditAction.ADMIN_DUPLICATE_GRANT_BLOCKED));
}
```

---

### 3. Missing Audit on Failed Operations

#### Files Affected:
- TicketTypeServiceImpl.purchaseTickets()
- AuthorizationService.requireOrganizerAccess()
- DiscountServiceImpl.*
- TicketValidationServiceImpl.*

#### Step 1: Add Audit Enum Values
```java
// In AuditAction.java enum - ADD THESE:
public enum AuditAction {
    // ... existing ...
    
    // Ticket purchase
    TICKET_PURCHASE_ATTEMPT,
    TICKET_PURCHASE_FAILED,
    TICKET_PURCHASE_SUCCESS,
    
    // Discount operations
    DISCOUNT_CREATE_FAILED,
    DISCOUNT_UPDATE_FAILED,
    DISCOUNT_DELETE_FAILED,
    
    // Validation failures
    TICKET_VALIDATION_FAILED,
    
    // Authorization
    AUTHORIZATION_DENIED,
    
    // Admin operations
    ADMIN_DUPLICATE_GRANT_BLOCKED,
}
```

#### Step 2: Add Audit Helper Method
```java
// In a new AuditHelper service or TicketTypeServiceImpl:

private void auditFailure(UUID userId, Event event, String reason, 
                          HttpServletRequest request) {
    try {
        User actor = userId != null ? 
            userRepository.findById(userId).orElse(systemUserProvider.getSystemUser()) :
            systemUserProvider.getSystemUser();
            
        AuditLog auditLog = AuditLog.builder()
                .action(AuditAction.TICKET_PURCHASE_FAILED)
                .actor(actor)
                .event(event)
                .resourceType("TICKET")
                .details("reason=" + reason)
                .ipAddress(extractClientIp(request))
                .userAgent(extractUserAgent(request))
                .build();
        auditLogService.saveAuditLog(auditLog);
    } catch (Exception e) {
        log.error("Failed to emit failure audit: {}", e.getMessage());
    }
}
```

#### Step 3: Add Audit to Purchase Method
```java
@Override
@Transactional
public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
    HttpServletRequest request = getCurrentRequest();
    
    try {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    auditFailure(userId, null, "USER_NOT_FOUND", request);
                    throw new UserNotFoundException("User not found: " + userId);
                });

        TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
                .orElseThrow(() -> {
                    auditFailure(userId, null, "TICKET_TYPE_NOT_FOUND", request);
                    throw new TicketTypeNotFoundException("Ticket type not found");
                });

        Event event = ticketType.getEvent();

        if (!EventStatusEnum.PUBLISHED.equals(event.getStatus())) {
            auditFailure(userId, event, 
                "EVENT_NOT_PUBLISHED:status=" + event.getStatus(), request);
            throw new InvalidBusinessStateException(
                "Event is not published");
        }

        LocalDateTime now = LocalDateTime.now();
        if (event.getSalesStart() != null && now.isBefore(event.getSalesStart())) {
            auditFailure(userId, event, 
                "SALES_NOT_STARTED:start=" + event.getSalesStart(), request);
            throw new InvalidBusinessStateException(
                "Sales have not started yet");
        }

        if (event.getSalesEnd() != null && now.isAfter(event.getSalesEnd())) {
            auditFailure(userId, event, 
                "SALES_CLOSED:end=" + event.getSalesEnd(), request);
            throw new InvalidBusinessStateException(
                "Sales have closed");
        }

        // ... rest of purchase logic ...
        
        // On success, emit success audit
        emitTicketPurchasedAudit(user, event, ticketType, quantity);
        return createdTickets;
        
    } catch (Exception e) {
        if (!(e instanceof InvalidBusinessStateException)) {
            // Unexpected error - audit it
            auditFailure(userId, null, 
                "UNEXPECTED:" + e.getClass().getSimpleName(), request);
        }
        throw e;
    }
}
```

---

### 4. Ticket Overselling Under Load

#### Problem
Race condition allows selling more tickets than available.

#### Solution: Atomic Counter in Database
```sql
-- Step 1: Add column to track sold count
ALTER TABLE ticket_types ADD COLUMN sold_count INT DEFAULT 0;

-- Step 2: Populate existing values
UPDATE ticket_types SET sold_count = (
    SELECT COUNT(*) FROM tickets t 
    WHERE t.ticket_type_id = ticket_types.id 
    AND t.status != 'CANCELLED'
);

-- Step 3: Add trigger to keep counter up-to-date
CREATE OR REPLACE FUNCTION update_sold_count() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status != 'CANCELLED' AND OLD.status = 'CANCELLED' THEN
        UPDATE ticket_types SET sold_count = sold_count + 1 
        WHERE id = NEW.ticket_type_id;
    ELSIF NEW.status = 'CANCELLED' AND OLD.status != 'CANCELLED' THEN
        UPDATE ticket_types SET sold_count = sold_count - 1 
        WHERE id = NEW.ticket_type_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ticket_sold_count_trigger 
AFTER UPDATE ON tickets 
FOR EACH ROW 
EXECUTE FUNCTION update_sold_count();
```

#### In Java:
```java
// Add to TicketRepository
@Modifying
@Query("""
    UPDATE TicketType tt SET tt.soldCount = tt.soldCount + :quantity 
    WHERE tt.id = :ticketTypeId 
    AND (tt.soldCount + :quantity <= COALESCE(tt.totalAvailable, 2147483647))
""")
int incrementSoldCountAtomic(
    @Param("ticketTypeId") UUID ticketTypeId, 
    @Param("quantity") int quantity);

// Use in service:
@Override
@Transactional
public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
    // ... validation ...
    
    // Atomic increment - will return 0 if capacity exceeded
    int updated = ticketRepository.incrementSoldCountAtomic(ticketTypeId, quantity);
    
    if (updated == 0) {
        auditFailure(userId, event, "CAPACITY_EXCEEDED", request);
        throw new TicketsSoldOutException(
            "Not enough tickets available");
    }
    
    // Now safe to insert actual ticket records
    List<Ticket> createdTickets = new ArrayList<>();
    for (int i = 0; i < quantity; i++) {
        Ticket ticket = new Ticket();
        // ... set fields ...
        createdTickets.add(ticketRepository.save(ticket));
    }
    
    return createdTickets;
}
```

#### Load Testing
```bash
# Install Apache JMeter
# Create test plan with:
# - Thread Group: 100 threads
# - Ramp-up: 10 seconds
# - Loop count: 1
# - HTTP Request: POST /api/v1/tickets/purchase
#   - Body: {"ticketTypeId":"<id>","quantity":1}
#   - Headers: {"Authorization": "Bearer <token>"}

# Run test
jmeter -n -t tickets_load_test.jmx -l results.jtl

# Verify results
# - All requests either succeed (200) or fail (409)
# - No double-sales
# - Total sold == available tickets
```

---

### 5. Strengthen Cancelled Event Guard

#### Current Code
```java
// EventServiceImpl.java lines 160-167
if (EventStatusEnum.CANCELLED.equals(existingEvent.getStatus())
        && !EventStatusEnum.CANCELLED.equals(event.getStatus())) {
    throw new InvalidBusinessStateException("Cannot change status...");
}
```

#### Recommended Fix
```java
@Override
@Transactional
public Event updateEventForOrganizer(UUID organizerId, UUID id, UpdateEventRequest event) {
    // ... existing validation ...
    
    Event existingEvent = eventRepository.findById(id)
            .orElseThrow(() -> new EventNotFoundException(...));

    // ✅ NEW: Block ALL modifications to cancelled events
    if (EventStatusEnum.CANCELLED.equals(existingEvent.getStatus())) {
        auditFailure(organizerId, existingEvent, "ATTEMPT_TO_MODIFY_CANCELLED", 
            getCurrentRequest());
        throw new InvalidBusinessStateException(
                "Cannot modify a cancelled event. " +
                "All tickets for this event have been permanently cancelled " +
                "and cannot be restored. " +
                "To run a new event, please create a new event instead.");
    }

    // ... rest of update logic ...
}
```

#### Testing
```java
@Test
public void testUpdateCancelledEventFails() {
    Event event = createTestEvent();
    event.setStatus(EventStatusEnum.CANCELLED);
    eventRepository.save(event);
    
    UpdateEventRequest update = new UpdateEventRequest();
    update.setId(event.getId());
    update.setName("New Name"); // Trying to change just the name
    
    assertThrows(InvalidBusinessStateException.class,
        () -> eventService.updateEventForOrganizer(organizerId, event.getId(), update),
        "Should not allow any updates to cancelled events");
}
```

---

## HIGH-SEVERITY ISSUES - Fix in Next Sprint

---

### 6. Weak Database Password Fallback

#### Current
```properties
spring.datasource.password=${DB_PASSWORD:postgres123}
```

#### Fix
```properties
spring.datasource.password=${DB_PASSWORD}
```

#### Add Startup Validation
```java
@Configuration
public class DatabaseConfiguration {
    
    @Bean
    public ApplicationRunner checkDatabasePassword(
            @Value("${spring.datasource.password:#{null}}") String password) {
        return args -> {
            if (password == null || password.isEmpty()) {
                throw new IllegalStateException(
                    "FATAL: DB_PASSWORD environment variable is not set. " +
                    "Application cannot start without database credentials.");
            }
            
            if (password.equals("postgres123") || password.equals("admin") || 
                password.equals("password") || password.length() < 16) {
                throw new IllegalStateException(
                    "FATAL: Database password is weak. " +
                    "Use a strong password with 16+ chars, mixed case, numbers, symbols.");
            }
            
            log.info("Database password validation passed");
        };
    }
}
```

---

### 7-13. Remaining High-Severity Issues

(Space constraints - refer to DETAILED_FINDINGS.md for full implementations)

---

## Testing Checklist

- [ ] Unit tests for all new audit logging
- [ ] Integration tests for ticket purchase with mocked Keycloak
- [ ] Load test: 100 concurrent ticket purchases
- [ ] Security test: Attempt to escalate ADMIN role
- [ ] Security test: Attempt to modify cancelled event
- [ ] Verify all password paths use environment variables
- [ ] Verify no credentials in logs (test with DEBUG level)
- [ ] Verify cancelled tickets block validation
- [ ] Verify request context null safety
- [ ] Verify audit logs contain IP address and user agent

---

## Deployment Checklist

- [ ] All critical fixes merged and code reviewed
- [ ] Tests passing with > 90% code coverage
- [ ] Security scan (SonarQube) shows no critical issues
- [ ] Load test completed successfully
- [ ] Database backup taken
- [ ] Rollback plan documented
- [ ] Team training completed
- [ ] Monitoring alerts configured
- [ ] Incident response plan updated

---

**Total Estimated Fix Time: 40 hours**
**Recommended Timeline: 2-3 sprints**
**Production Deployment: After all critical + high severity fixes**

