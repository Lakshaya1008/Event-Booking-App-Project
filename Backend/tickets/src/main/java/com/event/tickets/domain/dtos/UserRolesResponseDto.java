package com.event.tickets.domain.dtos;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user role operations.
 *
 * FIX A-8: userId changed from String to UUID.
 *
 *   BEFORE: userId was String, constructed via userId.toString() in the controller.
 *   Every other ID field in every other DTO across the codebase is UUID. A client
 *   parsing this field as a UUID had to do manual string conversion. Inconsistent
 *   with the rest of the API contract.
 *
 *   AFTER: UUID. Controller updated to pass the UUID directly (no .toString()).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesResponseDto {

    private UUID userId;
    private String userName;
    private String email;
    private List<String> roles;
}