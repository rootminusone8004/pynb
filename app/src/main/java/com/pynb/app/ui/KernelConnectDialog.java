package com.pynb.app.ui;

import android.net.Uri;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pynb.app.R;
import com.pynb.app.databinding.DialogKernelConnectBinding;
import com.pynb.app.kernel.KernelManager;
import com.pynb.app.kernel.SshProfile;
import com.pynb.app.kernel.SshProfileStore;
import com.pynb.app.util.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The "Connect to Python" dialog: a Termux .venv on this phone, or a .venv on an SSH server. */
final class KernelConnectDialog {

    interface Host {
        /** Opens the system file picker; the result comes back through {@link #onKeyFilePicked}. */
        void requestKeyFile();

        void connectTermux(@NonNull String venv, @NonNull String workDir);

        void onConnectCancelled();
    }

    private static final int MAX_KEY_BYTES = 64 * 1024;

    private final AppCompatActivity activity;
    private final KernelManager kernel;
    private final SshProfileStore store;
    private final Host host;
    private final DialogKernelConnectBinding b;

    private List<SshProfile> profiles = new ArrayList<>();
    /** Saved server the form was filled from, or null for a new one. */
    @Nullable private SshProfile selected;
    /** Key file chosen in this dialog (not yet saved). */
    @Nullable private byte[] pickedKey;
    private boolean connecting;

    KernelConnectDialog(AppCompatActivity activity, KernelManager kernel, Host host) {
        this.activity = activity;
        this.kernel = kernel;
        this.store = kernel.getSshProfiles();
        this.host = host;
        this.b = DialogKernelConnectBinding.inflate(activity.getLayoutInflater());
    }

