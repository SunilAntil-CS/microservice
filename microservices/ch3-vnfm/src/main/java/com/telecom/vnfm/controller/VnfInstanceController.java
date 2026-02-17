package com.telecom.vnfm.controller;

import com.telecom.vnfm.model.VnfHealthStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VNFM API that NFVO calls via Service Discovery. Path must match what NFVO builds:
 * destinations.getUrl() + "/vnf_instances/{id}/health" → GET /vnf_instances/{id}/health.
 *
 * In production this would query a real VNF lifecycle manager or DB; here we use
 * an in-memory map for a self-contained example.
 * ------------------------------------------------------------------------------------
 * SPRING WEB ANNOTATIONS (same family as in VnfHealthController; this app uses MVC):
 * ------------------------------------------------------------------------------------
 * @RestController = @Controller + @ResponseBody. Return value is written to the
 *   response body (JSON via Jackson). No view resolution.
 * @RequestMapping("/vnf_instances") — base path for this controller.
 * @GetMapping(value = "/{id}/health", produces = MediaType.APPLICATION_JSON_VALUE)
 *   — GET only; path variable "id"; response Content-Type application/json.
 * @PathVariable String id — binds the {id} segment to the method parameter.
 *   (Same idea as @PathParam in JAX-RS or req.params.id in Express.)
 * ------------------------------------------------------------------------------------
 * TYPES AND CLASSES:
 * ------------------------------------------------------------------------------------
 * java.util.Map<String, VnfHealthStatus>
 *   - JDK interface for key-value storage. Key = VNF id, value = health DTO.
 *
 * java.util.concurrent.ConcurrentHashMap
 *   - JDK implementation of Map that is thread-safe. Safe for concurrent reads and
 *     writes. We use it so that if we ever add background updates to the map, we
 *     don't need to change the type. For a read-only map after init, a plain HashMap
 *     would work too, but ConcurrentHashMap is a good default for shared mutable state.
 *   - In other languages: Go has sync.Map; in Node you might use a lock or a concurrent structure.
 *
 * getOrDefault(id, defaultValue)
 *   - Map method (JDK 8+). Returns value for key, or defaultValue if key is absent.
 *   We return a valid VnfHealthStatus for unknown ids so the API contract is consistent.
 * ------------------------------------------------------------------------------------
 * Return type: VnfHealthStatus (plain object). Spring MVC (servlet stack) serialises
 * it to JSON using Jackson. No Mono/Flux here—blocking is fine for this simple endpoint.
 */
@RestController
@RequestMapping("/vnf_instances")
public class VnfInstanceController {

    private final Map<String, VnfHealthStatus> healthStore = new ConcurrentHashMap<>();

    public VnfInstanceController() {
        healthStore.put("vnf-1", new VnfHealthStatus("vnf-1", "ACTIVE", null));
        healthStore.put("vnf-2", new VnfHealthStatus("vnf-2", "ACTIVE", null));
        healthStore.put("vnf-3", new VnfHealthStatus("vnf-3", "STANDBY", null));
    }

    @GetMapping(value = "/{id}/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public VnfHealthStatus getHealth(@PathVariable String id) {
        return healthStore.getOrDefault(id,
                new VnfHealthStatus(id, "UNKNOWN", "VNF not registered"));
    }
}
