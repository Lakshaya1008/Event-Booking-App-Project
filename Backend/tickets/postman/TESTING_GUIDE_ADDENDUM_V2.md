# TESTING GUIDE - NEW FEATURES ADDENDUM

**Version**: 2.1  
**Date**: January 19, 2026  
**Covers**: Event Update API Fix, QR Code Exports, Sales Report Export, Approval Gate System, Discount Management

This addendum extends the main TESTING_GUIDE.md with comprehensive testing procedures for new features implemented in v2.0.

---

## Table of Contents

1. [Event Update API Testing](#event-update-api-testing) ← **NEW FIX**
2. [Discount Management Testing](#discount-management-testing)
3. [QR Code Export Testing](#qr-code-export-testing)
4. [Sales Report Export Testing](#sales-report-export-testing)
5. [Approval Gate Testing](#approval-gate-testing)
6. [Regression Test Suite](#regression-test-suite)
7. [Complete Test Checklist](#complete-test-checklist)

---

## Event Update API Testing

### Overview

**API Contract Fix (CRITICAL)**:
The `PUT /api/v1/events/{eventId}` endpoint has been fixed to follow REST best practices:
- **Source of Truth**: `eventId` comes ONLY from URL path parameter
- **Request Body**: Does NOT require `id` field for the event
- **Defensive Check**: If `id` is provided in body, it must match URL `eventId`
- **Backward Compatible**: Clients sending matching `id` still work
- **⚠️ CRITICAL**: `ticketTypes[].id` IS required when updating existing ticket types!

**Access Control**:
- ORGANIZER: Can update their own events only
- ADMIN: NO bypass (must own event)
- ATTENDEE/STAFF: NO access

**Endpoint**:
`PUT /api/v1/events/{eventId}`

### Required Fields

| Field | Required | Validation | Notes |
|-------|----------|------------|-------|
| `name` | ✅ Yes | @NotBlank | Event name |
| `venue` | ✅ Yes | @NotBlank | Event venue |
| `status` | ✅ Yes | @NotNull | DRAFT, PUBLISHED, CANCELLED, COMPLETED |
| `ticketTypes` | ✅ Yes | @NotEmpty, @Valid | At least one ticket type required |
| `ticketTypes[].id` | **Conditional** | - | Required to UPDATE existing, omit to CREATE new |
| `ticketTypes[].name` | ✅ Yes | @NotBlank | Ticket type name |
| `ticketTypes[].price` | ✅ Yes | @NotNull, @PositiveOrZero | Ticket price |

### ⚠️ COMMON PITFALL: Ticket Type ID

**If you omit `ticketTypes[].id` for an existing ticket type:**
- The API will try to CREATE a new ticket type
- If the name already exists → **500 Internal Server Error** (duplicate key constraint)

**Correct approach:**
1. First GET the event to see existing ticket type IDs
2. Include the `id` field when updating existing ticket types
3. Omit `id` only when creating new ticket types

### Test Scenarios

#### EU-001: Update Event with Existing Ticket Type (Include ID)

**Objective**: Correctly update an event with existing ticket types by including their IDs

```powershell
# Setup
$BASE_URL = "http://localhost:8081"
$ORGANIZER_TOKEN = "<organizer-jwt>"
$EVENT_ID = "<event-uuid-owned-by-organizer>"
$TICKET_TYPE_ID = "<existing-ticket-type-uuid>"  # Get this from GET /api/v1/events/{eventId}

# Request body WITH ticket type ID for update
$body = @{
    name = "Updated Tech Conference 2025"
    start = "2025-12-15T10:00:00"
    end = "2025-12-15T19:00:00"
    venue = "Main Hall - Updated"
    salesStart = "2025-11-01T00:00:00"
    salesEnd = "2025-12-14T23:59:59"
    status = "PUBLISHED"
    ticketTypes = @(@{
        id = $TICKET_TYPE_ID  # ← CRITICAL: Include ID to update existing
        name = "General Admission"
        price = 199.99
        description = "Standard ticket"
        totalAvailable = 200
    })
} | ConvertTo-Json -Depth 5

# Execute
$response = Invoke-RestMethod -Method Put `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ 
    Authorization = "Bearer $ORGANIZER_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body $body

# Assert
Write-Host "Updated Event: $($response.name)"
assert ($response.id -eq $EVENT_ID) "Event ID should match"
assert ($response.name -eq "Updated Tech Conference 2025") "Name should be updated"
assert ($response.venue -eq "Main Hall - Updated") "Venue should be updated"

Write-Host "✅ EU-001 PASS: Event updated successfully with ticket type ID"
```

**Expected Result**: 200 OK with updated event details

---

#### EU-001B: Create NEW Ticket Type During Update (Omit ID)

**Objective**: Add a new ticket type to an existing event

```powershell
# Request body without ticket type ID = creates NEW ticket type
$body = @{
    name = "Updated Tech Conference 2025"
    venue = "Main Hall - Updated"
    status = "PUBLISHED"
    ticketTypes = @(@{
        # No 'id' field = CREATE new ticket type
        name = "VIP Pass"  # New name, not existing
        price = 499.99
        description = "VIP access"
        totalAvailable = 50
    })
} | ConvertTo-Json -Depth 5
```

**Expected Result**: 200 OK, new ticket type created

---

#### EU-002: Update Event With Matching ID in Body (Backward Compatible)

**Objective**: Verify backward compatibility when `id` matches URL

```powershell
# Request body WITH matching id field
$body = @{
    id = $EVENT_ID  # Matches URL path parameter
    name = "Updated Tech Conference 2025 v2"
    start = "2025-12-15T10:00:00"
    end = "2025-12-15T19:00:00"
    venue = "Main Hall - Updated v2"
    salesStart = "2025-11-01T00:00:00"
    salesEnd = "2025-12-14T23:59:59"
    status = "PUBLISHED"
    ticketTypes = @(@{
        name = "General Admission"
        price = 199.99
        description = "Standard ticket"
        totalAvailable = 200
    })
} | ConvertTo-Json -Depth 5

$response = Invoke-RestMethod -Method Put `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ 
    Authorization = "Bearer $ORGANIZER_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body $body

assert ($response.name -eq "Updated Tech Conference 2025 v2")
Write-Host "✅ EU-002 PASS: Backward compatible with matching id"
```

**Expected Result**: 200 OK

---

#### EU-003: Reject Mismatched ID in Body

**Objective**: Verify defensive check rejects mismatched IDs

```powershell
$DIFFERENT_ID = [guid]::NewGuid().ToString()

$body = @{
    id = $DIFFERENT_ID  # Different from URL
    name = "Malicious Update Attempt"
    venue = "Hacked Venue"
    status = "PUBLISHED"
    ticketTypes = @()
} | ConvertTo-Json -Depth 5

try {
    Invoke-RestMethod -Method Put `
      -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
      -Headers @{ 
        Authorization = "Bearer $ORGANIZER_TOKEN"
        "Content-Type" = "application/json"
      } `
      -Body $body
    Write-Host "❌ EU-003 FAIL: Should have returned 400"
    exit 1
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    assert ($statusCode -eq 400) "Should return 400 Bad Request"
    Write-Host "✅ EU-003 PASS: Mismatched ID rejected"
}
```

**Expected Result**: 400 Bad Request with error message "Event ID in request body does not match path parameter"

---

#### EU-004: Missing Required Fields Returns 400

**Objective**: Verify validation still works for required fields

```powershell
# Missing required 'status' field
$body = @{
    name = "Updated Event"
    venue = "Test Venue"
    # status is missing
    ticketTypes = @(@{
        name = "General"
        price = 100.00
        totalAvailable = 100
    })
} | ConvertTo-Json -Depth 5

try {
    Invoke-RestMethod -Method Put `
      -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
      -Headers @{ 
        Authorization = "Bearer $ORGANIZER_TOKEN"
        "Content-Type" = "application/json"
      } `
      -Body $body
    Write-Host "❌ EU-004 FAIL: Should have returned 400"
    exit 1
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    assert ($statusCode -eq 400) "Should return 400 for missing status"
    Write-Host "✅ EU-004 PASS: Missing status field rejected"
}
```

**Expected Result**: 400 Bad Request

---

#### EU-005: Non-Owner Organizer Denied

**Objective**: Verify organizers cannot update other organizers' events

```powershell
$OTHER_ORGANIZER_TOKEN = "<different-organizer-jwt>"

$body = @{
    name = "Unauthorized Update"
    venue = "Test Venue"
    status = "PUBLISHED"
    ticketTypes = @()
} | ConvertTo-Json -Depth 5

try {
    Invoke-RestMethod -Method Put `
      -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
      -Headers @{ 
        Authorization = "Bearer $OTHER_ORGANIZER_TOKEN"
        "Content-Type" = "application/json"
      } `
      -Body $body
    Write-Host "❌ EU-005 FAIL: Should have returned 403"
    exit 1
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    assert ($statusCode -eq 403) "Should return 403 Forbidden"
    Write-Host "✅ EU-005 PASS: Non-owner denied"
}
```

**Expected Result**: 403 Forbidden

---

#### EU-006: Non-Existent Event Returns 404

**Objective**: Verify proper handling of non-existent events

```powershell
$FAKE_EVENT_ID = [guid]::NewGuid().ToString()

$body = @{
    name = "Update Non-Existent"
    venue = "Test Venue"
    status = "PUBLISHED"
    ticketTypes = @()
} | ConvertTo-Json -Depth 5

try {
    Invoke-RestMethod -Method Put `
      -Uri "$BASE_URL/api/v1/events/$FAKE_EVENT_ID" `
      -Headers @{ 
        Authorization = "Bearer $ORGANIZER_TOKEN"
        "Content-Type" = "application/json"
      } `
      -Body $body
    Write-Host "❌ EU-006 FAIL: Should have returned 404"
    exit 1
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    assert ($statusCode -eq 404) "Should return 404 Not Found"
    Write-Host "✅ EU-006 PASS: Non-existent event returns 404"
}
```

**Expected Result**: 404 Not Found

---

#### EU-007: Empty Ticket Types Returns 400

**Objective**: Verify @NotEmpty validation on ticketTypes

```powershell
$body = @{
    name = "Event with No Tickets"
    venue = "Test Venue"
    status = "PUBLISHED"
    ticketTypes = @()  # Empty array
} | ConvertTo-Json -Depth 5

try {
    Invoke-RestMethod -Method Put `
      -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
      -Headers @{ 
        Authorization = "Bearer $ORGANIZER_TOKEN"
        "Content-Type" = "application/json"
      } `
      -Body $body
    Write-Host "❌ EU-007 FAIL: Should have returned 400"
    exit 1
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    assert ($statusCode -eq 400) "Should return 400 for empty ticketTypes"
    Write-Host "✅ EU-007 PASS: Empty ticketTypes rejected"
}
```

**Expected Result**: 400 Bad Request

---

### Event Update Test Summary

| Test ID | Test Name | Expected Result |
|---------|-----------|-----------------|
| EU-001 | Update without id in body | 200 OK |
| EU-002 | Update with matching id | 200 OK |
| EU-003 | Reject mismatched id | 400 Bad Request |
| EU-004 | Missing required fields | 400 Bad Request |
| EU-005 | Non-owner denied | 403 Forbidden |
| EU-006 | Non-existent event | 404 Not Found |
| EU-007 | Empty ticketTypes | 400 Bad Request |

### Event Update Checklist

**API Contract** (Priority: Critical):
- [ ] EU-001: Update works without `id` in body
- [ ] EU-002: Backward compatible with matching `id`
- [ ] EU-003: Mismatched `id` rejected with 400

**Validation** (Priority: High):
- [ ] EU-004: Missing required fields return 400
- [ ] EU-007: Empty ticketTypes rejected

**Authorization** (Priority: Critical):
- [ ] EU-005: Non-owner organizer gets 403
- [ ] EU-006: Non-existent event returns 404

---

## Discount Management Testing

### Overview

**Business Rules**:
- Only ONE active discount per ticket type at a time
- Discounts apply at purchase time only (never retroactive)
- Two types: PERCENTAGE (0-100%) or FIXED_AMOUNT (currency)
- Discounts are automatically applied during ticket purchase

**Access Control**:
- ORGANIZER: Can create/manage discounts for their own events
- ADMIN: NO special access (no bypass)
- ATTENDEE/STAFF: NO access

**Endpoints**:
1. `POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts` - Create discount
2. `PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}` - Update discount
3. `DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}` - Delete discount
4. `GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}` - Get discount
5. `GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts` - List discounts

### Test Scenarios

#### DISC-001: Create Percentage Discount

**Objective**: Verify organizer can create percentage-based discount

```powershell
# Setup
$BASE_URL = "http://localhost:8081"
$ORGANIZER_TOKEN = "<organizer-jwt>"
$EVENT_ID = "<event-uuid>"
$TICKET_TYPE_ID = "<ticket-type-uuid>"

$body = @{
    discountType = "PERCENTAGE"
    value = 20.0
    validFrom = "2025-11-01T00:00:00"
    validTo = "2025-11-30T23:59:59"
    description = "Early Bird Special"
} | ConvertTo-Json

# Execute
$response = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts" `
  -Headers @{ 
    Authorization = "Bearer $ORGANIZER_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body $body

# Assert
Write-Host "Discount ID: $($response.id)"
assert ($response.discountType -eq "PERCENTAGE")
assert ($response.value -eq 20.0)
assert ($response.description -eq "Early Bird Special")

Write-Host "✅ DISC-001 PASS"
```

**Expected Result**: 201 Created with discount details

---

#### DISC-002: Create Fixed Amount Discount

**Objective**: Verify fixed amount discount creation

```powershell
$body = @{
    discountType = "FIXED_AMOUNT"
    value = 50.00
    validFrom = "2025-12-01T00:00:00"
    validTo = "2025-12-25T23:59:59"
    description = "Holiday Discount"
} | ConvertTo-Json

$response = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts" `
  -Headers @{ 
    Authorization = "Bearer $ORGANIZER_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body $body

assert ($response.discountType -eq "FIXED_AMOUNT")
assert ($response.value -eq 50.0)

Write-Host "✅ DISC-002 PASS"
```

**Expected Result**: 201 Created

---

#### DISC-003: Prevent Multiple Active Discounts

**Objective**: Verify only one active discount per ticket type

```powershell
# First discount already exists (from DISC-001)
# Try to create another overlapping discount

$body = @{
    discountType = "PERCENTAGE"
    value = 15.0
    validFrom = "2025-11-15T00:00:00"
    validTo = "2025-12-15T23:59:59"
    description = "Conflicting Discount"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Method Post `
      -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts" `
      -Headers @{ 
        Authorization = "Bearer $ORGANIZER_TOKEN"
        "Content-Type" = "application/json"
      } `
      -Body $body
    Write-Host "❌ DISC-003 FAIL: Should have returned 400"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 400)
    Write-Host "✅ DISC-003 PASS: Only one active discount allowed"
}
```

**Expected Result**: 400 Bad Request

---

#### DISC-004: Validate Percentage Range

**Objective**: Verify percentage validation (0-100)

```powershell
# Test invalid percentage > 100
$body = @{
    discountType = "PERCENTAGE"
    value = 150.0
    validFrom = "2026-01-01T00:00:00"
    validTo = "2026-01-31T23:59:59"
    description = "Invalid Discount"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Method Post `
      -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts" `
      -Headers @{ 
        Authorization = "Bearer $ORGANIZER_TOKEN"
        "Content-Type" = "application/json"
      } `
      -Body $body
    Write-Host "❌ DISC-004 FAIL: Should reject percentage > 100"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 400)
    Write-Host "✅ DISC-004 PASS"
}
```

**Expected Result**: 400 Bad Request

---

#### DISC-005: List Discounts for Ticket Type

**Objective**: Verify organizer can list all discounts

```powershell
$response = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" }

assert ($response.Count -ge 1)
Write-Host "Found $($response.Count) discount(s)"
Write-Host "✅ DISC-005 PASS"
```

**Expected Result**: 200 OK with list of discounts

---

#### DISC-006: Update Discount

**Objective**: Verify discount can be updated

```powershell
$DISCOUNT_ID = "<discount-uuid-from-disc-001>"

$body = @{
    discountType = "PERCENTAGE"
    value = 25.0
    validFrom = "2025-11-01T00:00:00"
    validTo = "2025-11-30T23:59:59"
    description = "Early Bird Special - Updated"
} | ConvertTo-Json

$response = Invoke-RestMethod -Method Put `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts/$DISCOUNT_ID" `
  -Headers @{ 
    Authorization = "Bearer $ORGANIZER_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body $body

assert ($response.value -eq 25.0)
assert ($response.description -eq "Early Bird Special - Updated")
Write-Host "✅ DISC-006 PASS"
```

**Expected Result**: 200 OK with updated discount

---

#### DISC-007: Delete Discount

**Objective**: Verify discount can be deleted

```powershell
Invoke-RestMethod -Method Delete `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts/$DISCOUNT_ID" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" }

Write-Host "✅ DISC-007 PASS: Discount deleted"
```

**Expected Result**: 204 No Content

---

#### DISC-008: Non-Owner Cannot Manage Discounts

**Objective**: Verify ownership enforcement

```powershell
$OTHER_ORGANIZER_TOKEN = "<different-organizer-jwt>"

$body = @{
    discountType = "PERCENTAGE"
    value = 10.0
    validFrom = "2026-01-01T00:00:00"
    validTo = "2026-01-31T23:59:59"
    description = "Unauthorized Discount"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Method Post `
      -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts" `
      -Headers @{ 
        Authorization = "Bearer $OTHER_ORGANIZER_TOKEN"
        "Content-Type" = "application/json"
      } `
      -Body $body
    Write-Host "❌ DISC-008 FAIL: Should have returned 403"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ DISC-008 PASS: Ownership enforced"
}
```

**Expected Result**: 403 Forbidden

---

#### DISC-009: Discount Application on Purchase

**Objective**: Verify discount is automatically applied during ticket purchase

```powershell
# Create a new discount
$body = @{
    discountType = "PERCENTAGE"
    value = 30.0
    validFrom = "2026-01-01T00:00:00"
    validTo = "2026-12-31T23:59:59"
    description = "Test Purchase Discount"
} | ConvertTo-Json

$discount = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts" `
  -Headers @{ 
    Authorization = "Bearer $ORGANIZER_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body $body

# Purchase ticket as attendee
$ATTENDEE_TOKEN = "<attendee-jwt>"
$purchaseBody = @{ quantity = 1 } | ConvertTo-Json

$tickets = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/tickets" `
  -Headers @{ 
    Authorization = "Bearer $ATTENDEE_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body $purchaseBody

# Verify discount was applied
$ticket = $tickets[0]
$basePrice = 100.0  # Assume ticket base price
$expectedPrice = $basePrice * 0.70  # 30% discount

assert ($ticket.price -eq $expectedPrice)
Write-Host "✅ DISC-009 PASS: Discount automatically applied"
```

**Expected Result**: Ticket purchased at discounted price

---

#### DISC-010: Expired Discount Not Applied

**Objective**: Verify expired discounts are not applied

```powershell
# Create expired discount
$body = @{
    discountType = "PERCENTAGE"
    value = 50.0
    validFrom = "2024-01-01T00:00:00"
    validTo = "2024-12-31T23:59:59"
    description = "Expired Discount"
} | ConvertTo-Json

$discount = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts" `
  -Headers @{ 
    Authorization = "Bearer $ORGANIZER_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body $body

# Purchase ticket - should get full price
$tickets = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/tickets" `
  -Headers @{ 
    Authorization = "Bearer $ATTENDEE_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body (@{ quantity = 1 } | ConvertTo-Json)

$ticket = $tickets[0]
$basePrice = 100.0

assert ($ticket.price -eq $basePrice)
Write-Host "✅ DISC-010 PASS: Expired discount not applied"
```

**Expected Result**: Full price charged

---

### Discount Testing Summary

| Test ID | Test Name | Expected Result |
|---------|-----------|-----------------|
| DISC-001 | Create Percentage Discount | 201 Created |
| DISC-002 | Create Fixed Amount Discount | 201 Created |
| DISC-003 | Prevent Multiple Active Discounts | 400 Bad Request |
| DISC-004 | Validate Percentage Range | 400 Bad Request |
| DISC-005 | List Discounts | 200 OK |
| DISC-006 | Update Discount | 200 OK |
| DISC-007 | Delete Discount | 204 No Content |
| DISC-008 | Non-Owner Access Denied | 403 Forbidden |
| DISC-009 | Discount Applied on Purchase | Price Discounted |
| DISC-010 | Expired Discount Not Applied | Full Price |

---

## QR Code Export Testing

### Overview

**Security Model (CRITICAL)**:
- QR codes encode ONLY ticket UUID (immutable identifier)
- QR image integrity is NOT a security boundary
- Security enforced during backend validation at scan time
- Safe to re-download, re-view, or share QR codes

**Access Control**:
- ATTENDEE: Own tickets only
- ORGANIZER: Tickets from own events only
- STAFF: NO access (403 Forbidden)
- ADMIN: NO access (403 Forbidden, no bypass)

**Endpoints**:
1. `GET /api/v1/tickets/{ticketId}/qr-codes/view` - Inline viewing (PNG)
2. `GET /api/v1/tickets/{ticketId}/qr-codes/png` - PNG download
3. `GET /api/v1/tickets/{ticketId}/qr-codes/pdf` - PDF download

### Test Scenarios

#### QR-001: ATTENDEE Views Own Ticket QR

**Objective**: Verify ATTENDEE can view QR for own tickets

```powershell
# Setup
$BASE_URL = "http://localhost:8081"
$ATTENDEE_TOKEN = "<attendee-jwt>"
$OWN_TICKET_ID = "<ticket-uuid-owned-by-attendee>"

# Execute
$response = Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/view" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" }

# Assert
assert ($response.StatusCode -eq 200) "Status should be 200"
assert ($response.Headers["Content-Type"] -eq "image/png") "Content-Type should be image/png"
assert ($response.Headers["Content-Disposition"] -match "inline") "Should be inline disposition"
assert ($response.Headers["Cache-Control"] -match "max-age=300") "Should have cache control"
assert ($response.Content.Length -gt 0) "Should have content"

Write-Host "✅ QR-001 PASS"
```

**Expected Result**: 200 OK, PNG image with inline disposition

---

#### QR-002: ATTENDEE Attempts to View Other's Ticket

**Objective**: Verify ownership enforcement

```powershell
$OTHER_TICKET_ID = "<ticket-owned-by-someone-else>"

try {
    $response = Invoke-WebRequest -Method Get `
      -Uri "$BASE_URL/api/v1/tickets/$OTHER_TICKET_ID/qr-codes/view" `
      -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" }
    Write-Host "❌ QR-002 FAIL: Should have returned 403"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403) "Should be 403 Forbidden"
    Write-Host "✅ QR-002 PASS"
}
```

**Expected Result**: 403 Forbidden

---

#### QR-003: Download QR as PNG

**Objective**: Verify PNG download with sanitized filename

```powershell
$response = Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/png" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" }

assert ($response.StatusCode -eq 200)
assert ($response.Headers["Content-Type"] -eq "image/png")
assert ($response.Headers["Content-Disposition"] -match "attachment")
assert ($response.Headers["Content-Disposition"] -match "[a-z0-9_]+\.png")

# Save for inspection
$response.Content | Set-Content -Path "qr-test.png" -Encoding Byte

Write-Host "✅ QR-003 PASS"
```

**Expected Result**: 200 OK, PNG file with sanitized filename (event_type_user_id.png)

---

#### QR-004: Download QR as PDF

**Objective**: Verify PDF generation with ticket details

```powershell
$response = Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/pdf" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" }

assert ($response.StatusCode -eq 200)
assert ($response.Headers["Content-Type"] -eq "application/pdf")
assert ($response.Headers["Content-Disposition"] -match "attachment")
assert ($response.Headers["Content-Disposition"] -match "\.pdf$")
assert ($response.Content.Length -gt 1000)

response.Content | Set-Content -Path "qr-test.pdf" -Encoding Byte

Write-Host "✅ QR-004 PASS"
```

**Expected Result**: 200 OK, PDF with QR code and ticket details

---

#### QR-005: ORGANIZER Views Ticket from Own Event

**Objective**: Verify organizers can view tickets from their events

```powershell
$ORGANIZER_TOKEN = "<organizer-jwt>"
$TICKET_FROM_OWN_EVENT = "<ticket-from-organizer-event>"

$response = Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_FROM_OWN_EVENT/qr-codes/view" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" }

assert ($response.StatusCode -eq 200)
Write-Host "✅ QR-005 PASS"
```

**Expected Result**: 200 OK

---

#### QR-006: STAFF Denied QR Export Access

**Objective**: Verify STAFF has NO access to QR exports

```powershell
$STAFF_TOKEN = "<staff-jwt>"

try {
    Invoke-WebRequest -Method Get `
      -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/view" `
      -Headers @{ Authorization = "Bearer $STAFF_TOKEN" }
    Write-Host "❌ QR-006 FAIL: STAFF should be denied"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ QR-006 PASS"
}
```

**Expected Result**: 403 Forbidden

---

#### QR-007: ADMIN Denied QR Export Access (No Bypass)

**Objective**: Verify ADMIN does NOT bypass ownership

```powershell
$ADMIN_TOKEN = "<admin-jwt>"

try {
    Invoke-WebRequest -Method Get `
      -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/view" `
      -Headers @{ Authorization = "Bearer $ADMIN_TOKEN" }
    Write-Host "❌ QR-007 FAIL: ADMIN should NOT bypass"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ QR-007 PASS"
}
```

**Expected Result**: 403 Forbidden

---

#### QR-008: Idempotency Verification

**Objective**: Verify same ticket produces identical QR

```powershell
$response1 = Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/png" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" }

Start-Sleep -Seconds 2

$response2 = Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/png" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" }

assert ($response1.Content.Length -eq $response2.Content.Length)

$hash1 = (Get-FileHash -InputStream ([System.IO.MemoryStream]::new($response1.Content))).Hash
$hash2 = (Get-FileHash -InputStream ([System.IO.MemoryStream]::new($response2.Content))).Hash
assert ($hash1 -eq $hash2) "Hashes should match"

Write-Host "✅ QR-008 PASS: Idempotency verified"
```

**Expected Result**: Identical file hashes

---

#### QR-009: Audit Log Verification

**Objective**: Verify QR operations are audited

```powershell
# Perform operations
Invoke-WebRequest -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/view" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" } | Out-Null

Invoke-WebRequest -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/png" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" } | Out-Null

Invoke-WebRequest -Uri "$BASE_URL/api/v1/tickets/$OWN_TICKET_ID/qr-codes/pdf" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" } | Out-Null

Start-Sleep -Seconds 1

# Check audit logs
$auditLogs = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/audit/me?page=0&size=10" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" }

$viewedLogs = $auditLogs.content | Where-Object { $_.action -eq "QR_CODE_VIEWED" }
$pngLogs = $auditLogs.content | Where-Object { $_.action -eq "QR_CODE_DOWNLOADED_PNG" }
$pdfLogs = $auditLogs.content | Where-Object { $_.action -eq "QR_CODE_DOWNLOADED_PDF" }

assert ($viewedLogs.Count -ge 1) "Should have QR_CODE_VIEWED log"
assert ($pngLogs.Count -ge 1) "Should have QR_CODE_DOWNLOADED_PNG log"
assert ($pdfLogs.Count -ge 1) "Should have QR_CODE_DOWNLOADED_PDF log"

Write-Host "✅ QR-009 PASS: Audit logs verified"
```

**Expected Result**: Audit logs contain QR_CODE_VIEWED, QR_CODE_DOWNLOADED_PNG, QR_CODE_DOWNLOADED_PDF actions

---

### QR Code Testing Summary

| Test ID | Test Name | Expected Result |
|---------|-----------|-----------------|
| QR-001 | ATTENDEE Views Own QR | 200 OK |
| QR-002 | ATTENDEE Views Other's QR | 403 Forbidden |
| QR-003 | Download PNG | 200 OK |
| QR-004 | Download PDF | 200 OK |
| QR-005 | ORGANIZER Views Own Event QR | 200 OK |
| QR-006 | STAFF Denied Access | 403 Forbidden |
| QR-007 | ADMIN Denied Access | 403 Forbidden |
| QR-008 | Idempotency | Identical Content |
| QR-009 | Audit Logging | Logs Present |

---

## Sales Report Export Testing

### Overview

**Business Rules**:
- Excel (.xlsx) format with comprehensive sales analytics
- Reuses same EventService.getSalesDashboard() method (NO duplicate logic)
- ORGANIZER must own event, ADMIN does NOT bypass ownership
- Filename includes timestamp for version tracking

**Access Control**:
- ORGANIZER: Must own event (ownership enforced)
- ADMIN: NO bypass (must own event)
- ATTENDEE/STAFF: NO access

**Endpoint**:
`GET /api/v1/events/{eventId}/sales-report.xlsx`

### Test Scenarios

#### SR-001: ORGANIZER Exports Sales Report

**Objective**: Verify organizer can export sales report for own event

```powershell
# Setup
$BASE_URL = "http://localhost:8081"
$ORGANIZER_TOKEN = "<organizer-jwt>"
$EVENT_ID = "<event-uuid-owned-by-organizer>"

# Execute
$response = Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/sales-report.xlsx" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" }

