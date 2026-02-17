package com.telecom.nfvo.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * DTO (Data Transfer Object) for VNF health—returned by VNFM and by our fallback.
 *
 * CONTRACT: Same JSON shape on both sides so WebClient can deserialise and we can
 * return a consistent format on fallback (graceful degradation).
 * ------------------------------------------------------------------------------------
 * JAVA RECORD (Java 14+, standard in 16+):
 * ------------------------------------------------------------------------------------
 * public record VnfHealthStatus(String vnfId, String status, String message)
 *   - Record = compact way to define an immutable data carrier. The compiler generates:
 *     constructor, getters (vnfId(), status(), message()), equals(), hashCode(), toString().
 *   - No setters—immutable. Good for DTOs and value objects.
 *   - "Compact constructor" (the block with Objects.requireNonNull) runs after the
 *     canonical constructor; use it for validation or normalisation.
 *   - In other languages: similar to data class (Kotlin), case class (Scala), struct (Go),
 *     or a frozen dataclass (Python).
 * ------------------------------------------------------------------------------------
 * JACKSON ANNOTATION (JSON serialisation/deserialisation):
 * ------------------------------------------------------------------------------------
 * @JsonInclude(JsonInclude.Include.NON_NULL)
 *   - When serialising this object to JSON, omit any field whose value is null.
 *   - Example: message=null → JSON has no "message" key. Reduces payload size and
 *     keeps API responses clean.
 *   - Jackson is the default JSON library in Spring Boot. This is from com.fasterxml.jackson.
 *   - In other frameworks: Same Jackson annotations work in Quarkus/Micronaut; in Node
 *     you might use @Exclude() or a transform; in Go use json:",omitempty" on the struct tag.
 * ------------------------------------------------------------------------------------
 * java.util.Objects (JDK built-in):
 * ------------------------------------------------------------------------------------
 * Objects.requireNonNull(vnfId, "vnfId must not be null")
 *   - Throws NullPointerException with the given message if vnfId is null. Used in the
 *     compact constructor to enforce non-null contract at construction time.
 *   - Standard library; no framework dependency.
 * ------------------------------------------------------------------------------------
 * TYPES: String vnfId, String status, String message
 *   - All are Java reference types. "message" is nullable (optional detail for errors).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VnfHealthStatus(
        String vnfId,
        String status,
        String message
) {
    /** Compact constructor: runs after the generated constructor; validates non-null for required fields. */
    public VnfHealthStatus {
        Objects.requireNonNull(vnfId, "vnfId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
