package com.wrist.tingji.core;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES/GCM for API keys, backed by Android Keystore. Some watch firmwares ship a
 * broken keystore, so this degrades to a private-prefs obfuscated value instead
 * of crashing or silently losing the key.
 */
public class SecureStore {
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "wrist_tingji_secret_v1";
    private static final String PREFIX = "v1:";
    private static final String FALLBACK = "f1:";

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        try {
            SecretKey key = getOrCreateKey();
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] out = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] joined = new byte[iv.length + out.length];
            System.arraycopy(iv, 0, joined, 0, iv.length);
            System.arraycopy(out, 0, joined, iv.length, out.length);
            return PREFIX + Base64.encodeToString(joined, Base64.NO_WRAP);
        } catch (Throwable t) {
            return FALLBACK + Base64.encodeToString(plain.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            if (value.startsWith(FALLBACK)) {
                return new String(Base64.decode(value.substring(FALLBACK.length()), Base64.NO_WRAP),
                        StandardCharsets.UTF_8);
            }
            if (!value.startsWith(PREFIX)) return value;
            byte[] joined = Base64.decode(value.substring(PREFIX.length()), Base64.NO_WRAP);
            if (joined.length < 13) return "";
            byte[] iv = new byte[12];
            System.arraycopy(joined, 0, iv, 0, 12);
            byte[] ct = new byte[joined.length - 12];
            System.arraycopy(joined, 12, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(STORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            KeyStore.Entry entry = ks.getEntry(ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true);
        kg.init(b.build());
        return kg.generateKey();
    }
}
