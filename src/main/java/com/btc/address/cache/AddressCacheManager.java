package com.btc.address.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class AddressCacheManager {

    @Inject
    @ConfigProperty(name = "bitcoin.cache.path", defaultValue = "/data")
    String dataPath;

    private static final String FILE_NAME = "address-cache.json";
    private Path cachePath;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @PostConstruct
    void init() {
        this.cachePath = Paths.get(dataPath, FILE_NAME);
        reloadCache();
    }

    private synchronized void reloadCache() {
        try {
            Files.createDirectories(cachePath.getParent());
            cache.clear();
            if (Files.exists(cachePath)) {
                var type = mapper.getTypeFactory().constructMapType(Map.class, String.class, CacheEntry.class);
                Map<String, CacheEntry> loaded = mapper.readValue(cachePath.toFile(), type);
                if (loaded != null) cache.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("❌ Rechargement du cache échouée: " + e.getMessage());
        }
    }

    public synchronized void saveCache() {
        Path temp = null;
        try {
            temp = Files.createTempFile(cachePath.getParent(), "btc-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), cache);
            Files.move(temp, cachePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("❌ Erreur de sauvegarde: " + e.getMessage());
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }

    public synchronized void refreshFromDisk() {
        reloadCache();
    }

    public Map<String, CacheEntry> getMultiEntries(Iterable<String> hashes) {
        return StreamSupport.stream(hashes.spliterator(), false)
                .filter(cache::containsKey)
                .collect(Collectors.toMap(h -> h, h -> cache.get(h)));
    }

    public void addEntries(Map<String, Boolean> newEntries, Map<String, Integer> indices) {
        newEntries.forEach((h, u) -> cache.put(h, new CacheEntry(u, indices.getOrDefault(h, -1))));
        saveCache();
    }

    public int getMaxUsedIndex() {
        return cache.values().stream()
                .filter(CacheEntry::used)
                .mapToInt(CacheEntry::index)
                .max()
                .orElse(-1);
    }
}