package com.event.tickets.services.impl;

import com.event.tickets.domain.CreateTicketTypeRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.Discount;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.EventStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketStatusEnum;
import com.event.tickets.domain.entities.TicketType;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.InvalidBusinessStateException;
import com.event.tickets.exceptions.TicketTypeDeleteNotAllowedException;
import com.event.tickets.exceptions.TicketTypeNotFoundException;
import com.event.tickets.exceptions.TicketsSoldOutException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.TicketTypeRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.DiscountService;
import com.event.tickets.services.EmailService;
import com.event.tickets.services.QrCodeService;
import com.event.tickets.services.SystemUserProvider;
import com.event.tickets.services.TicketTypeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

/**
 * ORIGINAL FIXES (carried forward — do not remove):
 *
 * FIX-TT1 (BUG 4-1) — createTicketType() guards CANCELLED/COMPLETED events.
 * FIX-TT2 (BUG 4-2) — deleteTicketType() uses COUNT query instead of collection load.
 * FIX-TT3 (BUG 5-1) — 3-arg purchaseTickets() removed from public interface.
 * FIX-TT4 (BUG 5-2) — 4-arg overload no longer double-loads the ticket type.
 *
 * SESSION 5 FIXES (M5 bugs — applied in this file):
 *
 * FIX-H01 (BUG-TT-A) — Event loaded with pessimistic lock in purchaseTickets().
 *   BEFORE: eventRepository.findById() — no lock. Two concurrent purchases could
 *   both read the same maxCapacity and both pass the event-level capacity check
 *   before either committed → event oversold at the event level.
 *   AFTER:  eventRepository.findByIdWithLock() acquires a row-level PESSIMISTIC_WRITE
 *   lock on the event row, serializing concurrent purchases at the event level.
 *   REQUIRES: EventRepository.findByIdWithLock() — see EventRepository_ADD_METHOD.java.
 *   NOTE: locking event before ticketType in all purchase paths — consistent order
 *   prevents deadlock.
 *
 * FIX-H02 (BUG-TT-B) — Cross-event getId() on @ManyToOne(LAZY) proxy.
 *   NO CODE CHANGE. Spring Boot 3.x uses Hibernate 6. In Hibernate 6,
 *   proxy.getId() on a @ManyToOne(LAZY) resolves directly from the FK column
 *   stored in the owning row without initializing the proxy (no extra DB call).
 *   This is guaranteed behaviour in Hibernate 6, not version-dependent guesswork.
 *   Documented as safe. No runtime risk on this stack.
 *
 * FIX-H03 (BUG-TT-D) — TOCTOU race on discount application — PARTIAL MITIGATION ONLY.
 *   The TicketType pessimistic lock (findByIdWithLock) serializes concurrent purchases.
 *   However, a concurrent DiscountServiceImpl.updateDiscount() does not hold the
 *   TicketType lock, so a discount change between findActiveDiscount() and ticket save
 *   is still theoretically possible. Full fix requires DiscountServiceImpl — logged
 *   as BUG-TT-D PARTIALLY MITIGATED. Each ticket stores its own pricing snapshot
 *   (originalPrice, discountApplied, pricePaid) at purchase time, so even if a race
 *   occurs the per-ticket amounts remain internally self-consistent.
 *   DEFERRED to M8 session (DiscountServiceImpl).
 *
 * FIX-H04 (BUG-TT-E) — QR generation side effects inside transaction loop — DEFERRED.
 *   QrCodeService internals not provided in this session. Cannot safely change
 *   generateQrCode() call behavior without knowing what external side effects it has.
 *   If it writes files or calls external APIs, those are not rolled back on transaction
 *   failure. Logged as BUG-TT-E DEFERRED. Requires QrCodeService review in M6 session.
 *   NO CODE CHANGE in this file.
 *
 * FIX-H05 (BUG-TT-F) — Email sent synchronously inside @Transactional.
 *   BEFORE: emailService.sendTicketConfirmationEmail() called inside the
 *   @Transactional boundary. If email threw an unchecked exception, the DB
 *   transaction could roll back, losing the purchase. Also blocked the HTTP
 *   response until email completed. Inconsistent with EventServiceImpl which
 *   uses @Async for cancellation emails (FIX-E4).
 *   AFTER:  Email registered via TransactionSynchronizationManager.afterCommit().
 *   Email sends only after the DB transaction commits successfully. Email
 *   failure never rolls back the purchase. String/UUID values are captured
 *   before the callback to avoid accessing detached entities after commit.
 *
 * FIX-H06 (BUG-TT-G) — No price validation in createTicketType().
 *   BEFORE: null or negative price reached the DB constraint (NOT NULL, precision 10,2)
 *   with no business-level error message — caller received an opaque constraint
 *   violation or DataIntegrityViolationException.
 *   AFTER:  Explicit null + negative guard throws InvalidBusinessStateException
 *   with a clear message before any DB call is made.
 *
 * FIX-H07 (BUG-TT-H) — listTicketTypesForEvent() returned live Hibernate collection.
 *   BEFORE: returned event.getTicketTypes() directly — a live Hibernate PersistentBag.
 *   Serializing or iterating it after the @Transactional(readOnly=true) boundary
 *   closes throws LazyInitializationException (when OEMIV is disabled, which is the
 *   recommended setting for production and the default in Spring Boot 2.7+).
 *   AFTER:  returns new ArrayList<>(event.getTicketTypes()) — a detached snapshot
 *   loaded within the transaction, safe to use after the transaction closes.
 *
 * FIX-H08 (BUG-TT-I) — updateTicketType() capacity check race + opaque 500 on version conflict.
 *   BEFORE: findByIdAndEventId (no lock). Between countActiveByTicketTypeId and save,
 *   a concurrent purchase could sell tickets that violate the new totalAvailable.
 *   @Version mismatch surfaced as unhandled ObjectOptimisticLockingFailureException → 500.
 *   AFTER:  findByIdAndEventIdWithLock() acquires the same row lock that purchaseTickets()
 *   holds via findByIdWithLock(), serializing the two operations. ObjectOptimisticLocking-
 *   FailureException is caught and rethrown as a clear InvalidBusinessStateException.
 *   REQUIRES: TicketTypeRepository.findByIdAndEventIdWithLock() — see snippet file.
 *
 * FIX-H09 (BUG-TT-J) — updateTicketType() missing event status guard.
 *   BEFORE: organizer could PUT /ticket-types/{id} on a CANCELLED or COMPLETED event,
 *   renaming or repricing ticket types that are no longer active. Inconsistent with
 *   createTicketType() which already blocked this via SALES_ACTIVE_STATUSES.
 *   AFTER:  same SALES_ACTIVE_STATUSES guard added to updateTicketType().
 *   Implemented as part of FIX-H08 in updateTicketType().
 *
 * FIX-H10 (BUG-TT-K) — deleteTicketType() race could silently delete paid tickets.
 *   BEFORE: countActiveByTicketTypeId returned 0 → concurrent purchase created a ticket
 *   in the gap → CascadeType.ALL on TicketType.tickets cascaded the delete, silently
 *   wiping a paid ticket from the DB. @Version on TicketType did NOT protect this
 *   because ticket creation saves via ticketRepository, not via the TicketType entity,
 *   so TicketType's @Version is not bumped by a purchase.
 *   AFTER:  findByIdAndEventIdWithLock() acquires the same row lock that purchaseTickets()
 *   holds. Concurrent purchase blocks until this delete transaction completes. If the
 *   purchase commits first, this transaction reloads — the count is now > 0 and the
 *   TicketTypeDeleteNotAllowedException fires correctly.
 *   REQUIRES: TicketTypeRepository.findByIdAndEventIdWithLock() — see snippet file.
 */
