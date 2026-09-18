# Security Policy

## Reporting a Vulnerability

If you believe you have found a security vulnerability in this project, **please do not open a public issue**.

Instead, report it responsibly by emailing us at: [rootminusone8004@gmail.com](mailto:rootminusone8004@gmail.com)

We aim to respond to security reports within **7 business days** and will work with you to assess the issue and release a fix as soon as possible.

## Disclosure Policy

We follow a **coordinated disclosure** process. Once a vulnerability is confirmed, we will:

1. Acknowledge receipt of the report.
2. Investigate and confirm the issue.
3. Prepare a fix and plan a release.
4. Credit the reporter (if they wish).
5. Publicly disclose the vulnerability once a patch is available.

## Supported Versions

We currently provide security updates for the following versions:

| Version | Supported |
|---------|-----------|
| 1.0.x   | ✅        |
| < 1.0   | ❌        |

Older versions are no longer supported. Please upgrade to the **latest** release.

## Privacy & Security Best Practices

pynb is engineered with strict privacy and security standards:

- **100% Offline & Local**: No internet access permission (`android.permission.INTERNET`) is requested in `AndroidManifest.xml`.
- **Zero Telemetry / Analytics**: No user tracking, ads, background telemetry, or cloud data syncing.
- **Scoped Storage & SAF**: Files are read strictly via Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT` and `ACTION_OPEN_DOCUMENT_TREE`) without requesting broad legacy storage permissions.
- **Safe Rendering**: Code blocks are strictly parsed as text and rendered into native views without execution.