# Assert
assert ($response.StatusCode -eq 200) "Status should be 200"
assert ($response.Headers["Content-Type"] -eq "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") "Content-Type should be Excel"
assert ($response.Headers["Content-Disposition"] -match "attachment") "Should be attachment disposition"
assert ($response.Headers["Content-Disposition"] -match "\.xlsx$") "Should have .xlsx extension"
assert ($response.Content.Length -gt 1000) "Should have substantial content"

# Save for inspection
$response.Content | Set-Content -Path "sales-report.xlsx" -Encoding Byte

Write-Host "✅ SR-001 PASS"
```

**Expected Result**: 200 OK, Excel file with sales data

---

#### SR-002: ADMIN Cannot Export Without Ownership

**Objective**: Verify ADMIN does NOT bypass ownership

```powershell
$ADMIN_TOKEN = "<admin-jwt>"

try {
    Invoke-WebRequest -Method Get `
      -Uri "$BASE_URL/api/v1/events/$EVENT_ID/sales-report.xlsx" `
      -Headers @{ Authorization = "Bearer $ADMIN_TOKEN" }
    Write-Host "❌ SR-002 FAIL: ADMIN should NOT bypass"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ SR-002 PASS"
}
```

**Expected Result**: 403 Forbidden

---

#### SR-003: ATTENDEE Denied Access

**Objective**: Verify ATTENDEE cannot export sales reports

```powershell
$ATTENDEE_TOKEN = "<attendee-jwt>"

