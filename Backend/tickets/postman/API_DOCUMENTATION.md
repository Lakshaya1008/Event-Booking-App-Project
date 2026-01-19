# Event Booking App — Complete API Reference and Postman Guide

This document reflects the current codebase (controllers, DTOs, properties) and shows how to test every API in Postman.

**Last Updated:** January 19, 2026  
**API Version:** v1.1  
**Status:** Production Ready with Export Features  

- Base URL: http://localhost:8081
- API version: v1
- Auth: OAuth2 Bearer JWT (Keycloak)
- Pagination: Spring Pageable (page, size, sort)

## Recent Changes

### v1.1 - Event Update API Fix (January 19, 2026)

**BREAKING FIX**: The `PUT /api/v1/events/{eventId}` endpoint has been corrected:

- ✅ **Before**: Required `id` field in request body (broken - caused validation error)
- ✅ **After**: `id` field is OPTIONAL (eventId comes from URL path parameter)

**Migration Guide**:
- **Recommended**: Remove `id` field from request body
- **Backward Compatible**: If you include `id`, it must match URL `eventId`
- **Will Fail**: Sending a different `id` than URL returns 400 Bad Request

See [Event Management Endpoints](#1-organizer-events--apiv1events) for details.

---

## Contents
- Prerequisites and environment
- Authentication (get a token)
- Endpoint reference (paths, auth, payloads, responses)
- Role-based security requirements
- **User Registration & Authentication** (Current workflow + planned endpoint)
- **FIX:** Event Update API (id field now optional)
- **NEW:** Discount Management APIs
- **NEW:** Admin Governance APIs
- **NEW:** Event Staff Management APIs
- **NEW:** Invite Code APIs
- **NEW:** Audit Log APIs
- **NEW:** QR Code Export APIs (View/Download PNG/PDF)
- **NEW:** Sales Report Export (Excel)
- **NEW:** Approval Gate System
- Postman setup tips
- Troubleshooting

## Prerequisites and environment
- PostgreSQL: localhost:5432, DB: Event_Booking_App_db
- Keycloak: http://localhost:9090 (realm: event-ticket-platform)
- Spring Boot app: runs on port 8081 (server.port=8081)

Optional: docker-compose up to start Postgres, Adminer, and Keycloak:
- DB: postgres:latest on 5432
- Adminer UI: http://localhost:8888
- Keycloak dev: http://localhost:9090

## Authentication (get a token)
- Token endpoint: http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token
- Request (x-www-form-urlencoded):
  - grant_type: password
  - client_id: YOUR_CLIENT_ID
  - username: YOUR_USERNAME
  - password: YOUR_PASSWORD
  - scope: openid profile email
- Use the access_token as Authorization: Bearer <token>

## Role-based Security Requirements
- **ADMIN**: Can manage all user roles, view all audit logs, but CANNOT bypass business rules (event ownership)
- **ORGANIZER**: Can create/manage events, ticket types, manage STAFF for their events, view own event audit logs, export sales reports
- **ATTENDEE**: Can browse events, purchase tickets, view/download their ticket QR codes
- **STAFF**: Can validate tickets ONLY for events they're assigned to, view own audit trail, NO QR export access

**IMPORTANT NOTES:**
- Roles are managed exclusively by Keycloak (no database persistence)
- ADMIN can assign/revoke roles but cannot access other organizers' events or bypass approval gate
- STAFF role alone provides no access - must be assigned to specific events by organizers
- All sensitive operations are audited
- **NEW:** Approval Gate - All users (including those with roles) must be approved by ADMIN before accessing business operations
  - New users are created with PENDING approval status
  - PENDING or REJECTED users receive 403 Forbidden even with valid JWT and roles
  - Approval status is a business gate, separate from Keycloak authentication

## Endpoint Reference

### 0) User Registration & Authentication — /api/v1/auth

#### Current Registration Workflow

**IMPORTANT**: The system currently uses an **invite-code based registration** workflow through Keycloak. There is NO public self-registration endpoint implemented.

**How Users Are Created**:

1. **Admin/Organizer Creates Invite Code**
   ```
   POST /api/v1/invites
   ```
   - ADMIN can create invites for any role (ADMIN, ORGANIZER, ATTENDEE, STAFF)
   - ORGANIZER can create STAFF invites only for their events

2. **User Registers in Keycloak**
   - Users must be created directly in Keycloak Admin Console
   - OR through a future self-registration endpoint (planned but not implemented)
   - User receives credentials from Keycloak

3. **User Redeems Invite Code**
   ```
   POST /api/v1/invites/redeem
   ```
   - User authenticates with Keycloak (gets JWT)
   - Redeems invite code to get assigned role
   - User status set to PENDING (awaiting admin approval)

4. **Admin Approves User**
   ```
   POST /api/v1/admin/approvals/{userId}/approve
   ```
   - Admin reviews and approves the user
   - User can now access business operations

**Planned Registration Endpoint (NOT YET IMPLEMENTED)**:

- **POST /api/v1/auth/register** ⚠️ **PLANNED - NOT IMPLEMENTED**
  - **Status**: Endpoint configured in security but controller NOT implemented
  - **Purpose**: Public self-registration with invite code
  - **Security**: Configured as `permitAll()` in SecurityConfig
  - **Service Interface**: `RegistrationService.java` exists but no implementation
  
  **Planned Request (RegisterRequestDto)**:
  ```json
  {
    "email": "user@example.com",
    "password": "SecurePassword123!",
    "name": "John Doe",
    "inviteCode": "ABC123XYZ"
  }
  ```
  
  **Planned Response (RegisterResponseDto)**:
  ```json
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "name": "John Doe",
    "roleAssigned": "ATTENDEE",
    "approvalStatus": "PENDING",
    "message": "Registration successful. Please wait for admin approval."
  }
  ```
  
  **Planned Transaction Flow**:
  1. Validate invite code (exists, PENDING status, not expired)
  2. Check email not already registered
  3. Create user in Keycloak
  4. Assign role from invite code in Keycloak
  5. Create user record in database (status=PENDING)
  6. If STAFF role: Assign to event
  7. Mark invite code as REDEEMED
  
  **Rollback on Failure**:
  - Delete Keycloak user if created
  - Do NOT mark invite as used
  - Log failure for investigation
  
  **Error Responses**:
  - 400 Bad Request: Invalid invite code, email already in use, validation errors
  - 500 Internal Server Error: Keycloak creation failed, transaction rollback

**Current Workaround**:

Until the registration endpoint is implemented, use this workflow:

1. **Create User in Keycloak Admin Console**
   - Navigate to: http://localhost:9090/admin
   - Realm: event-ticket-platform
   - Users → Add user
   - Set credentials

2. **Get Access Token**
   ```bash
   POST http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token
   Content-Type: application/x-www-form-urlencoded
   
   grant_type=password&
   client_id=event-ticket-platform-app&
   client_secret=YOUR_CLIENT_SECRET&
   username=user@example.com&
   password=userpassword&
   scope=openid profile email
   ```

3. **Redeem Invite Code** (see Section 9: Invite Code System)
   ```bash
   POST /api/v1/invites/redeem
   Authorization: Bearer {access_token}
   
   {
     "code": "ABC123XYZ"
   }
   ```

4. **Admin Approves** (see Section 11: Approval Gate System)
   ```bash
   POST /api/v1/admin/approvals/{userId}/approve
   Authorization: Bearer {admin_token}
   ```

---

### 1) Organizer Events — /api/v1/events 
**Required Role: ORGANIZER**

- **POST /api/v1/events**
  - Creates a new event for the authenticated organizer.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (CreateEventRequestDto):
    ```json
    {
      "name": "Tech Conference 2025",
      "start": "2025-12-15T09:00:00",
      "end": "2025-12-15T18:00:00",
      "venue": "Convention Center",
      "salesStart": "2025-11-01T00:00:00",
      "salesEnd": "2025-12-14T23:59:59",
      "status": "PUBLISHED",
      "ticketTypes": [
        {
          "name": "Early Bird",
          "price": 199.99,
          "description": "Discounted",
          "totalAvailable": 100
        }
      ]
    }
    ```
  - Response 201 (CreateEventResponseDto): id, name, start, end, venue, salesStart, salesEnd, status, ticketTypes[], createdAt, updatedAt

- **PUT /api/v1/events/{eventId}**
  - Updates an event owned by the authenticated organizer.
  - **Source of Truth**: `eventId` from URL path parameter (NOT request body)
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (UpdateEventRequestDto):
    ```json
    {
      "name": "Updated Tech Conference 2025",
      "start": "2025-12-15T09:00:00",
      "end": "2025-12-15T18:00:00",
      "venue": "Updated Convention Center",
      "salesStart": "2025-11-01T00:00:00",
      "salesEnd": "2025-12-14T23:59:59",
      "status": "PUBLISHED",
      "ticketTypes": [
        {
          "id": "existing-ticket-type-uuid",
          "name": "Updated Early Bird",
          "price": 179.99,
          "description": "Updated discount",
          "totalAvailable": 120
        },
        {
          "name": "New VIP Pass",
          "price": 499.99,
          "description": "Premium access",
          "totalAvailable": 50
        }
      ]
    }
    ```
  - **Important Notes**:
    - **DO NOT** include `id` field in request body (eventId from URL is used)
    - If `id` is included in body, it must match the URL `eventId` or request will fail with 400
    - `ticketTypes[].id` is optional: include for updates, omit for new ticket types
    - All fields except `id` are required in the request body
  - Response 200 (UpdateEventResponseDto): Updated event with all fields
  - Error Responses:
    - 400 Bad Request: Missing required fields, validation errors, or ID mismatch
    - 403 Forbidden: Organizer does not own the event
    - 404 Not Found: Event does not exist

- **GET /api/v1/events**
  - Lists events for the authenticated organizer.
  - Headers: Authorization: Bearer {{access_token}}
  - Query (optional): page, size, sort (e.g., sort=start,desc)
  - Response 200: Page<ListEventResponseDto>

- **GET /api/v1/events/{eventId}**
  - Gets event details owned by the authenticated organizer.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 GetEventDetailsResponseDto | 404 Not Found

- **DELETE /api/v1/events/{eventId}**
  - Deletes an event owned by the authenticated organizer.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 204 No Content

- **GET /api/v1/events/{eventId}/sales-dashboard**
  - Gets comprehensive sales analytics for the event.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200: Sales dashboard with revenue, ticket counts, breakdown by ticket type
  ```json
  {
    "eventName": "Tech Conference 2025",
    "totalTicketsSold": 150,
    "totalRevenue": 29998.50,
    "ticketTypeBreakdown": [
      {
        "ticketTypeName": "Early Bird",
        "totalAvailable": 100,
        "sold": 85,
        "remaining": 15,
        "revenue": 16999.15,
        "price": 199.99
      }
    ]
  }
  ```

- **GET /api/v1/events/{eventId}/attendees-report**
  - Gets detailed attendee information for the event.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200: Attendees report with purchase details and validation history
  ```json
  {
    "eventName": "Tech Conference 2025",
    "totalAttendees": 85,
    "attendees": [
      {
        "attendeeName": "John Doe",
        "attendeeEmail": "john@example.com",
        "ticketType": "Early Bird",
        "ticketStatus": "PURCHASED",
        "purchaseDate": "2025-11-15T10:30:00",
        "validationCount": 1
      }
    ]
  }
  ```

- **GET /api/v1/events/{eventId}/sales-report.xlsx**
  - **Export Sales Report** (Excel .xlsx format)
  - Downloads comprehensive sales analytics as Excel spreadsheet
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
    - Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
    - Content-Disposition: attachment; filename="<event>_sales_report_<timestamp>.xlsx"
    - Example filename: summer_festival_sales_report_20260118_143022.xlsx
  - Excel Contents:
    - **Summary Section:**
      - Event name
      - Total tickets sold
      - Total revenue (currency formatted)
      - Generated timestamp
    - **Ticket Type Breakdown Table:**
      - Ticket type name
      - Price (currency formatted)
      - Total available
      - Sold count
      - Remaining count
      - Revenue per type (currency formatted)
  - **DATA SOURCE (CRITICAL):**
    - Reuses `EventService.getSalesDashboard()` for data consistency
    - NO duplicate queries or parallel business logic
    - Same authorization check (organizer must own event)
    - Single source of truth for sales data
  - **IDEMPOTENCY GUARANTEE:**
    - Same event state produces identical Excel content
    - No side effects, read-only operation
    - Safe to download multiple times
  - **ACCESS CONTROL:**
    - ORGANIZER: Must own the event (ownership checked in service)
    - ADMIN: NO bypass (must own event to export)
    - ATTENDEE: NO access (403 Forbidden)
    - STAFF: NO access (403 Forbidden)
  - Audit: Logs SALES_REPORT_EXPORTED action
  - Error Responses:
    - 403 Forbidden: User doesn't own event (even ADMIN)
    - 404 Not Found: Event doesn't exist
    - 401 Unauthorized: Invalid/missing JWT

### 2) Ticket Type Management — /api/v1/events/{eventId}/ticket-types
**Required Role: ORGANIZER**

- **POST /api/v1/events/{eventId}/ticket-types**
  - Creates a new ticket type for the event.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (CreateTicketTypeRequestDto):
    ```json
    {
      "name": "VIP Pass",
      "price": 499.99,
      "description": "Premium access with perks",
      "totalAvailable": 50
    }
    ```
  - Response 201 (CreateTicketTypeResponseDto)

- **GET /api/v1/events/{eventId}/ticket-types**
  - Lists all ticket types for the event.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200: List<CreateTicketTypeResponseDto>

- **GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}**
  - Gets specific ticket type details.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 CreateTicketTypeResponseDto | 404 Not Found

