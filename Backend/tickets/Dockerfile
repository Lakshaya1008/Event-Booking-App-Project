# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy pom first so dependency downloads can be cached between builds.
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

# Copy source and build the executable Spring Boot jar.
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as non-root user in the runtime image.
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8081
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

