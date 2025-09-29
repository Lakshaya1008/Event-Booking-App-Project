# Event Booking App — Complete API Reference and Postman Guide

This document reflects the current codebase (controllers, DTOs, properties) and shows how to test every API in Postman.

- Base URL: http://localhost:8081
- API version: v1
- Auth: OAuth2 Bearer JWT (Keycloak)
- Pagination: Spring Pageable (page, size, sort)

## Contents
- Prerequisites and environment
- Authentication (get a token)
- Endpoint reference (paths, auth, payloads, responses)
- Role-based security requirements
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
- **ORGANIZER**: Can create/manage events, ticket types, view dashboards
- **ATTENDEE**: Can browse events, purchase tickets, view their tickets
- **STAFF**: Can validate tickets and view validation reports

## Endpoint Reference

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
  - Headers: Authorization: Bearer {{access_token}}
  - Body (UpdateEventRequestDto): Same as create but with id field
  - Response 200 (UpdateEventResponseDto)

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
  - Returns a PNG image of the ticket QR code.
  - Headers: Authorization: Bearer {{access_token}}
  - Response: 200 image/png | 404 Not Found

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
$updateEventBody = @{
  id         = $EVENT_ID
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

### Get ticket QR code (PNG saved to file)
```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" } `
  -OutFile (".\ticket-" + $TICKET_ID + "-qr.png")
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

## Common errors and fixes
- **401 Unauthorized**: Token missing/expired/invalid (re-run token step)
- **403 Forbidden**: User doesn't have required role (ORGANIZER/ATTENDEE/STAFF)
- **400 Bad Request**: Validation errors, sold out, or invalid quantity; see { "error": "message" }
- **404 Not Found**: Wrong UUID or resource not visible to the user

## Ticket Purchase API Changes (Breaking Changes)
⚠️ **Important**: The ticket purchase API has breaking changes:

### Before (Legacy - No longer supported):
```
POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets
Body: (empty)
Response: 204 No Content
```

### After (Current Implementation):
```
POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets
Body: {"quantity": 3}
Response: 201 Created with array of ticket objects
```

**Migration Notes**:
- All clients must now send JSON body with quantity
- Response changed from 204 No Content to 201 Created
- Response now returns array of ticket objects instead of empty body
- Default quantity is 1 if not specified
- Valid quantity range: 1-10 tickets per purchase

## Notes
- **Role Requirements**: Each endpoint section specifies the required Keycloak role
- **Validation Methods**: Use ticket UUID for MANUAL, QR code UUID for QR_SCAN
- **Security**: All endpoints require proper Bearer token with correct role
- **Pagination**: Use page, size, sort parameters for list endpoints
- **Quantity Limits**: Ticket purchases are limited to 1-10 tickets per transaction
- **Atomic Operations**: Multiple ticket purchases are processed as a single transaction
- **Individual QR Codes**: Each purchased ticket receives its own unique QR code
