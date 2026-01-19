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
- **Request Body**: Does NOT require `id` field
- **Defensive Check**: If `id` is provided in body, it must match URL `eventId`
- **Backward Compatible**: Clients sending matching `id` still work

**Access Control**:
- ORGANIZER: Can update their own events only
- ADMIN: NO bypass (must own event)
- ATTENDEE/STAFF: NO access

**Endpoint**:
`PUT /api/v1/events/{eventId}`

### Test Scenarios

#### EU-001: Update Event Without ID in Body (Recommended)

**Objective**: Verify event update works without `id` in request body

```powershell
# Setup
$BASE_URL = "http://localhost:8081"
$ORGANIZER_TOKEN = "<organizer-jwt>"
$EVENT_ID = "<event-uuid-owned-by-organizer>"

# Request body WITHOUT id field
$body = @{
    name = "Updated Tech Conference 2025"
    start = "2025-12-15T10:00:00"
    end = "2025-12-15T19:00:00"
    venue = "Main Hall - Updated"
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

Write-Host "✅ EU-001 PASS: Event updated successfully without id in body"
```

**Expected Result**: 200 OK with updated event details

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
    name = "Early Bird Special"
    discountType = "PERCENTAGE"
    value = 20.0
    validFrom = "2025-11-01T00:00:00"
    validTo = "2025-11-30T23:59:59"
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
assert ($response.name -eq "Early Bird Special")
assert ($response.discountType -eq "PERCENTAGE")
assert ($response.value -eq 20.0)

Write-Host "✅ DISC-001 PASS"
```

**Expected Result**: 201 Created with discount details

---

#### DISC-002: Create Fixed Amount Discount

**Objective**: Verify fixed amount discount creation

```powershell
$body = @{
    name = "Holiday Discount"
    discountType = "FIXED_AMOUNT"
    value = 50.00
    validFrom = "2025-12-01T00:00:00"
    validTo = "2025-12-25T23:59:59"
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
    name = "Conflicting Discount"
    discountType = "PERCENTAGE"
    value = 15.0
    validFrom = "2025-11-15T00:00:00"
    validTo = "2025-12-15T23:59:59"
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
    name = "Invalid Discount"
    discountType = "PERCENTAGE"
    value = 150.0
    validFrom = "2026-01-01T00:00:00"
    validTo = "2026-01-31T23:59:59"
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
    name = "Early Bird Special - Updated"
    discountType = "PERCENTAGE"
    value = 25.0
    validFrom = "2025-11-01T00:00:00"
    validTo = "2025-11-30T23:59:59"
} | ConvertTo-Json

$response = Invoke-RestMethod -Method Put `
  -Uri "$BASE_URL/api/v1/events/$EVENT_ID/ticket-types/$TICKET_TYPE_ID/discounts/$DISCOUNT_ID" `
  -Headers @{ 
    Authorization = "Bearer $ORGANIZER_TOKEN"
    "Content-Type" = "application/json"
  } `
  -Body $body

assert ($response.value -eq 25.0)
assert ($response.name -eq "Early Bird Special - Updated")
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
    name = "Unauthorized Discount"
    discountType = "PERCENTAGE"
    value = 10.0
    validFrom = "2026-01-01T00:00:00"
    validTo = "2026-01-31T23:59:59"
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
    name = "Test Purchase Discount"
    discountType = "PERCENTAGE"
    value = 30.0
    validFrom = "2026-01-01T00:00:00"
    validTo = "2026-12-31T23:59:59"
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
    name = "Expired Discount"
    discountType = "PERCENTAGE"
    value = 50.0
    validFrom = "2024-01-01T00:00:00"
    validTo = "2024-12-31T23:59:59"
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

$response.Content | Set-Content -Path "qr-test.pdf" -Encoding Byte

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

assert ($viewedLogs.Count -gt 0) "Should have QR_CODE_VIEWED logs"
assert ($pngLogs.Count -gt 0) "Should have QR_CODE_DOWNLOADED_PNG logs"
assert ($pdfLogs.Count -gt 0) "Should have QR_CODE_DOWNLOADED_PDF logs"

Write-Host "✅ QR-009 PASS: Audit logging verified"
```