try {
    Invoke-WebRequest -Method Get `
      -Uri "$BASE_URL/api/v1/events/$EVENT_ID/sales-report.xlsx" `
      -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" }
    Write-Host "❌ SR-003 FAIL: ATTENDEE should be denied"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ SR-003 PASS"
}
```

**Expected Result**: 403 Forbidden

---

#### SR-004: Audit Log Verification

**Objective**: Verify sales report export is audited

```powershell
# Perform export
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/sales-report.xlsx" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" } | Out-Null

Start-Sleep -Seconds 1

# Check audit logs
$auditLogs = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/audit/events/$EVENT_ID?page=0&size=10" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" }

$salesReportLogs = $auditLogs.content | Where-Object { $_.action -eq "SALES_REPORT_EXPORTED" }

assert ($salesReportLogs.Count -ge 1) "Should have SALES_REPORT_EXPORTED log"

Write-Host "✅ SR-004 PASS: Audit log verified"
```

**Expected Result**: Audit log contains SALES_REPORT_EXPORTED action

---

### Sales Report Testing Summary

| Test ID | Test Name | Expected Result |
|---------|-----------|-----------------|
| SR-001 | ORGANIZER Exports Report | 200 OK |
| SR-002 | ADMIN Denied Without Ownership | 403 Forbidden |
| SR-003 | ATTENDEE Denied Access | 403 Forbidden |
| SR-004 | Audit Logging | Log Present |

---

## Approval Gate Testing

### Overview

**Security Model**:
- Business-level authorization layer running AFTER Keycloak auth
- Blocks unapproved users despite valid JWT and roles
- SAFE-BY-DEFAULT: All endpoints require approval unless explicitly allowlisted

**Approval States**:
- PENDING: New user, blocked from business operations
- APPROVED: Admin approved, full access
- REJECTED: Admin rejected, permanently blocked
- NULL: Legacy user, auto-approved

**Allowlisted Endpoints** (Bypass Approval):
- `/api/v1/auth/register` - Public registration
- `/actuator/health` - Health checks
- `/actuator/info` - Application metadata
- `/api/v1/invites/redeem` - Invite redemption

### Test Scenarios

#### AG-001: PENDING User Blocked from Business Operations

**Objective**: Verify PENDING users cannot access business endpoints

```powershell
# Setup: Create a new user in Keycloak (will be PENDING by default)
$NEW_USER_TOKEN = "<jwt-for-new-user>"

