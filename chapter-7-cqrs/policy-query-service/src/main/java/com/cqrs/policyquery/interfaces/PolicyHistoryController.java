package com.cqrs.policyquery.interfaces;

import com.cqrs.policyquery.application.PolicyHistoryQueryService;
import com.cqrs.policyquery.config.PolicyQueryProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST API to query indexed policy history.
 */
@RestController
@RequestMapping("/api/policy-history")
public class PolicyHistoryController {

    private final PolicyHistoryQueryService queryService;
    private final PolicyQueryProperties properties;

    public PolicyHistoryController(PolicyHistoryQueryService queryService, PolicyQueryProperties properties) {
        this.queryService = queryService;
        this.properties = properties;
    }

    @GetMapping(value = "/{subscriberId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PolicyHistoryPage getHistory(
            @PathVariable String subscriberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String policyName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        int pageSize = size != null ? size : properties.getPagination().getDefaultSize();
        return queryService.query(subscriberId, from, to, policyName, page, pageSize);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PolicyHistoryPage getHistoryAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String policyName,
            @RequestParam(required = false) String subscriberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        int pageSize = size != null ? size : properties.getPagination().getDefaultSize();
        return queryService.query(subscriberId, from, to, policyName, page, pageSize);
    }
}