**Expected Result**: 3 audit log entries with correct actions

---

### QR Export Test Checklist

**Access Control** (Priority: Critical):
- [ ] QR-001: ATTENDEE views own ticket → 200 OK
- [ ] QR-002: ATTENDEE views other's ticket → 403 Forbidden
- [ ] QR-005: ORGANIZER views ticket from own event → 200 OK
- [ ] QR-006: STAFF views any ticket → 403 Forbidden
- [ ] QR-007: ADMIN views any ticket → 403 Forbidden

**Format & Content** (Priority: High):
- [ ] QR-003: PNG download with sanitized filename
- [ ] QR-004: PDF download with ticket details
- [ ] View endpoint returns inline disposition
- [ ] Cache-Control header present on view endpoint

**Idempotency** (Priority: High):
- [ ] QR-008: Same ticket produces identical QR
- [ ] No state changes during export

**Audit** (Priority: Medium):
- [ ] QR-009: QR_CODE_VIEWED logged
- [ ] QR_CODE_DOWNLOADED_PNG logged
- [ ] QR_CODE_DOWNLOADED_PDF logged

---

## Sales Report Export Testing

### Overview

**Endpoint**: `GET /api/v1/events/{eventId}/sales-report.xlsx`

**Access Control**:
- ORGANIZER: Must own event (enforced in service)
- ADMIN: NO bypass (must own event)
- ATTENDEE/STAFF: NO access

**Data Source**: Reuses `EventService.getSalesDashboard()` (single source of truth)

### Test Scenarios

#### SR-001: ORGANIZER Exports Own Event

**Objective**: Verify organizer can export sales report

```powershell
$ORGANIZER_TOKEN = "<organizer-jwt>"
$OWN_EVENT_ID = "<event-owned-by-organizer>"

$response = Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/events/$OWN_EVENT_ID/sales-report.xlsx" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" }

assert ($response.StatusCode -eq 200)
assert ($response.Headers["Content-Type"] -match "spreadsheetml.sheet")
assert ($response.Headers["Content-Disposition"] -match "attachment")
assert ($response.Headers["Content-Disposition"] -match "sales_report_\d{8}_\d{6}\.xlsx")
assert ($response.Content.Length -gt 5000)

$response.Content | Set-Content -Path "sales-report.xlsx" -Encoding Byte

Write-Host "✅ SR-001 PASS"
```

**Expected Result**: 200 OK, Excel file with timestamp in filename

---

#### SR-002: ORGANIZER Attempts to Export Other Event

**Objective**: Verify ownership enforcement

```powershell
$OTHER_EVENT_ID = "<event-owned-by-other-organizer>"

try {
    Invoke-WebRequest -Method Get `
      -Uri "$BASE_URL/api/v1/events/$OTHER_EVENT_ID/sales-report.xlsx" `
      -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" }
    Write-Host "❌ SR-002 FAIL: Should be denied"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ SR-002 PASS"
}
```

**Expected Result**: 403 Forbidden

---

#### SR-003: ADMIN Denied Export (No Bypass)

**Objective**: Verify ADMIN does NOT bypass ownership

```powershell
$ADMIN_TOKEN = "<admin-jwt>"
$ANY_EVENT_ID = "<any-event-uuid>"

try {
    Invoke-WebRequest -Method Get `
      -Uri "$BASE_URL/api/v1/events/$ANY_EVENT_ID/sales-report.xlsx" `
      -Headers @{ Authorization = "Bearer $ADMIN_TOKEN" }
    Write-Host "❌ SR-003 FAIL: ADMIN should NOT bypass"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ SR-003 PASS"
}
```

**Expected Result**: 403 Forbidden

---

#### SR-004: ATTENDEE Denied Export

**Objective**: Verify ATTENDEE has no access

```powershell
$ATTENDEE_TOKEN = "<attendee-jwt>"

try {
    Invoke-WebRequest -Method Get `
      -Uri "$BASE_URL/api/v1/events/$ANY_EVENT_ID/sales-report.xlsx" `
      -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN" }
    Write-Host "❌ SR-004 FAIL: ATTENDEE should be denied"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ SR-004 PASS"
}
```

