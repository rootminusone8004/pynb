package com.pynb.app.kernel;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pinned SSH host keys (trust on first use). Unknown or changed keys are rejected and
 * remembered as a {@link Prompt}, so the UI can show the fingerprint and ask the user.
 */
public final class KnownHosts implements HostKeyRepository {

    /** A host key the user has to confirm before pynb connects. */
    public static final class Prompt {
        @NonNull public final String host;       // JSch alias: "host" or "[host]:port"
        @NonNull public final String keyType;
        @NonNull public final String keyBase64;
        @NonNull public final String fingerprint;
        /** Fingerprint of the previously trusted key, or null when the host is new. */
        @Nullable public final String previousFingerprint;

        Prompt(@NonNull String host, @NonNull String keyType, @NonNull String keyBase64,
               @NonNull String fingerprint, @Nullable String previousFingerprint) {
            this.host = host;
            this.keyType = keyType;
            this.keyBase64 = keyBase64;
            this.fingerprint = fingerprint;
            this.previousFingerprint = previousFingerprint;
        }

        public boolean isChanged() {
            return previousFingerprint != null;
        }
    }

    /** Thrown by {@link SshConnection#open} when the host key needs the user's confirmation. */
    public static final class UnverifiedHostException extends Exception {
        @NonNull public final Prompt prompt;

        UnverifiedHostException(@NonNull Prompt prompt) {
            super(prompt.isChanged() ? "The server's host key has changed." : "Unknown server host key.");
            this.prompt = prompt;
        }
    }

    private static final String PREFS = "pynb_known_hosts";

    private final SharedPreferences prefs;
    @Nullable private Prompt pending;

    KnownHosts(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Trusts the key from a prompt the user accepted (replacing any earlier key). */
    void trust(@NonNull Prompt prompt) {
        prefs.edit().putString(prompt.host, prompt.keyType + " " + prompt.keyBase64).apply();
    }

    /** The unknown/changed key seen by the last failed connect, if any. */
    @Nullable
    synchronized Prompt takePending() {
        Prompt p = pending;
        pending = null;
        return p;
    }

    @Override
    public synchronized int check(String host, byte[] key) {
        String keyB64 = Base64.encodeToString(key, Base64.NO_WRAP);
        String stored = prefs.getString(host, null);
        if (stored != null) {
            String storedKey = stored.substring(stored.indexOf(' ') + 1);
            if (storedKey.equals(keyB64)) {
                pending = null;
                return OK;
            }
            pending = new Prompt(host, keyType(key), keyB64, fingerprint(key),
                    fingerprint(Base64.decode(storedKey, Base64.NO_WRAP)));
            return CHANGED;
        }
        pending = new Prompt(host, keyType(key), keyB64, fingerprint(key), null);
        return NOT_INCLUDED;
    }

    @Override
    public void add(HostKey hostkey, UserInfo ui) {
        // Keys are only added through trust(), after the user confirmed the fingerprint.
    }

    @Override
    public void remove(String host, String type) {
        prefs.edit().remove(host).apply();
    }

    @Override
    public void remove(String host, String type, byte[] key) {
        prefs.edit().remove(host).apply();
    }

    @Override
    public String getKnownHostsRepositoryID() {
        return "pynb";
    }

    @Override
    public HostKey[] getHostKey() {
        List<HostKey> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            HostKey k = toHostKey(e.getKey(), String.valueOf(e.getValue()));
            if (k != null) out.add(k);
        }
        return out.toArray(new HostKey[0]);
    }

    @Override
    public HostKey[] getHostKey(String host, String type) {
        String stored = prefs.getString(host, null);
        HostKey k = stored == null ? null : toHostKey(host, stored);
        if (k == null || (type != null && !type.equals(k.getType()))) return new HostKey[0];
        return new HostKey[]{k};
    }

    @Nullable
    private static HostKey toHostKey(String host, String stored) {
        int sp = stored.indexOf(' ');
        if (sp < 0) return null;
        try {
            return new HostKey(host, Base64.decode(stored.substring(sp + 1), Base64.NO_WRAP));
        } catch (JSchException | IllegalArgumentException e) {
            return null;
        }
    }

    /** OpenSSH-style fingerprint: {@code SHA256:<base64 without padding>}. */
    static String fingerprint(byte[] key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
            return "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            return "?";
        }
    }

    /** Reads the algorithm name at the start of an SSH public key blob. */
    private static String keyType(byte[] key) {
        if (key.length >= 4) {
            int len = ((key[0] & 0xff) << 24) | ((key[1] & 0xff) << 16) | ((key[2] & 0xff) << 8) | (key[3] & 0xff);
            if (len > 0 && len <= key.length - 4 && len < 64) {
                return new String(key, 4, len, StandardCharsets.US_ASCII);
            }
        }
        return "unknown";
    }
}
