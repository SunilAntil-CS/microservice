package com.vnfm.vimmanager.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Decorator around VimClient that adds circuit breaker and retry with exponential backoff.
 * Delegates to the actual implementation (e.g. InMemoryVimSimulator).
 */
@Component
@Primary
public class ResilientVimClient implements VimClient {

    private final VimClient delegate;

    public ResilientVimClient(@Qualifier("inMemoryVimSimulator") VimClient delegate) {
        this.delegate = delegate;
    }

    @Override
    @CircuitBreaker(name = "vimClient", fallbackMethod = "executeFallback")
    @Retry(name = "vimClient")
    public VimResponse execute(VimRequest request) {
        return delegate.execute(request);
    }

    @SuppressWarnings("unused")
    public VimResponse executeFallback(VimRequest request, Exception e) {
        return VimResponse.fail("VIM unavailable: " + e.getMessage());
    }
}
