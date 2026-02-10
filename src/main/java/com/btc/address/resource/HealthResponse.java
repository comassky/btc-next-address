package com.btc.address.resource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record HealthResponse(String status, boolean b) {
}
