# Chapter 6 – LCM Core

A Spring Boot application for **VNF Lifecycle Management (LCM)** in a virtualized network function (VNF) orchestration context (Chapter 6). It provides the core LCM logic and integrates with PostgreSQL for persistence and Apache Kafka for event-driven communication.

## Tech Stack

- **Java 17**
- **Spring Boot 3.2** – Web, Data JPA, Validation, Actuator
- **Spring Kafka** – Apache Kafka integration
- **PostgreSQL 14** – Primary database
- **Lombok** – Boilerplate reduction

## Prerequisites

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose (for local PostgreSQL and Kafka)

## Quick Start

1. **Start infrastructure** (PostgreSQL + Kafka + Zookeeper):

   ```bash
   docker-compose up -d
   ```

2. **Run the application:**

   ```bash
   ./mvnw spring-boot:run
   ```

   To run **without Docker** (in-memory H2, Kafka disabled):

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

3. **Health check:**

   ```text
   http://localhost:8080/actuator/health
   ```

## Configuration

- **Server:** port `8080`
- **Database:** `vnfm_db` on `localhost:5432` (user: `vnfm`, password: `vnfm123`)
- **Kafka:** `localhost:9092`

See `src/main/resources/application.yml` and `docs/FLOW.md` for architecture and flow details.