# Try to access business endpoint
try {
    Invoke-RestMethod -Method Get `
      -Uri "$BASE_URL/api/v1/published-events" `
      -Headers @{ Authorization = "Bearer $NEW_USER_TOKEN" }
    Write-Host "❌ AG-001 FAIL: PENDING user should be blocked"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    $errorResponse = $_.ErrorDetails.Message | ConvertFrom-Json
    assert ($errorResponse.error -eq "APPROVAL_PENDING")
    Write-Host "✅ AG-001 PASS: PENDING user blocked"
}
```

**Expected Result**: 403 Forbidden with APPROVAL_PENDING error

---

#### AG-002: APPROVED User Has Full Access

**Objective**: Verify APPROVED users have normal access

```powershell
# Setup: Admin approves the user via /api/v1/admin/approvals/{userId}/approve
$APPROVED_USER_TOKEN = "<jwt-for-approved-user>"

# Access should work normally
$response = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/published-events" `
  -Headers @{ Authorization = "Bearer $APPROVED_USER_TOKEN" }

assert ($response -is [System.Array] -or $response -is [PSCustomObject])
Write-Host "✅ AG-002 PASS: APPROVED user has access"
```

**Expected Result**: 200 OK, normal response

---

#### AG-003: REJECTED User Permanently Blocked

**Objective**: Verify REJECTED users are permanently blocked

```powershell
$REJECTED_USER_TOKEN = "<jwt-for-rejected-user>"

