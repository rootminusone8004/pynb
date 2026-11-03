package com.pynb.app.kernel;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App-wide connection to a Python kernel (assets/kernel/pynb_kernel.py) running with the
 * interpreter of a user-chosen {@code .venv}, either in Termux on this phone or on a server
 * reached over SSH.
 *
 * <p><b>Termux:</b> the kernel is started through Termux's documented RUN_COMMAND intent
 * (https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent), which requires the
 * {@code com.termux.permission.RUN_COMMAND} permission and {@code allow-external-apps=true}
 * in {@code ~/.termux/termux.properties}. It then talks to the kernel over 127.0.0.1.</p>
 *
 * <p><b>SSH:</b> the kernel is started on the server with {@link RemoteScript}, listening on the
 * server's 127.0.0.1 only, and reached through an SSH local port forward. It outlives the SSH
 * connection (until its idle timeout), so pynb can reconnect after network drops or restarts.</p>
 */
public final class KernelManager {

    private static final String TAG = "KernelManager";

    public static final String TERMUX_PACKAGE = "com.termux";
    public static final String PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND";
    public static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    public static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";

    private static final String RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    private static final String EXTRA_PATH = "com.termux.RUN_COMMAND_PATH";
    private static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    private static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    private static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    private static final String EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
    private static final String EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";

    private static final String PREFS = "termux_kernel";
    private static final String PREF_VENV = "venv_path";
    private static final String PREF_WORKDIR = "work_dir";
    private static final String PREF_PORT = "session_port";
    private static final String PREF_TOKEN = "session_token";
    private static final String PREF_BACKEND = "backend";            // "termux" | "ssh"
    private static final String PREF_SSH_PROFILE = "ssh_profile";
    private static final String PREF_SSH_REMOTE_PORT = "ssh_remote_port";
    private static final String PREF_SSH_SESSION = "ssh_session";
    private static final String BACKEND_SSH = "ssh";

    private static final long START_TIMEOUT_MS = 30000;

    private static volatile KernelManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final ExecutorService controlExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<KernelState> state = new MutableLiveData<>(KernelState.disconnected());

    private volatile KernelClient client;
    private volatile String session;
    private volatile String processFailure;

    private final SshProfileStore sshProfiles;
    private final KnownHosts knownHosts;
    /** Open SSH session while connected to a remote kernel (null for Termux). */
    private volatile SshConnection ssh;
    private volatile int sshRemotePort;
    private volatile String sshSession;
    /** Profile of the last SSH connect attempt, retried after the user trusts its host key. */
    private volatile SshProfile lastSshAttempt;

