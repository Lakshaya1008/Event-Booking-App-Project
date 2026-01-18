# Event Booking App — Complete Testing Guide

**Last Updated:** January 19, 2026  
**Version:** 2.0  
**Target Application:** Event Booking App API v1  
**New Features Covered:** QR Code Exports, Sales Report Export, Approval Gate System

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
| EventController | `/api/v1/events` | ORGANIZER | High | - |
| EventController | `/api/v1/events/{id}/sales-report.xlsx` | ORGANIZER | High | ✅ Export |
| PublishedEventController | `/api/v1/published-events` | ATTENDEE | High | - |
| TicketController | `/api/v1/tickets` | ATTENDEE | Critical | - |
| TicketController | `/api/v1/tickets/{id}/qr-codes/view` | ATTENDEE/ORGANIZER | Critical | ✅ Export |
| TicketController | `/api/v1/tickets/{id}/qr-codes/png` | ATTENDEE/ORGANIZER | Critical | ✅ Export |
| TicketController | `/api/v1/tickets/{id}/qr-codes/pdf` | ATTENDEE/ORGANIZER | Critical | ✅ Export |
| TicketTypeController | `/api/v1/events/{id}/ticket-types` | ORGANIZER | High | - |
| TicketValidationController | `/api/v1/ticket-validations` | STAFF | Critical | - |
| AdminGovernanceController | `/api/v1/admin` | ADMIN | High | - |
| AdminGovernanceController | `/api/v1/admin/users/pending` | ADMIN | High | ✅ Approval |
| AdminGovernanceController | `/api/v1/admin/users/{id}/approve` | ADMIN | High | ✅ Approval |
| AdminGovernanceController | `/api/v1/admin/users/{id}/reject` | ADMIN | High | ✅ Approval |
| EventStaffController | `/api/v1/events/{id}/staff` | ORGANIZER | Medium | - |
| InviteCodeController | `/api/v1/invites` | ADMIN/ORGANIZER | Medium | - |
| AuditController | `/api/v1/audit` | ADMIN/ORGANIZER | Low | - |

**Total Endpoints**: 50+ (including 7 new export/approval endpoints)

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
    pm.environment.set("refresh_token", jsonData.refresh_token);
    pm.environment.set("token_expiry", Date.now() + (jsonData.expires_in * 1000));
});
```

#### 5.5.2 Create Event

**Request:**
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
    pm.expect(jsonData.ticketTypes).to.be.an('array').that.has.lengthOf(2);
});

pm.test("Save event data", function () {
    const jsonData = pm.response.json();
    pm.environment.set("event_id", jsonData.id);
    pm.environment.set("ticket_type_id", jsonData.ticketTypes[0].id);
    pm.environment.set("vip_ticket_type_id", jsonData.ticketTypes[1].id);
});
```

#### 5.5.3 Purchase Ticket

**Request:**
```
POST {{base_url}}/api/v1/events/{{event_id}}/ticket-types/{{ticket_type_id}}/tickets
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
    "quantity": 2
}
```

**Tests:**
```javascript
pm.test("Tickets purchased successfully", function () {
    pm.response.to.have.status(201);
});

pm.test("Correct number of tickets returned", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.be.an('array').that.has.lengthOf(2);
});

pm.test("Save first ticket ID", function () {
    const jsonData = pm.response.json();
    pm.environment.set("ticket_id", jsonData[0].id);
});
```

#### 5.5.4 Validate Ticket

**Request:**
```
POST {{base_url}}/api/v1/ticket-validations
Authorization: Bearer {{access_token}}
Content-Type: application/json

{
    "id": "{{ticket_id}}",
    "method": "MANUAL"
}
```

**Tests:**
```javascript
pm.test("Ticket validated successfully", function () {
    pm.response.to.have.status(200);
});

pm.test("Validation response is correct", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('ticketId');
    pm.expect(jsonData).to.have.property('status');
});
```

### 5.6 Running Postman Tests

#### Using Newman (CLI)

```powershell
# Install Newman globally
npm install -g newman

# Run collection with environment
newman run EventBookingApp.postman_collection.json `
  -e EventBookingApp.postman_environment.json `
  --reporters cli,html `
  --reporter-html-export newman-report.html

# Run with iteration data
newman run EventBookingApp.postman_collection.json `
  -e EventBookingApp.postman_environment.json `
  -d test-data.json `
  -n 10

# Run specific folder
newman run EventBookingApp.postman_collection.json `
  -e EventBookingApp.postman_environment.json `
  --folder "1. Event Management"
```

