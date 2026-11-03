package com.pynb.app.kernel;

import java.security.SecureRandom;

/**
 * Builds the POSIX {@code sh} script that starts the kernel bridge on an SSH server.
 *
 * <p>The script is sent on stdin of {@code sh -s}, so neither the kernel source nor the
 * session token ever appears on a command line (visible to other users through {@code ps}).
 * It prints exactly one result line: {@code PYNB_PORT <port>} on success, or
 * {@code PYNB_ERR <message>} followed by details.</p>
 */
public final class RemoteScript {

    public static final String OK_PREFIX = "PYNB_PORT ";
    public static final String ERR_PREFIX = "PYNB_ERR ";

    private RemoteScript() {
    }

    /** Single-quotes {@code s} for sh. */
    public static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** Trims and drops trailing slashes (keeps a lone "/"). */
    public static String normalize(String path) {
        String p = path == null ? "" : path.trim();
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    /**
     * A shell word for a remote path: {@code ~} and {@code ~/...} expand to the user's home,
     * everything else is quoted literally. Relative paths are relative to the home directory,
     * where SSH sessions start.
     */
    public static String remotePath(String path) {
        String p = normalize(path);
        if (p.isEmpty() || p.equals("~")) return "\"$HOME\"";
        if (p.startsWith("~/")) return "\"$HOME\"/" + shellQuote(p.substring(2));
        return shellQuote(p);
    }

    /** The folder that contains {@code path} ({@code ~} when there is none). */
    public static String parentOf(String path) {
        String p = normalize(path);
        int slash = p.lastIndexOf('/');
        if (slash > 0) return p.substring(0, slash);
        if (slash == 0) return "/";
        return "~";
    }

    /**
     * @param venv       the remote .venv directory as typed by the user
     * @param workDir    working directory, or empty for the folder containing the venv
     * @param kernelSrc  contents of {@code pynb_kernel.py}
     * @param token      session token (hex)
     * @param sessionId  short id used to name this session's files in {@code ~/.pynb}
     */
    public static String startScript(String venv, String workDir, String kernelSrc, String token, String sessionId) {
        if (!token.matches("[0-9a-f]+") || !sessionId.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException("token and session id must be hex");
        }
        String venvNorm = normalize(venv);
        String work = workDir == null || workDir.trim().isEmpty() ? parentOf(venvNorm) : normalize(workDir);
        String delimiter = uniqueDelimiter(kernelSrc);
        String base = "\"$d\"/" + sessionId;

        StringBuilder sb = new StringBuilder();
        sb.append("umask 077\n");
        sb.append("d=\"$HOME/.pynb\"\n");
        sb.append("mkdir -p \"$d\" || { echo ").append(shellQuote(ERR_PREFIX + "Could not create ~/.pynb on the server.")).append("; exit 0; }\n");
        sb.append("py=").append(remotePath(venvNorm)).append("/bin/python\n");
        sb.append("if [ ! -x \"$py\" ]; then echo ")
                .append(shellQuote(ERR_PREFIX + "No Python at " + venvNorm + "/bin/python on the server. "
                        + "Create the environment with: python3 -m venv " + venvNorm))
                .append("; exit 0; fi\n");
        sb.append("cd ").append(remotePath(work)).append(" 2>/dev/null || { echo ")
                .append(shellQuote(ERR_PREFIX + "Working directory not found on the server: " + work))
                .append("; exit 0; }\n");
        // Write the kernel to a temp file then rename, so concurrent sessions never read half a file.
        sb.append("cat > \"$d/pynb_kernel.py.$$\" <<'").append(delimiter).append("'\n");
        sb.append(kernelSrc);
        if (!kernelSrc.endsWith("\n")) sb.append('\n');
        sb.append(delimiter).append('\n');
        sb.append("mv -f \"$d/pynb_kernel.py.$$\" \"$d/pynb_kernel.py\"\n");
        sb.append("printf '%s' '").append(token).append("' > ").append(base).append(".token\n");
        sb.append("rm -f ").append(base).append(".port\n");
        sb.append("if command -v setsid >/dev/null 2>&1; then set -- setsid; else set --; fi\n");
        sb.append("nohup \"$@\" \"$py\" -u \"$d/pynb_kernel.py\" --port 0 --token-file ").append(base)
                .append(".token --ready-file ").append(base).append(".port </dev/null >").append(base)
                .append(".log 2>&1 &\n");
        sb.append("pid=$!\n");
        sb.append("i=0\n");
        sb.append("while [ $i -lt 150 ]; do\n");
        sb.append("  if [ -s ").append(base).append(".port ]; then echo \"").append(OK_PREFIX)
                .append("$(cat ").append(base).append(".port)\"; exit 0; fi\n");
        sb.append("  if ! kill -0 $pid 2>/dev/null; then echo ")
                .append(shellQuote(ERR_PREFIX + "The kernel exited while starting:"))
                .append("; tail -c 1500 ").append(base).append(".log; rm -f ").append(base).append(".token; exit 0; fi\n");
        sb.append("  sleep 0.2 2>/dev/null || sleep 1\n");
        sb.append("  i=$((i+1))\n");
        sb.append("done\n");
        sb.append("echo ").append(shellQuote(ERR_PREFIX + "The kernel did not start within 30 s:"))
                .append("; tail -c 1500 ").append(base).append(".log\n");
        return sb.toString();
    }

    /** Removes a session's leftover files after the kernel was shut down. */
    public static String cleanupCommand(String sessionId) {
        if (!sessionId.matches("[0-9a-f]+")) throw new IllegalArgumentException("session id must be hex");
        return "rm -f \"$HOME/.pynb/" + sessionId + ".log\" \"$HOME/.pynb/" + sessionId + ".port\" \"$HOME/.pynb/"
                + sessionId + ".token\"";
    }

    /** Parses the script's output: the remote port, or throws with the script's error text. */
    public static int parsePort(String output) {
        String out = output == null ? "" : output;
        int at = out.indexOf(OK_PREFIX);
        if (at >= 0) {
            int end = out.indexOf('\n', at);
            String num = out.substring(at + OK_PREFIX.length(), end < 0 ? out.length() : end).trim();
            try {
                int port = Integer.parseInt(num);
                if (port > 0 && port < 65536) return port;
            } catch (NumberFormatException ignored) {
            }
        }
        int err = out.indexOf(ERR_PREFIX);
        String msg = err >= 0 ? out.substring(err + ERR_PREFIX.length()).trim()
                : "Unexpected reply from the server:\n" + out.trim();
        throw new IllegalStateException(msg.isEmpty() ? "The kernel could not be started on the server." : msg);
    }

    private static String uniqueDelimiter(String body) {
        SecureRandom random = new SecureRandom();
        while (true) {
            String d = "PYNB_EOF_" + Long.toHexString(random.nextLong() & Long.MAX_VALUE);
            if (!body.contains(d)) return d;
        }
    }
}
