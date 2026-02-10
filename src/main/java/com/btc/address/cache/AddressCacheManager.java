package com.btc.address.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterForReflection
public class AddressCacheManager {

    // Chemin fixe : plus besoin de @ConfigProperty
    private static final String DATA_PATH = "/data";
    private static final String CACHE_FILE_NAME = "address-cache.json";
    
    private final Path cacheFilePath = Paths.get(DATA_PATH, CACHE_FILE_NAME);

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Map<String, CacheEntry> cache;

    @PostConstruct
    void init() {
        try {
            // S'assure que /data existe au démarrage
            Files.createDirectories(Paths.get(DATA_PATH));
            loadCache();
        } catch (IOException e) {
            System.err.println("CRITICAL: Cannot access /data. Cache will be lost on restart.");
            this.cache = new ConcurrentHashMap<>();
        }
    }

    private synchronized void loadCache() {
        this.cache = new ConcurrentHashMap<>();
        if (!Files.exists(cacheFilePath)) {
            System.out.println("No existing cache found at " + cacheFilePath + ". Starting fresh.");
            return;
        }

        try {
            var typeFactory = mapper.getTypeFactory();
            Map<String, CacheEntry> loadedCache = mapper.readValue(cacheFilePath.toFile(),
                    typeFactory.constructMapType(ConcurrentHashMap.class, String.class, CacheEntry.class));

            Optional.ofNullable(loadedCache).ifPresent(this.cache::putAll);
            System.out.println("Cache loaded: " + this.cache.size() + " entries.");
        } catch (IOException e) {
            System.err.println("Failed to load cache: " + e.getMessage());
        }
    }

    public synchronized void saveCache() {
        try {
            // Écriture atomique dans /data
            Path tempFile = Files.createTempFile(cacheFilePath.getParent(), "cache-", ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), cache);
            Files.move(tempFile, cacheFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Failed to save cache to " + cacheFilePath + ": " + e.getMessage());
        }
    }

    public void addEntry(String hash, boolean used) {
        cache.put(hash, new CacheEntry(hash, used));
        saveCache();
    }

    public boolean isAddressUsed(String hash) {
        return Optional.ofNullable(cache.get(hash)).map(CacheEntry::used).orElse(false);
    }
}