**Expected Result**: 403 Forbidden

---

#### SR-005: Data Consistency with Dashboard

**Objective**: Verify Excel data matches dashboard API

```powershell
# Get dashboard data
$dashboardData = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events/$OWN_EVENT_ID/sales-dashboard" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" }

# Download Excel
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/events/$OWN_EVENT_ID/sales-report.xlsx" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" } `
  -OutFile "sales-verify.xlsx"

# Manual verification required: Open Excel and compare with dashboard data
Write-Host "Dashboard Total Revenue: $$($dashboardData.totalRevenue)"
Write-Host "Dashboard Total Sold: $($dashboardData.totalTicketsSold)"
Write-Host "Please verify Excel matches these values"
Write-Host "✅ SR-005: Manual verification required"
```

**Expected Result**: Excel data matches dashboard data exactly

---

#### SR-006: Audit Log Verification

**Objective**: Verify SALES_REPORT_EXPORTED audit action

```powershell
Invoke-WebRequest -Method Get `
  -Uri "$BASE_URL/api/v1/events/$OWN_EVENT_ID/sales-report.xlsx" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" } | Out-Null

Start-Sleep -Seconds 1

$auditLogs = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/audit/me?page=0&size=10" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN" }

$exportLogs = $auditLogs.content | Where-Object { $_.action -eq "SALES_REPORT_EXPORTED" }

assert ($exportLogs.Count -gt 0) "Should have export logs"
assert ($exportLogs[0].resourceId -eq $OWN_EVENT_ID) "Should log correct eventId"

Write-Host "✅ SR-006 PASS"
```

**Expected Result**: SALES_REPORT_EXPORTED audit log created

---

### Sales Export Test Checklist

**Access Control** (Priority: Critical):
- [ ] SR-001: ORGANIZER exports own event → 200 OK
- [ ] SR-002: ORGANIZER exports other event → 403 Forbidden
- [ ] SR-003: ADMIN exports any event → 403 Forbidden
- [ ] SR-004: ATTENDEE exports any event → 403 Forbidden

**Format** (Priority: High):
- [ ] Excel file opens without errors
- [ ] Filename includes timestamp
- [ ] Content-Type is correct
- [ ] Content-Disposition is attachment

**Content** (Priority: High):
- [ ] Contains summary section
- [ ] Contains breakdown table
- [ ] Currency formatting applied
- [ ] Headers are styled

**Data Consistency** (Priority: Critical):
- [ ] SR-005: Data matches dashboard API

**Audit** (Priority: Medium):
- [ ] SR-006: SALES_REPORT_EXPORTED logged

---

## Approval Gate Testing

### Overview

**Purpose**: Business-level authorization after Keycloak authentication

**Approval States**:
- PENDING → 403 Forbidden
- APPROVED → Full access
- REJECTED → 403 Forbidden
- NULL (legacy) → Auto-approved

**Allowlisted Endpoints** (bypass approval):
- `/api/v1/auth/register`
- `/actuator/health`
- `/actuator/info`
- `/api/v1/invites/redeem`

### Test Scenarios

#### AG-001: PENDING User Blocked

**Objective**: Verify PENDING users cannot access business operations

```powershell
# Setup: Get token for PENDING user
$PENDING_TOKEN = "<token-for-pending-user>"

try {
    Invoke-RestMethod -Method Get `
      -Uri "$BASE_URL/api/v1/events" `
      -Headers @{ Authorization = "Bearer $PENDING_TOKEN" }
    Write-Host "❌ AG-001 FAIL: PENDING user should be blocked"
    exit 1
} catch {
    $errorResponse = $_.ErrorDetails.Message | ConvertFrom-Json
    assert ($errorResponse.error -eq "APPROVAL_PENDING")
    assert ($errorResponse.status -eq "403")
    Write-Host "✅ AG-001 PASS"
}
```

**Expected Result**: 403 with APPROVAL_PENDING error

---

#### AG-002: ADMIN Approves User

**Objective**: Verify approval workflow

