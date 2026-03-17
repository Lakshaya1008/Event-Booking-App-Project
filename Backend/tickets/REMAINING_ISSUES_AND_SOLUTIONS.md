# REMAINING ISSUES & SOLUTIONS
## (Not covered by the 7 applied fixes)

---

## 🔴 CRITICAL REMAINING ISSUES

### ISSUE #1: Ticket Overselling Race Condition
**Severity:** 🔴 CRITICAL  
**Location:** `TicketTypeServiceImpl.purchaseTickets()` Lines 123-135  
**Current Code:**
```java
int activeForType = ticketRepository.countActiveByTicketTypeId(
        ticketType.getId(), TicketStatusEnum.CANCELLED);
if (ticketType.getTotalAvailable() != null
        && activeForType + quantity > ticketType.getTotalAvailable()) {
    throw new TicketsSoldOutException();
}
// INSERT HAPPENS HERE - Race condition window!
```

**Problem:** Between count and insert, another thread can create tickets  
**Impact:** Overselling when high concurrency  
**Example:** 10 available, 2 users request 5 each → Both pass check → 15 sold

**Solution A: Database Constraint (Recommended)**
```sql
-- Add CHECK constraint at database level
ALTER TABLE tickets ADD CONSTRAINT check_capacity
  CHECK ((SELECT COUNT(*) FROM tickets t 
          WHERE t.ticket_type_id = ticket_type_id 
          AND t.status != 'CANCELLED') 
         <= (SELECT total_available FROM ticket_types tt 
             WHERE tt.id = ticket_type_id));
```
**Risk:** ❌ CHECK constraints across tables are complex in PostgreSQL

**Solution B: Atomic Counter (Better)**
```sql
-- Add counter column
ALTER TABLE ticket_types ADD COLUMN sold_count INT DEFAULT 0;

-- Add trigger to keep in sync
CREATE TRIGGER update_sold_count AFTER INSERT ON tickets
FOR EACH ROW
WHEN (NEW.status != 'CANCELLED')
EXECUTE FUNCTION increment_sold_count();

-- Add constraint
ALTER TABLE ticket_types ADD CONSTRAINT check_sold_count
  CHECK (sold_count <= total_available);
```

**Solution C: Optimistic Locking (Easiest Code Change)**
```java
// Add @Version to TicketType
@Entity
public class TicketType {
    @Version
    private Long version;
    // ... other fields
}

// In service
public List<Ticket> purchaseTickets(...) {
    TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)...;
    
    int activeForType = ticketRepository.countActiveByTicketTypeId(...);
    if (activeForType + quantity > ticketType.getTotalAvailable()) {
        throw new TicketsSoldOutException();
    }
    
    // Save will throw OptimisticLockException if version changed
    ticketTypeRepository.save(ticketType);
    
    // Insert tickets
    for (int i = 0; i < quantity; i++) {
        ticketRepository.save(ticket);
    }
}
```

**Recommendation:** Use Solution B (Atomic Counter) + add load testing

---

## 🟠 HIGH REMAINING ISSUES

### ISSUE #2: Email Failure on Success
**Severity:** 🟠 HIGH  
**Location:** `TicketTypeServiceImpl.purchaseTickets()` Line 169  
**Current Code:**
```java
emailService.sendTicketConfirmationEmail(
        user.getEmail(), user.getName(),
        event.getName(), ticketType.getName(),
        quantity, createdTickets.get(0).getId());
```

**Problem:** If email service fails, exception bubbles up  
- Ticket is already created ✓
- But user gets error response ❌
- User doesn't know if purchase succeeded

**Solution:**
```java
// Wrap email in try-catch (synchronous)
try {
    emailService.sendTicketConfirmationEmail(
            user.getEmail(), user.getName(),
            event.getName(), ticketType.getName(),
            quantity, createdTickets.get(0).getId());
    log.info("Confirmation email sent for ticket {}", createdTickets.get(0).getId());
} catch (Exception e) {
    log.error("Failed to send confirmation email, but ticket was created", e);
    // Return success to user anyway - ticket is created
    // Background job will retry later
}

// Or: Implement async email queue
// emailService.queueConfirmationEmail(user.getEmail(), ...);
// Return success immediately, send email asynchronously
```

