# Testing Order

To test the Event Booking App backend efficiently, follow this sequence:

1. **User Registration**
   - Register users (with/without invite code)
2. **Invite Code Generation & Redemption**
   - Generate invite codes (ADMIN/ORGANIZER)
   - Redeem invite codes (users)
3. **Event Creation**
   - Create events (ORGANIZER)
4. **Ticket Type Creation**
   - Add ticket types to events (ORGANIZER)
5. **Ticket Purchase**
   - Purchase tickets (ATTENDEE/ORGANIZER)
6. **Discount Creation**
   - Add discounts to ticket types (ORGANIZER)
7. **Ticket Validation**
   - Validate tickets (STAFF/ORGANIZER)
8. **Admin Role Assignment**
   - Assign roles to users (ADMIN)
9. **User Approval/Rejection**
   - Approve/reject users (ADMIN)

---

# API Testing Guide (Updated)

This document lists all API endpoints, their HTTP methods, authentication/authorization requirements, request payloads (with mandatory/optional fields), and business rules. Use this as a reference for QA and development.

---

## AuthController

### POST /api/v1/auth/register
- **Method:** POST
- **Auth:** None
- **Roles:** None
- **Payload:**
  - `email` (String, mandatory)
  - `password` (String, mandatory)
  - `name` (String, mandatory)
  - `inviteCode` (String, optional)
- **Example Payload:**
```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!",
  "name": "John Doe",
  "inviteCode": "ABCD-EFGH-IJKL-MNOP"
}
```
- **Business Rules:** Email unique, password complexity, invite code format

---

## InviteCodeController

### POST /api/v1/invites
- **Method:** POST
- **Auth:** JWT
- **Roles:** ADMIN or ORGANIZER
- **Payload:**
  - `roleName` (String, mandatory; ORGANIZER, ATTENDEE, STAFF)
  - `expirationHours` (Integer, mandatory)
  - `eventId` (UUID, optional, mandatory if roleName=STAFF)
- **Example Payload:**
```json
{
  "roleName": "STAFF",
  "expirationHours": 24,
  "eventId": "c0a80123-4567-8901-2345-678901234567"
}
```
- **Business Rules:** Role-specific rules, event ownership

### POST /api/v1/invites/redeem
- **Method:** POST
- **Auth:** JWT
- **Roles:** Any authenticated
- **Payload:**
  - `code` (String, mandatory)
- **Example Payload:**
```json
{
  "code": "ABCD-EFGH-IJKL-MNOP"
}
```
- **Business Rules:** Single-use, atomic, audit

---

## EventController

### POST /api/v1/events
- **Method:** POST
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:**
  - `name` (String, mandatory)
  - `venue` (String, mandatory)
  - `status` (EventStatusEnum, mandatory)
  - `ticketTypes` (List, mandatory)
  - `start` (LocalDateTime, optional)
  - `end` (LocalDateTime, optional)
  - `salesStart` (LocalDateTime, optional)
  - `salesEnd` (LocalDateTime, optional)
- **Example Payload:**
```json
{
  "name": "Spring Fest",
  "venue": "Main Hall",
  "status": "DRAFT",
  "ticketTypes": [
    { "name": "VIP", "price": 100.0 },
    { "name": "General", "price": 50.0 }
  ],
  "start": "2026-03-01T10:00:00",
  "end": "2026-03-01T18:00:00",
  "salesStart": "2026-02-20T09:00:00",
  "salesEnd": "2026-03-01T09:00:00"
}
```

---

## TicketTypeController

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets
- **Method:** POST
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:**
  - `quantity` (Integer, mandatory, default 1)
- **Example Payload:**
```json
{
  "quantity": 2
}
```

### POST /api/v1/events/{eventId}/ticket-types
- **Method:** POST
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:**
  - `name` (String, mandatory)
  - `price` (Double, mandatory)
  - `description` (String, optional)
  - `totalAvailable` (Integer, optional)
- **Example Payload:**
```json
{
  "name": "VIP",
  "price": 100.0,
  "description": "Access to VIP lounge",
  "totalAvailable": 50
}
```

---

## DiscountController

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- **Method:** POST
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:**
  - `discountType` (DiscountType, mandatory)
  - `value` (BigDecimal, mandatory)
  - `validFrom` (LocalDateTime, mandatory)
  - `validTo` (LocalDateTime, mandatory)
  - `active` (Boolean, optional)
  - `description` (String, optional)
- **Example Payload:**
```json
{
  "discountType": "PERCENTAGE",
  "value": 10.0,
  "validFrom": "2026-02-25T00:00:00",
  "validTo": "2026-03-01T00:00:00",
  "active": true,
  "description": "Early bird discount"
}
```

---

## AdminGovernanceController

### POST /api/v1/admin/users/{userId}/roles
- **Method:** POST
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:**
  - `roleName` (String, mandatory)
- **Example Payload:**
```json
{
  "roleName": "ORGANIZER"
}
```

---

## ApprovalController

### POST /api/v1/admin/approvals/{userId}/reject
- **Method:** POST
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:**
  - `reason` (String, mandatory)
- **Example Payload:**
```json
{
  "reason": "Insufficient documentation provided."
}
```

---

## TicketValidationController

### POST /api/v1/ticket-validations
- **Method:** POST
- **Auth:** JWT
- **Roles:** STAFF or ORGANIZER
- **Payload:**
  - `id` (UUID, mandatory)
  - `method` (TicketValidationMethod, mandatory)
- **Example Payload:**
```json
{
  "id": "c0a80123-4567-8901-2345-678901234567",
  "method": "QR_CODE"
}
```

---

# End of Guide

This document covers all endpoints requiring request bodies, with mandatory and optional fields, example payloads, and authentication details. For GET/DELETE endpoints, refer to the original guide for path and auth details.
