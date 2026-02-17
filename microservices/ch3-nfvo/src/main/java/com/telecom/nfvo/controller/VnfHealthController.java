package com.telecom.nfvo.controller;

import com.telecom.nfvo.model.VnfHealthStatus;
import com.telecom.nfvo.service.VnfmServiceProxy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST API for VNF health—what the NFVO Dashboard (or any client) calls.
 *
 * Flow: GET /api/v1/vnf/{vnfId}/health → proxy → VNFM (or fallback) → JSON response.
 * ------------------------------------------------------------------------------------
 * SPRING WEB ANNOTATIONS (spring-web / Spring MVC / WebFlux):
 * ------------------------------------------------------------------------------------
 * @RestController
 *   - Combines @Controller and @ResponseBody. Every method's return value is written
 *     directly to the HTTP response body (not a view name). Spring uses HTTP message
 *     converters to serialise to JSON when the client accepts application/json.
 *   - In other frameworks: Quarkus @Path on class + @GET on method (JAX-RS); Micronaut
 *     @Controller; in Node (Express) you'd do app.get(...) and res.json(...).
 *
 * @RequestMapping("/api/v1/vnf")
 *   - Prefix for all mappings in this class. Final path = prefix + method path.
 *   - "value" or "path" can be used. Optional: method, consumes, produces (see below).
 *   - In other frameworks: JAX-RS @Path("api/v1/vnf"); Express router.use("/api/v1/vnf", ...).
 *
 * @GetMapping(value = "/{vnfId}/health", produces = MediaType.APPLICATION_JSON_VALUE)
 *   - Maps HTTP GET to the method. value = "/{vnfId}/health" → path variable "vnfId".
 *   - produces = "application/json" → we declare that the response is JSON; Spring
 *     selects the Jackson converter and sets Content-Type header.
 *   - In other frameworks: JAX-RS @GET @Path("{vnfId}/health") @Produces(MediaType.APPLICATION_JSON).
 *
 * @PathVariable String vnfId
 *   - Binds the path segment where {vnfId} appears to the method parameter. For
 *     /api/v1/vnf/vnf-1/health, vnfId = "vnf-1". Type conversion (e.g. to Integer) is
 *     possible if needed.
 *   - In other frameworks: JAX-RS @PathParam("vnfId"); Express req.params.vnfId.
 * ------------------------------------------------------------------------------------
 * TYPES AND CLASSES:
 * ------------------------------------------------------------------------------------
 * org.springframework.http.MediaType
 *   - Constants for MIME types. APPLICATION_JSON_VALUE = "application/json". Used to
 *     specify produces/consumes in a type-safe way.
 *
 * reactor.core.publisher.Mono<VnfHealthStatus>
 *   - Return type: we return a Mono so the thread is not blocked. When the Mono emits
 *     a value (from the proxy), Spring WebFlux subscribes, gets the value, and writes
 *     it as JSON. If the Mono errors, Spring translates to an error response (e.g. 500).
 *   - Reactive stack: Spring WebFlux supports Mono/Flux return types; Spring MVC also
 *     supports them and subscribes internally. Our controller stays non-blocking.
 * ------------------------------------------------------------------------------------
 * Constructor injection: Spring injects VnfmServiceProxy (our @Service bean). Single
 * constructor → no need for @Autowired (implicit since Spring 4.3).
 */
@RestController
@RequestMapping("/api/v1/vnf")
public class VnfHealthController {

    private final VnfmServiceProxy vnfmServiceProxy;

    public VnfHealthController(VnfmServiceProxy vnfmServiceProxy) {
        this.vnfmServiceProxy = vnfmServiceProxy;
    }

    @GetMapping(value = "/{vnfId}/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<VnfHealthStatus> getVnfHealth(@PathVariable String vnfId) {
        return vnfmServiceProxy.getVnfHealth(vnfId);
    }
}
