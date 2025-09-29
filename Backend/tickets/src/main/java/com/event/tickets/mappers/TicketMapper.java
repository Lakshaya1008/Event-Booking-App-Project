package com.event.tickets.mappers;

import com.event.tickets.domain.dtos.GetTicketResponseDto;
import com.event.tickets.domain.dtos.ListTicketResponseDto;
import com.event.tickets.domain.dtos.ListTicketTicketTypeResponseDto;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TicketMapper {

  ListTicketTicketTypeResponseDto toListTicketTicketTypeResponseDto(TicketType ticketType);

  ListTicketResponseDto toListTicketResponseDto(Ticket ticket);

  @Mapping(target = "price", source = "ticket.ticketType.price")
  @Mapping(target = "description", source = "ticket.ticketType.description")
  @Mapping(target = "eventName", source = "ticket.ticketType.event.name")
  @Mapping(target = "eventVenue", source = "ticket.ticketType.event.venue")
  @Mapping(target = "eventStart", source = "ticket.ticketType.event.start")
  @Mapping(target = "eventEnd", source = "ticket.ticketType.event.end")
  GetTicketResponseDto toGetTicketResponseDto(Ticket ticket);

}