    void show() {
        boolean termuxInstalled = KernelManager.isTermuxInstalled(activity);
        b.tvTermuxMissing.setVisibility(termuxInstalled ? View.GONE : View.VISIBLE);
        b.tilVenv.setEnabled(termuxInstalled);
        b.tilWorkDir.setEnabled(termuxInstalled);
        b.etVenv.setText(kernel.getSavedVenvPath());
        b.etWorkDir.setText(kernel.getSavedWorkDir());

        b.toggleBackend.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) showBackend(id == R.id.btnBackendSsh);
        });
        b.toggleBackend.check(kernel.lastUsedSsh() || !termuxInstalled ? R.id.btnBackendSsh : R.id.btnBackendTermux);
        showBackend(b.toggleBackend.getCheckedButtonId() == R.id.btnBackendSsh);

        b.toggleSshAuth.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) showAuth(id == R.id.btnAuthPassword ? SshProfile.Auth.PASSWORD : SshProfile.Auth.KEY);
        });
        b.btnChooseKey.setOnClickListener(v -> host.requestKeyFile());
        b.actSshSaved.setOnItemClickListener((parent, view, position, id) ->
                fill(position == 0 ? null : profiles.get(position - 1)));
        b.btnForgetServer.setOnClickListener(v -> forgetSelected());

        reloadProfiles(store.last());

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.kernel_connect_title)
                .setView(b.getRoot())
                .setPositiveButton(R.string.kernel_connect, null)
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(d -> {
                    if (!connecting) host.onConnectCancelled();
                })
                .create();
        // Set the click listener after show() so invalid input doesn't close the dialog.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (b.toggleBackend.getCheckedButtonId() == R.id.btnBackendSsh ? connectSsh() : connectTermux()) {
                connecting = true;
                dialog.dismiss();
            }
        }));
        dialog.show();
    }

    void onKeyFilePicked(@Nullable Uri uri) {
        if (uri == null) return;
        byte[] bytes;
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("cannot open");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_KEY_BYTES) throw new IllegalStateException("too large");
            }
            bytes = out.toByteArray();
        } catch (Exception e) {
            Toast.makeText(activity, R.string.ssh_key_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        String kind = keyKind(new String(bytes, StandardCharsets.UTF_8));
        if (kind == null) {
            Toast.makeText(activity, R.string.ssh_key_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        pickedKey = bytes;
        String name = FileUtils.getFileName(activity, uri);
        b.tvKeyStatus.setText(activity.getString(R.string.ssh_key_loaded,
                (name != null && !name.isEmpty() ? name + " · " : "") + kind));
    }

    // ------------------------------------------------------------------ form

    private void showBackend(boolean ssh) {
        b.groupSsh.setVisibility(ssh ? View.VISIBLE : View.GONE);
        b.groupTermux.setVisibility(ssh ? View.GONE : View.VISIBLE);
    }

    private void showAuth(SshProfile.Auth auth) {
        boolean key = auth == SshProfile.Auth.KEY;
        b.groupSshKey.setVisibility(key ? View.VISIBLE : View.GONE);
        b.tilSshPassword.setVisibility(key ? View.GONE : View.VISIBLE);
    }

    private void reloadProfiles(@Nullable SshProfile select) {
        profiles = store.list();
        List<String> labels = new ArrayList<>();
        labels.add(activity.getString(R.string.ssh_new_server));
        for (SshProfile p : profiles) labels.add(p.id());
        b.actSshSaved.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, labels));
        b.tilSshSaved.setVisibility(profiles.isEmpty() ? View.GONE : View.VISIBLE);
        fill(select);
    }

    private void fill(@Nullable SshProfile p) {
        selected = p;
        pickedKey = null;
        b.actSshSaved.setText(p == null ? activity.getString(R.string.ssh_new_server) : p.id(), false);
        b.etSshHost.setText(p == null ? "" : p.host);
        b.etSshPort.setText(String.valueOf(p == null ? 22 : p.port));
        b.etSshUser.setText(p == null ? "" : p.user);
        b.etSshVenv.setText(p == null ? "~/.venv" : p.venv);
        b.etSshWorkDir.setText(p == null ? "" : p.workDir);
        b.etSshPassphrase.setText("");
        b.etSshPassword.setText("");

        SshProfile.Auth auth = p == null ? SshProfile.Auth.KEY : p.auth;
        b.toggleSshAuth.check(auth == SshProfile.Auth.KEY ? R.id.btnAuthKey : R.id.btnAuthPassword);
        showAuth(auth);

        boolean savedKey = p != null && store.hasPrivateKey(p);
        b.tvKeyStatus.setText(savedKey ? R.string.ssh_key_saved : R.string.ssh_key_none);
        b.etSshPassphrase.setHint(savedKey ? activity.getString(R.string.ssh_saved_secret_hint) : null);
        boolean savedPassword = p != null && store.hasPassword(p);
        b.etSshPassword.setHint(savedPassword ? activity.getString(R.string.ssh_saved_secret_hint) : null);
        b.btnForgetServer.setVisibility(p == null ? View.GONE : View.VISIBLE);
    }

    private void forgetSelected() {
        SshProfile p = selected;
        if (p == null) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ssh_forget)
                .setMessage(p.id())
                .setPositiveButton(R.string.ssh_forget, (d, w) -> {
                    store.delete(p.id());
                    Toast.makeText(activity, R.string.ssh_forgotten, Toast.LENGTH_SHORT).show();
                    reloadProfiles(null);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ connect

    private boolean connectTermux() {
        String venv = text(b.etVenv);
        if (venv.isEmpty() || !KernelManager.isTermuxInstalled(activity)) return false;
        host.connectTermux(venv, text(b.etWorkDir));
        return true;
    }

    private boolean connectSsh() {
        String hostName = text(b.etSshHost);
        String user = text(b.etSshUser);
        String venv = text(b.etSshVenv);
        int port;
        try {
            port = Integer.parseInt(text(b.etSshPort));
        } catch (NumberFormatException e) {
            port = -1;
        }
        // Accept "user@host" pasted into the host field.
        int at = hostName.indexOf('@');
        if (at > 0 && user.isEmpty()) {
            user = hostName.substring(0, at);
            hostName = hostName.substring(at + 1);
        }
        if (hostName.isEmpty() || user.isEmpty() || venv.isEmpty() || port < 1 || port > 65535) {
            Toast.makeText(activity, R.string.ssh_missing_fields, Toast.LENGTH_SHORT).show();
            return false;
        }
        SshProfile.Auth auth = b.toggleSshAuth.getCheckedButtonId() == R.id.btnAuthPassword
                ? SshProfile.Auth.PASSWORD : SshProfile.Auth.KEY;
        SshProfile profile = new SshProfile(hostName, port, user, auth, venv, text(b.etSshWorkDir));

        String passphrase = text(b.etSshPassphrase);
        String password = b.etSshPassword.getText() == null ? "" : b.etSshPassword.getText().toString();
        boolean savedKey = selected != null && store.hasPrivateKey(selected);
        boolean savedPassword = selected != null && store.hasPassword(selected);
        if (auth == SshProfile.Auth.KEY && pickedKey == null && !savedKey) {
            Toast.makeText(activity, R.string.ssh_missing_key, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (auth == SshProfile.Auth.PASSWORD && password.isEmpty() && !savedPassword) {
            Toast.makeText(activity, R.string.ssh_missing_password, Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            store.save(profile, selected != null ? selected.id() : null, pickedKey,
                    passphrase.isEmpty() && pickedKey == null ? null : passphrase,
                    password.isEmpty() ? null : password);
        } catch (IllegalStateException e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
        kernel.connectSsh(profile);
        return true;
    }

    private static String text(android.widget.TextView v) {
        return v.getText() == null ? "" : v.getText().toString().trim();
    }

    /** A short description of a private key file, or null if it is not one JSch can read. */
    @Nullable
    static String keyKind(String text) {
        if (text.contains("PuTTY-User-Key-File")) return null; // .ppk: needs converting
        if (text.contains("BEGIN OPENSSH PRIVATE KEY")) return "OpenSSH key";
        if (text.contains("BEGIN RSA PRIVATE KEY")) return "RSA key";
        if (text.contains("BEGIN EC PRIVATE KEY")) return "ECDSA key";
        if (text.contains("BEGIN DSA PRIVATE KEY")) return "DSA key";
        if (text.contains("BEGIN ENCRYPTED PRIVATE KEY") || text.contains("BEGIN PRIVATE KEY")) return "PKCS#8 key";
        return null;
    }
}
