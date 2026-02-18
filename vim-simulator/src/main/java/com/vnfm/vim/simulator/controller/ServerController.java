package com.vnfm.vim.simulator.controller;

import com.vnfm.vim.simulator.api.CreateServerRequest;
import com.vnfm.vim.simulator.api.ServerResponse;
import com.vnfm.vim.simulator.domain.Server;
import com.vnfm.vim.simulator.exception.VimException;
import com.vnfm.vim.simulator.service.VimSimulatorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * REST API for mock VIM: allocate VM (POST /servers), release (DELETE), status (GET).
 */
@RestController
@RequestMapping("/servers")
public class ServerController {

    private final VimSimulatorService vimSimulatorService;

    public ServerController(VimSimulatorService vimSimulatorService) {
        this.vimSimulatorService = vimSimulatorService;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<ServerResponse> createServer(@Valid @RequestBody(required = false) CreateServerRequest request) {
        int cpu = request != null ? request.getCpu() : 1;
        int memory = request != null ? request.getMemory() : 1024;
        Server server = vimSimulatorService.createServer(cpu, memory);
        ServerResponse body = new ServerResponse(
                server.getResourceId(),
                server.getIp(),
                server.getStatus(),
                server.getCpu(),
                server.getMemory()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServer(@PathVariable String id) {
        vimSimulatorService.deleteServer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<ServerResponse> getServer(@PathVariable String id) {
        Optional<Server> server = vimSimulatorService.getServer(id);
        if (server.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Server s = server.get();
        ServerResponse body = new ServerResponse(s.getResourceId(), s.getIp(), s.getStatus(), s.getCpu(), s.getMemory());
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(VimException.class)
    public ResponseEntity<ErrorBody> handleVimException(VimException ex) {
        HttpStatus status = mapErrorTypeToStatus(ex.getErrorType());
        return ResponseEntity.status(status).body(new ErrorBody(ex.getErrorType(), ex.getMessage()));
    }

    private static HttpStatus mapErrorTypeToStatus(String errorType) {
        if (errorType == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        return switch (errorType.toUpperCase()) {
            case "TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "QUOTA" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @SuppressWarnings("unused")
    public static class ErrorBody {
        private String errorType;
        private String message;

        public ErrorBody(String errorType, String message) {
            this.errorType = errorType;
            this.message = message;
        }

        public String getErrorType() { return errorType; }
        public String getMessage() { return message; }
    }
}
