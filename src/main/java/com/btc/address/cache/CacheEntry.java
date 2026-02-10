package com.btc.address.cache;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CacheEntry(
        boolean used,
        Instant timestamp) {
    public CacheEntry(boolean used) {
        this(used, Instant.now());
    }
}