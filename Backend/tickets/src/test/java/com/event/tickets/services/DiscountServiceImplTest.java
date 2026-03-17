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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscountServiceImpl")
class DiscountServiceImplTest {

    @Mock private DiscountRepository discountRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks
    private DiscountServiceImpl service;

    private UUID organizerId;
    private UUID eventId;
    private UUID ticketTypeId;
    private TicketType ticketType;
    private Event event;

    @BeforeEach
    void setUp() {
        organizerId  = UUID.randomUUID();
        eventId      = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();

        event = new Event();
        event.setId(eventId);

        ticketType = new TicketType();
        ticketType.setId(ticketTypeId);
        ticketType.setPrice(new BigDecimal("100.00"));
        ticketType.setEvent(event);
    }

    private CreateDiscountRequestDto buildRequest(boolean active) {
        CreateDiscountRequestDto req = new CreateDiscountRequestDto();
        req.setDiscountType(DiscountType.PERCENTAGE);
        req.setValue(new BigDecimal("10"));
        req.setValidFrom(LocalDateTime.now().minusDays(1));
        req.setValidTo(LocalDateTime.now().plusDays(30));
        req.setActive(active);
        return req;
    }

    // ── createDiscount ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createDiscount — FIX #6")
    class CreateDiscount {

        @Test
        @DisplayName("FIX #6 — allows creating new discount when existing one is expired (active flag still true)")
        void allowsNewDiscountWhenExistingIsExpired() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));

            // FIX #6: existsActiveDiscountForTicketType now receives :now param
            // Returns false because the existing discount is expired (validTo in past)
            when(discountRepository.existsActiveDiscountForTicketType(eq(ticketTypeId), any(LocalDateTime.class)))
                    .thenReturn(false);
            when(discountRepository.save(any())).thenAnswer(inv -> {
                Discount d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                d.setCreatedAt(LocalDateTime.now());
                return d;
            });

            CreateDiscountRequestDto req = buildRequest(true);

            assertThatCode(() ->
                    service.createDiscount(organizerId, eventId, ticketTypeId, req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws DiscountAlreadyExistsException when a truly active discount exists")
        void throwsWhenActiveDiscountExists() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(discountRepository.existsActiveDiscountForTicketType(eq(ticketTypeId), any(LocalDateTime.class)))
                    .thenReturn(true);

            CreateDiscountRequestDto req = buildRequest(true);

            assertThatThrownBy(() ->
                    service.createDiscount(organizerId, eventId, ticketTypeId, req))
                    .isInstanceOf(DiscountAlreadyExistsException.class)
                    .hasMessageContaining("active discount already exists");
        }

        @Test
        @DisplayName("allows creating inactive discount even when active one exists")
        void allowsInactiveDiscountWhenActiveExists() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(discountRepository.save(any())).thenAnswer(inv -> {
                Discount d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                d.setCreatedAt(LocalDateTime.now());
                return d;
            });

            // active=false — skip the active discount check entirely
            CreateDiscountRequestDto req = buildRequest(false);

            assertThatCode(() ->
                    service.createDiscount(organizerId, eventId, ticketTypeId, req))
                    .doesNotThrowAnyException();

            // existsActiveDiscountForTicketType must NOT be called when active=false
            verify(discountRepository, never()).existsActiveDiscountForTicketType(any(), any());
        }

        @Test
        @DisplayName("passes LocalDateTime.now() to existsActiveDiscountForTicketType")
        void passesNowToRepository() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(discountRepository.existsActiveDiscountForTicketType(any(), any()))
                    .thenReturn(false);
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
            verify(discountRepository).existsActiveDiscountForTicketType(
                    eq(ticketTypeId), nowCaptor.capture());

            LocalDateTime passedNow = nowCaptor.getValue();
            assertThat(passedNow).isAfter(before).isBefore(after);
        }

        @Test
        @DisplayName("throws when validTo is before validFrom")
        void throwsWhenInvalidDateRange() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));

            CreateDiscountRequestDto req = buildRequest(true);
            req.setValidFrom(LocalDateTime.now().plusDays(10));
            req.setValidTo(LocalDateTime.now().plusDays(1)); // validTo before validFrom

            assertThatThrownBy(() ->
                    service.createDiscount(organizerId, eventId, ticketTypeId, req))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("Valid to date must be after");
        }

        @Test
        @DisplayName("throws when PERCENTAGE discount value exceeds 100")
        void throwsWhenPercentageOver100() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));

            CreateDiscountRequestDto req = buildRequest(true);
            req.setDiscountType(DiscountType.PERCENTAGE);
            req.setValue(new BigDecimal("150")); // invalid

            assertThatThrownBy(() ->
                    service.createDiscount(organizerId, eventId, ticketTypeId, req))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("between 0 and 100");
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