- **PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}**
  - Updates a ticket type.
  - Headers: Authorization: Bearer {{access_token}}
  - Body (UpdateTicketTypeRequestDto): Same as create with id field
  - Response 200 (UpdateTicketTypeResponseDto)

- **DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}**
  - Deletes a ticket type (only if no tickets sold).
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 204 No Content | 400 Bad Request (if tickets exist)

---

### 2A) Discount Management — /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
**Required Role: ORGANIZER**

**Business Rules:**
- Only ONE active discount per ticket type at a time
- Discounts apply at purchase time only (never retroactive)
- Two discount types: PERCENTAGE (0-100%) or FIXED_AMOUNT (currency)
- Discounts are automatically applied during ticket purchase

- **POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts**
  - Creates a new discount for a ticket type.
  - Authorization: ORGANIZER must own the event
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (CreateDiscountRequestDto):
    ```json
    {
      "name": "Early Bird Special",
      "discountType": "PERCENTAGE",
      "value": 20.0,
      "validFrom": "2025-11-01T00:00:00",
      "validTo": "2025-11-30T23:59:59"
    }
    ```
    OR for fixed amount discount:
    ```json
    {
      "name": "Holiday Discount",
      "discountType": "FIXED_AMOUNT",
      "value": 50.00,
      "validFrom": "2025-12-01T00:00:00",
      "validTo": "2025-12-25T23:59:59"
    }
    ```
  - Validation Rules:
    - `name`: Required, max 100 characters
    - `discountType`: Required, must be "PERCENTAGE" or "FIXED_AMOUNT"
    - `value`: Required, positive number
      - For PERCENTAGE: 0-100
      - For FIXED_AMOUNT: must not exceed ticket price
    - `validTo`: Must be after `validFrom`
  - Response 201 (DiscountResponseDto):
    ```json
    {
      "id": "660e8400-e29b-41d4-a716-446655440000",
      "name": "Early Bird Special",
      "discountType": "PERCENTAGE",
      "value": 20.0,
      "validFrom": "2025-11-01T00:00:00",
      "validTo": "2025-11-30T23:59:59",
      "ticketTypeId": "440e8400-e29b-41d4-a716-446655440000",
      "createdAt": "2026-01-19T10:00:00"
    }
    ```
  - Error Responses:
    - 400 Bad Request: Validation errors or another active discount exists
    - 403 Forbidden: User doesn't own the event
    - 404 Not Found: Event or ticket type not found