---

## 6. Performance Testing

### 6.1 Performance Metrics to Measure

| Metric | Target | Critical Threshold |
|--------|--------|-------------------|
| Response Time (p50) | < 100ms | < 500ms |
| Response Time (p95) | < 300ms | < 1000ms |
| Response Time (p99) | < 500ms | < 2000ms |
| Throughput | > 1000 req/sec | > 500 req/sec |
| Error Rate | < 0.1% | < 1% |
| CPU Usage | < 70% | < 90% |
| Memory Usage | < 70% | < 85% |

### 6.2 Postman Performance Tests

Add to individual requests:

```javascript
// Response time thresholds
pm.test("Response time is acceptable", function () {
    const responseTime = pm.response.responseTime;
    
    if (responseTime < 100) {
        console.log("✅ Excellent: " + responseTime + "ms");
    } else if (responseTime < 300) {
        console.log("✓ Good: " + responseTime + "ms");
    } else if (responseTime < 500) {
        console.log("⚠ Acceptable: " + responseTime + "ms");
    } else {
        console.log("❌ Slow: " + responseTime + "ms");
    }
    
    pm.expect(responseTime).to.be.below(500);
});

// Response size check
pm.test("Response size is reasonable", function () {
    const responseSize = pm.response.responseSize;
    pm.expect(responseSize).to.be.below(1024 * 100); // 100KB max
});
```

### 6.3 Performance Test Collection

Create a separate collection for performance tests:

```javascript
// Collection-level variable for metrics
let metrics = {
    totalRequests: 0,
    totalTime: 0,
    minTime: Infinity,
    maxTime: 0,
    errors: 0
};

// Test script
pm.test("Record metrics", function () {
    const collectionMetrics = pm.variables.get("metrics") || metrics;
    collectionMetrics.totalRequests++;
    collectionMetrics.totalTime += pm.response.responseTime;
    collectionMetrics.minTime = Math.min(collectionMetrics.minTime, pm.response.responseTime);
    collectionMetrics.maxTime = Math.max(collectionMetrics.maxTime, pm.response.responseTime);
    if (pm.response.code >= 400) {
        collectionMetrics.errors++;
    }
    pm.variables.set("metrics", collectionMetrics);
    
    // Log summary
    console.log("Avg Response Time: " + (collectionMetrics.totalTime / collectionMetrics.totalRequests).toFixed(2) + "ms");
    console.log("Error Rate: " + ((collectionMetrics.errors / collectionMetrics.totalRequests) * 100).toFixed(2) + "%");
});
```

---

## 7. Security Testing

### 7.1 Authentication Tests

```javascript
// Test 1: No token
pm.test("Rejects request without token", function () {
    pm.response.to.have.status(401);
});

// Test 2: Invalid token
pm.test("Rejects invalid token", function () {
    pm.response.to.have.status(401);
});

// Test 3: Expired token
pm.test("Rejects expired token", function () {
    pm.response.to.have.status(401);
});
```

### 7.2 Authorization Tests

| Test Case | User Role | Endpoint | Expected |
|-----------|-----------|----------|----------|
| ATTENDEE accessing admin | ATTENDEE | POST /api/v1/admin/users/{id}/roles | 403 |
| ORGANIZER accessing other's event | ORGANIZER | GET /api/v1/events/{other-id} | 404 |
| STAFF validating unassigned event | STAFF | POST /api/v1/ticket-validations | 403 |
| ADMIN accessing event management | ADMIN | GET /api/v1/events | 403 |

### 7.3 Security Test Scripts

```javascript
// Test unauthorized access
pm.test("Authorization denied for wrong role", function () {
    pm.response.to.have.status(403);
});

// Test for sensitive data leakage
pm.test("No sensitive data in error response", function () {
    const jsonData = pm.response.json();
    pm.expect(JSON.stringify(jsonData)).to.not.include("password");
    pm.expect(JSON.stringify(jsonData)).to.not.include("secret");
    pm.expect(JSON.stringify(jsonData)).to.not.include("stackTrace");
});

// Test rate limiting
pm.test("Rate limit headers present", function () {
    pm.expect(pm.response.headers.has('X-Rate-Limit-Remaining')).to.be.true;
});
```

### 7.4 OWASP Top 10 Checklist

