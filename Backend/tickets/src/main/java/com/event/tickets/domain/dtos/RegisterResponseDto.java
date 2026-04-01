package com.event.tickets.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponseDto {

    /** Success message describing what the user should do next. */
    private String message;

    /** The normalized (lowercase) email the account was created with. */
    private String email;

    private boolean requiresApproval;

    private String assignedRole;

    /** Human-readable instructions for what the user should do next. */
    private String instructions;
}