- **PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}**
  - Updates an existing discount.
  - Authorization: ORGANIZER must own the event
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (CreateDiscountRequestDto): Same as create
  - Response 200 (DiscountResponseDto)

- **DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}**
  - Deletes a discount.
  - Authorization: ORGANIZER must own the event
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 204 No Content

- **GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}**
  - Gets a specific discount.
  - Authorization: ORGANIZER must own the event
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (DiscountResponseDto) | 404 Not Found

- **GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts**
  - Lists all discounts for a ticket type (active and inactive).
  - Authorization: ORGANIZER must own the event
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200: List<DiscountResponseDto>

**Notes:**
- Discounts are automatically applied during ticket purchase based on purchase timestamp
- Only discounts valid at the time of purchase are applied
- Discount information is saved with each ticket for reporting purposes
- Organizers can view discount effectiveness in sales reports

---

### 3) Published Events — /api/v1/published-events
**Required Role: ATTENDEE**

- **GET /api/v1/published-events**
  - Lists published events with optional search.
  - Headers: Authorization: Bearer {{access_token}}
  - Query (optional): q, page, size, sort
  - Response 200: Page<ListPublishedEventResponseDto>

- **GET /api/v1/published-events/{eventId}**
  - Gets published event details.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 GetPublishedEventDetailsResponseDto | 404 Not Found

### 4) Ticket Purchase — /api/v1/events/{eventId}/ticket-types
**Required Role: ATTENDEE**

- **POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets**
  - Purchases tickets for the authenticated user with quantity support.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (PurchaseTicketRequestDto):
    ```json
    {
      "quantity": 3
    }
    ```
    **Note**: 
    - `quantity` is optional and defaults to 1 if not provided
    - Valid range: 1-10 tickets per purchase
    - Each ticket gets its own unique QR code
  - Response 201: List<GetTicketResponseDto> - Array of created tickets
  ```json
  [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "status": "PURCHASED",
      "price": 199.99,
      "description": "Early Bird ticket",
      "eventName": "Tech Conference 2025",
      "eventVenue": "Convention Center",
      "eventStart": "2025-12-15T09:00:00",
      "eventEnd": "2025-12-15T18:00:00"
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "status": "PURCHASED",
      "price": 199.99,
      "description": "Early Bird ticket",
      "eventName": "Tech Conference 2025",
      "eventVenue": "Convention Center",
      "eventStart": "2025-12-15T09:00:00",
      "eventEnd": "2025-12-15T18:00:00"
    }
  ]
  ```
  **Error Responses**:
  - 400 Bad Request: Sold out, invalid quantity, or validation errors
  - 401 Unauthorized: Invalid/missing token
  - 403 Forbidden: User doesn't have ATTENDEE role
  - 404 Not Found: Event or ticket type not found

### 5) User Tickets — /api/v1/tickets
**Required Role: ATTENDEE**

- **GET /api/v1/tickets**
  - Lists tickets for the authenticated user.
  - Headers: Authorization: Bearer {{access_token}}
  - Query (optional): page, size, sort
  - Response 200: Page<ListTicketResponseDto>

- **GET /api/v1/tickets/{ticketId}**
  - Gets ticket details for the authenticated user.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 GetTicketResponseDto | 404 Not Found

- **GET /api/v1/tickets/{ticketId}/qr-codes**
  - **DEPRECATED**: Legacy endpoint, use `/qr-codes/view` instead
  - Returns a PNG image of the ticket QR code.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 image/png | 404 Not Found

### 5A) QR Code Export APIs — /api/v1/tickets/{ticketId}/qr-codes
**Required Role: ATTENDEE or ORGANIZER**

**SECURITY MODEL (CRITICAL):**
- QR codes encode ONLY immutable ticket ID (UUID)
- QR image integrity is NOT a security boundary
- Security is enforced during backend validation at scan time, NOT at download
- QR codes are SAFE to re-download, re-view, or share
- Authorization checked: User must own ticket OR own the event
- STAFF and ADMIN have NO access to QR exports

