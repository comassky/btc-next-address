package com.btc.address.service;

import java.util.*;
import java.util.stream.IntStream;
import java.nio.charset.StandardCharsets;
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
     * Verifies if a specific BTC address belongs to the provided xpub by scanning indices up to gapLimit.
     */
    public VerificationResult verifyAddressOwnership(String xpub, String targetAddress) {
        if (targetAddress == null || targetAddress.isBlank()) {
            return new VerificationResult(false, -1);
        }
        
        DeterministicKey externalChainKey = BIP84Deriver.createMasterKey(xpub);
        
        return IntStream.range(0, gapLimit)
                .parallel()
                .filter(i -> {
                    var derived = BIP84Deriver.deriveAddress(externalChainKey, i);
                    return targetAddress.equals(derived.address());
                })
                .mapToObj(i -> new VerificationResult(true, i))
                .findFirst()
                .orElse(new VerificationResult(false, -1));
    }

    /**
     * Finds the next unused Bitcoin address by checking cache first, then scanning the blockchain.
     */
    public NextAddressResult findNextUnusedAddress(String xpub, int startIndex, String salt) {
        final String effectiveSalt = (salt == null || salt.isBlank()) 
            ? BIP84Deriver.generateSaltFromXpub(xpub) : salt;

        DeterministicKey externalChainKey = BIP84Deriver.createMasterKey(xpub);
        int batchSize = 20; 
        int currentStart = startIndex;

        while (currentStart < startIndex + gapLimit) {
            List<BIP84Deriver.DerivedAddress> currentBatch = new ArrayList<>();
            List<String> addressesToScan = new ArrayList<>();
            Map<String, String> addrToHashMap = new HashMap<>();

            for (int i = 0; i < batchSize; i++) {
                int currentIndex = currentStart + i;
                // Safety check: stop if we exceed the dynamic gap limit inside the batch
                if (currentIndex >= startIndex + gapLimit) break;

                var derived = BIP84Deriver.deriveAddress(externalChainKey, currentIndex);
                String hash = BIP84Deriver.generateHash(derived.address(), effectiveSalt);
                
                currentBatch.add(derived);
                addrToHashMap.put(derived.address(), hash);

                Optional<Boolean> cachedStatus = cacheManager.getKnownStatus(hash);
                if (cachedStatus.isPresent() && !cachedStatus.get()) {
                    return buildResult(derived, currentIndex, effectiveSalt, hash);
                }
                
                if (cachedStatus.isEmpty()) {
                    addressesToScan.add(derived.address());
                }
            }

            if (!addressesToScan.isEmpty()) {
                Map<String, Boolean> scanResults = blockchainChecker.checkAddressesBatch(addressesToScan);
                Map<String, Boolean> entriesToCache = new HashMap<>();
                
                scanResults.forEach((addr, used) -> {
                    String h = addrToHashMap.get(addr);
                    if (h != null) entriesToCache.put(h, used);
                });
                
                cacheManager.addEntries(entriesToCache);

                for (var derived : currentBatch) {
                    if (scanResults.containsKey(derived.address()) && !scanResults.get(derived.address())) {
                        int finalIndex = currentStart + currentBatch.indexOf(derived);
                        return buildResult(derived, finalIndex, effectiveSalt, addrToHashMap.get(derived.address()));
                    }
                }
            }
            currentStart += batchSize;
        }
        throw new RuntimeException("No unused address found within the gap limit of " + gapLimit);
    }

    private NextAddressResult buildResult(BIP84Deriver.DerivedAddress derived, int index, String salt, String hash) {
        String qrCode = generateQrCodeSvgBase64("bitcoin:" + derived.address());
        return new NextAddressResult(derived.address(), derived.publicKey(), index, qrCode, salt, hash);
    }

    private String generateQrCodeSvgBase64(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);
            
            StringBuilder sb = new StringBuilder();
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 200 200\" shape-rendering=\"crispEdges\">")
              .append("<path fill=\"#ffffff\" d=\"M0 0h200v200H0z\"/>")
              .append("<path stroke=\"#000000\" d=\"");
            
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 200; x++) {
                    if (bitMatrix.get(x, y)) {
                        sb.append("M").append(x).append(" ").append(y).append("h1v1h-1z ");
                    }
                }
            }
            sb.append("\"/></svg>");
            
            byte[] svgBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svgBytes);
        } catch (WriterException e) {
            return "";
        }
    }
}