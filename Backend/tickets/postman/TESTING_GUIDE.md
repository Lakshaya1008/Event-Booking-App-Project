# Event Booking App — Complete Testing Guide

**Last Updated:** January 22, 2026  

**Version:** 3.0  
**Target Application:** Event Booking App API v1.0  
**Features Covered:** Complete API Testing with Approval Gate System

---

## Recent Updates

### v2.1 - Event Update API Fix Testing

**IMPORTANT**: The `PUT /api/v1/events/{eventId}` endpoint contract has been fixed:
- `id` field in request body is now **OPTIONAL**
- `eventId` comes **ONLY** from URL path parameter
- If `id` is included in body, it must match URL `eventId`

See [TESTING_GUIDE_ADDENDUM_V2.md](./TESTING_GUIDE_ADDENDUM_V2.md) → Section "Event Update API Testing" for complete test scenarios.

---

## Table of Contents

1. [Testing Overview](#1-testing-overview)
2. [Environment Setup](#2-environment-setup)
3. [Unit Testing](#3-unit-testing)
4. [Integration Testing](#4-integration-testing)
5. [API Testing with Postman](#5-api-testing-with-postman)
6. [**NEW:** QR Code Export Testing](#6-qr-code-export-testing)
7. [**NEW:** Sales Report Export Testing](#7-sales-report-export-testing)
8. [**NEW:** Approval Gate Testing](#8-approval-gate-testing)
9. [Performance Testing](#9-performance-testing)
10. [Security Testing](#10-security-testing)
11. [Load Testing with JMeter](#11-load-testing-with-jmeter)
12. [Metrics and Monitoring](#12-metrics-and-monitoring)
13. [CI/CD Testing Pipeline](#13-cicd-testing-pipeline)
14. [Troubleshooting Guide](#14-troubleshooting-guide)
15. [Test Checklists](#15-test-checklists)

---

## 1. Testing Overview

### 1.1 Test Pyramid

```
                    ┌─────────────────┐
                    │    E2E Tests    │  ← Fewer, Slower, High Confidence
                    │   (Postman/UI)  │
                    ├─────────────────┤
                    │ Integration     │  ← Medium Count, API/DB Tests
                    │   Tests         │
                    ├─────────────────┤
                    │   Unit Tests    │  ← Many, Fast, Isolated
                    │  (JUnit/Mockito)│
                    └─────────────────┘
```

### 1.2 API Endpoints to Test

| Controller | Endpoint Base | Role Required | Priority | New in v2.0 |
|------------|---------------|---------------|----------|-------------|
| **N/A (Planned)** | `/api/v1/auth/register` | **PUBLIC** | **High** | ⚠️ **NOT IMPLEMENTED** |
| EventController | `/api/v1/events` | ORGANIZER | High | - |
| EventController | `/api/v1/events/{id}/sales-report.xlsx` | ORGANIZER | High | ✅ Export |
| PublishedEventController | `/api/v1/published-events` | ATTENDEE | High | - |
| TicketController | `/api/v1/tickets` | ATTENDEE | Critical | - |
| TicketController | `/api/v1/tickets/{id}/qr-codes/view` | ATTENDEE/ORGANIZER | Critical | ✅ Export |
| TicketController | `/api/v1/tickets/{id}/qr-codes/png` | ATTENDEE/ORGANIZER | Critical | ✅ Export |
| TicketController | `/api/v1/tickets/{id}/qr-codes/pdf` | ATTENDEE/ORGANIZER | Critical | ✅ Export |
| TicketTypeController | `/api/v1/events/{id}/ticket-types` | ORGANIZER | High | - |
| DiscountController | `/api/v1/events/{id}/ticket-types/{tid}/discounts` | ORGANIZER | High | ✅ NEW |
| TicketValidationController | `/api/v1/ticket-validations` | STAFF | Critical | - |
| AdminGovernanceController | `/api/v1/admin` | ADMIN | High | - |
| ApprovalController | `/api/v1/admin/approvals/pending` | ADMIN | High | ✅ Approval |
| ApprovalController | `/api/v1/admin/approvals/{id}/approve` | ADMIN | High | ✅ Approval |
| ApprovalController | `/api/v1/admin/approvals/{id}/reject` | ADMIN | High | ✅ Approval |
| EventStaffController | `/api/v1/events/{id}/staff` | ORGANIZER | Medium | - |
| InviteCodeController | `/api/v1/invites` | ADMIN/ORGANIZER | Medium | - |
| AuditController | `/api/v1/audit` | ADMIN/ORGANIZER | Low | - |

**Total Endpoints**: 55+ (including 8 new features: discounts, exports, and approval endpoints)

**⚠️ NOTE**: `/api/v1/auth/register` is configured in SecurityConfig as a public endpoint but the controller implementation does not exist yet. Users must be created manually in Keycloak and then redeem invite codes. See API Documentation Section 0 for current workaround.

### 1.3 Test Categories

- **Functional Tests**: Verify business logic and API contracts
- **Security Tests**: Verify authentication, authorization, and data protection
- **Performance Tests**: Verify response times and throughput
- **Stress Tests**: Verify system behavior under extreme load
- **Regression Tests**: Verify existing functionality after changes
- **✅ NEW - Export Tests**: Verify QR code and report generation, idempotency, filename sanitization
- **✅ NEW - Approval Gate Tests**: Verify business authorization layer, approval workflows
- **✅ NEW - Audit Tests**: Verify new audit actions (QR_CODE_*, SALES_REPORT_EXPORTED)

---

## 2. Environment Setup

### 2.1 Prerequisites

| Component | Version | Port | Purpose |
|-----------|---------|------|---------|
| Java | 21+ | - | Runtime |
| Maven | 3.9+ | - | Build tool |
| PostgreSQL | Latest | 5432 | Database |
| Keycloak | Latest | 9090 | Authentication |
| Docker | Latest | - | Container runtime |
| Postman | Latest | - | API testing |
| JMeter | 5.6+ | - | Load testing |

### 2.2 Quick Start with Docker

```powershell
# Navigate to project directory
cd "C:\Users\LAKSHAYA\Desktop\CODING\java\Projects\project 2 Event booking App\Event-Booking-App-Project\Backend\tickets"

# Start infrastructure services
docker-compose up -d

# Verify services are running
docker-compose ps

# Expected output:
# NAME        SERVICE    STATUS    PORTS
# db          db         running   0.0.0.0:5432->5432/tcp
# adminer     adminer    running   0.0.0.0:8888->8080/tcp
# keycloak    keycloak   running   0.0.0.0:9090->8080/tcp
```

### 2.3 Keycloak Setup

#### 2.3.1 Create Realm
```
1. Access Keycloak Admin: http://localhost:9090
2. Login: admin / admin
3. Click dropdown on left sidebar → Create Realm
4. Realm name: "event-ticket-platform"
5. Click Create
```

#### 2.3.2 Create Client - Step by Step

**Step 1: General Settings**
```
1. Go to Clients → Create client
2. Client ID: event-ticket-platform-app
3. Name: Event Ticket Platform App (optional)
4. Click Next
```

**Step 2: Capability Config** ✅ (Your settings are correct!)
```
┌─────────────────────────────────────────────────────────────┐
│ Client authentication         │  ON  ✅                     │
│ Authorization                 │  ON  ✅                     │
├─────────────────────────────────────────────────────────────┤
│ Authentication flow:                                        │
│   ☑ Standard flow            ✅                             │
│   ☑ Direct access grants     ✅ (Required for testing!)    │
│   ☑ Service account roles    ✅                             │
│   ☐ Implicit flow                                           │
│   ☐ Standard Token Exchange                                 │
│   ☐ OAuth 2.0 Device Authorization Grant                    │
│   ☐ OIDC CIBA Grant                                         │
├─────────────────────────────────────────────────────────────┤
│ PKCE Method                   │  S256  ✅                   │
│ Require DPoP bound tokens     │  OFF   ✅                   │
└─────────────────────────────────────────────────────────────┘
Click Next
```

**Step 3: Login Settings** ⚠️ (Fill these in!)
```
┌─────────────────────────────────────────────────────────────┐
│ Root URL                      │  (LEAVE EMPTY - optional!)  │
├─────────────────────────────────────────────────────────────┤
│ Home URL                      │  (leave empty)              │
├─────────────────────────────────────────────────────────────┤
│ Valid redirect URIs           │  http://localhost:8081/*    │
│                               │  (add http://localhost:3000/*│
│                               │   if you have a frontend)   │
├─────────────────────────────────────────────────────────────┤
│ Valid post logout redirect    │  http://localhost:8081/*    │
│ URIs                          │                             │
├─────────────────────────────────────────────────────────────┤
│ Web origins                   │  *                          │
│                               │  (asterisk for development) │
└─────────────────────────────────────────────────────────────┘
Click Save

NOTE: If "Root URL is not a valid URL" error appears, leave Root URL empty!
```

**Step 4: Get Client Secret**
```
1. After saving, go to Credentials tab
2. Copy the Client secret value
3. Update application.properties:
   keycloak.admin.client-secret=YOUR_COPIED_SECRET
```

#### 2.3.3 Create Realm Roles
```
1. Go to Realm roles (left sidebar)
2. Click "Create role" for each:

   Role Name       │ Description
   ─────────────────┼────────────────────────────────
   ADMIN           │ System administrator
   ORGANIZER       │ Event organizer/creator
   ATTENDEE        │ Event attendee/ticket buyer
   STAFF           │ Event staff for ticket validation
```

#### 2.3.4 Create Test Users

**Create User Steps:**
```
1. Go to Users → Add user
2. Fill in:
   - Username: (see table below)
   - Email: user@test.com
   - Email verified: ON
   - Click Create
3. Go to Credentials tab:
   - Set password
   - Temporary: OFF
4. Go to Role mapping tab:
   - Click "Assign role"
   - Filter by realm roles
   - Select appropriate role(s)
```

**Test Users Table:**

| Username | Password | Email | Roles | Purpose |
|----------|----------|-------|-------|---------|
| `admin.user` | `admin123` | admin@test.com | ADMIN | Admin testing |
| `organizer.user` | `org123` | org@test.com | ORGANIZER | Event management |
| `attendee.user` | `att123` | att@test.com | ATTENDEE | Ticket purchase |
| `staff.user` | `staff123` | staff@test.com | STAFF | Ticket validation |
| `multi.user` | `multi123` | multi@test.com | ORGANIZER, ATTENDEE | Multi-role testing |

#### 2.3.5 Configure Role Mapper (IMPORTANT!)

Your app expects roles in a specific JWT claim format:

```
1. Go to Clients → event-ticket-platform-app
2. Go to Client scopes tab
3. Click "event-ticket-platform-app-dedicated"
4. Click "Add mapper" → "By configuration"
5. Select "User Realm Role"
6. Configure:
   - Name: realm-roles
   - Token Claim Name: roles (or realm_access.roles)
   - Add to ID token: ON
   - Add to access token: ON
   - Add to userinfo: ON
7. Save
```

#### 2.3.6 Verify Setup with Token Test

```powershell
# Get a token to verify setup
$tokenResponse = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=event-ticket-platform-app&username=organizer.user&password=org123&scope=openid"

# Display token (decode at jwt.io to verify roles)
$tokenResponse.access_token
```

### 2.4 Application Configuration

```properties
# src/main/resources/application.properties (already configured)
server.port=8081
spring.datasource.url=jdbc:postgresql://localhost:5432/Event_Booking_App_db
spring.datasource.username=postgres
spring.datasource.password=postgres123
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9090/realms/event-ticket-platform
keycloak.admin.server-url=http://localhost:9090
keycloak.admin.realm=event-ticket-platform
keycloak.admin.client-id=event-ticket-platform-app
keycloak.admin.client-secret=your-client-secret
keycloak.admin.username=admin
keycloak.admin.password=admin
```

### 2.5 Start Application

```powershell
# Build the application
.\mvnw.cmd clean package -DskipTests

# Run the application
.\mvnw.cmd spring-boot:run

# Or run the JAR directly
java -jar target\tickets-0.0.1-SNAPSHOT.jar
```

### 2.6 Verify Application Health

```powershell
# Check if application is running (actuator health endpoint)
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/health"

# Expected response:
# @{status=UP; components=...}

# View available metrics
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/metrics"

# Check application info
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/info"
```

---

## 3. Unit Testing

### 3.1 Test Configuration

The project uses the **same PostgreSQL database** for both development and tests:

```properties
# src/test/resources/application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/Event_Booking_App_db
spring.datasource.username=postgres
spring.datasource.password=postgres123
spring.jpa.hibernate.ddl-auto=update
```

> **Note:** Make sure Docker PostgreSQL is running before running tests!
> ```powershell
> docker-compose up -d db
> ```

### 3.2 Running Unit Tests

```powershell
# Run all tests
.\mvnw.cmd test

# Run specific test class
.\mvnw.cmd test -Dtest=EventBookingAppApplicationTests

# Run with verbose output
.\mvnw.cmd test -Dtest=EventBookingAppApplicationTests -X

# Run tests with coverage report (JaCoCo auto-generates on test phase)
.\mvnw.cmd clean test

# Generate standalone coverage report
.\mvnw.cmd jacoco:report
```

### 3.3 Test Reports

After running tests, find reports at:
- **Surefire Reports**: `target/surefire-reports/`
- **JaCoCo Coverage**: `target/site/jacoco/index.html`

To view the coverage report in browser:
```powershell
# Open coverage report in default browser
Start-Process "target/site/jacoco/index.html"
```

### 3.4 Sample Unit Test Template

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void createEvent_ShouldReturnCreated() throws Exception {
        // Given
        CreateEventRequestDto request = new CreateEventRequestDto();
        request.setName("Test Event");
        // ... set other fields

        // When/Then
        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Event"));
    }
}
```

---

## 4. Integration Testing

### 4.1 Database Integration Tests

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class EventRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    @Autowired
    private EventRepository eventRepository;

    @Test
    void shouldSaveAndRetrieveEvent() {
        Event event = new Event();
        event.setName("Integration Test Event");
        // ...
        Event saved = eventRepository.save(event);
        assertNotNull(saved.getId());
    }
}
```

### 4.2 API Integration Tests

```powershell
# Run integration tests only
.\mvnw.cmd test -Dgroups=integration

# Run with specific profile
.\mvnw.cmd test -Dspring.profiles.active=test
```

---

## 5. API Testing with Postman

### 5.1 Postman Environment Setup

Create a new Postman Environment with these variables:

| Variable | Initial Value | Description |
|----------|---------------|-------------|
| `base_url` | `http://localhost:8081` | Application base URL |
| `keycloak_url` | `http://localhost:9090` | Keycloak URL |
| `realm` | `event-ticket-platform` | Keycloak realm |
| `client_id` | `event-ticket-platform-app` | Client ID |
| `access_token` | (empty) | JWT token (auto-populated) |
| `event_id` | (empty) | Current event UUID |
| `ticket_type_id` | (empty) | Current ticket type UUID |
| `ticket_id` | (empty) | Current ticket UUID |
| `user_id` | (empty) | Current user UUID |

### 5.2 Authentication Pre-request Script

Add this to the Collection's Pre-request Script:

```javascript
// Token Management Pre-request Script
const keycloakUrl = pm.environment.get("keycloak_url");
const realm = pm.environment.get("realm");
const clientId = pm.environment.get("client_id");
const username = pm.environment.get("username");
const password = pm.environment.get("password");

// Check if token exists and is valid
const tokenExpiry = pm.environment.get("token_expiry");
const currentTime = Math.floor(Date.now() / 1000);

if (!tokenExpiry || currentTime >= (tokenExpiry - 60)) {
    // Token expired or missing, get new one
    pm.sendRequest({
        url: `${keycloakUrl}/realms/${realm}/protocol/openid-connect/token`,
        method: 'POST',
        header: {
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: {
            mode: 'urlencoded',
            urlencoded: [
                { key: 'grant_type', value: 'password' },
                { key: 'client_id', value: clientId },
                { key: 'username', value: username },
                { key: 'password', value: password },
                { key: 'scope', value: 'openid profile email' }
            ]
        }
    }, function(err, response) {
        if (!err) {
            const jsonResponse = response.json();
            pm.environment.set("access_token", jsonResponse.access_token);
            pm.environment.set("token_expiry", currentTime + jsonResponse.expires_in);
            console.log("Token refreshed successfully");
        } else {
            console.error("Failed to get token:", err);
        }
    });
}
```

### 5.3 Complete Test Suite Structure

```
📁 Event Booking App Collection
├── 📁 0. Setup & Auth
│   ├── Get Admin Token
│   ├── Get Organizer Token
│   ├── Get Attendee Token
│   └── Get Staff Token
├── 📁 1. Event Management (ORGANIZER)
│   ├── Create Event
│   ├── List My Events
│   ├── Get Event by ID
│   ├── Update Event
│   ├── Get Sales Dashboard
│   ├── Get Attendees Report
│   └── Delete Event
├── 📁 2. Ticket Type Management (ORGANIZER)
│   ├── Create Ticket Type
│   ├── List Ticket Types
│   ├── Get Ticket Type
│   ├── Update Ticket Type
│   └── Delete Ticket Type
├── 📁 3. Published Events (ATTENDEE)
│   ├── List Published Events
│   ├── Search Published Events
│   └── Get Published Event Details
├── 📁 4. Ticket Purchase (ATTENDEE)
│   ├── Purchase Single Ticket
│   ├── Purchase Multiple Tickets
│   ├── List My Tickets
│   ├── Get Ticket Details
│   └── Get Ticket QR Code
├── 📁 5. Ticket Validation (STAFF)
│   ├── Validate Ticket (Manual)
│   ├── Validate Ticket (QR Scan)
│   ├── List Event Validations
│   └── Get Ticket Validation History
├── 📁 6. Admin Governance (ADMIN)
│   ├── Get Available Roles
│   ├── Assign Role to User
│   ├── Get User Roles
│   └── Revoke Role from User
├── 📁 7. Event Staff Management (ORGANIZER)
│   ├── Assign Staff to Event
│   ├── List Event Staff
│   └── Remove Staff from Event
├── 📁 8. Invite Codes (ADMIN/ORGANIZER)
│   ├── Generate Invite Code
│   ├── Redeem Invite Code
│   ├── List Invite Codes
│   ├── List Event Invites
│   └── Revoke Invite Code
├── 📁 9. Audit Logs
│   ├── View All Audit Logs (ADMIN)
│   ├── View Event Audit Logs (ORGANIZER)
│   └── View My Audit Trail
└── 📁 10. Negative Tests & Edge Cases
    ├── Unauthorized Access
    ├── Invalid Token
    ├── Validation Errors
    └── Business Rule Violations
```

### 5.4 Test Scripts for Assertions

#### Response Time Check
```javascript
pm.test("Response time is less than 500ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(500);
});
```

#### Status Code Check
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});
```

#### JSON Schema Validation
```javascript
const schema = {
    type: "object",
    required: ["id", "name", "start", "end", "venue"],
    properties: {
        id: { type: "string", format: "uuid" },
        name: { type: "string" },
        start: { type: "string" },
        end: { type: "string" },
        venue: { type: "string" }
    }
};

pm.test("Schema is valid", function () {
    pm.response.to.have.jsonSchema(schema);
});
```

#### Extract and Save Variables
```javascript
pm.test("Extract event ID", function () {
    const jsonData = pm.response.json();
    pm.environment.set("event_id", jsonData.id);
    pm.environment.set("ticket_type_id", jsonData.ticketTypes[0].id);
});
```

### 5.5 Complete Test Requests

This section provides test requests for all endpoints, including minimum valid payloads, full valid payloads, and invalid examples with expected status codes.

#### 5.5.1 Get Access Token

**Request:**
```
POST {{keycloak_url}}/realms/{{realm}}/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id={{client_id}}
username={{username}}
password={{password}}
scope=openid profile email
```

**Tests:**
```javascript
pm.test("Token received", function () {
    pm.response.to.have.status(200);
    const jsonData = pm.response.json();
    pm.environment.set("access_token", jsonData.access_token);
    pm.environment.set("token_expiry", Date.now() + (jsonData.expires_in * 1000));
});
```

#### 5.5.2 Create Event (ORGANIZER)

**Minimum Valid Payload:**
```
POST {{base_url}}/api/v1/events
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "Test Conference",
  "venue": "Convention Center",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "name": "General Admission",
      "price": 199.99
    }
  ]
}
```

**Full Valid Payload:**
```
POST {{base_url}}/api/v1/events
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "Test Conference {{$timestamp}}",
  "start": "2025-12-15T09:00:00",
  "end": "2025-12-15T18:00:00",
  "venue": "Test Convention Center",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "name": "General Admission",
      "price": 99.99,
      "description": "Standard entry",
      "totalAvailable": 500
    },
    {
      "name": "VIP",
      "price": 299.99,
      "description": "VIP access",
      "totalAvailable": 50
    }
  ]
}
```

**Invalid Payload Examples:**
- Missing `name`: 400 Bad Request
- Missing `venue`: 400 Bad Request
- Missing `status`: 400 Bad Request
- Empty `ticketTypes`: 400 Bad Request
- Invalid `status` value: 400 Bad Request

**Tests:**
```javascript
pm.test("Event created successfully", function () {
    pm.response.to.have.status(201);
});

pm.test("Response has required fields", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('id');
    pm.expect(jsonData).to.have.property('name');
    pm.expect(jsonData).to.have.property('ticketTypes');
});

pm.test("Response fields are correct", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.name).to.eql("Test Conference {{$timestamp}}");
    pm.expect(jsonData.venue).to.eql("Test Convention Center");
    pm.expect(jsonData.ticketTypes).to.be.an("array").that.has.lengthOf(2);
    pm.expect(jsonData.ticketTypes[0]).to.include.keys("id", "name", "price", "description", "totalAvailable");
    pm.expect(jsonData.ticketTypes[1]).to.include.keys("id", "name", "price", "description", "totalAvailable");
});

pm.test("Save event data", function () {
    const jsonData = pm.response.json();
    pm.environment.set("event_id", jsonData.id);
    pm.environment.set("ticket_type_id", jsonData.ticketTypes[0].id);
});
```

#### 5.5.3 Update Event (ORGANIZER)

**Minimum Valid Payload:**
```
PUT {{base_url}}/api/v1/events/{{event_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "Updated Conference",
  "venue": "Updated Venue",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "name": "General Admission",
      "price": 199.99
    }
  ]
}
```

**Full Valid Payload:**
```
PUT {{base_url}}/api/v1/events/{{event_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "Updated Conference {{$timestamp}}",
  "start": "2025-12-15T10:00:00",
  "end": "2025-12-15T19:00:00",
  "venue": "Updated Convention Center",
  "salesStart": "2025-11-01T00:00:00",
  "salesEnd": "2025-12-14T23:59:59",
  "status": "PUBLISHED",
  "ticketTypes": [
    {
      "id": "{{ticket_type_id}}",
      "name": "Updated General Admission",
      "price": 149.99,
      "description": "Updated description",
      "totalAvailable": 400
    }
  ]
}
```

**Invalid Payload Examples:**
- Missing `name`: 400 Bad Request
- Missing `venue`: 400 Bad Request
- Missing `status`: 400 Bad Request
- Empty `ticketTypes`: 400 Bad Request
- ID in body doesn't match URL: 400 Bad Request
- Non-owner organizer: 403 Forbidden
- Non-existent event: 404 Not Found

**Tests:**
```javascript
pm.test("Event updated successfully", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has updated fields", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.name).to.eql("Updated Conference {{$timestamp}}");
    pm.expect(jsonData.venue).to.eql("Updated Convention Center");
    pm.expect(jsonData.ticketTypes).to.be.an("array").that.has.lengthOf(1);
    pm.expect(jsonData.ticketTypes[0]).to.include.keys("id", "name", "price", "description", "totalAvailable");
});

pm.test("No new event ID generated", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.id).to.eql(pm.environment.get("event_id"));
});
```

#### 5.5.4 List Events (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events?page=0&size=20&sort=start,desc
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<ListEventResponseDto>

#### 5.5.5 Get Event (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events/{{event_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - GetEventDetailsResponseDto | 404 Not Found

#### 5.5.6 Delete Event (ORGANIZER)

**Request:**
```
DELETE {{base_url}}/api/v1/events/{{event_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 204 No Content | 403 Forbidden | 404 Not Found

#### 5.5.7 Get Sales Dashboard (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events/{{event_id}}/sales-dashboard
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Map<String, Object>

#### 5.5.8 Get Attendees Report (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events/{{event_id}}/attendees-report
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Map<String, Object>

#### 5.5.9 Export Sales Report (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events/{{event_id}}/sales-report.xlsx
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet

#### 5.5.10 List Published Events (ATTENDEE/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/published-events?q=tech&page=0&size=20&sort=start,asc
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<ListPublishedEventResponseDto>

#### 5.5.11 Get Published Event (ATTENDEE/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/published-events/{{event_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - GetPublishedEventDetailsResponseDto | 404 Not Found

#### 5.5.12 Purchase Ticket (ATTENDEE/ORGANIZER)

**Minimum Valid Payload (uses default quantity=1):**
```
POST {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/tickets
Authorization: Bearer {{access_token}}
Content-Type: application/json

{}
```

**Full Valid Payload:**
```
POST {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/tickets
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "quantity": 2
}
```

**Invalid Payload Examples:**
- quantity < 1: 400 Bad Request
- quantity > 10: 400 Bad Request
- Sold out: 400 Bad Request

**Tests:**
```javascript
pm.test("Tickets purchased", function(){ pm.expect(pm.response.code).to.eql(201); });
const tickets = pm.response.json();
if (tickets && tickets.length) { pm.environment.set('ticket_id', tickets[0].id); }
```

#### 5.5.13 List My Tickets (ATTENDEE/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/tickets?page=0&size=20&sort=id,desc
Authorization: Bearer {{access_token}}
```

**Tests:**
```javascript
const page = pm.response.json();
if (page && page.content && page.content.length) { pm.environment.set('ticket_id', page.content[0].id); }
pm.test('OK', function(){ pm.expect(pm.response.code).to.eql(200); });
```

#### 5.5.14 Get Ticket (ATTENDEE/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/tickets/{{ticket_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - GetTicketResponseDto | 404 Not Found

#### 5.5.15 View QR Code (ATTENDEE/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/view
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - image/png | 403 Forbidden | 404 Not Found

#### 5.5.16 Download QR Code PNG (ATTENDEE/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/png
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - image/png | 403 Forbidden | 404 Not Found

#### 5.5.17 Download QR Code PDF (ATTENDEE/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/tickets/{{ticket_id}}/qr-codes/pdf
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - application/pdf | 403 Forbidden | 404 Not Found

#### 5.5.18 Create Ticket Type (ORGANIZER)

**Minimum Valid Payload:**
```
POST {{base_url}}/api/v1/events/{{event_id}}/ticket-types
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "VIP Pass",
  "price": 499.99
}
```

**Full Valid Payload:**
```
POST {{base_url}}/api/v1/events/{{event_id}}/ticket-types
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "VIP Pass",
  "price": 499.99,
  "description": "Premium access",
  "totalAvailable": 50
}
```

**Invalid Payload Examples:**
- Missing `name`: 400 Bad Request
- Missing `price`: 400 Bad Request
- price <= 0: 400 Bad Request

#### 5.5.19 List Ticket Types (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events/{{event_id}}/ticket-types
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - List<CreateTicketTypeResponseDto>

#### 5.5.20 Get Ticket Type (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - CreateTicketTypeResponseDto | 404 Not Found

#### 5.5.21 Update Ticket Type (ORGANIZER)

**Minimum Valid Payload:**
```
PUT {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "Updated VIP Pass",
  "price": 549.99
}
```

**Full Valid Payload:**
```
PUT {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "name": "Updated VIP Pass",
  "price": 549.99,
  "description": "Updated premium access",
  "totalAvailable": 75
}
```

**Invalid Payload Examples:**
- Missing `name`: 400 Bad Request
- Missing `price`: 400 Bad Request
- price <= 0: 400 Bad Request

#### 5.5.22 Delete Ticket Type (ORGANIZER)

**Request:**
```
DELETE {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 204 No Content | 400 Bad Request | 403 Forbidden | 404 Not Found

#### 5.5.23 Create Discount (ORGANIZER)

**Minimum Valid Payload:**
```
POST {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "discountType": "PERCENTAGE",
  "value": 20.0,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59"
}
```

**Full Valid Payload:**
```
POST {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "discountType": "PERCENTAGE",
  "value": 20.0,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59",
  "active": true,
  "description": "Early Bird Special"
}
```

**Invalid Payload Examples:**
- Missing `discountType`: 400 Bad Request
- Missing `value`: 400 Bad Request
- value <= 0: 400 Bad Request
- validTo before validFrom: 400 Bad Request
- Invalid discountType: 400 Bad Request

#### 5.5.24 Update Discount (ORGANIZER)

**Request:** Same as create, with discount ID in URL

```
PUT {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts/{{discount_id}}
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "discountType": "PERCENTAGE",
  "value": 25.0,
  "validFrom": "2025-11-01T00:00:00",
  "validTo": "2025-11-30T23:59:59",
  "description": "Updated Early Bird Special"
}
```

#### 5.5.25 Delete Discount (ORGANIZER)

**Request:**
```
DELETE {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts/{{discount_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 204 No Content

#### 5.5.26 Get Discount (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts/{{discount_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - DiscountResponseDto | 404 Not Found

#### 5.5.27 List Discounts (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/discounts
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - List<DiscountResponseDto>

#### 5.5.28 Validate Ticket (STAFF/ORGANIZER)

**Minimum Valid Payload:**
```
POST {{base_url}}/api/v1/ticket-validations
Authorization: Bearer {{access_token}}
Content-Type: application/json

{}
```

**Full Valid Payload:**
```
POST {{base_url}}/api/v1/ticket-validations
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "id": "{{ticket_id}}",
  "method": "MANUAL"
}
```

**Expected:** 200 OK - TicketValidationResponseDto

#### 5.5.29 List Validations for Event (STAFF/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/ticket-validations/events/{{event_id}}?page=0&size=20
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<TicketValidationResponseDto>

#### 5.5.30 Get Validations by Ticket (STAFF/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/ticket-validations/tickets/{{ticket_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - List<TicketValidationResponseDto>

#### 5.5.31 Get Available Roles (ADMIN)

**Request:**
```
GET {{base_url}}/api/v1/admin/roles
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - AvailableRolesResponseDto

