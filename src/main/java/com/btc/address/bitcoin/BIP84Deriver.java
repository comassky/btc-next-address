package com.btc.address.bitcoin;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.base.Base58;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Optimized BIP84 (Native SegWit) address deriver.
 * Handles both xpub and zpub prefixes for Mainnet.
 */
@RegisterForReflection
public class BIP84Deriver {

    private static final BitcoinNetwork NETWORK = BitcoinNetwork.MAINNET;
    
    // Header xpub standard (0x0488B21E)
    private static final int XPUB_VERSION = 0x0488B21E;

    @RegisterForReflection
    public record DerivedAddress(String address, String publicKey, int index) {}

    /**
     * Prepares the master key from the xpub/zpub.
     * Logic: m/84'/0'/0' (account level) -> m/0 (external chain)
     */
    public static DeterministicKey createMasterKey(String key) {
        String normalizedKey = key.startsWith("zpub") ? convertZpubToXpub(key) : key;
        DeterministicKey masterPubKey = DeterministicKey.deserializeB58(null, normalizedKey, NETWORK);
        return HDKeyDerivation.deriveChildKey(masterPubKey, ChildNumber.ZERO);
    }

    /**
     * Derives a Bech32 address from the pre-calculated external chain key.
     * Path: m/0/index
     */
    public static DerivedAddress deriveAddress(DeterministicKey externalChainKey, int index) {
        DeterministicKey childKey = HDKeyDerivation.deriveChildKey(externalChainKey, index);
        Address segwitAddress = childKey.toAddress(ScriptType.P2WPKH, NETWORK);

        return new DerivedAddress(
                segwitAddress.toString(),
                childKey.getPublicKeyAsHex(),
                index
        );
    }

    /**
     * Converts a zpub (BIP84) to an xpub format that bitcoinj can deserialize.
     * This only changes the version bytes, the underlying public key remains identical.
     */
    private static String convertZpubToXpub(String zpub) {
        try {
            byte[] data = Base58.decodeChecked(zpub);
            byte[] payload = Arrays.copyOfRange(data, 4, data.length);
            return Base58.encodeChecked(XPUB_VERSION, payload);
        } catch (Exception e) {
            return zpub;
        }
    }

    // --- Security Utilities ---
    public static String generateHash(String address, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((address + salt).getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    public static String generateSaltFromXpub(String xpub) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(xpub.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Salt generation failed", e);
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