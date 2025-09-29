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
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

  CreateTicketTypeRequest fromDto(CreateTicketTypeRequestDto dto);

  CreateEventRequest fromDto(CreateEventRequestDto dto);

  CreateEventResponseDto toDto(Event event);

  ListEventTicketTypeResponseDto toDto(TicketType ticketType);

  ListEventResponseDto toListEventResponseDto(Event event);

  GetEventDetailsTicketTypesResponseDto toGetEventDetailsTicketTypesResponseDto(
      TicketType ticketType);

  GetEventDetailsResponseDto toGetEventDetailsResponseDto(Event event);

  UpdateTicketTypeRequest fromDto(UpdateTicketTypeRequestDto dto);

  UpdateEventRequest fromDto(UpdateEventRequestDto dto);

  UpdateTicketTypeResponseDto toUpdateTicketTypeResponseDto(TicketType ticketType);

  UpdateEventResponseDto toUpdateEventResponseDto(Event event);

  ListPublishedEventResponseDto toListPublishedEventResponseDto(Event event);

  GetPublishedEventDetailsTicketTypesResponseDto toGetPublishedEventDetailsTicketTypesResponseDto(
      TicketType ticketType);

  GetPublishedEventDetailsResponseDto toGetPublishedEventDetailsResponseDto(Event event);
}
