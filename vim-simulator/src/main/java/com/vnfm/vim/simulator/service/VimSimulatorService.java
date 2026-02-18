package com.vnfm.vim.simulator.service;

import com.vnfm.vim.simulator.config.FailureProperties;
import com.vnfm.vim.simulator.config.LatencyProperties;
import com.vnfm.vim.simulator.config.PoolProperties;
import com.vnfm.vim.simulator.domain.Server;
import com.vnfm.vim.simulator.exception.VimException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory VIM simulator: pool of VMs, configurable failure rate and latency.
 */
@Service
public class VimSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(VimSimulatorService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";

    private final FailureProperties failureProperties;
    private final LatencyProperties latencyProperties;
    private final PoolProperties poolProperties;

    /** resourceId -> Server (allocated VMs). */
    private final ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();

    public VimSimulatorService(FailureProperties failureProperties,
                               LatencyProperties latencyProperties,
                               PoolProperties poolProperties) {
        this.failureProperties = failureProperties;
        this.latencyProperties = latencyProperties;
        this.poolProperties = poolProperties;
    }

    /**
     * Allocate a VM. Applies latency and failure simulation, checks pool capacity.
     */
    public Server createServer(int cpu, int memory) {
        applyLatency();
        maybeFail("createServer");

        synchronized (servers) {
            if (servers.size() >= poolProperties.getMaxServers()) {
                throw new VimException("QUOTA", "No capacity: pool limit " + poolProperties.getMaxServers() + " reached");
            }
            String resourceId = "vm-" + UUID.randomUUID().toString();
            String ip = "10.0." + (servers.size() % 254 + 1) + "." + (resourceId.hashCode() % 254 + 1);
            Server server = new Server(resourceId, ip, cpu, memory, STATUS_ACTIVE);
            servers.put(resourceId, server);
            log.info("Allocated server resourceId={} ip={} cpu={} memory={} poolSize={}/{}",
                    resourceId, ip, cpu, memory, servers.size(), poolProperties.getMaxServers());
            return server;
        }
    }

    /**
     * Release a VM.
     */
    public void deleteServer(String resourceId) {
        applyLatency();
        maybeFail("deleteServer");

        Server removed = servers.remove(resourceId);
        if (removed == null) {
            throw new VimException("INTERNAL", "Server not found: " + resourceId);
        }
        log.info("Released server resourceId={} poolSize={}/{}", resourceId, servers.size(), poolProperties.getMaxServers());
    }

    /**
     * Get server status by resourceId.
     */
    public Optional<Server> getServer(String resourceId) {
        applyLatency();
        maybeFail("getServer");

        return Optional.ofNullable(servers.get(resourceId));
    }

    private void applyLatency() {
        int min = latencyProperties.getMinMs();
        int max = latencyProperties.getMaxMs();
        if (max <= 0) return;
        int delay = min >= max ? min : min + (int) (Math.random() * (max - min + 1));
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new VimException("TIMEOUT", "Interrupted during latency simulation");
            }
        }
    }

    private void maybeFail(String operation) {
        if (failureProperties.getRate() <= 0.0) return;
        if (Math.random() >= failureProperties.getRate()) return;

        List<String> types = failureProperties.getErrorTypes();
        String errorType = types.isEmpty() ? "INTERNAL" : types.get((int) (Math.random() * types.size()));
        String message = errorType + " simulated for " + operation;
        log.warn("Simulated failure: {}", message);
        throw new VimException(errorType, message);
    }
}
