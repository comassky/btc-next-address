package com.btc.address.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class BlockchainChecker {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Checks if an address has at least one transaction (confirmed or unconfirmed).
     * Uses multiple APIs in cascade for reliability.
     */
    public boolean isAddressUsed(String address) {
        // 1. Try Mempool.space (Very reliable, no API key required)
        try {
            return checkMempoolSpace(address);
        } catch (Exception e) {
            System.err.println("Mempool.space error for " + address + ": " + e.getMessage());
        }
        
        // 2. Fallback to Blockchair
        try {
            return checkBlockchair(address);
        } catch (Exception e) {
            System.err.println("Blockchair error for " + address + ": " + e.getMessage());
        }

        // 3. Fallback to Blockchain.info
        try {
            return checkBlockchainInfo(address);
        } catch (Exception e) {
            System.err.println("Blockchain.info error for " + address + ": " + e.getMessage());
        }
        
        // If all APIs fail, throw exception to avoid false negatives
        throw new RuntimeException("Unable to check address " + address + " (all APIs failed)");
    }

    private boolean checkMempoolSpace(String address) throws Exception {
        String url = "https://mempool.space/api/address/" + address;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("User-Agent", "BTC-Address-Checker/1.0")
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            // Check confirmed transactions (chain_stats) and mempool (mempool_stats)
            int chainTx = root.path("chain_stats").path("tx_count").asInt(0);
            int mempoolTx = root.path("mempool_stats").path("tx_count").asInt(0);
            return (chainTx + mempoolTx) > 0;
        }
        throw new RuntimeException("Status code: " + response.statusCode());
    }

    private boolean checkBlockchair(String address) throws Exception {
        // Blockchair often has strict rate limits
        Thread.sleep(200); 
        String url = "https://api.blockchair.com/bitcoin/addresses?q=address(" + address + ")";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.path("data").path(address);
            if (data.isMissingNode()) return false;
            
            int txCount = data.path("transaction_count").asInt(0);
            return txCount > 0;
        }
        throw new RuntimeException("Status code: " + response.statusCode());
    }

    private boolean checkBlockchainInfo(String address) throws Exception {
        // Using rawaddr to get n_tx. limit=0 to avoid loading txs.
        String url = "https://blockchain.info/rawaddr/" + address + "?limit=0";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            int nTx = root.path("n_tx").asInt(0);
            return nTx > 0;
        }
        throw new RuntimeException("Status code: " + response.statusCode());
    }
}
