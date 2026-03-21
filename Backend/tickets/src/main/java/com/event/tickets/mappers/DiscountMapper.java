package com.event.tickets.mappers;

import com.event.tickets.domain.dtos.DiscountResponseDto;
import com.event.tickets.domain.entities.Discount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting between Discount entity and DTOs.
 *
 * FIX D-5: createdBy mapping added.
 *   Discount.createdBy (UUID) is now mapped to DiscountResponseDto.createdBy.
 *   MapStruct maps same-name primitive/UUID fields automatically, but the explicit
 *   @Mapping is added here for clarity and to prevent accidental regression.
 */
@Mapper(componentModel = "spring")
public interface DiscountMapper {

    @Mapping(source = "ticketType.id",   target = "ticketTypeId")
    @Mapping(source = "ticketType.name", target = "ticketTypeName")
    @Mapping(source = "createdBy",       target = "createdBy")    // FIX D-5
    DiscountResponseDto toResponseDto(Discount discount);
}