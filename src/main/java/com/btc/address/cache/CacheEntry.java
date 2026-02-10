package com.btc.address.cache;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CacheEntry(
        @JsonIgnore String hash,
        boolean used,
        Instant timestamp) {
    public CacheEntry(String hash, boolean used) {
        this(hash, used, Instant.now());
    }
}