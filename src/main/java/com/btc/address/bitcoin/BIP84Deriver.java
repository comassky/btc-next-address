package com.btc.address.bitcoin;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Optimized BIP84 (Native SegWit) address deriver.
 */
public class BIP84Deriver {

    private static final BitcoinNetwork NETWORK = BitcoinNetwork.MAINNET;

    public static class DerivedAddress {
        public final String address;
        public final String publicKey;
        public final int index;

        public DerivedAddress(String address, String publicKey, int index) {
            this.address = address;
            this.publicKey = publicKey;
            this.index = index;
        }
    }

    /**
     * Prepares the master key from the xpub.
     * Should be called only once before the loop.
     */
    public static DeterministicKey createMasterKey(String xpub) {
        // Deserialize xpub (expensive, do only once)
        DeterministicKey masterPubKey = DeterministicKey.deserializeB58(null, xpub, NETWORK);
        // Derive external chain (m/0)
        return HDKeyDerivation.deriveChildKey(masterPubKey, ChildNumber.ZERO);
    }

    /**
     * Derives an address from the pre-calculated external chain key.
     * Much faster inside a loop.
     */
    public static DerivedAddress deriveAddress(DeterministicKey externalChainKey, int index) {
        // Derive child key at desired index (m/0/i)
        DeterministicKey childKey = HDKeyDerivation.deriveChildKey(externalChainKey, new ChildNumber(index));

        // Generate Native SegWit address (P2WPKH -> bc1q...)
        Address segwitAddress = childKey.toAddress(ScriptType.P2WPKH, NETWORK);

        return new DerivedAddress(
                segwitAddress.toString(),
                childKey.getPublicKeyAsHex(),
                index
        );
    }

    // --- Security Utilities ---

    public static String generateHash(String address, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((address + salt).getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateSaltFromXpub(String xpub) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(xpub.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
