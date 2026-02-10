package com.btc.address.resource;

import com.btc.address.bitcoin.BIP84Deriver;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record AddressData(int index, BIP84Deriver.DerivedAddress derived, String hash) {}