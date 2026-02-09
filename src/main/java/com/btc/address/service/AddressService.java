package com.btc.address.service;

import com.btc.address.bitcoin.BIP84Deriver;
import com.btc.address.blockchain.BlockchainChecker;
import com.btc.address.cache.AddressCacheManager;
import com.btc.address.resource.NextAddressResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bitcoinj.crypto.DeterministicKey;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.stream.IntStream;

@ApplicationScoped
public class AddressService {
    @Inject
    BlockchainChecker blockchainChecker;

    @Inject
    AddressCacheManager cacheManager;

    /**
     * Find the next unused address starting from the given index
     */
    public NextAddressResult findNextUnusedAddress(String xpub, int startIndex, String salt) {
        final String effectiveSalt = (salt == null || salt.isBlank()) ? BIP84Deriver.generateSaltFromXpub(xpub) : salt;

        DeterministicKey externalChainKey = BIP84Deriver.createMasterKey(xpub);

        int maxAttempts = 100;

        return IntStream.range(startIndex, startIndex + maxAttempts)
                .mapToObj(i -> {
                    try {
                        BIP84Deriver.DerivedAddress derived = BIP84Deriver.deriveAddress(externalChainKey, i);
                        String hash = BIP84Deriver.generateHash(derived.address, effectiveSalt);

                        if (cacheManager.isAddressUsed(hash)) {
                            return null;
                        }

                        boolean isUsed = blockchainChecker.isAddressUsed(derived.address);
                        cacheManager.addEntry(hash, isUsed);

                        if (!isUsed) {
                            String qrCode = generateQrCodeBase64("bitcoin:" + derived.address);
                            return new NextAddressResult(derived.address, derived.publicKey, i, qrCode, effectiveSalt, hash);
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing address at index " + i + ": " + e.getMessage());
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No unused address found in " + maxAttempts + " attempts"));
    }

    private String generateQrCodeBase64(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 300, 300);
            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (WriterException | IOException e) {
            System.err.println("Could not generate QR code: " + e.getMessage());
            return ""; // Return empty string or a default placeholder image
        }
    }

    public NextAddressResult findNextUnusedAddress(String xpub) {
        return findNextUnusedAddress(xpub, 0, null);
    }
}