try {
    Invoke-RestMethod -Method Get `
      -Uri "$BASE_URL/api/v1/published-events" `
      -Headers @{ Authorization = "Bearer $REJECTED_USER_TOKEN" }
    Write-Host "❌ AG-003 FAIL: REJECTED user should be blocked"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ AG-003 PASS: REJECTED user blocked"
}
```

**Expected Result**: 403 Forbidden

---

#### AG-004: Allowlisted Endpoints Bypass Approval

**Objective**: Verify allowlisted endpoints work for PENDING users

```powershell
# Health endpoint should work for PENDING user
$response = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/actuator/health" `
  -Headers @{ Authorization = "Bearer $NEW_USER_TOKEN" }

assert ($response.status -eq "UP")
Write-Host "✅ AG-004 PASS: Allowlisted endpoints work"
```

**Expected Result**: 200 OK

---

#### AG-005: Legacy Users Auto-Approved

**Objective**: Verify existing users are auto-migrated to APPROVED

```powershell
$LEGACY_USER_TOKEN = "<jwt-for-existing-user>"

# Should work normally (auto-approved)
$response = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/published-events" `
  -Headers @{ Authorization = "Bearer $LEGACY_USER_TOKEN" }

Write-Host "✅ AG-005 PASS: Legacy user auto-approved"
```

**Expected Result**: 200 OK

---

### Approval Gate Testing Summary

| Test ID | Test Name | Expected Result |
|---------|-----------|-----------------|
| AG-001 | PENDING User Blocked | 403 Forbidden |
| AG-002 | APPROVED User Access | 200 OK |
| AG-003 | REJECTED User Blocked | 403 Forbidden |
| AG-004 | Allowlisted Endpoints Work | 200 OK |
| AG-005 | Legacy Users Auto-Approved | 200 OK |

---

## Regression Test Suite

### Overview

Run these tests after any changes to ensure existing functionality still works.

#### REG-001: Complete Event Lifecycle

**Objective**: Test full event creation, update, ticket purchase, validation flow

```powershell
# 1. Create event
$createBody = @{
  name = "Regression Test Event"
  venue = "Test Venue"
  status = "PUBLISHED"
  ticketTypes = @(@{
    name = "Standard"
    price = 50.00
    totalAvailable = 10
  })
} | ConvertTo-Json -Depth 3

