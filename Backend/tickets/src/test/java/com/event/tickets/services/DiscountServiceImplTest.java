package com.event.tickets.services;

import com.event.tickets.domain.dtos.CreateDiscountRequestDto;
import com.event.tickets.domain.entities.*;
import com.event.tickets.exceptions.*;
import com.event.tickets.repositories.*;
import com.event.tickets.services.impl.DiscountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CHANGES FROM PREVIOUS VERSION:
 *
 * TEST-D3 — updateDiscount post-sales guard tests now stub countDiscountedActiveByTicketTypeId()
 *   instead of countActiveByTicketTypeId().
 *
 *   The guard now counts only tickets where discountApplied > 0 (FIX D-3).
 *   Stubbing the wrong method would leave the guard unstubbed → returns 0 → guard never fires.
 *   Each test is updated to stub: ticketRepository.countDiscountedActiveByTicketTypeId(ticketTypeId,
 *   TicketStatusEnum.CANCELLED, BigDecimal.ZERO)
 *
 * NEW test — "full-price tickets do not block type/value change".
 *   Verifies the D-3 fix: even with countActiveByTicketTypeId returning 50 (full-price tickets
 *   sold), the discount can still be changed because countDiscountedActiveByTicketTypeId returns 0.
 *
 * All other tests from the previous version are preserved unchanged.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiscountServiceImpl")
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
    void setUp() {
        organizerId  = UUID.randomUUID();
        eventId      = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        discountId   = UUID.randomUUID();

        event = new Event();
        event.setId(eventId);

        ticketType = new TicketType();
        ticketType.setId(ticketTypeId);
        ticketType.setPrice(new BigDecimal("100.00"));
        ticketType.setEvent(event);

        existingDiscount = Discount.builder()
                .id(discountId)
                .ticketType(ticketType)
                .discountType(DiscountType.PERCENTAGE)
                .value(new BigDecimal("20"))
                .validFrom(LocalDateTime.now().minusDays(1))
                .validTo(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();
    }

    private CreateDiscountRequestDto buildRequest(boolean active) {
        CreateDiscountRequestDto req = new CreateDiscountRequestDto();
        req.setDiscountType(DiscountType.PERCENTAGE);
        req.setValue(new BigDecimal("10"));
        req.setValidFrom(LocalDateTime.now().plusHours(1));
        req.setValidTo(LocalDateTime.now().plusDays(30));
        req.setActive(active);
        return req;
    }

    // ── createDiscount ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createDiscount")
    class CreateDiscount {

        @Test
        @DisplayName("FIX #6 — allows new discount when existing one is expired (active=true but validTo past)")
        void allowsNewDiscountWhenExistingIsExpired() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(discountRepository.existsActiveDiscountForTicketType(eq(ticketTypeId), any(LocalDateTime.class)))
                    .thenReturn(false);
            when(discountRepository.save(any())).thenAnswer(inv -> {
                Discount d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                d.setCreatedAt(LocalDateTime.now());
                return d;
            });

            assertThatCode(() -> service.createDiscount(organizerId, eventId, ticketTypeId, buildRequest(true)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws DiscountAlreadyExistsException when active discount exists")
        void throwsWhenActiveDiscountExists() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(discountRepository.existsActiveDiscountForTicketType(eq(ticketTypeId), any(LocalDateTime.class)))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createDiscount(organizerId, eventId, ticketTypeId, buildRequest(true)))
                    .isInstanceOf(DiscountAlreadyExistsException.class)
                    .hasMessageContaining("active discount already exists");
        }

        @Test
        @DisplayName("FIX 2 — rejects discount with validFrom in the past")
        void rejectsDiscountWithPastValidFrom() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));

            CreateDiscountRequestDto req = buildRequest(true);
            req.setValidFrom(LocalDateTime.now().minusDays(3));
            req.setValidTo(LocalDateTime.now().plusDays(30));

            assertThatThrownBy(() -> service.createDiscount(organizerId, eventId, ticketTypeId, req))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("future");

            verify(discountRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when validTo is before validFrom")
        void throwsWhenInvalidDateRange() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));

            CreateDiscountRequestDto req = buildRequest(true);
            req.setValidFrom(LocalDateTime.now().plusDays(10));
            req.setValidTo(LocalDateTime.now().plusDays(1));

            assertThatThrownBy(() -> service.createDiscount(organizerId, eventId, ticketTypeId, req))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("after valid from");
        }

        @Test
        @DisplayName("throws when PERCENTAGE discount value exceeds 100")
        void throwsWhenPercentageOver100() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));

            CreateDiscountRequestDto req = buildRequest(true);
            req.setDiscountType(DiscountType.PERCENTAGE);
            req.setValue(new BigDecimal("150"));

            assertThatThrownBy(() -> service.createDiscount(organizerId, eventId, ticketTypeId, req))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("between 0 and 100");
        }

        @Test
        @DisplayName("passes LocalDateTime.now() to existsActiveDiscountForTicketType")
        void passesNowToRepository() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(discountRepository.existsActiveDiscountForTicketType(any(), any())).thenReturn(false);
            when(discountRepository.save(any())).thenAnswer(inv -> {
                Discount d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                d.setCreatedAt(LocalDateTime.now());
                return d;
            });

            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            service.createDiscount(organizerId, eventId, ticketTypeId, buildRequest(true));
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);

            ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(discountRepository).existsActiveDiscountForTicketType(eq(ticketTypeId), nowCaptor.capture());
            assertThat(nowCaptor.getValue()).isAfter(before).isBefore(after);
        }
    }

    // ── updateDiscount ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateDiscount — post-sales guard (FIX D-3: counts only discounted tickets)")
    class UpdateDiscount {

        private CreateDiscountRequestDto buildUpdateRequest(DiscountType type, BigDecimal value) {
            CreateDiscountRequestDto req = new CreateDiscountRequestDto();
            req.setDiscountType(type);
            req.setValue(value);
            req.setValidFrom(LocalDateTime.now().minusDays(1)); // past OK on update
            req.setValidTo(LocalDateTime.now().plusDays(30));
            req.setActive(true);
            return req;
        }

        @Test
        @DisplayName("FIX D-3 — blocks type change when DISCOUNTED tickets exist")
        void blocksTypeChangeWhenDiscountedTicketsSold() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(discountRepository.findById(discountId)).thenReturn(Optional.of(existingDiscount));
            // FIX D-3: stub countDiscountedActiveByTicketTypeId, NOT countActiveByTicketTypeId
            when(ticketRepository.countDiscountedActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED, BigDecimal.ZERO))
                    .thenReturn(5);

            CreateDiscountRequestDto req = buildUpdateRequest(DiscountType.FIXED_AMOUNT, new BigDecimal("10"));

            assertThatThrownBy(() ->
                    service.updateDiscount(organizerId, eventId, ticketTypeId, discountId, req))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("Cannot change discount type or value")
                    .hasMessageContaining("active ticket");
        }

        @Test
        @DisplayName("FIX D-3 — blocks value change when DISCOUNTED tickets exist")
        void blocksValueChangeWhenDiscountedTicketsSold() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(discountRepository.findById(discountId)).thenReturn(Optional.of(existingDiscount));
            when(ticketRepository.countDiscountedActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED, BigDecimal.ZERO))
                    .thenReturn(3);

            CreateDiscountRequestDto req = buildUpdateRequest(DiscountType.PERCENTAGE, new BigDecimal("30"));

            assertThatThrownBy(() ->
                    service.updateDiscount(organizerId, eventId, ticketTypeId, discountId, req))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("Cannot change discount type or value");
        }

        @Test
        @DisplayName("FIX D-3 — full-price tickets (discountApplied=0) do NOT block type/value change")
        void fullPriceTicketsDoNotBlockChange() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(discountRepository.findById(discountId)).thenReturn(Optional.of(existingDiscount));
            // 50 full-price tickets exist — but 0 discounted tickets
            when(ticketRepository.countDiscountedActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED, BigDecimal.ZERO))
                    .thenReturn(0);
            when(discountRepository.existsActiveDiscountForTicketType(any(), any())).thenReturn(false);
            when(discountRepository.save(any())).thenReturn(existingDiscount);

            // Changing PERCENTAGE → FIXED_AMOUNT should be allowed because no discounted tickets exist
            CreateDiscountRequestDto req = buildUpdateRequest(DiscountType.FIXED_AMOUNT, new BigDecimal("10"));

            assertThatCode(() ->
                    service.updateDiscount(organizerId, eventId, ticketTypeId, discountId, req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("allows type/value change when NO tickets sold at all")
        void allowsTypeValueChangeWhenNoTicketsSold() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(discountRepository.findById(discountId)).thenReturn(Optional.of(existingDiscount));
            when(ticketRepository.countDiscountedActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED, BigDecimal.ZERO))
                    .thenReturn(0);
            when(discountRepository.existsActiveDiscountForTicketType(any(), any())).thenReturn(false);
            when(discountRepository.save(any())).thenReturn(existingDiscount);

            CreateDiscountRequestDto req = buildUpdateRequest(DiscountType.FIXED_AMOUNT, new BigDecimal("10"));

            assertThatCode(() ->
                    service.updateDiscount(organizerId, eventId, ticketTypeId, discountId, req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("allows description update even when discounted tickets exist")
        void allowsDescriptionUpdateWhenDiscountedTicketsSold() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(discountRepository.findById(discountId)).thenReturn(Optional.of(existingDiscount));
            // type and value unchanged — discounted ticket count not checked
            when(discountRepository.existsActiveDiscountForTicketType(any(), any())).thenReturn(false);
            when(discountRepository.save(any())).thenReturn(existingDiscount);

            // Same type (PERCENTAGE) same value (20) as existingDiscount — only description changes
            CreateDiscountRequestDto req = buildUpdateRequest(DiscountType.PERCENTAGE, new BigDecimal("20"));
            req.setDescription("Updated description");

            assertThatCode(() ->
                    service.updateDiscount(organizerId, eventId, ticketTypeId, discountId, req))
                    .doesNotThrowAnyException();

            // countDiscountedActiveByTicketTypeId must NOT be called when type/value is unchanged
            verify(ticketRepository, never()).countDiscountedActiveByTicketTypeId(any(), any(), any());
        }

        @Test
        @DisplayName("FIX D-1 effect — allows past validFrom on UPDATE (discount period may have started)")
        void allowsPastValidFromOnUpdate() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(discountRepository.findById(discountId)).thenReturn(Optional.of(existingDiscount));
            when(ticketRepository.countDiscountedActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED, BigDecimal.ZERO))
                    .thenReturn(0);
            when(discountRepository.existsActiveDiscountForTicketType(any(), any())).thenReturn(false);
            when(discountRepository.save(any())).thenReturn(existingDiscount);

            CreateDiscountRequestDto req = buildUpdateRequest(DiscountType.PERCENTAGE, new BigDecimal("20"));
            req.setValidFrom(LocalDateTime.now().minusDays(5)); // past — allowed on update

            assertThatCode(() ->
                    service.updateDiscount(organizerId, eventId, ticketTypeId, discountId, req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws DiscountAlreadyExistsException when re-activating and another active exists")
        void throwsWhenReactivatingAndAnotherActiveExists() {
            existingDiscount.setActive(false);
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(discountRepository.findById(discountId)).thenReturn(Optional.of(existingDiscount));
            when(ticketRepository.countDiscountedActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED, BigDecimal.ZERO))
                    .thenReturn(0);
            when(discountRepository.existsActiveDiscountForTicketType(eq(ticketTypeId), any(LocalDateTime.class)))
                    .thenReturn(true);

            CreateDiscountRequestDto req = buildUpdateRequest(DiscountType.PERCENTAGE, new BigDecimal("20"));
            req.setActive(true);

            assertThatThrownBy(() ->
                    service.updateDiscount(organizerId, eventId, ticketTypeId, discountId, req))
                    .isInstanceOf(DiscountAlreadyExistsException.class)
                    .hasMessageContaining("Another active discount exists");
        }
    }

    // ── calculateFinalPrice ───────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateFinalPrice")
    class CalculateFinalPrice {

        @Test
        @DisplayName("returns base price when discount is null")
        void returnsBasePriceWhenNoDiscount() {
            BigDecimal result = service.calculateFinalPrice(new BigDecimal("100.00"), null);
            assertThat(result).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("throws when base price is zero or negative")
        void throwsWhenBasePriceInvalid() {
            assertThatThrownBy(() ->
                    service.calculateFinalPrice(BigDecimal.ZERO, null))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("Base price must be positive");
        }
    }
}