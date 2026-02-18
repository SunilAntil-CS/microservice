package com.vnfm.nfvo.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses command-line arguments and invokes LCM API via LcmApiClient.
 * Supports: instantiate, terminate, status, list; optional --requestId and --delay.
 */
@Component
public class CliRunner implements CommandLineRunner {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${nfvo.lcm.base-url:http://localhost:8080}")
    private String defaultBaseUrl;

    public CliRunner(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        ParsedArgs parsed = parseArgs(args);
        String baseUrl = parsed.baseUrl != null ? parsed.baseUrl : defaultBaseUrl;
        LcmApiClient client = new LcmApiClient(baseUrl, restTemplate, objectMapper);

        if (parsed.command == null || parsed.command.isEmpty()) {
            System.err.println("Error: no command specified. Use: instantiate | terminate | status | list");
            printUsage();
            return;
        }

        String cmd = parsed.command.toLowerCase();
        switch (cmd) {
            case "instantiate" -> runInstantiate(parsed, client);
            case "terminate" -> runTerminate(parsed, client);
            case "status" -> runStatus(parsed, client);
            case "list" -> runList(parsed, client);
            default -> {
                System.err.println("Unknown command: " + cmd);
                printUsage();
            }
        }
    }

    private void runInstantiate(ParsedArgs p, LcmApiClient client) {
        if (p.positional.size() < 3) {
            System.err.println("Usage: instantiate <vnf-type> <cpu> <memory> [--requestId <id>] [--delay]");
            return;
        }
        String vnfType = p.positional.get(0);
        int cpu = parseInt(p.positional.get(1), "cpu");
        int memory = parseInt(p.positional.get(2), "memory");
        if (cpu < 0 || memory < 0) return;

        LcmApiClient.ApiResponse resp = client.instantiate(vnfType, cpu, memory, p.requestId);
        printResponse(resp);

        if (Boolean.TRUE.equals(p.delay) && resp.status == 202 && resp.body != null) {
            String vnfId = extractVnfIdFromInstantiateResponse(resp.body);
            if (vnfId != null) {
                System.out.println("\n--- Polling status (--delay) ---");
                pollStatusUntilStable(vnfId, client);
            }
        }
    }

    private void runTerminate(ParsedArgs p, LcmApiClient client) {
        if (p.positional.size() < 1) {
            System.err.println("Usage: terminate <vnf-id> [--requestId <id>]");
            return;
        }
        String vnfId = p.positional.get(0);
        LcmApiClient.ApiResponse resp = client.terminate(vnfId, p.requestId);
        printResponse(resp);
    }

    private void runStatus(ParsedArgs p, LcmApiClient client) {
        if (p.positional.size() < 1) {
            System.err.println("Usage: status <vnf-id>");
            return;
        }
        String vnfId = p.positional.get(0);
        LcmApiClient.ApiResponse resp = client.status(vnfId);
        printResponse(resp);
    }

    private void runList(ParsedArgs p, LcmApiClient client) {
        LcmApiClient.ApiResponse resp = client.list();
        printResponse(resp);
    }

    private void pollStatusUntilStable(String vnfId, LcmApiClient client) {
        String[] terminal = { "INSTANTIATED", "ACTIVE", "FAILED", "TERMINATED" };
        for (int i = 0; i < 30; i++) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            LcmApiClient.ApiResponse r = client.status(vnfId);
            if (r.status != 200) {
                System.out.println("Status check failed: " + r.status + " " + r.body);
                return;
            }
            String state = extractStateFromStatusResponse(r.body);
            System.out.println("  [" + (i + 1) + "] state = " + state);
            for (String t : terminal) {
                if (t.equalsIgnoreCase(state)) {
                    System.out.println("  (reached terminal state)");
                    return;
                }
            }
        }
        System.out.println("  (timeout waiting for terminal state)");
    }

    private String extractVnfIdFromInstantiateResponse(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(json, Map.class);
            Object v = m.get("vnfId");
            return v != null ? v.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractStateFromStatusResponse(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(json, Map.class);
            Object v = m.get("state");
            return v != null ? v.toString() : "?";
        } catch (Exception e) {
            return "?";
        }
    }

    private int parseInt(String s, String name) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            System.err.println("Invalid " + name + ": " + s);
            return -1;
        }
    }

    private void printResponse(LcmApiClient.ApiResponse resp) {
        System.out.println("HTTP " + resp.status);
        if (resp.headers != null && resp.headers.getLocation() != null) {
            System.out.println("Location: " + resp.headers.getLocation());
        }
        if (resp.body != null && !resp.body.isBlank()) {
            try {
                Object json = objectMapper.readValue(resp.body, Object.class);
                String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                System.out.println(pretty);
            } catch (Exception e) {
                System.out.println(resp.body);
            }
        }
    }

    private ParsedArgs parseArgs(String[] args) {
        ParsedArgs p = new ParsedArgs();
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--requestId".equals(a) && i + 1 < args.length) {
                p.requestId = args[++i];
            } else if ("--delay".equals(a)) {
                p.delay = true;
            } else if ("--baseUrl".equals(a) && i + 1 < args.length) {
                p.baseUrl = args[++i];
            } else if (!a.startsWith("--")) {
                positional.add(a);
            }
        }
        p.command = positional.isEmpty() ? null : positional.get(0);
        p.positional = positional.size() <= 1 ? List.of() : positional.subList(1, positional.size());
        return p;
    }

    private void printUsage() {
        System.out.println("NFVO Simulator – LCM REST API client");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  instantiate <vnf-type> <cpu> <memory> [--requestId <id>] [--delay]");
        System.out.println("  terminate <vnf-id> [--requestId <id>]");
        System.out.println("  status <vnf-id>");
        System.out.println("  list");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --baseUrl <url>   LCM base URL (default: http://localhost:8080)");
        System.out.println("  --requestId <id>  Idempotency key; duplicate requests return cached response");
        System.out.println("  --delay           After instantiate, poll status until terminal state");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  instantiate my-vnf 2 4");
        System.out.println("  instantiate my-vnf 2 4 --requestId req-123 --delay");
        System.out.println("  terminate 550e8400-e29b-41d4-a716-446655440000 --requestId req-456");
        System.out.println("  status 550e8400-e29b-41d4-a716-446655440000");
        System.out.println("  list");
    }

    private static class ParsedArgs {
        String baseUrl;
        String requestId;
        Boolean delay;
        String command;
        List<String> positional;
    }
}
