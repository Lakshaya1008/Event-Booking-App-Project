package com.event.tickets.mappers;

import com.event.tickets.domain.CreateTicketTypeRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.dtos.CreateTicketTypeRequestDto;
import com.event.tickets.domain.dtos.CreateTicketTypeResponseDto;
import com.event.tickets.domain.dtos.UpdateTicketTypeRequestDto;
import com.event.tickets.domain.dtos.UpdateTicketTypeResponseDto;
import com.event.tickets.domain.entities.TicketType;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TicketTypeMapper {

  TicketTypeMapper INSTANCE = Mappers.getMapper(TicketTypeMapper.class);

  // Request DTO to Domain mappings
  CreateTicketTypeRequest fromDto(CreateTicketTypeRequestDto dto);
  UpdateTicketTypeRequest fromUpdateDto(UpdateTicketTypeRequestDto dto);

  // Entity to Response DTO mappings
  CreateTicketTypeResponseDto toCreateResponseDto(TicketType ticketType);
  UpdateTicketTypeResponseDto toUpdateResponseDto(TicketType ticketType);
}
