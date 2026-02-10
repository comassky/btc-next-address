package com.btc.address.service;

import java.util.*;
import org.bitcoinj.crypto.DeterministicKey;
import com.btc.address.bitcoin.BIP84Deriver;
import com.btc.address.blockchain.BlockchainChecker;
import com.btc.address.cache.AddressCacheManager;
import com.btc.address.resource.NextAddressResult;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AddressService {

    @Inject
    BlockchainChecker blockchainChecker;
    @Inject
    AddressCacheManager cacheManager;

    public NextAddressResult findNextUnusedAddress(String xpub, int startIndex, String salt) {
        final String effectiveSalt = (salt == null || salt.isBlank())
                ? BIP84Deriver.generateSaltFromXpub(xpub)
                : salt;

        DeterministicKey externalChainKey = BIP84Deriver.createMasterKey(xpub);
        int batchSize = 20;
        int currentStart = startIndex;

        while (currentStart < startIndex + 1000) {
            List<BIP84Deriver.DerivedAddress> currentBatch = new ArrayList<>();
            List<String> addressesToScan = new ArrayList<>();
            Map<String, String> addrToHash = new HashMap<>();

            // 1. CHECK CACHE FIRST
            for (int i = 0; i < batchSize; i++) {
                BIP84Deriver.DerivedAddress derived = BIP84Deriver.deriveAddress(externalChainKey, currentStart + i);
                String hash = BIP84Deriver.generateHash(derived.address, effectiveSalt);

                currentBatch.add(derived);
                addrToHash.put(derived.address, hash);

                Optional<Boolean> cachedStatus = cacheManager.getKnownStatus(hash);

                if (cachedStatus.isEmpty()) {
                    addressesToScan.add(derived.address);
                } else if (!cachedStatus.get()) {
                    // CACHE HIT: Address is known to be unused!
                    return buildResult(derived, currentStart + i, effectiveSalt, hash);
                }
            }

            // 2. SCAN ONLY UNKNOWN ADDRESSES
            if (!addressesToScan.isEmpty()) {
                Map<String, Boolean> scanResults = blockchainChecker.checkAddressesBatch(addressesToScan);

                Map<String, Boolean> entriesToCache = new HashMap<>();
                scanResults.forEach((addr, used) -> entriesToCache.put(addrToHash.get(addr), used));

                cacheManager.addEntries(entriesToCache);

                for (BIP84Deriver.DerivedAddress derived : currentBatch) {
                    if (scanResults.containsKey(derived.address) && !scanResults.get(derived.address)) {
                        String hash = addrToHash.get(derived.address);
                        return buildResult(derived, currentBatch.indexOf(derived) + currentStart, effectiveSalt, hash);
                    }
                }
            }

            currentStart += batchSize;
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
        }

        throw new RuntimeException("No unused address found within the next 100 derivations.");
    }

    private NextAddressResult buildResult(BIP84Deriver.DerivedAddress derived, int index, String salt, String hash) {
        String qrCode = generateQrCodeSvgBase64("bitcoin:" + derived.address);
        return new NextAddressResult(derived.address, derived.publicKey, index, qrCode, salt, hash);
    }

    private String generateQrCodeSvgBase64(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            StringBuilder sb = new StringBuilder();
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 ")
                    .append(width).append(" ").append(height).append("\" shape-rendering=\"crispEdges\">")
                    .append("<path fill=\"#ffffff\" d=\"M0 0h").append(width).append("v").append(height)
                    .append("H0z\"/>")
                    .append("<path stroke=\"#000000\" d=\"");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (bitMatrix.get(x, y))
                        sb.append("M").append(x).append(" ").append(y).append("h1v1h-1z ");
                }
            }
            sb.append("\"/></svg>");
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(sb.toString().getBytes());
        } catch (WriterException e) {
            return "";
        }
    }

    public NextAddressResult findNextUnusedAddress(String xpub) {
        return findNextUnusedAddress(xpub, 0, null);
    }

    public VerificationResult verifyAddressOwnership(String xpub, String targetAddress) {
        DeterministicKey externalChainKey = BIP84Deriver.createMasterKey(xpub);
        int maxLookup = 1000;

        for (int i = 0; i < maxLookup; i++) {
            BIP84Deriver.DerivedAddress derived = BIP84Deriver.deriveAddress(externalChainKey, i);

            if (derived.address.equalsIgnoreCase(targetAddress)) {
                return new VerificationResult(true, i);
            }
        }

        return new VerificationResult(false, -1);
    }
}