| Vulnerability | Test | Status |
|---------------|------|--------|
| A01 Broken Access Control | Role-based access tests | ⬜ |
| A02 Cryptographic Failures | JWT validation | ⬜ |
| A03 Injection | SQL injection tests | ⬜ |
| A04 Insecure Design | Business logic tests | ⬜ |
| A05 Security Misconfiguration | Error message tests | ⬜ |
| A06 Vulnerable Components | Dependency scan | ⬜ |
| A07 Auth Failures | Token validation | ⬜ |
| A08 Data Integrity | Input validation | ⬜ |
| A09 Security Logging | Audit log verification | ⬜ |
| A10 SSRF | URL validation | ⬜ |

---

## 8. Load Testing with JMeter

### 8.1 JMeter Test Plan Structure

```
Test Plan
├── Thread Group (100 users, 60s ramp-up, 300s duration)
│   ├── Config Elements
│   │   ├── HTTP Header Manager
│   │   ├── HTTP Cookie Manager
│   │   └── CSV Data Set Config (user credentials)
│   ├── Setup Thread Group (Get tokens)
│   ├── Main Thread Group
│   │   ├── GET /api/v1/published-events (50%)
│   │   ├── POST /api/v1/tickets (30%)
│   │   ├── GET /api/v1/tickets (15%)
│   │   └── POST /api/v1/ticket-validations (5%)
│   └── Listeners
│       ├── Summary Report
│       ├── Aggregate Report
│       └── Response Time Graph
```

### 8.2 JMeter CLI Commands

```powershell
# Run load test
jmeter -n -t LoadTest.jmx -l results.jtl -e -o report

# Run with specific properties
jmeter -n -t LoadTest.jmx `
  -Jthreads=100 `
  -Jrampup=60 `
  -Jduration=300 `
  -l results.jtl

# Generate HTML report from results
jmeter -g results.jtl -o html-report
```

### 8.3 Load Test Scenarios

#### Scenario 1: Normal Load
- Users: 50
- Ramp-up: 30 seconds
- Duration: 5 minutes
- Expected: < 200ms response, 0% error rate

#### Scenario 2: Peak Load
- Users: 200
- Ramp-up: 60 seconds
- Duration: 10 minutes
- Expected: < 500ms response, < 0.1% error rate

#### Scenario 3: Stress Test
- Users: 500
- Ramp-up: 120 seconds
- Duration: 15 minutes
- Expected: System degrades gracefully

#### Scenario 4: Spike Test
- Users: 100 → 500 → 100 (spike pattern)
- Duration: 10 minutes
- Expected: Recovery within 30 seconds

### 8.4 Sample JMeter Test Plan (XML)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Event Booking Load Test">
      <boolProp name="TestPlan.functional_mode">false</boolProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Users">
        <stringProp name="ThreadGroup.num_threads">${__P(threads,100)}</stringProp>
        <stringProp name="ThreadGroup.ramp_time">${__P(rampup,60)}</stringProp>
        <stringProp name="ThreadGroup.duration">${__P(duration,300)}</stringProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
      </ThreadGroup>
      <hashTree>
        <!-- Add samplers here -->
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

---

## 9. Metrics and Monitoring

### 9.1 Enable Actuator Endpoints

Add to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Add to `application.properties`:
```properties
# Actuator Configuration
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when_authorized
management.metrics.export.prometheus.enabled=true
```

### 9.2 Key Metrics to Monitor

#### Application Metrics
```powershell
# Health check
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/health"

# Metrics list
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/metrics"

# Specific metric - HTTP requests
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/metrics/http.server.requests"

# JVM memory
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/metrics/jvm.memory.used"

# Prometheus format (for Grafana)
Invoke-RestMethod -Method Get -Uri "http://localhost:8081/actuator/prometheus"
```

### 9.3 Custom Metrics Dashboard

Track these key metrics:

| Metric | Prometheus Query | Alert Threshold |
|--------|-----------------|-----------------|
| Request Rate | `rate(http_server_requests_seconds_count[5m])` | N/A |
| Error Rate | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | > 1% |
| P95 Latency | `histogram_quantile(0.95, http_server_requests_seconds_bucket)` | > 500ms |
| JVM Heap | `jvm_memory_used_bytes{area="heap"}` | > 80% |
| Active Threads | `jvm_threads_live_threads` | > 200 |
| DB Connections | `hikaricp_connections_active` | > 80% pool |

