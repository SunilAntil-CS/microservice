# Chapter 4 Module 1: Managing Transactions with Sagas

This folder contains a **working Saga example** based on **Chapter 4 Module 1** of the microservices notes: *The Death of ACID and the Birth of Sagas*.

## Modules

| Module | Description |
|--------|-------------|
| **ch4-saga-common** | Shared DTOs: Commands (`CreateTicketCommand`, `CancelCreateTicketCommand`), Replies (`CreateTicketReply`), and `TicketLineItem`. No Spring; plain JAR. |
| **ch4-order-service** | **Orchestrator.** Owns Order aggregate; runs `CreateOrderSaga` (create order → create ticket → approve). Calls Kitchen via HTTP. Port **8081**. |
| **ch4-kitchen-service** | **Participant.** Owns Ticket aggregate; handles create and cancel commands. Port **8082**. |

## Quick Start

```bash
# 1. Install shared library
cd ch4-saga-common && mvn clean install && cd ..

# 2. Start Kitchen (participant) first
cd ch4-kitchen-service && mvn spring-boot:run &
# Wait until "Started KitchenSagaApplication"

# 3. Start Order (orchestrator)
cd ch4-order-service && mvn spring-boot:run

# 4. Create an order
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"restaurantId":1,"consumerId":100,"lineItems":[{"menuItemId":"item-1","quantity":2}]}'
```

## Flow Document

For a **detailed explanation of the flow** (theory, architecture, happy path, failure path, and production notes), see:

- **[docs/Ch4-PROJECT-FLOW.md](docs/Ch4-PROJECT-FLOW.md)**

## Concepts Covered

- **Why ACID is lost** across microservices (Database-per-Service).
- **Saga** as a sequence of local transactions with **compensation** (undo).
- **Orchestration** (central coordinator) vs choreography.
- **Compensatable** step: Create Ticket → compensate with Cancel Ticket.
- **Production considerations:** durability (saga state in DB + messaging), resilience (circuit breaker), idempotency.

Code is commented for learning and structured for production-style layering (domain, saga, api, proxy).
