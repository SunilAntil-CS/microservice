package com.telecom.mediation.controller;

import com.telecom.mediation.model.RawCdr;
import com.telecom.mediation.service.CdrMediationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API to receive raw CDRs (e.g. from Switch/Tower or a test client).
 *
 * POST /api/v1/cdr: Accepts RawCdr JSON. Validates with @Valid (Jakarta Validation);
 * then delegates to CdrMediationService.processCdr() which saves CDR + outbox in one transaction.
 *
 * @Valid: Triggers validation on the RawCdr (e.g. @NotBlank, @NotNull, @Positive).
 * If validation fails, Spring returns 400 Bad Request with constraint messages.
 *
 * @ResponseStatus(HttpStatus.CREATED): Return 201 on success (resource created).
 */
@RestController
@RequestMapping("/api/v1")
public class CdrController {

    private final CdrMediationService cdrMediationService;

    public CdrController(CdrMediationService cdrMediationService) {
        this.cdrMediationService = cdrMediationService;
    }

    @PostMapping(value = "/cdr", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void submitCdr(@RequestBody @Valid RawCdr rawCdr) {
        cdrMediationService.processCdr(rawCdr);
    }
}
