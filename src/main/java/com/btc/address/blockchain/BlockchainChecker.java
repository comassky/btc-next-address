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
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class BlockchainChecker {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Boolean> checkAddressesBatch(List<String> addresses) {
        // 1. Essai avec Blockchair (Le plus efficace en batch de 20+)
        try {
            return checkBlockchairBatch(addresses);
        } catch (Exception e) {
            System.err.println("Blockchair KO, essai Blockchain.info: " + e.getMessage());
        }

        // 2. Fallback sur Blockchain.info (Batch supporté via l'API balance)
        try {
            return checkBlockchainInfoBatch(addresses);
        } catch (Exception e) {
            System.err.println("Blockchain.info KO, essai Mempool.space: " + e.getMessage());
        }

        // 3. Dernier recours : Mempool.space (Pas de batch, donc appels individuels)
        try {
            return checkMempoolIndividual(addresses);
        } catch (Exception e) {
            System.err.println("Toutes les APIs ont échoué: " + e.getMessage());
        }

        throw new RuntimeException("Limite de débit atteinte partout. Réessayez dans 5 minutes.");
    }

    private Map<String, Boolean> checkBlockchairBatch(List<String> addresses) throws Exception {
        String list = String.join(",", addresses);
        String url = "https://api.blockchair.com/bitcoin/dashboards/addresses/" + list + "?limit=0";

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
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
        throw new RuntimeException("HTTP " + response.statusCode());
    }

    private Map<String, Boolean> checkBlockchainInfoBatch(List<String> addresses) throws Exception {
        String list = String.join("|", addresses);
        String url = "https://blockchain.info/balance?active=" + list;

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
        throw new RuntimeException("HTTP " + response.statusCode());
    }

    private Map<String, Boolean> checkMempoolIndividual(List<String> addresses) {
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        // On itère sur les adresses. Mempool bloque vite, donc on limite.
        for (String addr : addresses) {
            try {
                String url = "https://mempool.space/api/address/" + addr;
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonNode root = mapper.readTree(response.body());
                    int txCount = root.path("chain_stats").path("tx_count").asInt(0) 
                                + root.path("mempool_stats").path("tx_count").asInt(0);
                    results.put(addr, txCount > 0);
                }
                // Petit délai pour être "gentil" avec Mempool.space
                Thread.sleep(200); 
            } catch (Exception e) {
                results.put(addr, true); // En cas d'erreur, on assume 'utilisée' pour éviter les doublons
            }
        }
        return results;
    }
}