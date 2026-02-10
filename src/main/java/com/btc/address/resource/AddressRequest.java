package com.btc.address.resource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record AddressRequest(int startIndex, String salt) {
    public AddressRequest() {
        this(0, null);
    }
}