**IDEMPOTENCY GUARANTEE:**
- Same ticket always generates identical QR code content
- No state changes during export operations
- Safe to download multiple times

**ACCESS CONTROL:**
- ATTENDEE: Own tickets only
- ORGANIZER: Tickets from events they own only
- STAFF: NO access (403 Forbidden)
- ADMIN: NO access (403 Forbidden, no bypass)

- **GET /api/v1/tickets/{ticketId}/qr-codes/view**
  - **View QR Code** (inline display, no download prompt)
  - Returns PNG image for browser/app display
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 image/png
    - Content-Type: image/png
    - Content-Disposition: inline; filename="qr-code.png"
    - Cache-Control: max-age=300, public (5-minute browser cache)
  - Audit: Logs QR_CODE_VIEWED action

- **GET /api/v1/tickets/{ticketId}/qr-codes/png**
  - **Download QR Code** (PNG format, forces download)
  - Returns PNG image with sanitized filename
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 image/png
    - Content-Type: image/png
    - Content-Disposition: attachment; filename="<event>_<type>_<user>_<id>.png"
    - Example filename: summer_festival_vip_john_doe_a1b2c3d4.png
  - Filename sanitization:
    - Lowercase conversion
    - Spaces replaced with underscores
    - Special characters removed
    - Max 50 characters per component
  - Audit: Logs QR_CODE_DOWNLOADED_PNG action

- **GET /api/v1/tickets/{ticketId}/qr-codes/pdf**
  - **Download QR Code** (PDF format with ticket details)
  - Returns PDF document containing QR code and ticket information
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 application/pdf
    - Content-Type: application/pdf
    - Content-Disposition: attachment; filename="<event>_<type>_<user>_<id>.pdf"
  - PDF Contents:
    - Title: "Event Ticket QR Code"
    - Event name
    - Ticket type
    - Ticket holder name
    - Ticket ID
    - QR code image (300x300px)
  - Audit: Logs QR_CODE_DOWNLOADED_PDF action

**QR Code Content (SECURITY CLARIFICATION):**
```
QR Code Data: <ticket-uuid>
Example: "550e8400-e29b-41d4-a716-446655440000"

Why this is secure:
- UUID is immutable (cannot be guessed or forged)
- Backend validates ticket ownership/status at scan time
- Sharing QR does not compromise security (validation fails if ticket already used)
- Multiple downloads of same QR are safe (same UUID, no replay risk)
```

**Error Responses:**
- 403 Forbidden: User doesn't own ticket AND doesn't own event
- 404 Not Found: Ticket doesn't exist
- 401 Unauthorized: Invalid/missing JWT

### 6) Ticket Validation — /api/v1/ticket-validations
**Required Role: STAFF**

- **POST /api/v1/ticket-validations**
  - Validates a ticket (MANUAL or QR_SCAN method).
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (TicketValidationRequestDto):
    ```json
    {
      "id": "<ticket-uuid-for-manual-or-qr-code-uuid-for-qr-scan>",
      "method": "MANUAL"
    }
    ```
    **Note**: For "QR_SCAN" method, use QR code UUID. For "MANUAL" method, use ticket UUID.
  - Response 200 (TicketValidationResponseDto): ticketId, status

- **GET /api/v1/ticket-validations/events/{eventId}**
  - Lists all validations for a specific event (paginated).
  - Headers: Authorization: Bearer {{access_token}}
  - Query (optional): page, size, sort
  - Response 200: Page<TicketValidationResponseDto>

- **GET /api/v1/ticket-validations/tickets/{ticketId}**
  - Gets validation history for a specific ticket.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200: List<TicketValidationResponseDto>

---

### 7) Admin Governance APIs — /api/v1/admin
**Required Role: ADMIN**

**CRITICAL:** ADMIN can manage roles but CANNOT bypass business authorization. ADMINs cannot access events they don't own or validate tickets without proper assignment.

- **POST /api/v1/admin/users/{userId}/roles**
  - Assigns a role to a user via Keycloak Admin API.
  - Backend is the SOLE authority for Keycloak Admin operations.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (AssignRoleRequestDto):
    ```json
    {
      "roleName": "ORGANIZER"
    }
    ```
    **Valid roles:** ADMIN, ORGANIZER, ATTENDEE, STAFF
  - Response 200 (UserRolesResponseDto):
    ```json
    {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "userName": "john.doe",
      "email": "john@example.com",
      "roles": ["ATTENDEE", "ORGANIZER"]
    }
    ```

- **DELETE /api/v1/admin/users/{userId}/roles/{roleName}**
  - Revokes a role from a user.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (UserRolesResponseDto): Updated user roles

- **GET /api/v1/admin/users/{userId}/roles**
  - Gets all roles assigned to a user.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (UserRolesResponseDto)

- **GET /api/v1/admin/roles**
  - Gets all available roles in the system.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (AvailableRolesResponseDto):
    ```json
    {
      "roles": ["ADMIN", "ORGANIZER", "ATTENDEE", "STAFF"],
      "message": "Available roles in the system"
    }
    ```

---

### 8) Event Staff Management — /api/v1/events/{eventId}/staff
**Required Role: ORGANIZER**

Organizers can assign/remove STAFF for their own events. STAFF must first have the STAFF role (assigned by ADMIN), then be assigned to specific events.

- **POST /api/v1/events/{eventId}/staff**
  - Assigns a STAFF member to an event.
  - Prerequisites: User must have STAFF role in Keycloak
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (AssignStaffRequestDto):
    ```json
    {
      "userId": "550e8400-e29b-41d4-a716-446655440000"
    }
    ```
  - Response 201 (EventStaffResponseDto):
    ```json
    {
      "eventId": "660e8400-e29b-41d4-a716-446655440000",
      "eventName": "Tech Conference 2025",
      "staffMembers": [
        {
          "userId": "550e8400-e29b-41d4-a716-446655440000",
          "userName": "john.staff",
          "email": "john@example.com"
        }
      ],
      "totalStaffCount": 1
    }
    ```

- **DELETE /api/v1/events/{eventId}/staff/{userId}**
  - Removes a STAFF member from an event.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (EventStaffResponseDto): Updated staff list

- **GET /api/v1/events/{eventId}/staff**
  - Lists all STAFF members assigned to an event.
  - Headers: Authorization: Bearer {{access_token}}
  - Response 200 (EventStaffResponseDto)

**Staff Assignment Flow:**
1. ADMIN assigns STAFF role to user via `/api/v1/admin/users/{userId}/roles`
2. ORGANIZER assigns staff to their event via `/api/v1/events/{eventId}/staff`
3. STAFF can now validate tickets for that event

