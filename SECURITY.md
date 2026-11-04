# 🔒 Security Policy

## Reporting a Vulnerability

If you believe you have discovered a security vulnerability in this project, **please do not open a public issue**.

Instead, report it responsibly by emailing us at: [rootminusone8004@gmail.com](mailto:rootminusone8004@gmail.com)

Please provide a detailed description of the issue, steps to reproduce, and any relevant proof-of-concept material. We aim to acknowledge receipt within **7 business days** and will work with you to assess the issue and release a patch as promptly as possible.

---

## Disclosure Policy

We follow a **coordinated disclosure** process. Once a reported vulnerability is confirmed, we will:

1. Acknowledge receipt of the report.
2. Investigate and verify the issue.
3. Prepare a fix and plan a release.
4. Credit the reporter (if desired).
5. Publicly disclose the vulnerability once a patched release is published.

---

## Supported Versions

We currently provide security updates for the following versions:

| Version | Supported |
|---------|-----------|
| 1.3.x   | ✅ Yes     |
| < 1.3   | ❌ No      |

Older versions are not maintained. Please keep your installation updated to the **latest stable release**.

---

## How pynb Protects You

- **No tracking**: no analytics, telemetry, ads, accounts or cloud sync.
- **Network only when you ask for it**: the `INTERNET` permission is used solely for SSH connections to servers you add. Opening, reading and editing notebooks never touches the network.
- **Scoped storage**: notebooks and folders are opened through Android's Storage Access Framework; pynb requests no broad storage permission.
- **Reading is safe**: opening a notebook never runs its code. Code runs only when you press ▶ on a connected kernel, in the Python environment you chose.
- **Kernel access control**: the Python kernel listens only on `127.0.0.1` (on the phone or on the server) and rejects every request without the random per-session token. On a server it is reached only through the SSH tunnel.
- **SSH credentials**: private keys, passphrases and passwords are encrypted with AES-256-GCM using a key held in the **Android Keystore**; they are never stored or sent in plain text.
- **Host key verification**: the first connection shows the server's SHA-256 fingerprint for you to confirm, and pynb warns you and refuses to connect silently if the key changes later.
- **Sandboxed diagrams**: Mermaid diagrams render offline in a WebView with network access, file access and navigation disabled.

---

## Security Best Practices

To help ensure the security and privacy of your notebooks and servers:
- Always download and install pynb from **official releases** or trusted repositories such as F-Droid.
- Keep your Android operating system and security patches up to date.
- Verify APK signatures when sideloading.
- Compare a server's host key fingerprint with the one shown by `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` (or your cloud console) before trusting it.
- Prefer key-based SSH logins, protect keys with a passphrase, and use **Forget this server** to remove credentials you no longer need.

---

## Thank You

We appreciate your assistance in keeping pynb secure, private, and reliable!
