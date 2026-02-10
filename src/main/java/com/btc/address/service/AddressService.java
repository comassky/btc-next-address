package com.btc.address.service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.nio.charset.StandardCharsets;

import com.btc.address.resource.AddressData;
import org.bitcoinj.crypto.DeterministicKey;
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
     * Verifies if a target address belongs to a given xpub using parallelized derivation.
     */
    public VerificationResult verifyAddressOwnership(String xpub, String targetAddress) {
        if (targetAddress == null || targetAddress.isBlank()) {
            return new VerificationResult(false, -1);
        }

        final var masterKey = BIP84Deriver.createMasterKey(xpub);

        return IntStream.range(0, gapLimit)
                .parallel() // Leverages multiple CPU cores for heavy SECP256K1 crypto
                .mapToObj(i -> new VerificationResult(targetAddress.equals(BIP84Deriver.deriveAddress(masterKey, i).address()), i))
                .filter(VerificationResult::valid)
                .findFirst()
                .orElse(new VerificationResult(false, -1));
    }

    /**
     * Finds the next unused Bitcoin address using a high-performance batching strategy.
     */
    public NextAddressResult findNextUnusedAddress(String xpub, int startIndex, String salt) {
        final String effectiveSalt = (salt == null || salt.isBlank())
                ? BIP84Deriver.generateSaltFromXpub(xpub) : salt;

        final var masterKey = BIP84Deriver.createMasterKey(xpub);
        final int batchSize = 20;

        // Iterate through address blocks until the gap limit is reached
        for (int currentStart = startIndex; currentStart < startIndex + gapLimit; currentStart += batchSize) {
            final int end = Math.min(currentStart + batchSize, startIndex + gapLimit);

            // 1. Bulk derivation and hashing
            final var batch = IntStream.range(currentStart, end)
                    .mapToObj(i -> {
                        var derived = BIP84Deriver.deriveAddress(masterKey, i);
                        var hash = BIP84Deriver.generateHash(derived.address(), effectiveSalt);
                        return new AddressData(i, derived, hash);
                    }).toList();

            // 2. Multi-status cache lookup (minimizes I/O overhead)
            final var cachedStatuses = cacheManager.getMultiStatus(batch.stream().map(AddressData::hash).toList());

            // 3. Early exit if an unused address is found in cache (Pattern Matching logic)
            final var foundInCache = batch.stream()
                    .filter(item -> cachedStatuses.get(item.hash()) instanceof Boolean used && !used)
                    .findFirst();

            if (foundInCache.isPresent()) {
                var item = foundInCache.get();
                return buildResult(item.derived(), item.index(), effectiveSalt, item.hash());
            }

            // 4. Identify unknown addresses for Blockchain Scanning
            var toScan = batch.stream()
                    .filter(item -> !cachedStatuses.containsKey(item.hash()))
                    .toList();

            if (!toScan.isEmpty()) {
                // Perform external network call for the unknown batch
                var scanResults = blockchainChecker.checkAddressesBatch(toScan.stream().map(d -> d.derived().address()).toList());

                // Bulk update the local cache with fresh results
                var newEntries = new HashMap<String, Boolean>();
                toScan.forEach(item -> {
                    if (scanResults.get(item.derived().address()) instanceof Boolean used) {
                        newEntries.put(item.hash(), used);
                    }
                });
                cacheManager.addEntries(newEntries);

                // Return the first confirmed unused address from the fresh scan
                for (var item : toScan) {
                    if (scanResults.get(item.derived().address()) instanceof Boolean used && !used) {
                        return buildResult(item.derived(), item.index(), effectiveSalt, item.hash());
                    }
                }
            }
        }

        throw new RuntimeException("Gap limit reached: No unused address found within " + gapLimit + " indices.");
    }

    /**
     * Constructs the final response object including the QR code.
     */
    private NextAddressResult buildResult(BIP84Deriver.DerivedAddress derived, int index, String salt, String hash) {
        return new NextAddressResult(
                derived.address(),
                derived.publicKey(),
                index,
                generateQrCodeSvgBase64("bitcoin:" + derived.address()),
                salt,
                hash
        );
    }

    /**
     * Generates a lightweight SVG QR code as a Base64 string.
     */
    private String generateQrCodeSvgBase64(String text) {
        try {
            var matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 200, 200);
            var sb = new StringBuilder();

            // Building a high-performance, crisp SVG using path drawing
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 200\" shape-rendering=\"crispEdges\">")
                    .append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>")
                    .append("<path fill=\"#000000\" d=\"");

            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) sb.append("M").append(x).append(" ").append(y).append("h1v1h-1z ");
                }
            }
            sb.append("\"/></svg>");

            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (WriterException _) { // Java 21+ Unnamed variable syntax
            return "";
        }
    }
}