---

### 9) Invite Code System — /api/v1/invites
**Required Role: ADMIN or ORGANIZER**

Role-specific, single-use, time-bound invite codes for controlled onboarding.

**CRITICAL RULES:**
- One invite code = one role (cannot be reused for different roles)
- STAFF invites MUST have eventId and are event-scoped
- Other roles (ORGANIZER, ATTENDEE) must NOT have eventId
- ORGANIZER can ONLY create STAFF invites for their own events
- ADMIN can create invites for any role
- Invite codes are single-use with optimistic locking

- **POST /api/v1/invites**
  - Generates a new invite code.
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (GenerateInviteCodeRequestDto):
    ```json
    {
      "roleName": "STAFF",
      "eventId": "660e8400-e29b-41d4-a716-446655440000",
      "expirationHours": 48
    }
    ```
    **Notes:**
    - `roleName`: ORGANIZER, ATTENDEE, or STAFF
    - `eventId`: Required ONLY for STAFF invites, must be null for others
    - `expirationHours`: Hours until code expires (positive integer)
  - Response 201 (InviteCodeResponseDto):
    ```json
    {
      "id": "770e8400-e29b-41d4-a716-446655440000",
      "code": "ABCD-EFGH-IJKL-MNOP",
      "roleName": "STAFF",
      "eventId": "660e8400-e29b-41d4-a716-446655440000",
      "eventName": "Tech Conference 2025",
      "status": "PENDING",
      "createdBy": "admin.user",
      "createdAt": "2026-01-18T10:00:00",
      "expiresAt": "2026-01-20T10:00:00",
      "redeemedBy": null,
      "redeemedAt": null
    }
    ```

- **POST /api/v1/invites/redeem**
  - Redeems an invite code (any authenticated user).
  - Process: Validates code → Assigns role via Keycloak → Creates event-staff assignment (if STAFF) → Marks code USED
  - Headers: Content-Type: application/json; Authorization: Bearer {{access_token}}
  - Body (RedeemInviteCodeRequestDto):
    ```json
    {
      "code": "ABCD-EFGH-IJKL-MNOP"
    }
    ```
  - Response 200 (RedeemInviteCodeResponseDto):
    ```json
    {
      "message": "Invite code redeemed successfully",
      "roleAssigned": "STAFF",
      "eventName": "Tech Conference 2025",
      "currentRoles": ["ATTENDEE", "STAFF"]
    }
    ```
  - Error Responses:
    - 400: Code already redeemed, expired, or revoked
    - 404: Code not found

- **DELETE /api/v1/invites/{codeId}?reason=No%20longer%20needed**
  - Revokes an invite code (before redemption).
  - Authorization: ADMIN or creator only
  - Headers: Authorization: Bearer {{access_token}}
  - Query: reason (optional)
  - Response 204 No Content

- **GET /api/v1/invites**
  - Lists invite codes.
  - ADMIN: sees all codes; ORGANIZER: sees only their own
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<InviteCodeResponseDto>

- **GET /api/v1/invites/events/{eventId}**
  - Lists invite codes for a specific event.
  - Authorization: ADMIN or event organizer
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<InviteCodeResponseDto>

---

### 10) Audit Log APIs — /api/v1/audit
**Read-Only, Immutable Audit Trail**

All sensitive operations are logged: role changes, staff assignments, invite operations, ticket validations.

- **GET /api/v1/audit**
  - Gets all audit logs (ADMIN only).
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<AuditLogDto>
    ```json
    {
      "content": [
        {
          "id": "880e8400-e29b-41d4-a716-446655440000",
          "action": "ROLE_ASSIGNED",
          "actorName": "admin.user",
          "actorId": "990e8400-e29b-41d4-a716-446655440000",
          "targetUserName": "john.doe",
          "targetUserId": "550e8400-e29b-41d4-a716-446655440000",
          "eventName": null,
          "eventId": null,
          "resourceType": "Role",
          "resourceId": null,
          "details": "Assigned role: ORGANIZER",
          "ipAddress": "192.168.1.100",
          "createdAt": "2026-01-18T10:00:00"
        }
      ],
      "totalElements": 1,
      "totalPages": 1
    }
    ```

- **GET /api/v1/audit/events/{eventId}**
  - Gets audit logs for a specific event (ORGANIZER must own event).
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<AuditLogDto>

- **GET /api/v1/audit/me**
  - Gets audit trail for the authenticated user (own actions).
  - Headers: Authorization: Bearer {{access_token}}
  - Query: page, size, sort
  - Response 200: Page<AuditLogDto>

**Audit Actions Logged:**
- ROLE_ASSIGNED, ROLE_REVOKED
- STAFF_ASSIGNED, STAFF_REMOVED
- INVITE_CREATED, INVITE_REDEEMED, INVITE_REVOKED
- EVENT_CREATED, EVENT_UPDATED, EVENT_DELETED
- TICKET_VALIDATED, TICKET_PURCHASED
- **NEW:** QR_CODE_VIEWED, QR_CODE_DOWNLOADED_PNG, QR_CODE_DOWNLOADED_PDF (QR export tracking)
- **NEW:** SALES_REPORT_EXPORTED (report export tracking)

---

### 11) Approval Gate System
**PRODUCTION SECURITY FEATURE**

The Approval Gate is a business-level authorization layer that runs AFTER Keycloak authentication.

**WHY IT EXISTS:**
- Keycloak handles authentication (who you are) ✓
- Keycloak assigns roles (what you claim to be) ✓
- Approval Gate enforces trust (whether we accept your claim) ✓

**SECURITY MODEL:**
- User authenticates with Keycloak → gets JWT with roles
- JWT is cryptographically valid
- User has legitimate role assignment
- BUT: User may not be approved for business operations
- Approval Gate blocks unapproved users DESPITE valid authentication

**APPROVAL STATES:**
- **PENDING**: New user awaiting admin review → 403 Forbidden on all business operations
- **APPROVED**: Admin approved user → Full access according to roles
- **REJECTED**: Admin rejected user → 403 Forbidden permanently
- **NULL**: Legacy user (auto-migrated to APPROVED on first access)

**EXECUTION FLOW:**
```
Request → JWT Validation (Keycloak) 
→ Role Extraction (Spring Security) 
→ User Provisioning (create if new) 
→ >>> APPROVAL GATE CHECK <<< 
→ Role-based Authorization (@PreAuthorize) 
→ Ownership/Scope Check (AuthorizationService) 
→ Business Logic
```

**ALLOWLISTED ENDPOINTS (Bypass Approval Gate):**
- `/api/v1/auth/register` - Public registration (no auth required)
- `/actuator/health` - Health checks (infrastructure)
- `/actuator/info` - Application metadata (infrastructure)
- `/api/v1/invites/redeem` - Invite redemption (deliberate exception for onboarding)

