package com.btc.address.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class AddressCacheManager {

    private static final String DATA_PATH = "/data";
    private static final String CACHE_FILE_NAME = "address-cache.json";
    private final Path cacheFilePath = Paths.get(DATA_PATH, CACHE_FILE_NAME);

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Modernized Multi-status check using Streams for any Iterable.
     */
    public Map<String, Boolean> getMultiStatus(Iterable<String> hashes) {
        return StreamSupport.stream(hashes.spliterator(), false)
                .filter(cache::containsKey)
                .collect(Collectors.toMap(
                        hash -> hash,
                        hash -> cache.get(hash).used(),
                        (existing, _) -> existing // Merge function using unnamed variable '_'
                ));
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(Paths.get(DATA_PATH));
            loadCache();
        } catch (IOException e) {
            // Using modern error logging (could use a Logger, but keeping your print style)
            System.err.printf("CRITICAL: IO Error during initialization of %s: %s%n", DATA_PATH, e.getMessage());
        }
    }

    private synchronized void loadCache() {
        if (Files.notExists(cacheFilePath)) {
            System.out.println("No existing cache found. Starting fresh at " + cacheFilePath);
            return;
        }
        try {
            var type = mapper.getTypeFactory().constructMapType(Map.class, String.class, CacheEntry.class);
            Map<String, CacheEntry> loaded = mapper.readValue(cacheFilePath.toFile(), type);

            if (loaded != null) {
                cache.putAll(loaded);
                System.out.printf("✅ Cache loaded: %d entries.%n", cache.size());
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to load cache: " + e.getMessage());
        }
    }

    /**
     * Modernized Atomic Save with enhanced error handling.
     */
    public synchronized void saveCache() {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(cacheFilePath.getParent(), "cache-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), cache);
            Files.move(tempFile, cacheFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tempFile = null;
        } catch (IOException e) {
            System.err.println("❌ Persistent storage failure: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException _) {
                }
            }
        }
    }

    public void addEntries(Map<String, Boolean> newEntries) {
        // Pattern Matching can be used if entry values were more complex
        newEntries.forEach((hash, used) -> cache.put(hash, new CacheEntry(used)));
        saveCache();
    }
}