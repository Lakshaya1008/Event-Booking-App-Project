package com.event.tickets.mappers;

import com.event.tickets.domain.dtos.TicketValidationResponseDto;
import com.event.tickets.domain.entities.TicketValidation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TicketValidationMapper {

    @Mapping(target = "ticketId",       source = "ticket.id")
    @Mapping(target = "validatedById",  source = "validatedBy.id")
    @Mapping(target = "validatedByName",source = "validatedBy.name")
    @Mapping(target = "validatedAt",    source = "createdAt")
    TicketValidationResponseDto toTicketValidationResponseDto(TicketValidation ticketValidation);
}