package com.telecom.vnfm.common.event;

import java.io.Serializable;

/**
 * MARKER INTERFACE: Domain Event (ETSI MANO / DDD).
 * ---------------------------------------------------------------------------
 * All events exchanged between LCM, VIM Adapter, and NFVO implement this interface.
 * They are serialized to JSON and published via Kafka. Using a shared contract
 * (vnfm-common JAR) keeps producer and consumer schemas in sync without a
 * separate schema registry for this interview-grade implementation.
 *
 * ARCHITECTURE: Pure Choreography (Event-Driven Saga)
 * - We do NOT use an Orchestrator (Temporal, Camunda). For a strictly 2-actor
 *   system (LCM and VIM Adapter), choreography provides perfect decoupling:
 *   each service reacts to events and emits new events; no central coordinator.
 * - The "Lost in Space" flaw of choreography (no single place knows the full
 *   saga state) is mitigated by the Timeout Watchdog in the LCM service:
 *   a @Scheduled job finds VnfInstances stuck in DEPLOYING_INFRA for > 15
 *   minutes and marks them FAILED, ensuring we never leave instances stuck forever.
 */
public interface DomainEvent extends Serializable {
}