```powershell
$ADMIN_TOKEN = "<admin-jwt>"
$PENDING_USER_ID = "<pending-user-uuid>"

$response = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/admin/users/$PENDING_USER_ID/approve" `
  -Headers @{ Authorization = "Bearer $ADMIN_TOKEN" }

assert ($response.approvalStatus -eq "APPROVED")
Write-Host "✅ AG-002 PASS"
```

**Expected Result**: User approved successfully

---

#### AG-003: APPROVED User Accesses Operations

**Objective**: Verify approved users have full access

```powershell
# After approval, user should have access
$response = Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events" `
  -Headers @{ Authorization = "Bearer $PENDING_TOKEN" }  # Now approved

assert ($response -ne $null)
Write-Host "✅ AG-003 PASS"
```

**Expected Result**: 200 OK

---

#### AG-004: REJECTED User Blocked

**Objective**: Verify rejected users are permanently blocked

```powershell
$rejectBody = @{ reason = "Test rejection" } | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/admin/users/$PENDING_USER_ID/reject" `
  -Headers @{ Authorization = "Bearer $ADMIN_TOKEN"; "Content-Type" = "application/json" } `
  -Body $rejectBody

try {
    Invoke-RestMethod -Method Get `
      -Uri "$BASE_URL/api/v1/events" `
      -Headers @{ Authorization = "Bearer $PENDING_TOKEN" }
    Write-Host "❌ AG-004 FAIL: REJECTED user should be blocked"
    exit 1
} catch {
    $errorResponse = $_.ErrorDetails.Message | ConvertFrom-Json
    assert ($errorResponse.error -eq "APPROVAL_REJECTED")
    Write-Host "✅ AG-004 PASS"
}
```

**Expected Result**: 403 with APPROVAL_REJECTED error

---

#### AG-005: Allowlist Verification

**Objective**: Verify allowlisted endpoints bypass approval

```powershell
# Health check (no auth)
$health = Invoke-RestMethod -Uri "$BASE_URL/actuator/health"
assert ($health.status -eq "UP")

# Info (no auth)
$info = Invoke-RestMethod -Uri "$BASE_URL/actuator/info"
assert ($info -ne $null)

Write-Host "✅ AG-005 PASS: Allowlisted endpoints accessible"
```

**Expected Result**: All allowlisted endpoints accessible

---

#### AG-006: ADMIN Subject to Approval

**Objective**: Verify even ADMIN must be approved

```powershell
$PENDING_ADMIN_TOKEN = "<token-for-pending-admin>"

try {
    Invoke-RestMethod -Method Get `
      -Uri "$BASE_URL/api/v1/admin/roles" `
      -Headers @{ Authorization = "Bearer $PENDING_ADMIN_TOKEN" }
    Write-Host "❌ AG-006 FAIL: PENDING ADMIN should be blocked"
    exit 1
} catch {
    assert ($_.Exception.Response.StatusCode -eq 403)
    Write-Host "✅ AG-006 PASS: ADMIN subject to approval"
}
```

**Expected Result**: 403 Forbidden

---

### Approval Gate Test Checklist

**Approval States** (Priority: Critical):
- [ ] AG-001: PENDING user blocked → 403 APPROVAL_PENDING
- [ ] AG-003: APPROVED user has access → 200 OK
- [ ] AG-004: REJECTED user blocked → 403 APPROVAL_REJECTED

**Approval Workflow** (Priority: High):
- [ ] AG-002: ADMIN can approve users
- [ ] ADMIN can reject users with reason
- [ ] Approval updates database status

**Allowlist** (Priority: Critical):
- [ ] AG-005: Health check accessible
- [ ] Info endpoint accessible
- [ ] Registration accessible
- [ ] Invite redemption accessible for PENDING users

**Security** (Priority: Critical):
- [ ] AG-006: ADMIN subject to approval (no bypass)
- [ ] SAFE-BY-DEFAULT: Non-allowlisted paths require approval

---

## Regression Test Suite

### Priority 1: Critical Path (Must Pass)

