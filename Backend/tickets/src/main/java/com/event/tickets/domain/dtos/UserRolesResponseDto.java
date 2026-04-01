package com.event.tickets.domain.dtos;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesResponseDto {

    private UUID userId;
    private String userName;
    private String email;
    private List<String> roles;
}