**ALL OTHER ENDPOINTS:** Approval required, NO exceptions

**ERROR RESPONSE (403 Forbidden):**
```json
{
  "error": "APPROVAL_PENDING",
  "message": "Your account is awaiting approval from an administrator. You will be notified once your account has been reviewed.",
  "status": "403",
  "timestamp": "2026-01-18T12:00:00Z"
}
```

**ADMIN APPROVAL ENDPOINTS:**
- `POST /api/v1/admin/users/{userId}/approve` - Approve pending user
- `POST /api/v1/admin/users/{userId}/reject` - Reject pending user
- `GET /api/v1/admin/users/pending` - List pending approvals

**CRITICAL NOTES:**
- Approval is SEPARATE from roles (user can have role but be unapproved)
- ADMIN does NOT bypass approval gate (even ADMIN can be pending)
- Approval gate applies to existing endpoints (backward compatible)
- Legacy users auto-migrate to APPROVED (zero downtime deployment)

---

# Event Booking App — Complete PowerShell Testing Guide

This guide includes ALL endpoints with copy/paste commands for Windows PowerShell.

## Quick facts
- Base URL: http://localhost:8081
- API version: v1
- Auth: OAuth2 Bearer JWT (Keycloak). All endpoints require a token by default.
- Time format: ISO-8601 (YYYY-MM-DDTHH:mm:ss)
- IDs: UUID

## 1) Prerequisites
- App running on port 8081
- Keycloak on http://localhost:9090 with realm event-ticket-platform
- A Keycloak user and client (client_id) you can use with Resource Owner Password (password) grant

## 2) Set variables (PowerShell)
```powershell
$BASE_URL = "http://localhost:8081"
$KEYCLOAK_URL = "http://localhost:9090"
$REALM = "event-ticket-platform"
$CLIENT_ID = "<your-client-id>"
$USERNAME = "<your-username>"
$PASSWORD = "<your-password>"
```

## 3) Get access token
```powershell
$tokenResponse = Invoke-RestMethod -Method Post `
  -Uri "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" `
  -Headers @{ "Content-Type" = "application/x-www-form-urlencoded" } `
  -Body "grant_type=password&client_id=$CLIENT_ID&username=$USERNAME&password=$PASSWORD&scope=openid profile email"
$ACCESS_TOKEN = $tokenResponse.access_token
$ACCESS_TOKEN.Substring(0,20)  # sanity peek
```

## 4) ORGANIZER ENDPOINTS - Event Management

### Create an event (captures IDs)
```powershell
$createEventBody = @{
  name       = "Tech Conference 2025"
  start      = "2025-12-15T09:00:00"
  end        = "2025-12-15T18:00:00"
  venue      = "Convention Center"
  salesStart = "2025-11-01T00:00:00"
  salesEnd   = "2025-12-14T23:59:59"
  status     = "PUBLISHED"
  ticketTypes = @(@{
    name = "Early Bird"; price = 199.99; description = "Discounted"; totalAvailable = 100
  })
} | ConvertTo-Json -Depth 6

$createEventRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $createEventBody

$EVENT_ID = $createEventRes.id
$TICKET_TYPE_ID = $createEventRes.ticketTypes[0].id
$EVENT_ID; $TICKET_TYPE_ID
```

### List organizer events
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events?page=0&size=20&sort=start,desc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get organizer event by ID
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Update the event
```powershell
# IMPORTANT: Do NOT include 'id' field in request body
# The eventId comes from the URL path parameter only
$updateEventBody = @{
  name       = "Tech Conference 2025 - Updated"
  start      = "2025-12-15T10:00:00"
  end        = "2025-12-15T19:00:00"
  venue      = "Main Hall"
  salesStart = "2025-11-01T00:00:00"
  salesEnd   = "2025-12-14T23:59:59"
  status     = "PUBLISHED"
  ticketTypes = @(@{
    id = $TICKET_TYPE_ID; name = "Regular"; price = 299.99; description = "Standard"; totalAvailable = 400
  })
} | ConvertTo-Json -Depth 6

Invoke-RestMethod -Method Put `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $updateEventBody
```

### Get sales dashboard
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/sales-dashboard" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get attendees report
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/attendees-report" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Export sales report (Excel .xlsx)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/sales-report.xlsx" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile ".\sales-report.xlsx"
Write-Host "Sales report downloaded: sales-report.xlsx"
# Open in Excel (optional)
# Start-Process ".\sales-report.xlsx"
```

## 5) ORGANIZER ENDPOINTS - Ticket Type Management

### Create a new ticket type
```powershell
$createTicketTypeBody = @{
  name = "VIP Pass"
  price = 499.99
  description = "Premium access with perks"
  totalAvailable = 50
} | ConvertTo-Json

$newTicketType = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $createTicketTypeBody

$VIP_TICKET_TYPE_ID = $newTicketType.id
$VIP_TICKET_TYPE_ID
```

### List all ticket types for event
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get specific ticket type
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$VIP_TICKET_TYPE_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Update ticket type
```powershell
$updateTicketTypeBody = @{
  id = $VIP_TICKET_TYPE_ID
  name = "VIP Pass - Updated"
  price = 549.99
  description = "Premium access with even more perks"
  totalAvailable = 30
} | ConvertTo-Json

Invoke-RestMethod -Method Put `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$VIP_TICKET_TYPE_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $updateTicketTypeBody
```

### Delete ticket type (only if no tickets sold)
```powershell
Invoke-WebRequest -Method Delete `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$VIP_TICKET_TYPE_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } | Select-Object StatusCode
```

## 6) ATTENDEE ENDPOINTS

### Published events
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/published-events?q=tech&page=0&size=10&sort=start,asc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }

Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/published-events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Purchase tickets with quantity support
```powershell
# Purchase single ticket (default quantity=1)
$purchaseSingleBody = @{ quantity = 1 } | ConvertTo-Json
$purchaseSingleRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/tickets" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $purchaseSingleBody
$purchaseSingleRes  # Array with 1 ticket

# Purchase multiple tickets
$purchaseMultipleBody = @{ quantity = 3 } | ConvertTo-Json
$purchaseMultipleRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/tickets" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $purchaseMultipleBody
$purchaseMultipleRes  # Array with 3 tickets
$TICKET_ID = $purchaseMultipleRes[0].id  # Capture first ticket ID
```

### List my tickets and capture ticket ID
```powershell
$ticketsPage = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/tickets?page=0&size=20&sort=id,desc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
$TICKET_ID = $ticketsPage.content[0].id
$TICKET_ID
```

### Get my ticket details
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get ticket QR code (PNG saved to file - DEPRECATED)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile (".\ticket-" + $TICKET_ID + "-qr.png")
```