```powershell
# Authentication
✓ Get token from Keycloak
✓ Token contains roles

# QR Exports (Critical)
✓ QR-001: ATTENDEE views own QR
✓ QR-002: ATTENDEE denied other's QR
✓ QR-007: ADMIN denied QR (no bypass)

# Sales Export (Critical)
✓ SR-001: ORGANIZER exports own event
✓ SR-002: ORGANIZER denied other event
✓ SR-003: ADMIN denied (no bypass)

# Approval Gate (Critical)
✓ AG-001: PENDING user blocked
✓ AG-003: APPROVED user has access
✓ AG-006: ADMIN subject to approval
```

### Priority 2: High Importance

```powershell
# QR Exports
✓ QR-003: PNG download with filename
✓ QR-004: PDF download
✓ QR-008: Idempotency

# Sales Export
✓ SR-004: ATTENDEE denied
✓ SR-005: Data consistency

# Approval Gate
✓ AG-002: Approval workflow
✓ AG-005: Allowlist verification
```

### Priority 3: Medium Importance

```powershell
# Audit Logging
✓ QR-009: QR audit logs
✓ SR-006: Sales export audit logs

# Additional Coverage
✓ QR-005: ORGANIZER access
✓ QR-006: STAFF denied
✓ AG-004: Rejection workflow
```

---

## Complete Test Checklist

### Event Update API Tests (CRITICAL)
- [ ] EU-001: Update event without id in body → 200 OK
- [ ] EU-002: Update event with matching id → 200 OK
- [ ] EU-003: Update event with mismatched id → 400 Bad Request
- [ ] EU-004: Update with missing required fields → 400 Bad Request
- [ ] EU-005: Update other organizer's event → 403 Forbidden
- [ ] EU-006: Update non-existent event → 404 Not Found
- [ ] EU-007: Update with empty ticketTypes → 400 Bad Request

### Functional Tests
- [ ] All new endpoints return correct status codes
- [ ] Request/response formats match API spec
- [ ] Error messages are clear and actionable
- [ ] Filenames are properly sanitized
- [ ] Discount calculations are accurate
- [ ] Only one active discount per ticket type enforced

### Security Tests
- [ ] Access control enforced correctly
- [ ] Ownership checks cannot be bypassed
- [ ] ADMIN does NOT bypass business rules
- [ ] Approval gate applies to all non-allowlisted paths
- [ ] JWT validation happens before approval check
- [ ] Non-owners cannot manage discounts
- [ ] Event update id mismatch rejected (defensive check)

### Performance Tests
- [ ] QR generation < 200ms (p95)
- [ ] Excel generation < 500ms (p95)
- [ ] Approval check < 10ms (p95)
- [ ] Discount lookup < 50ms (p95)

### Business Logic Tests
- [ ] Discounts applied automatically at purchase time
- [ ] Expired discounts not applied
- [ ] Percentage discounts validated (0-100)
- [ ] Fixed amount discounts do not exceed ticket price
- [ ] Discount data saved with tickets for reporting

### Idempotency Tests
- [ ] Same QR downloaded multiple times → identical
- [ ] Same Excel downloaded multiple times → consistent
- [ ] No side effects during read operations
- [ ] Discount queries return consistent results

### Audit Tests
- [ ] All new operations logged
- [ ] Audit logs include all required fields
- [ ] Audit logs are immutable

### Regression Tests
- [ ] Existing endpoints still work
- [ ] Existing security rules maintained
- [ ] No breaking changes introduced
- [ ] Event update backward compatible (matching id works)

---

## Running the Complete Test Suite

