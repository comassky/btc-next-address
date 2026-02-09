package com.btc.address.resource;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record NextAddressResult(
        String address,
        @JsonIgnore String publicKey,
        int index,
        String qrCodeImage,
        @JsonIgnore String salt,
        @JsonIgnore String hash
) {}