**Better Solution: Async Email Queue**
```java
@Component
public class EmailQueue {
    @Autowired private EmailService emailService;
    
    @Async
    public void sendConfirmationEmailAsync(String email, String name, 
                                          String eventName, String ticketType, 
                                          int quantity, UUID ticketId) {
        try {
            emailService.sendTicketConfirmationEmail(
                    email, name, eventName, ticketType, quantity, ticketId);
        } catch (Exception e) {
            log.error("Failed to send async email, queuing for retry", e);
            // Queue to database/Redis for retry
        }
    }
}
```

---

### ISSUE #3: Event Date Range Validation Incomplete
**Severity:** 🟠 HIGH  
**Location:** `EventServiceImpl.validateDateOrdering()` Lines 425-432  
**Current Code:**
```java
private void validateDateOrdering(LocalDateTime start, LocalDateTime end,
                                  LocalDateTime salesStart, LocalDateTime salesEnd) {
    if (start != null && end != null && !end.isAfter(start)) {
        throw new InvalidBusinessStateException("Event end date must be after start date.");
    }
    if (salesStart != null && salesEnd != null && !salesEnd.isAfter(salesStart)) {
        throw new InvalidBusinessStateException("Sales end date must be after sales start date.");
    }
}
```

**Missing Validations:**
- ❌ salesStart must be >= eventStart
- ❌ salesEnd must be <= eventEnd
- ❌ salesEnd should be before eventEnd (can't sell after event happens)

**Example Invalid Scenarios Allowed:**
- Event: Jan 15-20, Sales: Jan 10-25 ❌ (sales before event starts)
- Event: Jan 15-20, Sales: Jan 18-25 ❌ (sales end after event ends)

**Solution:**
```java
private void validateDateOrdering(LocalDateTime start, LocalDateTime end,
                                  LocalDateTime salesStart, LocalDateTime salesEnd) {
    // Original checks
    if (start != null && end != null && !end.isAfter(start)) {
        throw new InvalidBusinessStateException("Event end date must be after start date.");
    }
    if (salesStart != null && salesEnd != null && !salesEnd.isAfter(salesStart)) {
        throw new InvalidBusinessStateException("Sales end date must be after sales start date.");
    }
    
    // NEW: Validate sales window is within event window
    if (salesStart != null && start != null && salesStart.isBefore(start)) {
        throw new InvalidBusinessStateException(
            "Sales cannot start before the event starts.");
    }
    
    if (salesEnd != null && end != null && salesEnd.isAfter(end)) {
        throw new InvalidBusinessStateException(
            "Sales cannot end after the event ends.");
    }
}
```

---

### ISSUE #4: Discount Fixed Amount Validation Missing Price Cap
**Severity:** 🟠 HIGH  
**Location:** `DiscountServiceImpl.validateDiscountRequest()` Lines 167-177  
**Current Code:**
```java
if (request.getDiscountType() == DiscountType.FIXED_AMOUNT) {
    if (request.getValue().compareTo(BigDecimal.ZERO) <= 0)
        throw new InvalidInputException("Fixed amount discount must be positive");
}
```

**Problem:** No check that discount doesn't exceed ticket price  
**Example:** $10 discount on $5 ticket = negative price (organizer pays customer!)

**Solution:**
```java
if (request.getDiscountType() == DiscountType.FIXED_AMOUNT) {
    if (request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidInputException("Fixed amount discount must be positive");
    }
    
    // NEW: Get ticket price and validate
    TicketType ticketType = ticketTypeRepository.findById(request.getTicketTypeId())
            .orElseThrow(...);
    
    if (request.getValue().compareTo(ticketType.getPrice()) >= 0) {
        throw new InvalidInputException(
            String.format("Discount amount ($%.2f) cannot be >= ticket price ($%.2f)",
                request.getValue(), ticketType.getPrice()));
    }
}
```

---

### ISSUE #5: Rate Limiting Missing on Invite Code Generation
**Severity:** 🟠 HIGH  
**Location:** `InviteCodeServiceImpl.generateInviteCode()`  
**Problem:** No rate limiting on invite code generation  
**Risk:** Admin could create thousands of codes → DOS

**Solution:**
```java
// Add to Keycloak admin endpoints
@PostMapping("/invite-codes")
@RateLimit(limit = 100, window = 3600) // 100 per hour
public ResponseEntity<InviteCodeResponseDto> generateInviteCode(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody GenerateInviteCodeRequest request) {
    // ... implementation
}

// Or: Use annotation-based approach
@Service
public class InviteCodeServiceImpl {
    @RateLimiter(name = "generateInviteCode", fallbackMethod = "rateLimitFallback")
    public InviteCodeResponseDto generateInviteCode(...) {
        // ... implementation
    }
    
    public InviteCodeResponseDto rateLimitFallback(...) {
        throw new InvalidBusinessStateException(
            "Too many invite codes generated. Please try again later.");
    }
}
```

---

## 🟡 MEDIUM REMAINING ISSUES

### ISSUE #6: Event Deletion with No Notification
**Severity:** 🟡 MEDIUM  
**Location:** `EventServiceImpl.deleteEventForOrganizer()`  
**Problem:** Event deleted but no email sent to attendees/staff

**Solution:**
```java
@Override
@Transactional
public void deleteEventForOrganizer(UUID organizerId, UUID id) {
    authorizationService.requireOrganizerAccess(organizerId, id);

    int activeTickets = ticketRepository.countActiveTicketsByEventId(id, TicketStatusEnum.CANCELLED);
    if (activeTickets > 0) {
        throw new InvalidBusinessStateException(String.format(
                "Cannot delete event '%s' — %d active ticket(s) exist. " +
                        "Cancel the event first to bulk-cancel all tickets.", id, activeTickets));
    }

    eventRepository.findById(id).ifPresent(event -> {
        // NEW: Send notifications before deletion
        try {
            Set<UUID> attendeeEmails = event.getAttendees().stream()
                    .map(User::getEmail)
                    .collect(Collectors.toSet());
            
            attendeeEmails.forEach(email -> {
                emailService.sendEventDeletionNotification(email, event.getName());
            });
        } catch (Exception e) {
            log.error("Failed to notify attendees of event deletion", e);
        }
        
        emitEventAudit(AuditAction.EVENT_DELETED, organizerId, event, ...);
        eventRepository.delete(event);
    });
}
```

---

### ISSUE #7: Missing Idempotency on Invite Code Redemption
**Severity:** 🟡 MEDIUM  
**Location:** `InviteCodeServiceImpl.redeemInviteCode()`  
**Problem:** If network error after success, retry could redeem twice

**Solution: Idempotency Key Pattern**
```java
// Add to request
@PostMapping("/invites/redeem")
public ResponseEntity<RedeemInviteCodeResponseDto> redeemInviteCode(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody RedeemInviteCodeRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    
    UUID userId = UUID.fromString(jwt.getSubject());
    
    // Check if already processed
    if (idempotencyKey != null) {
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByKey(idempotencyKey);
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get().getResponse());
        }
    }
    
    // Process normally
    RedeemInviteCodeResponseDto response = inviteCodeService.redeemInviteCode(
            userId, request.getCode());
    
    // Save idempotency record for retries
    if (idempotencyKey != null) {
        IdempotencyRecord record = new IdempotencyRecord(
                idempotencyKey, userId, response);
        idempotencyRepository.save(record);
    }
    
    return ResponseEntity.ok(response);
}
```

---

## 📋 PRIORITY MATRIX

| Issue | Severity | Effort | Priority | Impact |
|-------|----------|--------|----------|--------|
| Overselling | 🔴 Critical | 4 hrs | P0 | Revenue loss, angry customers |
| Email failure | 🟠 High | 2 hrs | P1 | Poor UX, confusion |
| Date range | 🟠 High | 1 hr | P1 | Confusing behavior |
| Discount cap | 🟠 High | 1 hr | P1 | Revenue loss |
| Rate limiting | 🟠 High | 2 hrs | P2 | DOS risk |
| Deletion notify | 🟡 Medium | 1 hr | P2 | Poor UX |
| Idempotency | 🟡 Medium | 3 hrs | P3 | Edge case |

---

## ✅ RECOMMENDATION

**Immediate (Before Production):**
1. Fix overselling (CRITICAL) - 4 hours
2. Fix email failure (HIGH) - 2 hours
3. Fix date validation (HIGH) - 1 hour
4. Fix discount validation (HIGH) - 1 hour

**Next Sprint:**
5. Add rate limiting
6. Add notifications
7. Add idempotency

**Timeline:** 8 hours critical + applied 7 fixes = ~40-50 hours total for full hardening

---

## 🎯 CONCLUSION

The 7 applied fixes address critical security issues.  
The remaining 7 issues address business logic and UX.  
Together they create a production-ready system.

Current status after 7 fixes: **8/10 Production Ready**  
After remaining 7 fixes: **9.5/10 Enterprise Ready**

