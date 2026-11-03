package com.pynb.app.kernel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/** A saved SSH server: where to connect and which remote .venv to run. Secrets are stored separately. */
public final class SshProfile {

    public enum Auth { KEY, PASSWORD }

    @NonNull public final String host;
    public final int port;
    @NonNull public final String user;
    @NonNull public final Auth auth;
    @NonNull public final String venv;
    @NonNull public final String workDir;

    public SshProfile(@NonNull String host, int port, @NonNull String user, @NonNull Auth auth,
                      @NonNull String venv, @NonNull String workDir) {
        this.host = host.trim();
        this.port = port;
        this.user = user.trim();
        this.auth = auth;
        this.venv = venv.trim();
        this.workDir = workDir.trim();
    }

    /** Stable id, also used as the display label: {@code user@host} plus {@code :port} if not 22. */
    @NonNull
    public String id() {
        return user + "@" + host + (port == 22 ? "" : ":" + port);
    }

    @NonNull
    @Override
    public String toString() {
        return id();
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("host", host).put("port", port).put("user", user)
                .put("auth", auth.name()).put("venv", venv).put("workDir", workDir);
    }

    @Nullable
    static SshProfile fromJson(@Nullable JSONObject o) {
        if (o == null) return null;
        Auth auth;
        try {
            auth = Auth.valueOf(o.optString("auth", Auth.KEY.name()));
        } catch (IllegalArgumentException e) {
            auth = Auth.KEY;
        }
        String host = o.optString("host", "");
        String user = o.optString("user", "");
        if (host.isEmpty() || user.isEmpty()) return null;
        return new SshProfile(host, o.optInt("port", 22), user, auth,
                o.optString("venv", "~/.venv"), o.optString("workDir", ""));
    }
}
