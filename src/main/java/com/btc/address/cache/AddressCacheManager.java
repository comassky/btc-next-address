package com.btc.address.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@RegisterForReflection
public class AddressCacheManager {
    private static final String DATA_PATH = "/data";
    private static final String CACHE_FILE = "/data/address-cache.json";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @RegisterForReflection
    public record CacheEntry(boolean used, java.time.Instant timestamp) {
        public CacheEntry(boolean used) { this(used, java.time.Instant.now()); }
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(Paths.get(DATA_PATH));
            Path path = Paths.get(CACHE_FILE);
            if (Files.exists(path)) {
                var type = mapper.getTypeFactory().constructMapType(Map.class, String.class, CacheEntry.class);
                Map<String, CacheEntry> loaded = mapper.readValue(path.toFile(), type);
                cache.putAll(loaded);
                System.out.println("✅ Cache loaded: " + cache.size() + " entries.");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Cache empty or inaccessible: " + e.getMessage());
        }
    }

    public Optional<Boolean> getKnownStatus(String hash) {
        return Optional.ofNullable(cache.get(hash)).map(CacheEntry::used);
    }

    public void addEntries(Map<String, Boolean> newEntries) {
        newEntries.forEach((hash, used) -> cache.put(hash, new CacheEntry(used)));
        saveCache(); 
    }

    public void addEntry(String hash, boolean used) {
        cache.put(hash, new CacheEntry(used));
        saveCache();
    }

    private synchronized void saveCache() {
        try {
            Path tempFile = Files.createTempFile(Paths.get(DATA_PATH), "cache-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), cache);
                Files.move(tempFile, Paths.get(CACHE_FILE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }
        } catch (IOException e) {
            System.err.println("❌ Cache save error: " + e.getMessage());
        }
    }
}