package com.btc.address.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

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
            ? BIP84Deriver.generateSaltFromXpub(xpub) : salt;

        DeterministicKey externalChainKey = BIP84Deriver.createMasterKey(xpub);
        
        int batchSize = 20; // On vérifie 20 adresses par appel API
        int currentStart = startIndex;

        // Limite de recherche à 100 adresses pour éviter les boucles infinies
        while (currentStart < startIndex + 100) {
            List<BIP84Deriver.DerivedAddress> batch = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                batch.add(BIP84Deriver.deriveAddress(externalChainKey, currentStart + i));
            }

            List<String> addressStrings = batch.stream().map(d -> d.address).toList();

            // Appel API groupé
            Map<String, Boolean> usageMap = blockchainChecker.checkAddressesBatch(addressStrings);

            for (int i = 0; i < batch.size(); i++) {
                BIP84Deriver.DerivedAddress derived = batch.get(i);
                String hash = BIP84Deriver.generateHash(derived.address, effectiveSalt);

                boolean isUsed = cacheManager.isAddressUsed(hash) || usageMap.getOrDefault(derived.address, true);

                if (!isUsed) {
                    // Cache l'état pour les futurs appels
                    cacheManager.addEntry(hash, false);
                    String qrCode = generateQrCodeSvgBase64("bitcoin:" + derived.address);
                    return new NextAddressResult(derived.address, derived.publicKey, currentStart + i, qrCode, effectiveSalt, hash);
                } else {
                    cacheManager.addEntry(hash, true);
                }
            }
            
            currentStart += batchSize;
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        throw new RuntimeException("Aucune adresse vierge trouvée dans les 100 prochaines adresses.");
    }

    private String generateQrCodeSvgBase64(String text) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);
            
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            StringBuilder sb = new StringBuilder();
            
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 ")
              .append(width).append(" ").append(height).append("\" shape-rendering=\"crispEdges\">");
            sb.append("<path fill=\"#ffffff\" d=\"M0 0h").append(width).append("v").append(height).append("H0z\"/>");
            sb.append("<path stroke=\"#000000\" d=\"");
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (bitMatrix.get(x, y)) {
                        sb.append("M").append(x).append(" ").append(y).append("h1v1h-1z ");
                    }
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
}