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
        try {
            Files.createDirectories(cachePath.getParent());
            if (Files.exists(cachePath)) {
                var type = mapper.getTypeFactory().constructMapType(Map.class, String.class, CacheEntry.class);
                Map<String, CacheEntry> loaded = mapper.readValue(cachePath.toFile(), type);
                if (loaded != null) cache.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("❌ Initialisation du cache échouée: " + e.getMessage());
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
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException _) {}
        }
    }

    public Map<String, Boolean> getMultiStatus(Iterable<String> hashes) {
        return StreamSupport.stream(hashes.spliterator(), false)
                .filter(cache::containsKey)
                .collect(Collectors.toMap(h -> h, h -> cache.get(h).used(), (old, _) -> old));
    }

    public void addEntries(Map<String, Boolean> newEntries) {
        newEntries.forEach((h, u) -> cache.put(h, new CacheEntry(u)));
        saveCache();
    }

    public void addEntry(String hash, boolean used) {
        cache.put(hash, new CacheEntry(used));
    }
}