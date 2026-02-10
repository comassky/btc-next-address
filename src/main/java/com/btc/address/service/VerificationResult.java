package com.btc.address.service;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record VerificationResult(boolean valid, int index) {}