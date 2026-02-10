package com.btc.address.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        try {
            return checkBlockchairBatch(addresses);
        } catch (Exception e) {
            System.err.println("Fallback Blockchair -> Blockchain.info: " + e.getMessage());
        }

        try {
            return checkBlockchainInfoBatch(addresses);
        } catch (Exception e) {
            System.err.println("Tous les services batch ont échoué: " + e.getMessage());
        }

        throw new RuntimeException("Limite de débit atteinte sur toutes les APIs. Attendez 5 minutes.");
    }

    private Map<String, Boolean> checkBlockchairBatch(List<String> addresses) throws Exception {
        String list = String.join(",", addresses);
        // Utilisation de l'endpoint dashboard pour vérifier plusieurs adresses d'un coup
        String url = "https://api.blockchair.com/bitcoin/dashboards/addresses/" + list + "?limit=0";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Quarkus-BTC-Scanner/1.0")
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Boolean> results = new HashMap<>();

        if (response.statusCode() == 200) {
            JsonNode data = mapper.readTree(response.body()).path("data");
            for (String addr : addresses) {
                // transaction_count > 0 signifie que l'adresse a été utilisée
                int txCount = data.path(addr).path("address").path("transaction_count").asInt(0);
                results.put(addr, txCount > 0);
            }
            return results;
        }
        throw new RuntimeException("Blockchair status: " + response.statusCode());
    }

    private Map<String, Boolean> checkBlockchainInfoBatch(List<String> addresses) throws Exception {
        String list = String.join("|", addresses);
        String url = "https://blockchain.info/balance?active=" + list;

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Boolean> results = new HashMap<>();

        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            for (String addr : addresses) {
                int nTx = root.path(addr).path("n_tx").asInt(0);
                results.put(addr, nTx > 0);
            }
            return results;
        }
        throw new RuntimeException("Blockchain.info status: " + response.statusCode());
    }
}