package com.btc.address.resource;

import com.btc.address.service.AddressService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@Path("/api/address")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AddressResource {

    @Inject
    AddressService addressService;

    @ConfigProperty(name = "bitcoin.xpub")
    Optional<String> xpub; // Modern way to handle optional configuration

    @POST
    @Path("/next")
    @RunOnVirtualThread // Efficiently handles blocking blockchain I/O on virtual threads
    public NextAddressResult getNextUnusedAddress(AddressRequest request) {
        // Using Pattern Matching-like flow with Optional
        return xpub.filter(key -> !key.isBlank())
                .map(key -> addressService.findNextUnusedAddress(
                        key,
                        Math.max(request.startIndex(), 0),
                        request.salt()))
                .orElseThrow(() -> new BadRequestException("bitcoin.xpub is not configured"));
    }

    @POST
    @Path("/verify")
    @RunOnVirtualThread
    public Response verify(VerifyRequest request) {
        // Guard clause using modern string check
        if (request.address() == null || request.address().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Address is required").build();
        }
        return xpub.filter(key -> !key.isBlank())
                .map(key -> addressService.verifyAddressOwnership(key, request.address()))
                .map(result -> Response.ok(result).build())
                .orElseGet(() -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Server configuration error").build());
    }

    @GET
    @Path("/health")
    @RunOnVirtualThread
    public HealthResponse health() {
        // Using unnamed variable '_' as the xpub value is not needed for the boolean check
        return xpub.filter(key -> !key.isBlank())
                .map(_ -> new HealthResponse("Address service is ready", true))
                .orElse(new HealthResponse("Address service not configured", false));
    }
}