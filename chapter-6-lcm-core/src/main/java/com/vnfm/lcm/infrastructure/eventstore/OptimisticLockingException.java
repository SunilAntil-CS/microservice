package com.vnfm.lcm.infrastructure.eventstore;

/**
 * Thrown when saving events if the aggregate's current version in the store
 * does not match the expected version (e.g. concurrent update).
 */
public class OptimisticLockingException extends RuntimeException {

    private final String aggregateId;
    private final int expectedVersion;
    private final int actualVersion;

    public OptimisticLockingException(String aggregateId, int expectedVersion, int actualVersion) {
        super(String.format("Optimistic lock failed for aggregate %s: expected version %d, found %d",
                aggregateId, expectedVersion, actualVersion));
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }

    public int getActualVersion() {
        return actualVersion;
    }
}
