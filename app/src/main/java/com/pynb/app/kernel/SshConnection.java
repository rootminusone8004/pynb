package com.pynb.app.kernel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/** One authenticated SSH session to a server: runs commands and forwards a local port to the kernel. */
final class SshConnection {

    private static final int CONNECT_TIMEOUT_MS = 20000;

    static {
        JSch.setConfig("StrictHostKeyChecking", "yes"); // KnownHosts decides; unknown keys go to the user
    }

    private final Session session;
    private final SshProfile profile;

    private SshConnection(Session session, SshProfile profile) {
        this.session = session;
        this.profile = profile;
    }

    static SshConnection open(@NonNull SshProfile p, @NonNull SshProfileStore store, @NonNull KnownHosts known)
            throws Exception {
        JSch jsch = new JSch();
        jsch.setHostKeyRepository(known);
        Session session;
        try {
            if (p.auth == SshProfile.Auth.KEY) {
                byte[] key = store.privateKey(p);
                if (key == null) {
                    throw new IllegalStateException("No private key is saved for " + p.id() + ". Choose the key file again.");
                }
                String pass = store.passphrase(p);
                try {
                    jsch.addIdentity(p.id(), key, null, pass == null ? null : pass.getBytes(StandardCharsets.UTF_8));
                } catch (JSchException e) {
                    throw new IllegalStateException("Could not read the private key"
                            + (pass == null ? " (if it is encrypted, enter its passphrase)" : " (wrong passphrase?)")
                            + ": " + e.getMessage(), e);
                }
            }
            session = jsch.getSession(p.user, p.host, p.port);
            if (p.auth == SshProfile.Auth.PASSWORD) {
                String password = store.password(p);
                if (password == null) {
                    throw new IllegalStateException("No password is saved for " + p.id() + ". Enter it again.");
                }
                session.setPassword(password);
                session.setUserInfo(new PasswordInfo(password));
                session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
            } else {
                session.setConfig("PreferredAuthentications", "publickey");
            }
            session.setServerAliveInterval(15000);
            session.setServerAliveCountMax(3);
            session.connect(CONNECT_TIMEOUT_MS);
        } catch (JSchException e) {
            KnownHosts.Prompt prompt = known.takePending();
            if (prompt != null) throw new KnownHosts.UnverifiedHostException(prompt);
            throw new IOException(friendlyMessage(p, e), e);
        }
        known.takePending();
        return new SshConnection(session, p);
    }

    boolean isConnected() {
        return session.isConnected();
    }

    @NonNull
    SshProfile profile() {
        return profile;
    }

    /**
     * Runs {@code command}, feeding {@code stdin}, and returns stdout (stderr appended when the
     * command wrote nothing to stdout). Blocks until the command exits.
     */
    String exec(@NonNull String command, @Nullable byte[] stdin) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        try {
            channel.setCommand(command);
            channel.setInputStream(stdin == null ? null : new ByteArrayInputStream(stdin));
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setErrStream(err);
            InputStream in = channel.getInputStream();
            channel.connect(CONNECT_TIMEOUT_MS);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            String text = out.toString(StandardCharsets.UTF_8.name());
            if (text.trim().isEmpty()) text = err.toString(StandardCharsets.UTF_8.name());
            return text;
        } finally {
            channel.disconnect();
        }
    }

    /** Forwards a free local port on 127.0.0.1 to {@code 127.0.0.1:remotePort} on the server. */
    int forward(int remotePort) throws JSchException {
        return session.setPortForwardingL("127.0.0.1", 0, "127.0.0.1", remotePort);
    }

    void close() {
        session.disconnect();
    }

    private static String friendlyMessage(SshProfile p, JSchException e) {
        Throwable cause = e.getCause();
        String where = p.host + ":" + p.port;
        if (cause instanceof UnknownHostException) {
            return "Unknown host \"" + p.host + "\". Check the address.";
        }
        if (cause instanceof ConnectException) {
            return "Connection to " + where + " was refused. Check the port and that the SSH server is running.";
        }
        if (cause instanceof SocketTimeoutException || cause instanceof NoRouteToHostException
                || String.valueOf(e.getMessage()).contains("timeout")) {
            return "Could not reach " + where + ". Check the address, your network, and that the firewall "
                    + "(for AWS: the security group) allows inbound SSH on port " + p.port + ".";
        }
        String msg = String.valueOf(e.getMessage());
        if (msg.contains("Auth fail") || msg.contains("Auth cancel")) {
            return "Authentication failed for " + p.id() + ". Check the user name (EC2: ec2-user, ubuntu, admin…) and "
                    + (p.auth == SshProfile.Auth.KEY ? "that the key is authorized on the server." : "the password.");
        }
        return "SSH connection to " + where + " failed: " + msg;
    }

    /** Answers password and keyboard-interactive prompts with the saved password. */
    private static final class PasswordInfo implements UserInfo, UIKeyboardInteractive {
        private final String password;

        PasswordInfo(String password) {
            this.password = password;
        }

        @Override public String getPassphrase() { return null; }
        @Override public String getPassword() { return password; }
        @Override public boolean promptPassword(String message) { return true; }
        @Override public boolean promptPassphrase(String message) { return false; }
        @Override public boolean promptYesNo(String message) { return false; }
        @Override public void showMessage(String message) { }

        @Override
        public String[] promptKeyboardInteractive(String destination, String name, String instruction,
                                                  String[] prompt, boolean[] echo) {
            // Only a single (password) prompt can be answered; 2FA codes etc. are not supported.
            if (prompt == null || prompt.length != 1 || echo[0]) return null;
            return new String[]{password};
        }
    }
}
