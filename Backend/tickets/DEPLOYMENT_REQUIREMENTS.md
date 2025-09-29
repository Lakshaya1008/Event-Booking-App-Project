# Deployment Requirements

This document lists what’s required to deploy the Tickets service safely and reproducibly, based on the current codebase.

Overview
- Stack: Spring Boot 3.5, Java 21, PostgreSQL, Keycloak (JWT resource server), Maven.
- Port: 8081 (configurable via SERVER_PORT).
- API base: /api/v1.

Prerequisites
- Java 21 runtime (JDK 21 for builds).
- PostgreSQL 14+ (managed or self-hosted) with a database and credentials provisioned.
- Keycloak (or compatible OIDC provider) with a realm and client configured; public JWKS available at issuer.
- Optional: Docker engine if using docker-compose for local infra.

Configuration (use environment variables in production)
- Database
  - SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:PORT/DB
  - SPRING_DATASOURCE_USERNAME=...
  - SPRING_DATASOURCE_PASSWORD=...
  - SPRING_JPA_HIBERNATE_DDL_AUTO=validate (recommend for prod; codebase currently uses update)
- Resource server / JWT
  - SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://KEYCLOAK_HOST/realms/REALM
- Server
  - SERVER_PORT=8081 (optional)
- Logging
  - LOGGING_LEVEL_ROOT=INFO (adjust as needed)

Security expectations
- All endpoints require a valid Bearer JWT by default (no explicit SecurityFilterChain present).
- If you need public endpoints (e.g., /api/v1/published-events/**), add a SecurityFilterChain to permit them and disable CSRF for stateless APIs; configure CORS for your frontend.

Database and migrations
- Current: spring.jpa.hibernate.ddl-auto=update.
- Recommended for prod: Flyway or Liquibase with versioned migrations and ddl-auto=validate.
- Ensure DB timezone/connection uses UTC; store timestamps in UTC.

Build
- Tooling: Maven 3.9+
- Command: mvn -DskipTests package
- Artifact: target/tickets-0.0.1-SNAPSHOT.jar

Run
- With environment variables set (see Configuration), run:
  - java -jar target/tickets-0.0.1-SNAPSHOT.jar
- Containerization (optional): add a Dockerfile and build with JDK 21 base; use multi-stage build for smaller images.

Keycloak / Auth setup
- Ensure Keycloak realm exists and a public client or confidential client (with proper flows) is configured.
- The JWT sub (subject) must be a UUID (code parses UUID from jwt.getSubject()).
- Claims preferred_username and email are used by UserProvisioningFilter to seed user records.

Observability (recommended)
- Add Spring Boot Actuator (spring-boot-starter-actuator) and expose minimally:
  - management.endpoints.web.exposure.include=health,info
  - management.endpoint.health.show-details=when_authorized
- Secure actuator endpoints via Spring Security; consider separate management port.

Health checks
- If Actuator enabled: GET /actuator/health (and /actuator/info) for readiness/liveness (with security as configured).
- Without Actuator: rely on server socket readiness and a simple authenticated GET (see Smoke tests below).

CORS and CSRF
- Add explicit CORS configuration for your frontend origin(s).
- Disable CSRF for stateless APIs (if not serving browser forms).

Networking and TLS
- Terminate TLS at a load balancer/ingress or run the app behind a reverse proxy; ensure HTTPS end-to-end where possible.
- Configure forwarded headers (server.forward-headers-strategy=native) behind proxies.

Scaling and resources
- JVM: set -Xms/-Xmx per environment; start with small container heap and observe GC.
- DB pool: tune HikariCP via spring.datasource.hikari.* (maxLifetime, maximumPoolSize, etc.).

Storage and backups
- Database backups according to RPO/RTO.
- No local filesystem persistence required by the service.

Known risks / notes
- Tests use H2; a table column named value in qr_codes conflicts with H2 (reserved identifier) and produces a DDL warning. PostgreSQL in prod is not affected. Consider renaming or quoting in a future migration for test stability.
- application.properties currently includes hardcoded DB credentials; move to environment variables in prod.

Smoke tests (post-deploy)
- Obtain a JWT from Keycloak for a test user.
- Organizer flow:
  - POST /api/v1/events (201) with a minimal body; capture event_id and ticket_type_id.
  - GET /api/v1/events (200), GET /api/v1/events/{eventId} (200).
  - POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets (204).
  - GET /api/v1/tickets (200) and GET /api/v1/tickets/{ticketId} (200).
- Published flow:
  - GET /api/v1/published-events (200) and GET /api/v1/published-events/{eventId} (200) — requires Bearer token unless you permit public.
- QR code:
  - GET /api/v1/tickets/{ticketId}/qr-codes (200 image/png).

Operational runbooks (suggested)
- Rollout: blue/green or rolling; DB migrations run before app rollout.
- Monitoring: 5xx/error rates, DB connection pool, JVM heap/GC, auth failures, ticket purchase errors.
- Alerting: elevated 5xx, DB connectivity issues, auth failures, thread pool exhaustion.

Appendix: Local infra via docker-compose (optional)
- Provided docker-compose.yml starts Postgres, Adminer (http://localhost:8888), and Keycloak (http://localhost:9090) for dev.

