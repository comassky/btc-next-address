package com.btc.address.cache;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CacheEntry(
        boolean used,
        int index,
        Instant timestamp) {
    public CacheEntry(boolean used, int index) {
        this(used, index, Instant.now());
    }
}