#### 5.5.32 Assign Role (ADMIN)

**Minimum Valid Payload:**
```
POST {{base_url}}/api/v1/admin/users/{{user_id}}/roles
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "roleName": "ORGANIZER"
}
```

**Full Valid Payload:**
```
POST {{base_url}}/api/v1/admin/users/{{user_id}}/roles
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "roleName": "ORGANIZER"
}
```

**Invalid Payload Examples:**
- Missing `roleName`: 400 Bad Request
- Invalid role name: 400 Bad Request

#### 5.5.33 Get User Roles (ADMIN)

**Request:**
```
GET {{base_url}}/api/v1/admin/users/{{user_id}}/roles
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - UserRolesResponseDto

#### 5.5.34 Revoke Role (ADMIN)

**Request:**
```
DELETE {{base_url}}/api/v1/admin/users/{{user_id}}/roles/STAFF
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - UserRolesResponseDto

#### 5.5.35 List Users with Approval Status (ADMIN)

**Request:**
```
GET {{base_url}}/api/v1/admin/approvals?page=0&size=20
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<UserApprovalDto>

#### 5.5.36 List Pending Approvals (ADMIN)

**Request:**
```
GET {{base_url}}/api/v1/admin/approvals/pending?page=0&size=20
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<UserApprovalDto>

