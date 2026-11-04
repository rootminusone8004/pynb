package com.rootminusone8004.pynb.kernel;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Saved SSH servers (plain prefs) and their credentials (encrypted with {@link SecretStore}).
 */
public final class SshProfileStore {

    private static final String PREFS = "pynb_ssh_profiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_LAST = "last_profile";

    private static volatile SshProfileStore instance;

    private final SharedPreferences prefs;
    private final SecretStore secrets;

    public static SshProfileStore get(@NonNull Context context) {
        if (instance == null) {
            synchronized (SshProfileStore.class) {
                if (instance == null) instance = new SshProfileStore(context.getApplicationContext());
            }
        }
        return instance;
    }

    private SshProfileStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secrets = new SecretStore(context);
    }

    /** Most recently used first. */
    @NonNull
    public synchronized List<SshProfile> list() {
        List<SshProfile> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_PROFILES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                SshProfile p = SshProfile.fromJson(arr.optJSONObject(i));
                if (p != null) out.add(p);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    @Nullable
    public SshProfile find(@Nullable String id) {
        if (id == null) return null;
        for (SshProfile p : list()) {
            if (p.id().equals(id)) return p;
        }
        return null;
    }

    @Nullable
    public SshProfile last() {
        SshProfile p = find(prefs.getString(KEY_LAST, null));
        if (p != null) return p;
        List<SshProfile> all = list();
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Saves (or moves to the top) a profile. A null secret argument keeps what is stored;
     * switching the auth method drops the other method's secrets.
     */
    public synchronized void save(@NonNull SshProfile profile, @Nullable byte[] privateKey,
                                  @Nullable String passphrase, @Nullable String password) {
        save(profile, null, privateKey, passphrase, password);
    }

    /**
     * Like {@link #save(SshProfile, byte[], String, String)}, but secrets not given are taken from
     * the saved profile {@code inheritFromId} (used when a saved server's host/user/port is edited).
     */
    public synchronized void save(@NonNull SshProfile profile, @Nullable String inheritFromId,
                                  @Nullable byte[] privateKey, @Nullable String passphrase,
                                  @Nullable String password) {
        String id = profile.id();
        if (inheritFromId != null && !inheritFromId.equals(id)) {
            if (privateKey == null) {
                privateKey = secrets.get(inheritFromId + "/key");
                if (passphrase == null) passphrase = secrets.getString(inheritFromId + "/passphrase");
            }
            if (password == null) password = secrets.getString(inheritFromId + "/password");
        }
        if (privateKey != null) {
            secrets.put(id + "/key", privateKey);
            secrets.putString(id + "/passphrase", passphrase == null || passphrase.isEmpty() ? null : passphrase);
        } else if (passphrase != null) {
            secrets.putString(id + "/passphrase", passphrase.isEmpty() ? null : passphrase);
        }
        if (password != null) secrets.putString(id + "/password", password);
        if (profile.auth == SshProfile.Auth.KEY) {
            secrets.remove(id + "/password");
        } else {
            secrets.remove(id + "/key");
            secrets.remove(id + "/passphrase");
        }

        JSONArray arr = new JSONArray();
        try {
            arr.put(profile.toJson());
            for (SshProfile p : list()) {
                if (!p.id().equals(id)) arr.put(p.toJson());
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).putString(KEY_LAST, id).apply();
    }

    public synchronized void delete(@NonNull String id) {
        JSONArray arr = new JSONArray();
        try {
            for (SshProfile p : list()) {
                if (!p.id().equals(id)) arr.put(p.toJson());
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply();
        secrets.remove(id + "/key");
        secrets.remove(id + "/passphrase");
        secrets.remove(id + "/password");
    }

    public boolean hasPrivateKey(@NonNull SshProfile p) {
        return secrets.has(p.id() + "/key");
    }

    public boolean hasPassword(@NonNull SshProfile p) {
        return secrets.has(p.id() + "/password");
    }

    @Nullable
    byte[] privateKey(@NonNull SshProfile p) {
        return secrets.get(p.id() + "/key");
    }

    @Nullable
    String passphrase(@NonNull SshProfile p) {
        return secrets.getString(p.id() + "/passphrase");
    }

    @Nullable
    String password(@NonNull SshProfile p) {
        return secrets.getString(p.id() + "/password");
    }
}
