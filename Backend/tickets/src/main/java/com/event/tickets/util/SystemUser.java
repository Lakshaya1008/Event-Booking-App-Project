package com.event.tickets.util;

import java.util.UUID;

/**
 * System User Constants
 *
 * FIXES APPLIED:
 *
 * FIX-SU1 — Single source of truth for the SYSTEM user UUID.
 *   BEFORE: The UUID "00000000-0000-0000-0000-000000000000" was hardcoded in
 *   three separate places:
 *     - SystemUser.java (SYSTEM_USER_UUID)
 *     - SystemUserProvider.java (SYSTEM_USER_ID)
 *     - DatabaseInitializer.java (SYSTEM_USER_ID)
 *   A typo in any one of them would silently break audit logging or cause a startup crash.
 *
 *   AFTER: All three classes reference SystemUser.SYSTEM_USER_UUID.
 *   Change the UUID in one place and it propagates everywhere.
 */
public final class SystemUser {

    /** The well-known UUID for the SYSTEM audit actor. All-zeros by convention. */
    public static final UUID SYSTEM_USER_UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private SystemUser() {}
}