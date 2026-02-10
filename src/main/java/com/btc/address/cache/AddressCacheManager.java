package com.btc.address.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@RegisterForReflection
public class AddressCacheManager {

    private static final String DATA_PATH = "/data";
    private static final String CACHE_FILE_NAME = "address-cache.json";
    private final Path cacheFilePath = Paths.get(DATA_PATH, CACHE_FILE_NAME);

    // FIXED: Added JavaTimeModule and disabled timestamp writing to fix the Instant error
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @RegisterForReflection
    public record CacheEntry(boolean used, Instant timestamp) {
        public CacheEntry(boolean used) {
            this(used, Instant.now());
        }
    }

    @PostConstruct
    void init() {
        try {
            // Ensure the directory exists (important for the first run with a Named Volume)
            Files.createDirectories(Paths.get(DATA_PATH));
            loadCache();
        } catch (IOException e) {
            System.err.println("CRITICAL: Cannot access /data. Cache will be volatile.");
        }
    }

    private synchronized void loadCache() {
        if (!Files.exists(cacheFilePath)) {
            System.out.println("No existing cache found at " + cacheFilePath + ". Starting fresh.");
            return;
        }

        try {
            var typeFactory = mapper.getTypeFactory();
            // Load the map from the JSON file
            Map<String, CacheEntry> loadedCache = mapper.readValue(cacheFilePath.toFile(),
                    typeFactory.constructMapType(Map.class, String.class, CacheEntry.class));

            if (loadedCache != null) {
                cache.putAll(loadedCache);
                System.out.println("✅ Cache loaded: " + cache.size() + " entries.");
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to load cache: " + e.getMessage());
        }
    }

    /**
     * Optimized batch save to reduce disk I/O
     */
    public synchronized void saveCache() {
        try {
            // Create a temp file in the same directory to ensure Atomic Move works
            Path tempFile = Files.createTempFile(cacheFilePath.getParent(), "cache-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), cache);
                // Atomic swap to prevent file corruption
                Files.move(tempFile, cacheFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to save cache to " + cacheFilePath + ": " + e.getMessage());
        }
    }

    /**
     * Checks if we already know the status of this address hash
     * Returns Optional.empty() if unknown, allowing the service to decide to scan
     */
    public Optional<Boolean> getKnownStatus(String hash) {
        return Optional.ofNullable(cache.get(hash)).map(CacheEntry::used);
    }

    /**
     * Adds multiple entries at once (Batch) and saves once to disk
     */
    public void addEntries(Map<String, Boolean> newEntries) {
        newEntries.forEach((hash, used) -> cache.put(hash, new CacheEntry(used)));
        saveCache();
    }

    /**
     * Compatibility method for single entries
     */
    public void addEntry(String hash, boolean used) {
        cache.put(hash, new CacheEntry(used));
        saveCache();
    }
}