#### 5.5.37 Approve User (ADMIN)

**Request:**
```
POST {{base_url}}/api/v1/admin/approvals/{{user_id}}/approve
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Map<String, String>

#### 5.5.38 Reject User (ADMIN)

**Minimum Valid Payload:**
```
POST {{base_url}}/api/v1/admin/approvals/{{user_id}}/reject
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "reason": "Account violates terms of service"
}
```

**Full Valid Payload:**
```
POST {{base_url}}/api/v1/admin/approvals/{{user_id}}/reject
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "reason": "Account violates terms of service or does not meet requirements"
}
```

**Invalid Payload Examples:**
- Missing `reason`: 400 Bad Request
- reason too short (<10 chars): 400 Bad Request
- reason too long (>500 chars): 400 Bad Request

#### 5.5.39 Assign Staff to Event (ORGANIZER)

**Minimum Valid Payload:**
```
POST {{base_url}}/api/v1/events/{{event_id}}/staff
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "userId": "{{user_id}}"
}
```

**Full Valid Payload:**
```
POST {{base_url}}/api/v1/events/{{event_id}}/staff
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "userId": "{{user_id}}"
}
```

**Invalid Payload Examples:**
- Missing `userId`: 400 Bad Request
- User doesn't have STAFF role: 400 Bad Request

#### 5.5.40 List Event Staff (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/events/{{event_id}}/staff
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - EventStaffResponseDto

#### 5.5.41 Remove Staff from Event (ORGANIZER)

**Request:**
```
DELETE {{base_url}}/api/v1/events/{{event_id}}/staff/{{user_id}}
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - EventStaffResponseDto

