# VIM Manager

Separate Spring Boot application that consumes VIM commands from Kafka, calls the VIM (mock or real), and publishes replies to `vim.replies`.

## Architecture

- **Kafka consumer** (`VimCommandsConsumer`): Listens to `vim.commands`, parses message (key = messageId, value = payload or wrapper with messageType + payload).
- **CommandHandler**: Idempotent processing using `processed_commands` table (stores `message_id`). Translates payload → VIM request, calls `VimClient`, writes reply to outbox.
- **VimClient**: Port for the actual VIM. Wrapped by `ResilientVimClient` (circuit breaker + retry with exponential backoff via Resilience4j).
- **ResourceRequestTranslator**: Converts generic resource request to VIM-specific format (pass-through for now).
- **In-memory VIM simulator**: Configurable via `vim.simulator.succeed` (true = success, false = fail) for testing.
- **Outbox**: Replies are written to the `outbox` table in the same transaction; `OutboxForwarder` publishes them to `vim.replies`.

## Idempotency

The `processed_commands` table stores `message_id` of each consumed command. Duplicate deliveries (same messageId) are ignored.

## Configuration

- `vim.commands-topic`: Topic to consume (default `vim.commands`).
- `vim.replies-topic`: Topic for replies (default `vim.replies`).
- `vim.simulator.succeed`: When true, simulator returns success; when false, returns failure.
- Resilience4j: `resilience4j.circuitbreaker.instances.vimClient` and `resilience4j.retry.instances.vimClient` (exponential backoff).

## Running

- **PostgreSQL**: Create DB `vim_manager_db`, user `vimmanager` / `vimmanager123`, and run `src/main/resources/schema.sql`.
- **Kafka**: Bootstrap at `localhost:9092`; create topics `vim.commands` and `vim.replies` if needed.
- Start the app: `mvn spring-boot:run` (or run `VimManagerApplication`).

## Tests

- **Integration test** (`VimManagerIntegrationTest`): Uses `@EmbeddedKafka`. Sends ReserveResources/ReleaseResources to `vim.commands`, asserts `processed_commands` and outbox reply. Run: `mvn test -Dtest=VimManagerIntegrationTest`.
