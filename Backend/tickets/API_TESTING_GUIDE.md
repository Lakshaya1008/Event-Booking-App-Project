# API Testing Guide

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
- **Business Rules:** Email unique, password complexity, invite code format

---

## InviteCodeController

### POST /api/v1/invites
- **Method:** POST
- **Auth:** JWT
- **Roles:** ADMIN or ORGANIZER
- **Payload:**
  - `roleName` (String, mandatory)
  - `expirationHours` (Integer, mandatory)
  - `eventId` (UUID, optional, mandatory if roleName=STAFF)
- **Business Rules:** Role-specific rules, event ownership

### POST /api/v1/invites/redeem
- **Method:** POST
- **Auth:** JWT
- **Roles:** Any authenticated
- **Payload:**
  - `code` (String, mandatory)
- **Business Rules:** Single-use, atomic, audit

### DELETE /api/v1/invites/{codeId}
- **Method:** DELETE
- **Auth:** JWT
- **Roles:** ADMIN or ORGANIZER
- **Payload:** None
- **Business Rules:** Only creator or ADMIN, only PENDING codes

### GET /api/v1/invites
- **Method:** GET
- **Auth:** JWT
- **Roles:** ADMIN or ORGANIZER
- **Payload:** None

### GET /api/v1/invites/events/{eventId}
- **Method:** GET
- **Auth:** JWT
- **Roles:** ADMIN or ORGANIZER
- **Payload:** None

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

### PUT /api/v1/events/{eventId}
- **Method:** PUT
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:**
  - `name` (String, mandatory)
  - `venue` (String, mandatory)
  - `status` (EventStatusEnum, mandatory)
  - `ticketTypes` (List, mandatory)
  - `id` (UUID, optional)
  - `start` (LocalDateTime, optional)
  - `end` (LocalDateTime, optional)
  - `salesStart` (LocalDateTime, optional)
  - `salesEnd` (LocalDateTime, optional)

### GET /api/v1/events
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### GET /api/v1/events/{eventId}
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### DELETE /api/v1/events/{eventId}
- **Method:** DELETE
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### GET /api/v1/events/{eventId}/sales-dashboard
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### GET /api/v1/events/{eventId}/attendees-report
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### GET /api/v1/events/{eventId}/sales-report.xlsx
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

---

## TicketTypeController

### POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets
- **Method:** POST
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:**
  - `quantity` (Integer, mandatory, default 1)

### POST /api/v1/events/{eventId}/ticket-types
- **Method:** POST
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:**
  - `name` (String, mandatory)
  - `price` (Double, mandatory)
  - `description` (String, optional)
  - `totalAvailable` (Integer, optional)

### GET /api/v1/events/{eventId}/ticket-types
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- **Method:** PUT
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:**
  - `name` (String, mandatory)
  - `price` (Double, mandatory)
  - `id` (UUID, optional)
  - `description` (String, optional)
  - `totalAvailable` (Integer, optional)

### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}
- **Method:** DELETE
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

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

### PUT /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- **Method:** PUT
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** same as create

### DELETE /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- **Method:** DELETE
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts/{discountId}
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### GET /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

---

## TicketController

### GET /api/v1/tickets
- **Method:** GET
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:** None

### GET /api/v1/tickets/{ticketId}
- **Method:** GET
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:** None

### GET /api/v1/tickets/{ticketId}/qr-codes
- **Method:** GET
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:** None

### GET /api/v1/tickets/{ticketId}/qr-codes/view
- **Method:** GET
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:** None

### GET /api/v1/tickets/{ticketId}/qr-codes/png
- **Method:** GET
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:** None

### GET /api/v1/tickets/{ticketId}/qr-codes/pdf
- **Method:** GET
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:** None

---

## AdminGovernanceController

### POST /api/v1/admin/users/{userId}/roles
- **Method:** POST
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:**
  - `roleName` (String, mandatory)

### DELETE /api/v1/admin/users/{userId}/roles/{roleName}
- **Method:** DELETE
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:** None

### GET /api/v1/admin/users/{userId}/roles
- **Method:** GET
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:** None

### GET /api/v1/admin/roles
- **Method:** GET
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:** None

---

## ApprovalController

### GET /api/v1/admin/approvals/pending
- **Method:** GET
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:** None

### POST /api/v1/admin/approvals/{userId}/approve
- **Method:** POST
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:** None

### POST /api/v1/admin/approvals/{userId}/reject
- **Method:** POST
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:**
  - `reason` (String, mandatory)

### GET /api/v1/admin/approvals
- **Method:** GET
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:** None

---

## AuditController

### GET /api/v1/audit
- **Method:** GET
- **Auth:** JWT
- **Roles:** ADMIN
- **Payload:** None

### GET /api/v1/audit/events/{eventId}
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### GET /api/v1/audit/me
- **Method:** GET
- **Auth:** JWT
- **Roles:** Any authenticated
- **Payload:** None

---

## EventStaffController

### POST /api/v1/events/{eventId}/staff
- **Method:** POST
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:**
  - `userId` (UUID, mandatory)

### DELETE /api/v1/events/{eventId}/staff/{userId}
- **Method:** DELETE
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

### GET /api/v1/events/{eventId}/staff
- **Method:** GET
- **Auth:** JWT
- **Roles:** ORGANIZER
- **Payload:** None

---

## TicketValidationController

### POST /api/v1/ticket-validations
- **Method:** POST
- **Auth:** JWT
- **Roles:** STAFF or ORGANIZER
- **Payload:**
  - `id` (UUID, mandatory)
  - `method` (TicketValidationMethod, mandatory)

### GET /api/v1/ticket-validations/events/{eventId}
- **Method:** GET
- **Auth:** JWT
- **Roles:** STAFF or ORGANIZER
- **Payload:** None

### GET /api/v1/ticket-validations/tickets/{ticketId}
- **Method:** GET
- **Auth:** JWT
- **Roles:** STAFF or ORGANIZER
- **Payload:** None

---

## PublishedEventController

### GET /api/v1/published-events
- **Method:** GET
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:** None

### GET /api/v1/published-events/{eventId}
- **Method:** GET
- **Auth:** JWT
- **Roles:** ATTENDEE or ORGANIZER
- **Payload:** None

---

# End of Guide