@Service
@RequiredArgsConstructor
@Slf4j
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

    /** Events in which ticket sales and ticket type modifications are meaningful. */
    private static final Set<EventStatusEnum> SALES_ACTIVE_STATUSES =
            Set.of(EventStatusEnum.DRAFT, EventStatusEnum.PUBLISHED);

    private static final int MAX_TICKETS_PER_USER_PER_TYPE = 10;

    // ── PUBLIC PURCHASE (only safe overload — FIX-TT3) ───────────────────────

    /**
     * The ONLY public purchase entry point.
     * Verifies the ticket type belongs to the given event, then delegates to doPurchase().
     */
    @Override
    @Transactional
    public List<Ticket> purchaseTickets(UUID userId, UUID eventId, UUID ticketTypeId, int quantity) {
        if (quantity < 1 || quantity > 10) {
            throw new InvalidBusinessStateException(
                    "Quantity must be between 1 and 10. Cannot purchase " + quantity + " tickets");
        }

        String clientIp  = extractClientIp(getCurrentRequest());
        String userAgent = extractUserAgent(getCurrentRequest());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    auditPurchaseFailure(userId, null, "USER_NOT_FOUND", clientIp, userAgent);
                    return new UserNotFoundException(
                            String.format("User with ID %s was not found", userId));
                });

        // FIX-H01 (BUG-TT-A): Pessimistic lock on event — prevents event-level oversell.
        // Two concurrent purchases previously both passed maxCapacity before either committed.
        // Now serialized: the second purchase waits until the first commits and then
        // re-reads the updated count. findByIdWithLock must be added to EventRepository.
        Event event = eventRepository.findByIdWithLock(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        // Ticket type is locked here (existing lock — prevents per-type oversell).
        TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
                .orElseThrow(() -> {
                    auditPurchaseFailure(userId, null, "TICKET_TYPE_NOT_FOUND", clientIp, userAgent);
                    return new TicketTypeNotFoundException(
                            String.format("Ticket type with ID %s was not found", ticketTypeId));
                });

        // FIX-H02 (BUG-TT-B): ticketType.getEvent().getId() on a @ManyToOne(LAZY) proxy.
        // Safe on Hibernate 6 (Spring Boot 3.x) — getId() resolves from FK column without
        // proxy initialization. No code change needed, documented here for clarity.
        if (!ticketType.getEvent().getId().equals(eventId)) {
            auditPurchaseFailure(userId, event, "CROSS_EVENT_PURCHASE_ATTEMPT", clientIp, userAgent);
            throw new InvalidBusinessStateException(
                    "Ticket type does not belong to the specified event.");
        }

        return doPurchase(user, event, ticketType, quantity, clientIp, userAgent);
    }

    // ── PRIVATE PURCHASE CORE ─────────────────────────────────────────────────

    private List<Ticket> doPurchase(User user, Event event, TicketType ticketType,
                                    int quantity, String clientIp, String userAgent) {
        UUID userId       = user.getId();
        UUID ticketTypeId = ticketType.getId();

        // Event status check
        if (!EventStatusEnum.PUBLISHED.equals(event.getStatus())) {
            String reason = EventStatusEnum.CANCELLED.equals(event.getStatus())
                    ? "EVENT_CANCELLED" : "EVENT_NOT_PUBLISHED";
            auditPurchaseFailure(userId, event, reason, clientIp, userAgent);
            throw new InvalidBusinessStateException(
                    EventStatusEnum.CANCELLED.equals(event.getStatus())
                            ? "This event has been cancelled."
                            : "Tickets are not available — the event is not open for sales.");
        }

        // Sales window check
        LocalDateTime now = LocalDateTime.now();
        if (event.getSalesStart() != null && now.isBefore(event.getSalesStart())) {
            auditPurchaseFailure(userId, event, "SALES_NOT_STARTED", clientIp, userAgent);
            throw new InvalidBusinessStateException(
                    String.format("Sales have not started yet. Sales open at %s.", event.getSalesStart()));
        }
        if (event.getSalesEnd() != null && now.isAfter(event.getSalesEnd())) {
            auditPurchaseFailure(userId, event, "SALES_CLOSED", clientIp, userAgent);
            throw new InvalidBusinessStateException(
                    String.format("Sales have closed. Sales ended at %s.", event.getSalesEnd()));
        }

        // Per-type capacity check (COUNT query — no entity loading)
        int activeForType = ticketRepository.countActiveByTicketTypeId(
                ticketTypeId, TicketStatusEnum.CANCELLED);
        if (ticketType.getTotalAvailable() != null
                && activeForType + quantity > ticketType.getTotalAvailable()) {
            auditPurchaseFailure(userId, event, "SOLD_OUT_TICKET_TYPE", clientIp, userAgent);
            throw new TicketsSoldOutException();
        }

        // Event-level capacity check (COUNT query — no entity loading).
        // FIX-H01: This check is now protected by the event row lock acquired in purchaseTickets().
        if (event.getMaxCapacity() != null) {
            int totalSold = ticketRepository.countActiveTicketsByEventId(
                    event.getId(), TicketStatusEnum.CANCELLED);
            if (totalSold + quantity > event.getMaxCapacity()) {
                auditPurchaseFailure(userId, event, "SOLD_OUT_EVENT", clientIp, userAgent);
                throw new TicketsSoldOutException(String.format(
                        "Event venue capacity of %d reached. Only %d ticket(s) remaining.",
                        event.getMaxCapacity(), event.getMaxCapacity() - totalSold));
            }
        }

        // Per-user limit check.
        // NOTE: countByTicketTypeIdAndPurchaserId intentionally includes CANCELLED tickets
        // to prevent buy-cancel-rebuy gaming. Edge case of organiser-forced cancellation
        // is handled via support. Documented intentional trade-off.
        int alreadyOwned = ticketRepository.countByTicketTypeIdAndPurchaserId(ticketTypeId, userId);
        if (alreadyOwned + quantity > MAX_TICKETS_PER_USER_PER_TYPE) {
            auditPurchaseFailure(userId, event, "PER_USER_LIMIT_EXCEEDED", clientIp, userAgent);
            throw new InvalidBusinessStateException(String.format(
                    "Purchase limit reached. You already own %d ticket(s) for this ticket type " +
                            "(including any cancelled tickets). Maximum %d per user per ticket type.",
                    alreadyOwned, MAX_TICKETS_PER_USER_PER_TYPE));
        }

        if (authorizationService.isOrganizer(userId, event)) {
            log.warn("Organizer '{}' purchasing {} ticket(s) to own event '{}'",
                    userId, quantity, event.getId());
            emitOrganizerSelfPurchaseAudit(user, event, quantity);
        }

        // Pricing.
        // FIX-H03 (BUG-TT-D) PARTIAL: discount lookup has no lock — TOCTOU risk remains.
        // Concurrent DiscountServiceImpl.updateDiscount() can change the discount between
        // this lookup and the ticket save below. Full fix requires DiscountServiceImpl
        // changes (M8 session). Per-ticket pricing is stored as a snapshot so each ticket
        // remains internally self-consistent regardless.
        BigDecimal basePrice = ticketType.getPrice();
        Optional<Discount> activeDiscount = discountService.findActiveDiscount(ticketTypeId);
        BigDecimal finalPrice;
        BigDecimal discountAmount;
        if (activeDiscount.isPresent()) {
            finalPrice     = discountService.calculateFinalPrice(basePrice, activeDiscount.get());
            discountAmount = basePrice.subtract(finalPrice);
        } else {
            finalPrice     = basePrice;
            discountAmount = BigDecimal.ZERO;
        }

        // Create tickets + QR codes.
        // FIX-H04 (BUG-TT-E) DEFERRED: qrCodeService.generateQrCode() called inside
        // transaction loop. QrCodeService internals not available — cannot change behavior
        // without verifying external side effects. Requires M6 session review.
        List<Ticket> createdTickets = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            Ticket ticket = new Ticket();
            ticket.setStatus(TicketStatusEnum.PURCHASED);
            ticket.setTicketType(ticketType);
            ticket.setPurchaser(user);
            ticket.setOriginalPrice(basePrice);
            ticket.setPricePaid(finalPrice);
            ticket.setDiscountApplied(discountAmount);
            Ticket savedTicket = ticketRepository.save(ticket);
            qrCodeService.generateQrCode(savedTicket);
            createdTickets.add(savedTicket);
        }

        emitTicketPurchasedAudit(user, event, ticketType, quantity);

        // FIX-H05 (BUG-TT-F): Register email for after-commit rather than sending inline.
        // BEFORE: email sent synchronously inside @Transactional — a thrown exception
        // would roll back the purchase. Also blocked HTTP response during SMTP call.
        // AFTER:  email fires only after the DB transaction commits successfully.
        // Email failure is logged but never rolls back the purchase.
        // String/UUID primitives are captured before the callback — entity fields are
        // not accessed after commit to avoid detached-entity issues.
        final String capturedUserEmail      = user.getEmail();
        final String capturedUserName       = user.getName();
        final String capturedEventName      = event.getName();
        final String capturedTicketTypeName = ticketType.getName();
        final int    capturedQuantity       = quantity;
        final UUID   capturedFirstTicketId  = createdTickets.get(0).getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    emailService.sendTicketConfirmationEmail(
                            capturedUserEmail, capturedUserName,
                            capturedEventName, capturedTicketTypeName,
                            capturedQuantity, capturedFirstTicketId);
                } catch (Exception e) {
                    log.error("Ticket confirmation email failed after commit (purchase committed). " +
                                    "userId={}, firstTicketId={}: {}",
                            user.getId(), capturedFirstTicketId, e.getMessage());
                }
            }
        });

        return createdTickets;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * FIX-TT1: Guards CANCELLED/COMPLETED events.
     * FIX-H06: Guards null/negative price before any DB call.
     */
    @Override
    @Transactional
    public TicketType createTicketType(UUID organizerId, UUID eventId, CreateTicketTypeRequest request) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        // FIX-H06 (BUG-TT-G): Service-level price guard.
        // TicketType.price is @Column(nullable=false, precision=10, scale=2).
        // Without this guard, null or negative price hits the DB constraint and surfaces
        // as DataIntegrityViolationException with no business-level message.
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidBusinessStateException(
                    "Ticket type price must be provided and cannot be negative.");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        // FIX-TT1 (carried forward)
        if (!SALES_ACTIVE_STATUSES.contains(event.getStatus())) {
            throw new InvalidBusinessStateException(String.format(
                    "Cannot add ticket types to a %s event. " +
                            "Only DRAFT or PUBLISHED events can have ticket types added.",
                    event.getStatus()));
        }

        TicketType ticketType = new TicketType();
        ticketType.setName(request.getName());
        ticketType.setPrice(request.getPrice());
        ticketType.setDescription(request.getDescription());
        ticketType.setTotalAvailable(request.getTotalAvailable());
        ticketType.setEvent(event);
        return ticketTypeRepository.save(ticketType);
    }

    /**
     * FIX-H07: Returns a detached ArrayList copy instead of the live Hibernate collection.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TicketType> listTicketTypesForEvent(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        // FIX-H07 (BUG-TT-H): Return a copy — not the live PersistentBag.
        // The Hibernate collection is only valid within this transaction. Accessing
        // event.getTicketTypes() after the transaction closes throws LazyInitializationException
        // when OpenEntityManagerInView is disabled (recommended in production).
        return new ArrayList<>(event.getTicketTypes());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TicketType> getTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        return ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId);
    }

    /**
     * FIX-H08 (BUG-TT-I): Locked load — serializes with concurrent purchases.
     * FIX-H09 (BUG-TT-J): Event status guard — consistent with createTicketType().
     */
    @Override
    @Transactional
    public TicketType updateTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId,
                                       UpdateTicketTypeRequest request) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        // FIX-H09 (BUG-TT-J): Guard against updating ticket types on closed events.
        // createTicketType() already blocked this. updateTicketType() was missing the guard,
        // allowing organizers to rename/reprice ticket types on CANCELLED or COMPLETED events.
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        if (!SALES_ACTIVE_STATUSES.contains(event.getStatus())) {
            throw new InvalidBusinessStateException(String.format(
                    "Cannot update ticket types on a %s event. " +
                            "Only DRAFT or PUBLISHED events can have ticket types modified.",
                    event.getStatus()));
        }

        try {
            // FIX-H08 (BUG-TT-I): Use pessimistic lock.
            // purchaseTickets() holds a lock on TicketType via findByIdWithLock().
            // By acquiring the same row lock here, a concurrent purchase is blocked
            // until this update commits, eliminating the race between the capacity
            // check and the save. findByIdAndEventIdWithLock must be added to
            // TicketTypeRepository (see TicketTypeRepository_ADD_METHOD.java).
            TicketType ticketType = ticketTypeRepository.findByIdAndEventIdWithLock(ticketTypeId, eventId)
                    .orElseThrow(() -> new TicketTypeNotFoundException(
                            String.format("Ticket type '%s' not found for event '%s'",
                                    ticketTypeId, eventId)));

            if (request.getTotalAvailable() != null) {
                int activeAlreadySold = ticketRepository.countActiveByTicketTypeId(
                        ticketTypeId, TicketStatusEnum.CANCELLED);
                if (request.getTotalAvailable() < activeAlreadySold) {
                    throw new InvalidBusinessStateException(String.format(
                            "Cannot set totalAvailable to %d — %d active (non-cancelled) " +
                                    "ticket(s) already sold.",
                            request.getTotalAvailable(), activeAlreadySold));
                }
            }

            ticketType.setName(request.getName());
            ticketType.setPrice(request.getPrice());
            ticketType.setDescription(request.getDescription());
            ticketType.setTotalAvailable(request.getTotalAvailable());
            return ticketTypeRepository.save(ticketType);

        } catch (ObjectOptimisticLockingFailureException e) {
            // FIX-H08 cont.: @Version on TicketType is a safety net if the pessimistic
            // lock above somehow does not cover a specific concurrent path. Without this
            // catch, the exception surfaces as an opaque 500. With it, the caller gets
            // a clear message and can retry.
            throw new InvalidBusinessStateException(
                    "This ticket type was concurrently modified. Please reload and retry your update.");
        }
    }

    /**
     * FIX-TT2: COUNT query instead of collection load.
     * FIX-H10 (BUG-TT-K): Locked load — prevents paid ticket cascade-delete race.
     */
    @Override
    @Transactional
    public void deleteTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        // FIX-H10 (BUG-TT-K): Use pessimistic lock.
        // BEFORE: count returned 0 → concurrent purchase created a ticket in the gap →
        // CascadeType.ALL on TicketType.tickets cascaded the delete to the paid ticket.
        // @Version on TicketType did NOT protect this: ticket creation saves via
        // ticketRepository.save(), which does not bump TicketType's @Version field.
        // AFTER: same row lock as purchaseTickets() (findByIdWithLock). A concurrent
        // purchase blocks until this transaction completes. If it committed first,
        // the count below returns > 0 and the delete is correctly rejected.
        // findByIdAndEventIdWithLock must be added to TicketTypeRepository (see snippet).
        TicketType ticketType = ticketTypeRepository.findByIdAndEventIdWithLock(ticketTypeId, eventId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type '%s' not found for event '%s'",
                                ticketTypeId, eventId)));

        // FIX-TT2 (carried forward): COUNT query — zero entity loading.
        int activeTickets = ticketRepository.countActiveByTicketTypeId(
                ticketTypeId, TicketStatusEnum.CANCELLED);
        if (activeTickets > 0) {
            throw new TicketTypeDeleteNotAllowedException(String.format(
                    "Cannot delete ticket type with %d active (non-cancelled) sold ticket(s). " +
                            "Cancel the event first, or set totalAvailable to 0 to stop further sales.",
                    activeTickets));
        }

        ticketTypeRepository.delete(ticketType);
    }

    // ── AUDIT HELPERS ─────────────────────────────────────────────────────────

    private void emitOrganizerSelfPurchaseAudit(User organizer, Event event, int quantity) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.ORGANIZER_SELF_PURCHASE)
                    .actor(organizer).event(event)
                    .resourceType("TICKET").resourceId(event.getId())
                    .details(String.format("quantity=%d,eventName=%s", quantity, event.getName()))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit ORGANIZER_SELF_PURCHASE audit: {}", e.getMessage());
        }
    }

    private void emitTicketPurchasedAudit(User buyer, Event event, TicketType ticketType, int quantity) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.TICKET_PURCHASED)
                    .actor(buyer).targetUser(buyer).event(event)
                    .resourceType("TICKET").resourceId(event.getId())
                    .details(String.format("ticketType=%s,quantity=%d", ticketType.getName(), quantity))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit TICKET_PURCHASED audit: {}", e.getMessage());
        }
    }

    private void auditPurchaseFailure(UUID userId, Event event, String reason,
                                      String clientIp, String userAgent) {
        try {
            User actor = userId != null
                    ? userRepository.findById(userId).orElse(systemUserProvider.getSystemUser())
                    : systemUserProvider.getSystemUser();
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.TICKET_PURCHASE_FAILED)
                    .actor(actor).event(event)
                    .resourceType("TICKET")
                    .details("reason=" + reason)
                    .ipAddress(clientIp != null ? clientIp : "unknown")
                    .userAgent(userAgent != null ? userAgent : "unknown")
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit purchase failure audit: {}", e.getMessage());
        }
    }
}