    public static KernelManager get(@NonNull Context context) {
        if (instance == null) {
            synchronized (KernelManager.class) {
                if (instance == null) {
                    instance = new KernelManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private KernelManager(Context appContext) {
        this.appContext = appContext;
        this.prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.sshProfiles = SshProfileStore.get(appContext);
        this.knownHosts = new KnownHosts(appContext);
    }

    public SshProfileStore getSshProfiles() {
        return sshProfiles;
    }

    /** True when the last kernel the user connected to was an SSH server. */
    public boolean lastUsedSsh() {
        return BACKEND_SSH.equals(prefs.getString(PREF_BACKEND, ""));
    }

    public LiveData<KernelState> getState() {
        return state;
    }

    @NonNull
    public KernelState currentState() {
        KernelState s = state.getValue();
        return s != null ? s : KernelState.disconnected();
    }

    @Nullable
    public KernelClient getClient() {
        return client;
    }

    public String getSavedVenvPath() {
        return prefs.getString(PREF_VENV, "~/.venv");
    }

    public String getSavedWorkDir() {
        return prefs.getString(PREF_WORKDIR, "");
    }

    public static boolean isTermuxInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean hasRunCommandPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, PERMISSION_RUN_COMMAND)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Expands {@code ~} and {@code $PREFIX} the way Termux does, and drops a trailing slash. */
    public static String expandTermuxPath(String path) {
        String p = path == null ? "" : path.trim();
        if (p.equals("~")) p = TERMUX_HOME;
        else if (p.startsWith("~/")) p = TERMUX_HOME + p.substring(1);
        else if (p.startsWith("$PREFIX/")) p = TERMUX_PREFIX + p.substring(7);
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    // ------------------------------------------------------------------ connect / reattach

    /**
     * Reconnects to a kernel started earlier (e.g. before the app process was killed) if it is
     * still running. Silent: stays disconnected on failure.
     */
    public void tryReattach() {
        if (client != null || currentState().status == KernelState.Status.CONNECTING) {
            return;
        }
        if (BACKEND_SSH.equals(prefs.getString(PREF_BACKEND, ""))) {
            reattachSsh();
            return;
        }
        int port = prefs.getInt(PREF_PORT, 0);
        String token = prefs.getString(PREF_TOKEN, null);
        if (port <= 0 || token == null) {
            return;
        }
        controlExecutor.execute(() -> {
            KernelClient candidate = new KernelClient(port, token);
            try {
                JSONObject info = candidate.info();
                client = candidate;
                state.postValue(new KernelState(
                        info.optBoolean("busy") ? KernelState.Status.BUSY : KernelState.Status.IDLE,
                        null, info.optString("python_version"), getSavedVenvPath()));
            } catch (Exception e) {
                prefs.edit().remove(PREF_PORT).remove(PREF_TOKEN).apply();
            }
        });
    }

    private void reattachSsh() {
        SshProfile profile = sshProfiles.find(prefs.getString(PREF_SSH_PROFILE, null));
        int remotePort = prefs.getInt(PREF_SSH_REMOTE_PORT, 0);
        String token = prefs.getString(PREF_TOKEN, null);
        String sid = prefs.getString(PREF_SSH_SESSION, null);
        if (profile == null || remotePort <= 0 || token == null || sid == null) {
            return;
        }
        controlExecutor.execute(() -> {
            if (client != null) return;
            SshConnection conn = null;
            try {
                conn = SshConnection.open(profile, sshProfiles, knownHosts);
                KernelClient candidate = new KernelClient(conn.forward(remotePort), token);
                JSONObject info = candidate.info();
                ssh = conn;
                sshRemotePort = remotePort;
                sshSession = sid;
                client = candidate;
                state.postValue(new KernelState(
                        info.optBoolean("busy") ? KernelState.Status.BUSY : KernelState.Status.IDLE,
                        null, info.optString("python_version"), profile.venv, profile.id(), null));
            } catch (KnownHosts.UnverifiedHostException | IOException e) {
                // Offline, or the kernel is gone. Stay quiet; a server-side kernel idles out by itself.
                if (conn != null) {
                    conn.close();
                    clearSession(); // SSH worked, so the kernel itself is gone
                }
            } catch (Exception e) {
                if (conn != null) conn.close();
                clearSession();
            }
        });
    }

    /** Starts a kernel with {@code <venv>/bin/python} in Termux and waits until it answers. */
    public void connect(@NonNull String venvInput, @Nullable String workDirInput) {
        String venv = expandTermuxPath(venvInput);
        String workDir = workDirInput == null || workDirInput.trim().isEmpty()
                ? parentOf(venv) : expandTermuxPath(workDirInput);
        prefs.edit().putString(PREF_VENV, venvInput.trim())
                .putString(PREF_WORKDIR, workDirInput == null ? "" : workDirInput.trim()).apply();

        state.setValue(new KernelState(KernelState.Status.CONNECTING, null, null, venvInput));
        controlExecutor.execute(() -> {
            shutdownQuietly();
            try {
                doConnect(venv, workDir, venvInput.trim());
            } catch (Exception e) {
                Log.w(TAG, "Kernel start failed", e);
                client = null;
                state.postValue(new KernelState(KernelState.Status.ERROR,
                        e.getMessage() != null ? e.getMessage() : e.toString(), null, venvInput));
            }
        });
    }

    private void doConnect(String venv, String workDir, String venvLabel) throws Exception {
        if (!isTermuxInstalled(appContext)) {
            throw new IllegalStateException("Termux is not installed.");
        }
        if (!hasRunCommandPermission(appContext)) {
            throw new IllegalStateException("pynb needs the \"Run commands in Termux environment\" permission.");
        }

        int port = findFreePort();
        String token = newToken();
        String sessionId = newToken().substring(0, 12);
        session = sessionId;
        processFailure = null;

        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE);
        intent.setAction(ACTION_RUN_COMMAND);
        intent.putExtra(EXTRA_PATH, venv + "/bin/python");
        intent.putExtra(EXTRA_ARGUMENTS, new String[]{
                "-u", "-c", readKernelScript(), "--port", String.valueOf(port), "--token", token});
        intent.putExtra(EXTRA_WORKDIR, workDir);
        intent.putExtra(EXTRA_BACKGROUND, true);
        intent.putExtra(EXTRA_LABEL, "pynb kernel");
        intent.putExtra(EXTRA_PENDING_INTENT, resultIntent(sessionId));

        try {
            // Connecting is always user-initiated while pynb is in the foreground, so a plain
            // startService() is allowed; Termux promotes its own service to the foreground.
            appContext.startService(intent);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Could not reach Termux. Open the Termux app once and try again.", e);
        } catch (SecurityException e) {
            throw new IllegalStateException("Termux refused the request. Grant pynb the \"Run commands in "
                    + "Termux environment\" permission and set allow-external-apps=true in "
                    + "~/.termux/termux.properties.", e);
        }

        KernelClient candidate = new KernelClient(port, token);
        long deadline = SystemClock.elapsedRealtime() + START_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (processFailure != null) {
                throw new IllegalStateException(processFailure);
            }
            try {
                JSONObject info = candidate.info();
                client = candidate;
                prefs.edit().putInt(PREF_PORT, port).putString(PREF_TOKEN, token)
                        .putString(PREF_BACKEND, "termux").apply();
                state.postValue(new KernelState(KernelState.Status.IDLE, null,
                        info.optString("python_version"), venvLabel));
                return;
            } catch (IOException notYet) {
                SystemClock.sleep(400);
            }
        }
        throw new IllegalStateException("The kernel did not start within "
                + (START_TIMEOUT_MS / 1000) + " s. Open Termux to check for errors, and make sure "
                + "allow-external-apps=true is set in ~/.termux/termux.properties.");
    }

    // ------------------------------------------------------------------ SSH

    /**
     * Starts a kernel on an SSH server with the profile's remote .venv. Save the profile and its
     * credentials in {@link #getSshProfiles()} first.
     */
    public void connectSsh(@NonNull SshProfile profile) {
        lastSshAttempt = profile;
        prefs.edit().putString(PREF_BACKEND, BACKEND_SSH).apply();
        state.setValue(new KernelState(KernelState.Status.CONNECTING, null, null, profile.venv, profile.id(), null));
        controlExecutor.execute(() -> {
            shutdownQuietly();
            try {
                doConnectSsh(profile);
            } catch (KnownHosts.UnverifiedHostException e) {
                state.postValue(new KernelState(KernelState.Status.ERROR, e.getMessage(), null,
                        profile.venv, profile.id(), e.prompt));
            } catch (Exception e) {
                Log.w(TAG, "SSH kernel start failed", e);
                client = null;
                state.postValue(new KernelState(KernelState.Status.ERROR,
                        e.getMessage() != null ? e.getMessage() : e.toString(), null, profile.venv, profile.id(), null));
            }
        });
    }

    /** Pins the host key the user just confirmed and retries the SSH connection. */
    public void trustHostKeyAndRetry(@NonNull KnownHosts.Prompt prompt) {
        knownHosts.trust(prompt);
        SshProfile profile = lastSshAttempt;
        if (profile != null) connectSsh(profile);
    }

    private void doConnectSsh(SshProfile profile) throws Exception {
        SshConnection conn = SshConnection.open(profile, sshProfiles, knownHosts);
        try {
            String token = newToken();
            String sid = newToken().substring(0, 12);
            String script = RemoteScript.startScript(profile.venv, profile.workDir, readKernelScript(), token, sid);
            int remotePort = RemoteScript.parsePort(conn.exec("sh -s", script.getBytes(StandardCharsets.UTF_8)));
            KernelClient candidate = new KernelClient(conn.forward(remotePort), token);

            JSONObject info = null;
            IOException last = null;
            for (int attempt = 0; attempt < 10 && info == null; attempt++) {
                try {
                    info = candidate.info();
                } catch (IOException e) {
                    last = e;
                    SystemClock.sleep(300);
                }
            }
            if (info == null) {
                throw new IOException("The kernel started on the server but could not be reached through SSH"
                        + (last != null ? ": " + last.getMessage() : "") + ". The server may forbid port "
                        + "forwarding (AllowTcpForwarding in sshd_config).", last);
            }
            ssh = conn;
            sshRemotePort = remotePort;
            sshSession = sid;
            client = candidate;
            prefs.edit().putString(PREF_BACKEND, BACKEND_SSH).putString(PREF_SSH_PROFILE, profile.id())
                    .putInt(PREF_SSH_REMOTE_PORT, remotePort).putString(PREF_SSH_SESSION, sid)
                    .putString(PREF_TOKEN, token).remove(PREF_PORT).apply();
            state.postValue(new KernelState(KernelState.Status.IDLE, null,
                    info.optString("python_version"), profile.venv, profile.id(), null));
        } catch (Exception e) {
            if (ssh != conn) conn.close();
            throw e;
        }
    }

    /**
     * After a request failed: if the SSH session dropped, opens a new one to the same remote
     * kernel. Returns the new client, or null when the kernel can't be reached any more.
     */
    @Nullable
    private synchronized KernelClient recoverSsh(KernelClient failed) {
        SshConnection conn = ssh;
        if (conn == null || client != failed) return null;
        if (conn.isConnected()) return null; // tunnel is fine; the kernel itself failed
        conn.close();
        try {
            SshConnection fresh = SshConnection.open(conn.profile(), sshProfiles, knownHosts);
            KernelClient c = new KernelClient(fresh.forward(sshRemotePort), failed.getToken());
            c.info();
            ssh = fresh;
            client = c;
            return c;
        } catch (Exception e) {
            Log.w(TAG, "SSH reconnect failed", e);
            return null;
        }
    }

    private PendingIntent resultIntent(String sessionId) {
        Intent result = new Intent(appContext, TermuxResultReceiver.class);
        result.putExtra(TermuxResultReceiver.EXTRA_SESSION, sessionId);
        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE; // Termux adds the result bundle
        }
        return PendingIntent.getBroadcast(appContext, sessionId.hashCode(), result, flags);
    }

    /** Called by {@link TermuxResultReceiver} when the Termux process for a session ends. */
    void onProcessFinished(String sessionId, int exitCode, int err, String errmsg, String stdout, String stderr) {
        if (sessionId == null || !sessionId.equals(session)) {
            return;
        }
        StringBuilder msg = new StringBuilder();
        if (errmsg != null && !errmsg.trim().isEmpty()) {
            msg.append(errmsg.trim());
        } else {
            msg.append("The kernel exited (code ").append(exitCode).append(").");
        }
        String details = (stderr != null && !stderr.trim().isEmpty()) ? stderr.trim() : (stdout != null ? stdout.trim() : "");
        if (!details.isEmpty()) {
            msg.append("\n\n").append(details.length() > 1500 ? details.substring(details.length() - 1500) : details);
        }
        processFailure = msg.toString();

        if (client != null) {
            // A running kernel died (crash, killed from Termux, idle timeout...)
            client = null;
            prefs.edit().remove(PREF_PORT).remove(PREF_TOKEN).apply();
            state.postValue(new KernelState(KernelState.Status.ERROR, processFailure, null, getSavedVenvPath()));
        }
    }

    // ------------------------------------------------------------------ execution

    /** Runs code on the kernel; blocks. Must be called off the main thread. */
    public JSONObject execute(String code) throws Exception {
        KernelClient c = client;
        if (c == null) {
            throw new IllegalStateException("Not connected to a Python kernel");
        }
        KernelState before = currentState();
        state.postValue(before.with(KernelState.Status.BUSY));
        try {
            return c.execute(code);
        } catch (IOException e) {
            if (ssh != null) {
                KernelClient recovered = recoverSsh(c);
                if (recovered != null) {
                    state.postValue(before.with(KernelState.Status.IDLE));
                    throw new IOException("The connection to " + before.target + " dropped while the cell was "
                            + "running. pynb reconnected; the cell may still be running on the server, but its "
                            + "output is lost. Run it again if needed.", e);
                }
            }
            // the kernel is gone (killed, phone slept the process away...)
            if (!isAlive(c)) {
                client = null;
                closeSsh(false);
                state.postValue(new KernelState(KernelState.Status.ERROR,
                        "Lost connection to the kernel: " + e.getMessage(), null, before.venvPath,
                        before.target, null));
            }
            throw e;
        } finally {
            if (client == c) {
                state.postValue(before.with(KernelState.Status.IDLE));
            }
        }
    }

    /**
     * Asks the connected kernel for completions; blocks, call off the main thread.
     *
     * @param cursor offset into {@code code} in code points (Python string indexing)
     * @return the kernel's reply ({@code matches}, {@code prefix_length}), or null when not
     *         connected or the kernel can't answer. Never changes the connection state.
     */
    @Nullable
    public JSONObject complete(String code, int cursor) {
        KernelClient c = client;
        if (c == null) return null;
        try {
            return c.complete(code, cursor);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAlive(KernelClient c) {
        try {
            c.info();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void interrupt() {
        KernelClient c = client;
        if (c == null) return;
        controlExecutor.execute(() -> {
            try {
                c.interrupt();
            } catch (Exception e) {
                Log.w(TAG, "interrupt failed", e);
            }
        });
    }

    public void restart(@Nullable Runnable onDone) {
        KernelClient c = client;
        if (c == null) return;
        controlExecutor.execute(() -> {
            try {
                c.restart();
            } catch (Exception e) {
                Log.w(TAG, "restart failed", e);
            }
            if (onDone != null) onDone.run();
        });
    }

    public void disconnect() {
        controlExecutor.execute(() -> {
            shutdownQuietly();
            state.postValue(KernelState.disconnected());
        });
    }

    private void shutdownQuietly() {
        KernelClient c = client;
        client = null;
        session = null;
        clearSession();
        if (c != null) {
            try {
                c.shutdown();
            } catch (Exception ignored) {
            }
        }
        closeSsh(true);
    }

    /** Closes the SSH session, optionally deleting the session's files on the server first. */
    private void closeSsh(boolean cleanup) {
        SshConnection conn = ssh;
        String sid = sshSession;
        ssh = null;
        sshSession = null;
        if (conn == null) return;
        if (cleanup && sid != null && conn.isConnected()) {
            try {
                SystemClock.sleep(300); // let the kernel exit and remove its ready file
                conn.exec(RemoteScript.cleanupCommand(sid), null);
            } catch (Exception ignored) {
            }
        }
        conn.close();
    }

    private void clearSession() {
        prefs.edit().remove(PREF_PORT).remove(PREF_TOKEN).remove(PREF_SSH_REMOTE_PORT)
                .remove(PREF_SSH_SESSION).apply();
    }

    // ------------------------------------------------------------------ helpers

    private String readKernelScript() throws IOException {
        try (InputStream is = appContext.getAssets().open("kernel/pynb_kernel.py")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : TERMUX_HOME;
    }
}
