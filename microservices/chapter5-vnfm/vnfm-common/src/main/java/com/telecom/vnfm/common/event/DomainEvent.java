package com.telecom.vnfm.common.event;

import java.io.Serializable;

/**
 * MARKER INTERFACE: Domain Event (ETSI MANO / DDD).
 * All events exchanged between LCM, VIM Adapter, and NFVO implement this interface.
 * Pure Choreography: no central orchestrator; Timeout Watchdog mitigates "Lost in Space".
 */
public interface DomainEvent extends Serializable {
}
