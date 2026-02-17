package com.telecom.vnfm.lcm.domain;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class ResultWithDomainEvents<R, E> {

    private final R result;
    private final List<E> events;

    public ResultWithDomainEvents(R result, List<E> events) {
        this.result = result;
        this.events = events != null ? List.copyOf(events) : Collections.emptyList();
    }
}