```powershell
# Setup
$BASE_URL = "http://localhost:8081"
$KEYCLOAK_URL = "http://localhost:9090"
$REALM = "event-ticket-platform"

# Get tokens for all roles
$ADMIN_TOKEN = Get-Token -Role "ADMIN"
$ORGANIZER_TOKEN = Get-Token -Role "ORGANIZER"
$ATTENDEE_TOKEN = Get-Token -Role "ATTENDEE"
$STAFF_TOKEN = Get-Token -Role "STAFF"
$PENDING_TOKEN = Get-Token -ApprovalStatus "PENDING"

# Run Event Update Tests (CRITICAL - API Contract Fix)
Write-Host "`n=== Event Update API Tests ===`n"
Run-Test EU-001
Run-Test EU-002
Run-Test EU-003
Run-Test EU-004
Run-Test EU-005
Run-Test EU-006
Run-Test EU-007

# Run QR Export Tests
Write-Host "`n=== QR Export Tests ===`n"
Run-Test QR-001
Run-Test QR-002
Run-Test QR-003
Run-Test QR-004
Run-Test QR-005
Run-Test QR-006
Run-Test QR-007
Run-Test QR-008
Run-Test QR-009

# Run Sales Export Tests
Write-Host "`n=== Sales Export Tests ===`n"
Run-Test SR-001
Run-Test SR-002
Run-Test SR-003
Run-Test SR-004
Run-Test SR-005
Run-Test SR-006

# Run Discount Management Tests
Write-Host "`n=== Discount Management Tests ===`n"
Run-Test DISC-001
Run-Test DISC-002
Run-Test DISC-003
Run-Test DISC-004
Run-Test DISC-005
Run-Test DISC-006
Run-Test DISC-007
Run-Test DISC-008
Run-Test DISC-009
Run-Test DISC-010

# Run Approval Gate Tests
Write-Host "`n=== Approval Gate Tests ===`n"
Run-Test AG-001
Run-Test AG-002
Run-Test AG-003
Run-Test AG-004
Run-Test AG-005
Run-Test AG-006

# Summary
Write-Host "`n=== Test Summary ===`n"
Write-Host "Total Tests: 38"
Write-Host "Passed: $passedCount"
Write-Host "Failed: $failedCount"
```

---

## Test Data Setup

### Creating Test Users

```powershell
# PENDING user (for approval gate tests)
Create-User -Email "pending@test.com" -Role "ATTENDEE" -ApprovalStatus "PENDING"

# APPROVED users (for functional tests)
Create-User -Email "attendee@test.com" -Role "ATTENDEE" -ApprovalStatus "APPROVED"
Create-User -Email "organizer@test.com" -Role "ORGANIZER" -ApprovalStatus "APPROVED"
Create-User -Email "staff@test.com" -Role "STAFF" -ApprovalStatus "APPROVED"
Create-User -Email "admin@test.com" -Role "ADMIN" -ApprovalStatus "APPROVED"

# REJECTED user (for negative tests)
Create-User -Email "rejected@test.com" -Role "ATTENDEE" -ApprovalStatus "REJECTED"
```

### Creating Test Events

```powershell
# Create event for organizer
$eventBody = @{
    name = "Test Event for QR Export"
    start = "2026-06-01T09:00:00"
    end = "2026-06-01T18:00:00"
    venue = "Test Venue"
    salesStart = "2026-05-01T00:00:00"
    salesEnd = "2026-05-31T23:59:59"
    status = "PUBLISHED"
    ticketTypes = @(@{
        name = "General Admission"
        price = 50.00
        description = "Standard ticket"
        totalAvailable = 100
    })
} | ConvertTo-Json -Depth 5

$event = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events" `
  -Headers @{ Authorization = "Bearer $ORGANIZER_TOKEN"; "Content-Type" = "application/json" } `
  -Body $eventBody

$TEST_EVENT_ID = $event.id
$TEST_TICKET_TYPE_ID = $event.ticketTypes[0].id
```

### Creating Test Tickets

```powershell
# Purchase ticket for attendee
$purchaseBody = @{ quantity = 2 } | ConvertTo-Json

$tickets = Invoke-RestMethod -Method Post `
  -Uri "$BASE_URL/api/v1/events/$TEST_EVENT_ID/ticket-types/$TEST_TICKET_TYPE_ID/tickets" `
  -Headers @{ Authorization = "Bearer $ATTENDEE_TOKEN"; "Content-Type" = "application/json" } `
  -Body $purchaseBody

$TEST_TICKET_ID = $tickets[0].id
```

---

**End of Testing Guide Addendum v2.0**

**Last Updated**: January 19, 2026  
**Next Review**: As needed based on feature additions  
**Maintainer**: Development Team