### View QR code (inline display)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes/view" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile ".\ticket-qr-view.png"
Write-Host "QR code saved for viewing: ticket-qr-view.png"
```

### Download QR code (PNG with sanitized filename)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes/png" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile ".\ticket-qr-download.png"
Write-Host "QR code downloaded: ticket-qr-download.png"
```

### Download QR code (PDF with ticket details)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes/pdf" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile ".\ticket-qr.pdf"
Write-Host "QR code PDF downloaded: ticket-qr.pdf"
```

## 7) STAFF ENDPOINTS - Ticket Validation

### Validate ticket manually
```powershell
$validateManual = @{ id = $TICKET_ID; method = "MANUAL" } | ConvertTo-Json
$validationResult = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/ticket-validations" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $validateManual
$validationResult
```

### Get QR code ID for QR scan validation (from ticket details)
```powershell
$ticketDetails = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
$QR_CODE_ID = $ticketDetails.qrCodes[0].id  # assuming ticket has QR codes

$validateQr = @{ id = $QR_CODE_ID; method = "QR_SCAN" } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/ticket-validations" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $validateQr
```

### List all validations for an event
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/ticket-validations/events/$EVENT_ID?page=0&size=20" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Get validation history for a specific ticket
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/ticket-validations/tickets/$TICKET_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

## 8) Cleanup

### Delete the event
```powershell
Invoke-WebRequest -Method Delete `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } | Select-Object StatusCode
```

---

## 9) ADMIN GOVERNANCE ENDPOINTS

### Get available roles
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/admin/roles" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Assign STAFF role to a user
```powershell
$USER_ID = "550e8400-e29b-41d4-a716-446655440000"  # Replace with actual user ID
$assignRoleBody = @{ roleName = "STAFF" } | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/admin/users/$USER_ID/roles" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $assignRoleBody
```

### Get user roles
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/admin/users/$USER_ID/roles" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Revoke role from user
```powershell
Invoke-RestMethod -Method Delete `
  -Uri "$BASE_URL/api/v1/admin/users/$USER_ID/roles/STAFF" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

---

## 10) EVENT STAFF MANAGEMENT ENDPOINTS (ORGANIZER)

### Assign staff to event
```powershell
$STAFF_USER_ID = "550e8400-e29b-41d4-a716-446655440000"  # User must have STAFF role
$assignStaffBody = @{ userId = $STAFF_USER_ID } | ConvertTo-Json

$staffAssignRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/staff" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $assignStaffBody
$staffAssignRes
```

### List event staff
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/staff" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Remove staff from event
```powershell
Invoke-RestMethod -Method Delete `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/staff/$STAFF_USER_ID" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

---

## 11) INVITE CODE ENDPOINTS

### Generate STAFF invite for event (ORGANIZER)
```powershell
$createInviteBody = @{
  roleName = "STAFF"
  eventId = $EVENT_ID
  expirationHours = 48
} | ConvertTo-Json

$inviteRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/invites" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $createInviteBody

$INVITE_CODE = $inviteRes.code
$INVITE_ID = $inviteRes.id
Write-Host "Invite Code: $INVITE_CODE"
```

### Generate ORGANIZER invite (ADMIN only)
```powershell
$createOrganizerInviteBody = @{
  roleName = "ORGANIZER"
  eventId = $null  # Must be null for non-STAFF roles
  expirationHours = 72
} | ConvertTo-Json

$orgInviteRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/invites" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $createOrganizerInviteBody

$orgInviteRes.code
```

### Redeem invite code (any authenticated user)
```powershell
$redeemBody = @{ code = $INVITE_CODE } | ConvertTo-Json

$redeemRes = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/invites/redeem" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN"; "Content-Type" = "application/json" } `
  -Body $redeemBody

$redeemRes  # Shows assigned role and current roles
```

### List my invite codes
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/invites?page=0&size=20&sort=createdAt,desc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### List invites for specific event
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/invites/events/$EVENT_ID?page=0&size=20" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Revoke invite code
```powershell
Invoke-WebRequest -Method Delete `
  -Uri "$BASE_URL/api/v1/invites/${INVITE_ID}?reason=No%20longer%20needed" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } | Select-Object StatusCode
```

---

## 12) AUDIT LOG ENDPOINTS

### View all audit logs (ADMIN only)
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/audit?page=0&size=20&sort=createdAt,desc" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### View audit logs for my event (ORGANIZER)
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/audit/events/$EVENT_ID?page=0&size=20" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### View my own audit trail (any authenticated user)
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/audit/me?page=0&size=20" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

---

## Notes
- **Role Requirements**: Each endpoint section specifies the required Keycloak role
- **Validation Methods**: Use ticket UUID for MANUAL, QR code UUID for QR_SCAN
- **Security**: All endpoints require proper Bearer token with correct role
- **Pagination**: Use page, size, sort parameters for list endpoints
- **Quantity Limits**: Ticket purchases are limited to 1-10 tickets per transaction
- **Atomic Operations**: Multiple ticket purchases are processed as a single transaction
- **Individual QR Codes**: Each purchased ticket receives its own unique QR code
- **NEW - QR Code Exports**: 
  - Three formats available: inline view (PNG), download (PNG), download (PDF)
  - QR codes encode only ticket UUID (immutable identifier)
  - Security enforced at validation time, not at export time
  - Safe to share, re-download, or re-view QR codes
  - ATTENDEE can export own tickets, ORGANIZER can export tickets from own events
  - STAFF and ADMIN have NO access to QR exports
- **NEW - Sales Report Export**: 
  - Excel (.xlsx) format with comprehensive sales analytics
  - Reuses same data source as sales dashboard (no duplicate logic)
  - ORGANIZER must own event, ADMIN does NOT bypass ownership
  - Filename includes timestamp for version tracking
- **NEW - Approval Gate**:
  - All users must be approved by ADMIN before accessing business operations
  - Valid JWT + roles ≠ automatic access (approval also required)
  - PENDING or REJECTED users receive 403 Forbidden
  - See "Approval Gate System" section for details

## Common Errors and Troubleshooting
- **401 Unauthorized**: Token missing/expired/invalid (re-run token step)
- **403 Forbidden**: User doesn't have required role or doesn't own the resource
  - ADMIN trying to access another organizer's event
  - ORGANIZER trying to create non-STAFF invites
  - STAFF trying to access audit logs or validate unassigned event tickets
- **400 Bad Request**: Validation errors, sold out, invalid quantity, or business rule violations
  - Invite code already redeemed
  - Invite code expired
  - User doesn't have STAFF role when assigning to event
  - Invalid role + eventId combination (e.g., ORGANIZER invite with eventId)
