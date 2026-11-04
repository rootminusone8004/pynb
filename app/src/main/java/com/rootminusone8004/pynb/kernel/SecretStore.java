package com.rootminusone8004.pynb.kernel;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Small encrypted key/value store for SSH passwords, private keys and passphrases.
 * Values are encrypted with AES-256-GCM under a key that lives in the Android Keystore and
 * never leaves it; only ciphertext is written to SharedPreferences.
 */
final class SecretStore {

    private static final String TAG = "SecretStore";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "pynb_ssh_secrets";
    private static final String PREFS = "pynb_ssh_secrets";
    private static final int IV_BYTES = 12;

    private final SharedPreferences prefs;

    SecretStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void put(String name, @Nullable byte[] value) {
        if (value == null) {
            prefs.edit().remove(name).apply();
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] ct = cipher.doFinal(value);
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            prefs.edit().putString(name, Base64.encodeToString(all, Base64.NO_WRAP)).apply();
        } catch (Exception e) {
            throw new IllegalStateException("Could not store the secret securely: " + e.getMessage(), e);
        }
    }

    void putString(String name, @Nullable String value) {
        put(name, value == null ? null : value.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns null when missing or unreadable (e.g. restored from a backup on another device). */
    @Nullable
    byte[] get(String name) {
        String stored = prefs.getString(name, null);
        if (stored == null) return null;
        try {
            byte[] all = Base64.decode(stored, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOfRange(all, 0, IV_BYTES)));
            return cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
        } catch (Exception e) {
            Log.w(TAG, "Could not decrypt " + name, e);
            return null;
        }
    }

    @Nullable
    String getString(String name) {
        byte[] v = get(name);
        return v == null ? null : new String(v, StandardCharsets.UTF_8);
    }

    boolean has(String name) {
        return prefs.contains(name);
    }

    void remove(String name) {
        prefs.edit().remove(name).apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }
}
