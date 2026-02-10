package com.btc.address.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class BlockchainChecker {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Boolean> checkAddressesBatch(List<String> addresses) {
        // Priority 1: Blockchair (Best batch support)
        try {
            return checkBlockchairBatch(addresses);
        } catch (Exception e) {
            System.err.println("Blockchair fallback -> Blockchain.info: " + e.getMessage());
        }

        // Priority 2: Blockchain.info
        try {
            return checkBlockchainInfoBatch(addresses);
        } catch (Exception e) {
            System.err.println("Blockchain.info fallback -> Mempool.space: " + e.getMessage());
        }

        // Priority 3: Mempool.space (Individual calls)
        try {
            return checkMempoolIndividual(addresses);
        } catch (Exception e) {
            System.err.println("All services failed: " + e.getMessage());
        }

        throw new RuntimeException("Rate limit reached on all APIs. Please wait 5 minutes.");
    }

    private Map<String, Boolean> checkBlockchairBatch(List<String> addresses) throws Exception {
        String list = String.join(",", addresses);
        String url = "https://api.blockchair.com/bitcoin/dashboards/addresses/" + list + "?limit=0";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Quarkus-BTC-Scanner/1.0")
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            Map<String, Boolean> results = new HashMap<>();
            JsonNode data = mapper.readTree(response.body()).path("data");
            for (String addr : addresses) {
                int txCount = data.path(addr).path("address").path("transaction_count").asInt(0);
                results.put(addr, txCount > 0);
            }
            return results;
        }
        throw new RuntimeException("Blockchair Status: " + response.statusCode());
    }

    private Map<String, Boolean> checkBlockchainInfoBatch(List<String> addresses) throws Exception {
        String list = String.join("|", addresses);
        // Fix: URL Encode the pipe symbols
        String encodedList = URLEncoder.encode(list, StandardCharsets.UTF_8);
        String url = "https://blockchain.info/balance?active=" + encodedList;

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Boolean> results = new HashMap<>();
            JsonNode root = mapper.readTree(response.body());
            for (String addr : addresses) {
                int nTx = root.path(addr).path("n_tx").asInt(0);
                results.put(addr, nTx > 0);
            }
            return results;
        }
        throw new RuntimeException("Blockchain.info Status: " + response.statusCode());
    }

    private Map<String, Boolean> checkMempoolIndividual(List<String> addresses) throws Exception {
        Map<String, Boolean> results = new HashMap<>();
        for (String addr : addresses) {
            String url = "https://mempool.space/api/address/" + addr;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                int txCount = root.path("chain_stats").path("tx_count").asInt(0)
                            + root.path("mempool_stats").path("tx_count").asInt(0);
                results.put(addr, txCount > 0);
            } else {
                throw new RuntimeException("Mempool.space Status: " + response.statusCode());
            }
            // Small delay to prevent Mempool.space rate limiting
            Thread.sleep(250);
        }
        return results;
    }
}