- **404 Not Found**: Wrong UUID or resource not visible to the user
- **409 Conflict**: Optimistic locking failure (concurrent modification detected)

## New Features Summary

### Admin Governance
- **Backend-only Keycloak access**: All role management through backend API
- **ADMIN limitations**: Can manage roles but CANNOT bypass business rules
- **Audit trail**: All role changes logged

### Event Staff Management
- **Two-step process**: 
  1. ADMIN assigns STAFF role via `/api/v1/admin/users/{userId}/roles`
  2. ORGANIZER assigns to specific events via `/api/v1/events/{eventId}/staff`
- **Event-scoped access**: STAFF can only validate for assigned events
- **Database persistence**: Event-staff relationships stored in `user_staffing_events` table

### Invite Code System
- **Role-specific**: One invite = one role only (cannot be reused)
- **Single-use**: Optimistic locking prevents double redemption
- **Time-bound**: Configurable expiration (hours)
- **Event-scoped STAFF invites**: Automatically creates event-staff assignment
- **Atomic redemption**: Role assignment + event assignment in one transaction

### Audit System
- **Immutable**: Append-only audit trail, no update/delete operations
- **Complete tracking**: Actor, target, event, resource, IP, user-agent
- **Role-based access**: 
  - ADMIN: All logs via `/api/v1/audit`
  - ORGANIZER: Own event logs via `/api/v1/audit/events/{eventId}`
  - Any user: Own action trail via `/api/v1/audit/me`
- **Actions logged**: ROLE_ASSIGNED, ROLE_REVOKED, STAFF_ASSIGNED, STAFF_REMOVED, INVITE_CREATED, INVITE_REDEEMED, INVITE_REVOKED, TICKET_VALIDATED, QR_CODE_VIEWED, QR_CODE_DOWNLOADED_PNG, QR_CODE_DOWNLOADED_PDF, SALES_REPORT_EXPORTED

### QR Code Export System (NEW)
- **Three export formats**: Inline view (PNG), Download PNG, Download PDF
- **Security Model**: 
  - QR codes encode only ticket UUID (immutable identifier)
  - Security boundary is backend validation, NOT image integrity
  - Safe to share, re-download, or re-view
- **Access Control**:
  - ATTENDEE: Own tickets only
  - ORGANIZER: Tickets from own events only
  - STAFF: NO access
  - ADMIN: NO access (no bypass)
- **Idempotency**: Same ticket always generates identical QR content
- **Audit Logging**: All QR operations logged (VIEWED, DOWNLOADED_PNG, DOWNLOADED_PDF)

### Sales Report Export (NEW)
- **Excel Format**: Comprehensive .xlsx report with formatting
- **Data Reuse**: Uses same `EventService.getSalesDashboard()` method (NO duplicate logic)
- **Contents**: 
  - Summary: Event name, total sold, total revenue, timestamp
  - Breakdown: Per ticket type analytics with currency formatting
- **Access Control**:
  - ORGANIZER: Must own event (ownership enforced)
  - ADMIN: NO bypass (must own event)
  - ATTENDEE/STAFF: NO access
- **Idempotency**: Same event state produces identical Excel content
- **Audit Logging**: SALES_REPORT_EXPORTED action logged

### Approval Gate System (NEW)
- **Business-Level Authorization**: Separate from Keycloak authentication
- **Execution Order**: JWT validation → Role extraction → User provisioning → **Approval Gate** → Business logic
- **States**: PENDING (blocked), APPROVED (allowed), REJECTED (blocked), NULL (legacy, auto-approved)
- **SAFE-BY-DEFAULT**: All endpoints require approval unless explicitly allowlisted
- **Allowlist**: Only 4 paths bypass approval (auth, health, info, invite redemption)
- **No Bypass**: Even ADMIN must be approved
- **Backward Compatible**: Legacy users auto-migrate to APPROVED

## Security Architecture
- **No roles in database**: All roles managed by Keycloak (single source of truth)
- **Centralized authorization**: AuthorizationService enforces all business rules
- **No bypasses**: Even ADMIN must follow event ownership rules
- **Approval Gate (NEW)**: Business-level authorization layer running after Keycloak auth
  - SAFE-BY-DEFAULT: All endpoints require approval unless explicitly allowlisted
  - Blocks PENDING/REJECTED users even with valid JWT and roles
  - Separate from authentication (approval = business trust, not identity verification)
- **QR Code Security (NEW)**:
  - QR encodes only ticket UUID (immutable, non-forgeable identifier)
  - Security boundary is backend validation at scan time, NOT QR image integrity
  - Ownership checked at export: User must own ticket OR own event
  - STAFF and ADMIN cannot export QR codes (no bypass)
- **Export Authorization (NEW)**:
  - Sales reports: ORGANIZER must own event, ADMIN does NOT bypass
  - QR exports: ATTENDEE owns ticket OR ORGANIZER owns event
  - All exports are read-only, idempotent operations
  - No file storage (streaming only)
- **Rate limiting**: 1000 req/min standard, 10 req/min for auth endpoints
- **Error sanitization**: Production mode removes sensitive information (UUIDs, stack traces)
- **Optimistic locking**: TicketType and InviteCode entities prevent concurrent modification issues
- **CORS**: Environment-configurable, strict in production
- **Transactional integrity**: All audit logging within service transactions

## Database Schema Notes
- **users**: User table with approval status fields (NEW)
  - `approval_status`: PENDING, APPROVED, REJECTED (VARCHAR, defaults to NULL for legacy users)
  - `approved_at`: Timestamp of approval (TIMESTAMP)
  - `approved_by`: User ID of admin who approved (UUID FK)
  - `rejection_reason`: Reason for rejection (VARCHAR)
- **audit_logs**: Immutable audit trail table
  - Includes new actions: QR_CODE_VIEWED, QR_CODE_DOWNLOADED_PNG, QR_CODE_DOWNLOADED_PDF, SALES_REPORT_EXPORTED
- **invite_codes**: Invite code management table
- **user_staffing_events**: Junction table for event-staff assignments
- **Version fields**: Added to TicketType and InviteCode for optimistic locking
- **Schema Management**: Hibernate auto-update (`spring.jpa.hibernate.ddl-auto=update`)
  - Development: Auto-creates columns on startup
  - Production: Uses validate mode (fail-fast if schema mismatch)
  - No manual SQL migrations required (code-driven schema)

---

**Documentation Version:** 3.0  
**Last Updated:** January 19, 2026  
**API Status:** Production Ready with Export Features  
**Security Status:** Hardened, Audited & Approval-Gated  
**New Features:** QR Code Exports (PNG/PDF), Sales Report Export (Excel), Approval Gate System