### 9.4 Logging Configuration

Add to `application.properties`:
```properties
# Logging levels
logging.level.com.event.tickets=DEBUG
logging.level.org.springframework.web=INFO
logging.level.org.hibernate.SQL=DEBUG

# Log file
logging.file.name=logs/application.log
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

### 9.5 Metrics Collection Script

```powershell
# metrics-collector.ps1
$BASE_URL = "http://localhost:8081"
$OUTPUT_FILE = "metrics-$(Get-Date -Format 'yyyyMMdd-HHmmss').csv"

# Header
"timestamp,requests_total,requests_per_sec,avg_response_time,error_rate,heap_used_mb" | Out-File $OUTPUT_FILE

while ($true) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    
    try {
        $metrics = Invoke-RestMethod -Uri "$BASE_URL/actuator/metrics/http.server.requests" -TimeoutSec 5
        $heap = Invoke-RestMethod -Uri "$BASE_URL/actuator/metrics/jvm.memory.used?tag=area:heap" -TimeoutSec 5
        
        $total = ($metrics.measurements | Where-Object { $_.statistic -eq "COUNT" }).value
        $totalTime = ($metrics.measurements | Where-Object { $_.statistic -eq "TOTAL_TIME" }).value
        $avgTime = if ($total -gt 0) { ($totalTime / $total) * 1000 } else { 0 }
        $heapMB = $heap.measurements[0].value / 1024 / 1024
        
        "$timestamp,$total,N/A,$([math]::Round($avgTime, 2)),N/A,$([math]::Round($heapMB, 2))" | Add-Content $OUTPUT_FILE
    }
    catch {
        "$timestamp,ERROR,ERROR,ERROR,ERROR,ERROR" | Add-Content $OUTPUT_FILE
    }
    
    Start-Sleep -Seconds 10
}
```

---

## 10. CI/CD Testing Pipeline

### 10.1 GitHub Actions Workflow

```yaml
# .github/workflows/test.yml
name: API Tests

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Cache Maven packages
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
      - name: Run unit tests
        run: ./mvnw test
      - name: Upload test results
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: target/surefire-reports/

  integration-tests:
    needs: unit-tests
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:latest
        env:
          POSTGRES_DB: Event_Booking_App_db
          POSTGRES_PASSWORD: postgres123
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Run integration tests
        run: ./mvnw verify -Dspring.profiles.active=test

  api-tests:
    needs: integration-tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Install Newman
        run: npm install -g newman newman-reporter-htmlextra
      - name: Run Postman tests
        run: |
          newman run postman/EventBookingApp.postman_collection.json \
            -e postman/test-environment.json \
            --reporters cli,htmlextra \
            --reporter-htmlextra-export newman-results.html
      - name: Upload API test results
        uses: actions/upload-artifact@v3
        with:
          name: api-test-results
          path: newman-results.html
```

### 10.2 Pre-commit Hooks

```bash
# .git/hooks/pre-commit
#!/bin/sh
echo "Running tests..."
./mvnw test -q
if [ $? -ne 0 ]; then
    echo "Tests failed. Commit aborted."
    exit 1
fi
```

---

## 11. Troubleshooting Guide

### 11.1 Common Issues and Solutions

| Issue | Symptoms | Solution |
|-------|----------|----------|
| Connection refused | `ECONNREFUSED` | Check if app is running on port 8081 |
| 401 Unauthorized | Token issues | Refresh token, check Keycloak config |
| 403 Forbidden | Role missing | Verify user has required role |
| 500 Internal Error | Server crash | Check logs at `target/logs/` |
| Slow responses | > 1s response | Check DB connections, add indexes |
| Out of memory | OOM errors | Increase JVM heap: `-Xmx1g` |

### 11.2 Diagnostic Commands

```powershell
# Check if services are running
Test-NetConnection -ComputerName localhost -Port 8081  # App
Test-NetConnection -ComputerName localhost -Port 5432  # PostgreSQL
Test-NetConnection -ComputerName localhost -Port 9090  # Keycloak

# Check Docker containers
docker-compose ps
docker-compose logs -f

# Check application logs
Get-Content -Path ".\logs\application.log" -Tail 100 -Wait

# Check database connectivity
psql -h localhost -U postgres -d Event_Booking_App_db -c "SELECT 1"