$event = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN"; "Content-Type" = "application/json" } `
  -Body $createBody

$EVENT_ID = $event.id
$TICKET_TYPE_ID = $event.ticketTypes[0].id

# 2. Update event
$updateBody = @{
  name = "Updated Regression Test Event"
  venue = "Updated Venue"
  status = "PUBLISHED"
  ticketTypes = @(@{
    id = $TICKET_TYPE_ID
    name = "VIP"
    price = 75.00
    totalAvailable = 5
  })
} | ConvertTo-Json -Depth 3

Invoke-RestMethod -Method Put `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN"; "Content-Type" = "application/json" } `
  -Body $updateBody | Out-Null

# 3. Purchase ticket
$tickets = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/tickets" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN"; "Content-Type" = "application/json" } `
  -Body (@{ quantity = 1 } | ConvertTo-Json)

$TICKET_ID = $tickets[0].id

# 4. Validate ticket
Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/ticket-validations" `
  -Headers @{ Authorization = "Bearer $STAFF_TOKEN"; "Content-Type" = "application/json" } `
  -Body (@{ id = $TICKET_ID; method = "MANUAL" } | ConvertTo-Json) | Out-Null

# 5. Export QR code
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/tickets/$TICKET_ID/qr-codes/png" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" } | Out-Null

# 6. Export sales report
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/sales-report.xlsx" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" } | Out-Null

# 7. Cleanup
Invoke-RestMethod -Method Delete `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" } | Out-Null

