package com.event.tickets.util;

import java.util.UUID;

/** System user constants. */
public final class SystemUser {

    /** The well-known UUID for the SYSTEM audit actor. All-zeros by convention. */
    public static final UUID SYSTEM_USER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private SystemUser() {}
}