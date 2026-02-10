package com.btc.address.service;

import java.util.Base64;
import java.util.Objects;
import java.util.stream.IntStream;

import org.bitcoinj.crypto.DeterministicKey;

import com.btc.address.bitcoin.BIP84Deriver;
import com.btc.address.blockchain.BlockchainChecker;
import com.btc.address.cache.AddressCacheManager;
import com.btc.address.resource.NextAddressResult;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AddressService {

    @Inject
    BlockchainChecker blockchainChecker;

    @Inject
    AddressCacheManager cacheManager;

    /**
     * Find the next unused address starting from the given index.
     * Leveraging Java 25 Virtual Threads for high-concurrency blockchain checks.
     */
    @RunOnVirtualThread
    public NextAddressResult findNextUnusedAddress(String xpub, int startIndex, String salt) {
        final String effectiveSalt = (salt == null || salt.isBlank()) 
            ? BIP84Deriver.generateSaltFromXpub(xpub) : salt;

        DeterministicKey externalChainKey = BIP84Deriver.createMasterKey(xpub);
        int maxAttempts = 100;

        return IntStream.range(startIndex, startIndex + maxAttempts)
                .mapToObj(i -> {
                    try {
                        BIP84Deriver.DerivedAddress derived = BIP84Deriver.deriveAddress(externalChainKey, i);
                        String hash = BIP84Deriver.generateHash(derived.address, effectiveSalt);

                        // 1. Cache Check (Fast)
                        if (cacheManager.isAddressUsed(hash)) {
                            return null;
                        }

                        // 2. Blockchain Check (Network I/O - Virtual Thread handles the wait)
                        boolean isUsed = blockchainChecker.isAddressUsed(derived.address);
                        cacheManager.addEntry(hash, isUsed);

                        if (!isUsed) {
                            // 3. QR Generation (AWT-free SVG)
                            String qrCode = generateQrCodeSvgBase64("bitcoin:" + derived.address);
                            return new NextAddressResult(
                                derived.address, 
                                derived.publicKey, 
                                i, 
                                qrCode, 
                                effectiveSalt, 
                                hash
                            );
                        }
                    } catch (Exception e) {
                        System.err.println("Error at index " + i + ": " + e.getMessage());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No unused address found after " + maxAttempts + " attempts"));
    }

    public NextAddressResult findNextUnusedAddress(String xpub) {
        return findNextUnusedAddress(xpub, 0, null);
    }

    /**
     * Generates a QR code SVG using only ZXing Core BitMatrix.
     * Zero dependencies on java.awt or javax.imageio.
     */
    private String generateQrCodeSvgBase64(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            // Encode to a bit matrix (logic only, no pixels yet)
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);
            
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            
            StringBuilder sb = new StringBuilder();
            
            // Scalable Vector Graphics (SVG) header
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 ")
              .append(width).append(" ").append(height)
              .append("\" shape-rendering=\"crispEdges\">");
            
            // White Background
            sb.append("<path fill=\"#ffffff\" d=\"M0 0h").append(width).append("v").append(height).append("H0z\"/>");
            
            // Black Path for QR modules
            sb.append("<path stroke=\"#000000\" d=\"");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (bitMatrix.get(x, y)) {
                        // "Move to X Y, horizontal line 1, vertical line 1, horizontal back 1"
                        sb.append("M").append(x).append(" ").append(y).append("h1v1h-1z ");
                    }
                }
            }
            sb.append("\"/></svg>");

            // Encode the XML string to Base64
            String base64Content = Base64.getEncoder().encodeToString(sb.toString().getBytes());
            return "data:image/svg+xml;base64," + base64Content;
            
        } catch (WriterException e) {
            System.err.println("QR Generation Error: " + e.getMessage());
            return ""; 
        }
    }
}