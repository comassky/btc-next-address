package com.btc.address.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@RegisterForReflection
public class AddressCacheManager {

    // On utilise /data par défaut pour le conteneur
    @ConfigProperty(name = "bitcoin.cache.path", defaultValue = "/data")
    String cacheDirectoryPath;

    private static final String CACHE_FILE_NAME = "address-cache.json";
    private Path cacheFilePath;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Map<String, CacheEntry> cache;

    @RegisterForReflection
    public record CacheEntry(
            @JsonIgnore String hash,
            boolean used,
            Instant timestamp
    ) {
        public CacheEntry(String hash, boolean used) {
            this(hash, used, Instant.now());
        }
    }

    @PostConstruct
    void init() {
        try {
            Path cacheDir = Paths.get(cacheDirectoryPath);
            Files.createDirectories(cacheDir); 
            this.cacheFilePath = cacheDir.resolve(CACHE_FILE_NAME);
            loadCache();
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to access /data. Cache will be volatile.");
            this.cache = new ConcurrentHashMap<>();
        }
    }

    private synchronized void loadCache() {
        this.cache = new ConcurrentHashMap<>();
        if (cacheFilePath == null || !Files.exists(cacheFilePath)) {
            return;
        }

        try {
            var typeFactory = mapper.getTypeFactory();
            Map<String, CacheEntry> loadedCache = mapper.readValue(cacheFilePath.toFile(),
                    typeFactory.constructMapType(ConcurrentHashMap.class, String.class, CacheEntry.class));

            Optional.ofNullable(loadedCache).ifPresent(this.cache::putAll);
            System.out.println("Cache loaded: " + this.cache.size() + " entries from " + cacheFilePath);
        } catch (IOException e) {
            System.err.println("Failed to load cache: " + e.getMessage());
        }
    }

    public synchronized void saveCache() {
        if (cacheFilePath == null) return;
        try {
            // Utilise un fichier temporaire dans le même dossier pour l'atomicité
            Path tempFile = Files.createTempFile(cacheFilePath.getParent(), "cache-", ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), cache);
            Files.move(tempFile, cacheFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Failed to save cache to /data: " + e.getMessage());
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