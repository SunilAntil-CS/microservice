# Chapter 5 VNFM — Standalone Project

This is a **separate project** for the VNFM Distributed Saga (Pure Choreography, Transactional Outbox, Idempotent Consumer). It lives under **microservices** and is **not** merged with `microservices/vnfm`.

## Structure

```
microservices/chapter5-vnfm/
├── pom.xml                    # Parent POM (com.telecom.vnfm.chapter5)
├── vnfm-common/               # Shared events and DTOs
├── vnfm-lcm-service/         # LCM (VnfInstance, outbox, relay, watchdog, consumers)
├── vnfm-vim-adapter-service/ # VIM Adapter (ACK, async simulator, webhook)
├── simulator-vim/            # OpenStack mock (202 + async webhooks)
├── simulator-nfvo/           # NFVO mock (Kafka listener)
└── chapter5/
    └── CHAPTER5_VNFM_CONCEPT_AND_FLOW.md
```

## IDE navigation (Go to Definition)

If **Go to Definition** (e.g. from `LcmController` to `lcmService.instantiateVnf`) doesn’t work, use this sequence:

1. **Open the workspace file** (not the parent folder):
   - **File → Open Workspace from File…**
   - Go to `microservices/chapter5-vnfm/` and open **`chapter5-vnfm.code-workspace`**
   - This makes `chapter5-vnfm` the only workspace root so the Java extension loads the parent POM and all modules correctly.

2. **Clean and reload the Java language server**:
   - **Ctrl+Shift+P** (or Cmd+Shift+P) → run **Java: Clean Java Language Server Workspace**
   - When prompted, choose **Reload and delete** (or **Restart and delete**)
   - Wait for the window to reload and for the status bar to finish **Building workspace** / **Importing projects**.

3. **Build from terminal** (optional but recommended):
   - In a terminal: `cd microservices/chapter5-vnfm && mvn clean install -DskipTests`
   - Then in the IDE: **Ctrl+Shift+P** → **Java: Force Java Compilation** → **Full** (so the language server picks up the build).

4. If it still doesn’t work: confirm the **Language Support for Java (Red Hat)** extension is installed and enabled, and that the JDK used for the project is Java 17 (same as in the POM).

## Build

From this directory or the repo root:

```bash
cd microservices/chapter5-vnfm
mvn clean install -DskipTests
```

Build order is handled by the parent: `vnfm-common` first, then the rest.

## Run (requires Kafka on localhost:9092)

1. **LCM:** `cd vnfm-lcm-service && mvn spring-boot:run` (port 8080)
2. **VIM Adapter:** `cd vnfm-vim-adapter-service && mvn spring-boot:run` (port 8081)
3. **Simulator VIM:** `cd simulator-vim && mvn spring-boot:run` (port 8082)
4. **Simulator NFVO:** `cd simulator-nfvo && mvn spring-boot:run` (port 8083)

## Quick test

- POST to LCM: `curl -X POST http://localhost:8080/api/v1/vnf/instantiate -H "Content-Type: application/json" -d '{"vnfId":"vnf-1","vcpu":4,"memoryMb":8192,"softwareVersion":"1.0"}'`
- Watch NFVO console for: `NFVO Dashboard Updated: VNF vnf-1 is now ...`
- Simulator randomly returns Success or Failure; on Failure, LCM runs compensation and NFVO sees state `FAILED`.

## Concept and flow

See **chapter5/CHAPTER5_VNFM_CONCEPT_AND_FLOW.md** for architecture, Choreography vs Orchestration, Outbox, Idempotent Consumer, and end-to-end flow.