Write-Host "✅ REG-001 PASS: Complete event lifecycle works"
```

**Expected Result**: All operations succeed without errors

---

## Complete Test Checklist

### Event Management
- [ ] Create event with minimum fields
- [ ] Create event with all fields
- [ ] Update event without id in body
- [ ] Update event with matching id
- [ ] Reject update with mismatched id
- [ ] List events (organizer)
- [ ] Get event details
- [ ] Delete event
- [ ] Get sales dashboard
- [ ] Get attendees report
- [ ] Export sales report

### Published Events
- [ ] List published events
- [ ] Search published events
- [ ] Get published event details

### Ticket Purchase
- [ ] Purchase with default quantity (1)
- [ ] Purchase with custom quantity
- [ ] Reject invalid quantity
- [ ] List my tickets
- [ ] Get ticket details

### QR Code Exports
- [ ] View QR inline (attendee)
- [ ] Download QR PNG (attendee)
- [ ] Download QR PDF (attendee)
- [ ] Organizer views own event QR
- [ ] Staff denied access
- [ ] Admin denied access
- [ ] Idempotency verification
- [ ] Audit logging

### Ticket Type Management
- [ ] Create ticket type (minimum)
- [ ] Create ticket type (full)
- [ ] List ticket types
- [ ] Get ticket type
- [ ] Update ticket type
- [ ] Delete ticket type

### Discount Management
- [ ] Create percentage discount
- [ ] Create fixed amount discount
- [ ] Prevent multiple active discounts
- [ ] Update discount
- [ ] Delete discount
- [ ] List discounts
- [ ] Discount applied on purchase
- [ ] Expired discount not applied

### Ticket Validation
- [ ] Manual validation
- [ ] QR validation
- [ ] List validations for event
- [ ] Get validations by ticket

### Admin Governance
- [ ] Get available roles
- [ ] Assign role
- [ ] Get user roles
- [ ] Revoke role

### Approval Management
- [ ] List users with approval status
- [ ] List pending approvals
- [ ] Approve user
- [ ] Reject user

### Event Staff Management
- [ ] Assign staff to event
- [ ] List event staff
- [ ] Remove staff from event

### Invite Code System
- [ ] Generate invite code (organizer)
- [ ] Generate invite code (staff)
- [ ] Redeem invite code
- [ ] List invite codes
- [ ] List event invite codes
- [ ] Revoke invite code

### Audit Logs
- [ ] View all audit logs (admin)
- [ ] View event audit logs (organizer)
- [ ] View my audit trail

### Approval Gate
- [ ] Pending user blocked
- [ ] Approved user access
- [ ] Rejected user blocked
- [ ] Allowlisted endpoints work
- [ ] Legacy users auto-approved

---

**Testing Complete**: All endpoints regenerated from controllers and DTOs. Run checklist to verify implementation.
