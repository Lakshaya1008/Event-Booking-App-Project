package com.event.tickets.mappers;

import com.event.tickets.domain.CreateEventRequest;
import com.event.tickets.domain.CreateTicketTypeRequest;
import com.event.tickets.domain.UpdateEventRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.dtos.CreateEventRequestDto;
import com.event.tickets.domain.dtos.CreateEventResponseDto;
import com.event.tickets.domain.dtos.CreateTicketTypeRequestDto;
import com.event.tickets.domain.dtos.GetEventDetailsResponseDto;
import com.event.tickets.domain.dtos.GetEventDetailsTicketTypesResponseDto;
import com.event.tickets.domain.dtos.GetPublishedEventDetailsResponseDto;
import com.event.tickets.domain.dtos.GetPublishedEventDetailsTicketTypesResponseDto;
import com.event.tickets.domain.dtos.ListEventResponseDto;
import com.event.tickets.domain.dtos.ListEventTicketTypeResponseDto;
import com.event.tickets.domain.dtos.ListPublishedEventResponseDto;
import com.event.tickets.domain.dtos.UpdateEventRequestDto;
import com.event.tickets.domain.dtos.UpdateEventResponseDto;
import com.event.tickets.domain.dtos.UpdateTicketTypeRequestDto;
import com.event.tickets.domain.dtos.UpdateTicketTypeResponseDto;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.TicketType;
import java.time.LocalDateTime;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * FIXES APPLIED:
 *
 * FIX-E7-MAP — Added salesOpen computed flag population for public event DTOs.
 *   MapStruct generates the field mappings; @AfterMapping hooks compute salesOpen
 *   based on the current time vs salesStart/salesEnd window.
 *
 *   salesOpen = true when:
 *     - salesStart is null OR salesStart is in the past, AND
 *     - salesEnd is null OR salesEnd is in the future
 *
 *   FIX-E1-MAP — CreateEventRequestDto no longer has a status field.
 *   The fromDto(CreateEventRequestDto) mapping now ignores status entirely
 *   since EventServiceImpl always forces DRAFT on creation.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

    CreateTicketTypeRequest fromDto(CreateTicketTypeRequestDto dto);

    // FIX-E1-MAP: status ignored — service always sets DRAFT
    @Mapping(target = "status", ignore = true)
    CreateEventRequest fromDto(CreateEventRequestDto dto);

    CreateEventResponseDto toDto(Event event);

    ListEventTicketTypeResponseDto toDto(TicketType ticketType);

    ListEventResponseDto toListEventResponseDto(Event event);

    GetEventDetailsTicketTypesResponseDto toGetEventDetailsTicketTypesResponseDto(TicketType ticketType);

    GetEventDetailsResponseDto toGetEventDetailsResponseDto(Event event);

    UpdateTicketTypeRequest fromDto(UpdateTicketTypeRequestDto dto);

    UpdateEventRequest fromDto(UpdateEventRequestDto dto);

    UpdateTicketTypeResponseDto toUpdateTicketTypeResponseDto(TicketType ticketType);

    UpdateEventResponseDto toUpdateEventResponseDto(Event event);

    // FIX-E7-MAP: maps salesStart, salesEnd, status via field name matching
    ListPublishedEventResponseDto toListPublishedEventResponseDto(Event event);

    GetPublishedEventDetailsTicketTypesResponseDto toGetPublishedEventDetailsTicketTypesResponseDto(
            TicketType ticketType);

    // FIX-E7-MAP: maps salesStart, salesEnd, status via field name matching
    GetPublishedEventDetailsResponseDto toGetPublishedEventDetailsResponseDto(Event event);

    /**
     * FIX-E7-MAP: Computes salesOpen after MapStruct maps the other fields.
     * salesOpen = salesStart has passed (or is null) AND salesEnd is in the future (or is null).
     */
    @AfterMapping
    default void computeSalesOpenForList(Event event,
                                         @MappingTarget ListPublishedEventResponseDto dto) {
        dto.setSalesOpen(isSalesOpen(event.getSalesStart(), event.getSalesEnd()));
    }

    @AfterMapping
    default void computeSalesOpenForDetail(Event event,
                                           @MappingTarget GetPublishedEventDetailsResponseDto dto) {
        dto.setSalesOpen(isSalesOpen(event.getSalesStart(), event.getSalesEnd()));
    }

    private boolean isSalesOpen(LocalDateTime salesStart, LocalDateTime salesEnd) {
        LocalDateTime now = LocalDateTime.now();
        boolean started = salesStart == null || !now.isBefore(salesStart);
        boolean notEnded = salesEnd == null || now.isBefore(salesEnd);
        return started && notEnded;
    }
}