package com.event.tickets.exceptions;

public class TicketTypeDeleteNotAllowedException extends EventTicketException {
    public TicketTypeDeleteNotAllowedException(String message) {
        super(message);
    }
}