# Decode JWT token (PowerShell)
$token = "your.jwt.token"
$parts = $token.Split('.')
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($parts[1] + "=="))
```

### 11.3 Debug Mode

```powershell
# Run application in debug mode
.\mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

# Connect debugger to port 5005
```

---

## 12. Test Checklists

### 12.1 Pre-Release Checklist

#### Environment Setup
- [ ] Docker services running (PostgreSQL, Keycloak)
- [ ] Keycloak realm configured
- [ ] Test users created with proper roles
- [ ] Application started successfully
- [ ] Database migrations applied

#### Functional Tests
- [ ] All CRUD operations for Events
- [ ] All CRUD operations for Ticket Types
- [ ] Published events listing and search
- [ ] Ticket purchase (single and multiple)
- [ ] QR code generation
- [ ] Ticket validation (manual and QR)
- [ ] Admin role management
- [ ] Event staff assignment
- [ ] Invite code generation and redemption
- [ ] Audit log creation and retrieval

#### Security Tests
- [ ] Authentication required for all protected endpoints
- [ ] Role-based access control working
- [ ] Token expiration handled
- [ ] Rate limiting working
- [ ] No sensitive data in error responses

#### Performance Tests
- [ ] Response times within SLA (< 500ms)
- [ ] No memory leaks under load
- [ ] Database connection pool not exhausted
- [ ] Concurrent ticket purchases work correctly

### 12.2 API Test Matrix

| Endpoint | GET | POST | PUT | DELETE | Auth | Role |
|----------|-----|------|-----|--------|------|------|
| /api/v1/events | ✅ | ✅ | ✅ | ✅ | ✅ | ORGANIZER |
| /api/v1/events/{id}/ticket-types | ✅ | ✅ | ✅ | ✅ | ✅ | ORGANIZER |
| /api/v1/published-events | ✅ | - | - | - | ✅ | ATTENDEE |
| /api/v1/tickets | ✅ | ✅ | - | - | ✅ | ATTENDEE |
| /api/v1/ticket-validations | ✅ | ✅ | - | - | ✅ | STAFF |
| /api/v1/admin/users/{id}/roles | ✅ | ✅ | - | ✅ | ✅ | ADMIN |
| /api/v1/events/{id}/staff | ✅ | ✅ | - | ✅ | ✅ | ORGANIZER |
| /api/v1/invites | ✅ | ✅ | - | ✅ | ✅ | ADMIN/ORG |
| /api/v1/audit | ✅ | - | - | - | ✅ | ADMIN |

### 12.3 Test Data Requirements

```json
{
  "testEvents": [
    {
      "name": "Published Event",
      "status": "PUBLISHED",
      "ticketTypes": 2
    },
    {
      "name": "Draft Event",
      "status": "DRAFT",
      "ticketTypes": 1
    },
    {
      "name": "Past Event",
      "end": "2024-01-01T00:00:00",
      "ticketTypes": 1
    }
  ],
  "testUsers": [
    { "role": "ADMIN", "count": 1 },
    { "role": "ORGANIZER", "count": 2 },
    { "role": "ATTENDEE", "count": 5 },
    { "role": "STAFF", "count": 2 }
  ],
  "testTickets": {
    "purchased": 10,
    "validated": 5,
    "cancelled": 2
  }
}
```

---

## Quick Reference Card

### PowerShell Variables Setup
```powershell
$BASE_URL = "http://localhost:8081"
$KEYCLOAK_URL = "http://localhost:9090"
$REALM = "event-ticket-platform"
$CLIENT_ID = "event-ticket-platform-app"
```

### Get Token
```powershell
$tokenResponse = Invoke-RestMethod -Method Post `
  -Uri "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" `
  -Headers @{ "Content-Type" = "application/x-www-form-urlencoded" } `
  -Body "grant_type=password&client_id=$CLIENT_ID&username=YOUR_USER&password=YOUR_PASS&scope=openid"
$ACCESS_TOKEN = $tokenResponse.access_token
```

### API Call Template
```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE_URL/api/v1/events" `
  -Headers @{ Authorization = "Bearer $ACCESS_TOKEN" }
```

### Run Tests
```powershell
.\mvnw.cmd test                    # Unit tests
.\mvnw.cmd verify                  # All tests
newman run collection.json         # Postman tests
jmeter -n -t LoadTest.jmx         # Load tests
```

---

**Document Version:** 1.0  
**Created:** January 18, 2026  
**Author:** Event Booking App Team  
**Status:** Production Ready
