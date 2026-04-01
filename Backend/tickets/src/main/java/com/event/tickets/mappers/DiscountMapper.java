package com.event.tickets.mappers;

import com.event.tickets.domain.dtos.DiscountResponseDto;
import com.event.tickets.domain.entities.Discount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DiscountMapper {

    @Mapping(source = "ticketType.id",   target = "ticketTypeId")
    @Mapping(source = "ticketType.name", target = "ticketTypeName")
    @Mapping(source = "createdBy",       target = "createdBy")
    DiscountResponseDto toResponseDto(Discount discount);
}