#### 5.5.42 Generate Invite Code (ADMIN/ORGANIZER)

**Minimum Valid Payload (ORGANIZER):**
```
POST {{base_url}}/api/v1/invites
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "roleName": "ORGANIZER",
  "expirationHours": 48
}
```

**Full Valid Payload (STAFF):**
```
POST {{base_url}}/api/v1/invites
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "roleName": "STAFF",
  "eventId": "{{event_id}}",
  "expirationHours": 48
}
```

**Invalid Payload Examples:**
- Missing `roleName`: 400 Bad Request
- Missing `expirationHours`: 400 Bad Request
- Invalid role name: 400 Bad Request
- eventId required for STAFF: 400 Bad Request

#### 5.5.43 Redeem Invite Code (Any authenticated)

**Minimum Valid Payload:**
```
POST {{base_url}}/api/v1/invites/redeem
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "code": "ABCD-EFGH-IJKL-MNOP"
}
```

**Full Valid Payload:**
```
POST {{base_url}}/api/v1/invites/redeem
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
  "code": "ABCD-EFGH-IJKL-MNOP"
}
```

**Invalid Payload Examples:**
- Missing `code`: 400 Bad Request
- Already redeemed: 400 Bad Request
- Expired: 400 Bad Request

#### 5.5.44 List Invite Codes (ADMIN/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/invites?page=0&size=20&sort=createdAt,desc
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<InviteCodeResponseDto>

#### 5.5.45 List Event Invite Codes (ADMIN/ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/invites/events/{{event_id}}?page=0&size=20
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<InviteCodeResponseDto>

#### 5.5.46 Revoke Invite Code (ADMIN/ORGANIZER)

**Request:**
```
DELETE {{base_url}}/api/v1/invites/{{invite_code_id}}?reason=No%20longer%20needed
Authorization: Bearer {{access_token}}
```

**Expected:** 204 No Content

#### 5.5.47 View All Audit Logs (ADMIN)

**Request:**
```
GET {{base_url}}/api/v1/audit?page=0&size=20&sort=createdAt,desc
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<AuditLogDto>

#### 5.5.48 View Event Audit Logs (ORGANIZER)

**Request:**
```
GET {{base_url}}/api/v1/audit/events/{{event_id}}?page=0&size=20
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<AuditLogDto>

#### 5.5.49 View My Audit Trail (Any authenticated)

**Request:**
```
GET {{base_url}}/api/v1/audit/me?page=0&size=20
Authorization: Bearer {{access_token}}
```

**Expected:** 200 OK - Page<AuditLogDto>
