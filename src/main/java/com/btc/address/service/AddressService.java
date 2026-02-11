package com.btc.address.service;

import java.util.*;
import java.util.stream.IntStream;
import java.nio.charset.StandardCharsets;

import com.btc.address.resource.AddressData;
import com.btc.address.bitcoin.BIP84Deriver;
import com.btc.address.blockchain.BlockchainChecker;
import com.btc.address.cache.AddressCacheManager;
import com.btc.address.resource.NextAddressResult;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bitcoinj.crypto.DeterministicKey;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AddressService {

    @Inject
    BlockchainChecker blockchainChecker;

    @Inject
    AddressCacheManager cacheManager;

    @ConfigProperty(name = "bitcoin.gap-limit")
    int gapLimit;

    @RegisterForReflection
    public record VerificationResult(boolean valid, int index) {}

    /**
     * Verifies if a specific address belongs to a provided xpub.
     */
    public VerificationResult verifyAddressOwnership(String xpub, String targetAddress) {
        if (targetAddress == null || targetAddress.isBlank()) {
            return new VerificationResult(false, -1);
        }

        final var masterKey = BIP84Deriver.createMasterKey(xpub);

        return IntStream.range(0, gapLimit)
                .parallel()
                .mapToObj(i -> new VerificationResult(targetAddress.equals(BIP84Deriver.deriveAddress(masterKey, i).address()), i))
                .filter(VerificationResult::valid)
                .findFirst()
                .orElse(new VerificationResult(false, -1));
    }

    /**
     * Main entry point to find the next available Bitcoin address.
     */
    public NextAddressResult findNextUnusedAddress(String xpub, int startIndex, String salt) {
        final String effectiveSalt = getEffectiveSalt(xpub, salt);
        final var masterKey = BIP84Deriver.createMasterKey(xpub);
        final int batchSize = 20;

        for (int currentStart = startIndex; currentStart < startIndex + gapLimit; currentStart += batchSize) {
            int currentEnd = Math.min(currentStart + batchSize, startIndex + gapLimit);

            // Step 1: Generate batch data
            List<AddressData> batch = deriveBatch(masterKey, currentStart, currentEnd, effectiveSalt);

            // Step 2: Check cache and attempt early exit
            Optional<NextAddressResult> cachedResult = checkCacheAndVerify(batch, effectiveSalt);
            if (cachedResult.isPresent()) return cachedResult.get();

            // Step 3: Scan unknown addresses on blockchain
            Optional<NextAddressResult> scannedResult = scanAndProcessUnknowns(batch, effectiveSalt);
            if (scannedResult.isPresent()) return scannedResult.get();
        }

        throw new RuntimeException("Gap limit reached: No unused address found within " + gapLimit + " indices.");
    }

    // --- Private Helper Methods ---

    private String getEffectiveSalt(String xpub, String salt) {
        return (salt == null || salt.isBlank()) ? BIP84Deriver.generateSaltFromXpub(xpub) : salt;
    }

    /**
     * Derives a batch of addresses and calculates their internal hashes.
     */
    private List<AddressData> deriveBatch(DeterministicKey masterKey, int start, int end, String salt) {
        List<AddressData> batch = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            var derived = BIP84Deriver.deriveAddress(masterKey, i);
            var hash = BIP84Deriver.generateHash(derived.address(), salt);
            batch.add(new AddressData(i, derived, hash));
        }
        return batch;
    }

    /**
     * Filters batch for addresses present in cache and performs a live double-check.
     */
    private Optional<NextAddressResult> checkCacheAndVerify(List<AddressData> batch, String salt) {
        List<String> hashes = batch.stream().map(AddressData::hash).toList();
        Map<String, Boolean> cachedStatuses = cacheManager.getMultiStatus(hashes);

        return batch.stream()
                .filter(item -> cachedStatuses.getOrDefault(item.hash(), true) == false)
                .findFirst()
                .flatMap(item -> {
                    if (isStillUnusedOnChain(item.derived().address())) {
                        return Optional.of(buildResult(item, salt));
                    } else {
                        cacheManager.addEntries(Map.of(item.hash(), true));
                        return Optional.empty();
                    }
                });
    }

    /**
     * Identifies unknown addresses, scans them, updates cache, and returns the first unused one.
     */
    private Optional<NextAddressResult> scanAndProcessUnknowns(List<AddressData> batch, String salt) {
        List<String> hashes = batch.stream().map(AddressData::hash).toList();
        Map<String, Boolean> cachedStatuses = cacheManager.getMultiStatus(hashes);

        List<AddressData> toScan = batch.stream()
                .filter(item -> !cachedStatuses.containsKey(item.hash()))
                .toList();

        if (toScan.isEmpty()) return Optional.empty();

        Map<String, Boolean> scanResults = blockchainChecker.checkAddressesBatch(
                toScan.stream().map(d -> d.derived().address()).toList()
        );

        // Update cache with all results from this scan
        Map<String, Boolean> newEntries = new HashMap<>();
        toScan.forEach(item -> {
            Boolean used = scanResults.get(item.derived().address());
            if (used != null) newEntries.put(item.hash(), used);
        });
        cacheManager.addEntries(newEntries);

        // Return the first one found as unused during the scan
        return toScan.stream()
                .filter(item -> scanResults.getOrDefault(item.derived().address(), true) == false)
                .findFirst()
                .map(item -> buildResult(item, salt));
    }

    private boolean isStillUnusedOnChain(String address) {
        return blockchainChecker.checkAddressesBatch(Collections.singletonList(address))
                .getOrDefault(address, true) == false;
    }

    private NextAddressResult buildResult(AddressData data, String salt) {
        return new NextAddressResult(
                data.derived().address(),
                data.derived().publicKey(),
                data.index(),
                generateQrCodeSvgBase64("bitcoin:" + data.derived().address()),
                salt,
                data.hash()
        );
    }

    /**
     * Generates a QR code with horizontal path merging to reduce SVG size.
     */
    private String generateQrCodeSvgBase64(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 200, 200);
            StringBuilder sb = new StringBuilder(4000);

            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 200\" shape-rendering=\"crispEdges\">")
                    .append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>")
                    .append("<path fill=\"#000000\" d=\"");

            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        int width = 1;
                        while (x + width < matrix.getWidth() && matrix.get(x + width, y)) width++;
                        sb.append("M").append(x).append(" ").append(y).append("h").append(width).append("v1h-").append(width).append("z ");
                        x += (width - 1);
                    }
                }
            }
            sb.append("\"/></svg>");

            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (WriterException _) {
            return "";
        }
    }
}