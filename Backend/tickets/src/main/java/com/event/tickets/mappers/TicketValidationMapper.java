package com.event.tickets.mappers;

import com.event.tickets.domain.dtos.TicketValidationResponseDto;
import com.event.tickets.domain.entities.TicketValidation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * H-04 FIX: validatedById, validatedByName, validatedAt now mapped.
 *
 * Previously only ticketId was mapped. The three identity/timestamp fields
 * were declared in TicketValidationResponseDto but never populated — every
 * scan response returned null for who scanned the ticket and when.
 *
 * Mappings added:
 *   - validatedById   ← validatedBy.id   (UUID of the staff/organizer who scanned)
 *   - validatedByName ← validatedBy.name (display name for frontend without extra call)
 *   - validatedAt     ← createdAt        (when the validation record was created)
 *
 * validatedBy is nullable (legacy rows pre-fix), so MapStruct correctly emits
 * null-safe property access — the generated code checks validatedBy != null.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TicketValidationMapper {

    @Mapping(target = "ticketId",       source = "ticket.id")
    @Mapping(target = "validatedById",  source = "validatedBy.id")
    @Mapping(target = "validatedByName",source = "validatedBy.name")
    @Mapping(target = "validatedAt",    source = "createdAt")
    TicketValidationResponseDto toTicketValidationResponseDto(TicketValidation ticketValidation);
}