package com.btc.address.resource;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.btc.address.service.AddressService;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/address")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AddressResource {

    @Inject
    private AddressService addressService;

    @ConfigProperty(name = "bitcoin.xpub")
    private String xpub;

    @POST
    @Path("/next")
    @RunOnVirtualThread
    public NextAddressResult getNextUnusedAddress(AddressRequest request) {
        if (xpub == null || xpub.isEmpty()) {
            throw new BadRequestException("bitcoin.xpub is not configured");
        }
        int startIndex = Math.max(request.startIndex(), 0);
        return addressService.findNextUnusedAddress(xpub, startIndex, request.salt());
    }

    @POST
    @Path("/verify")
    public Response verify(VerifyRequest request) {
        var result = addressService.verifyAddressOwnership(xpub, request.address());
        return Response.ok(result).build();
    }

    @GET
    @Path("/health")
    @RunOnVirtualThread
    public HealthResponse health() {
        boolean configured = xpub != null && !xpub.isEmpty();
        return new HealthResponse(configured ? "Address service is ready" : "Address service not configured");
    }

}
