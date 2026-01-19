package com.event.tickets.mappers;

import com.event.tickets.domain.dtos.DiscountResponseDto;
import com.event.tickets.domain.entities.Discount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting between Discount entity and DTOs.
 */
@Mapper(componentModel = "spring")
public interface DiscountMapper {

  /**
   * Converts Discount entity to response DTO.
   *
   * @param discount Discount entity
   * @return Response DTO
   */
  @Mapping(source = "ticketType.id", target = "ticketTypeId")
  @Mapping(source = "ticketType.name", target = "ticketTypeName")
  DiscountResponseDto toResponseDto(Discount discount);
}
