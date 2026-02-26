# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.5 REST API using Java 21, built with Maven. Base package: `dev.trailhead`.

## Build & Test Commands

```bash
./mvnw compile              # Compile
./mvnw test                 # Run all tests
./mvnw test -Dtest=ClassName          # Run a single test class
./mvnw test -Dtest=ClassName#method   # Run a single test method
./mvnw spring-boot:run      # Run the application
./mvnw package              # Build JAR (output in target/)
```

## Key Dependencies & Stack

- **Persistence**: Spring Data JPA with Flyway migrations (supports PostgreSQL, MySQL, H2)
- **Security**: Spring Security with OAuth2 Resource Server (JWT-based)
- **Validation**: Spring Boot Starter Validation (Jakarta Bean Validation)
- **Mail**: Spring Boot Starter Mail
- **Monitoring**: Spring Boot Actuator
- **Lombok**: Used for boilerplate reduction; annotation processing configured in maven-compiler-plugin

## Architecture Notes

- Database migrations go in `src/main/resources/db/migration/` (Flyway naming: `V1__description.sql`)
- H2 is available as a runtime dependency for local development/testing
- Application config: `src/main/resources/application.properties`
