package com.btc.address.resource;

import com.btc.address.service.AddressService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
    public NextAddressResult getNextUnusedAddress(AddressRequest request) {
        if (xpub == null || xpub.isEmpty()) {
            throw new BadRequestException("bitcoin.xpub is not configured");
        }
        int startIndex = Math.max(request.startIndex(), 0);
        return addressService.findNextUnusedAddress(xpub, startIndex, request.salt());
    }
    
    @GET
    @Path("/health")
    public HealthResponse health() {
        boolean configured = xpub != null && !xpub.isEmpty();
        return new HealthResponse(configured ? "Address service is ready" : "Address service not configured");
    }
    
    public record AddressRequest(int startIndex, String salt) {
        public AddressRequest() {
            this(0, null);
        }
    }
    
    public record HealthResponse(String status) {}
}
