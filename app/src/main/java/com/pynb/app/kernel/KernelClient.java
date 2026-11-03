package com.pynb.app.kernel;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client for the pynb kernel bridge (assets/kernel/pynb_kernel.py) running in Termux.
 * The bridge only listens on 127.0.0.1 and requires the per-session token.
 */
public class KernelClient {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int SHORT_READ_TIMEOUT_MS = 10000;
    private static final int COMPLETE_READ_TIMEOUT_MS = 4000;

    private final int port;
    private final String token;

    public KernelClient(int port, String token) {
        this.port = port;
        this.token = token;
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }

    public JSONObject info() throws IOException, JSONException {
        return request("GET", "/info", null, SHORT_READ_TIMEOUT_MS);
    }

    /** Blocks until the cell finishes (or is interrupted). */
    public JSONObject execute(String code) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("code", code);
        return request("POST", "/execute", body, 0);
    }

    /**
     * Completions at {@code cursor} (a code point offset into {@code code}). Answers while a cell
     * is running. Kernels older than version 2 have no /complete and answer 404 (IOException).
     */
    public JSONObject complete(String code, int cursor) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("code", code);
        body.put("cursor", cursor);
        return request("POST", "/complete", body, COMPLETE_READ_TIMEOUT_MS);
    }

    public void interrupt() throws IOException, JSONException {
        request("POST", "/interrupt", new JSONObject(), SHORT_READ_TIMEOUT_MS);
    }

    public void restart() throws IOException, JSONException {
        request("POST", "/restart", new JSONObject(), SHORT_READ_TIMEOUT_MS);
    }

    public void shutdown() throws IOException, JSONException {
        request("POST", "/shutdown", new JSONObject(), SHORT_READ_TIMEOUT_MS);
    }

    private JSONObject request(String method, String path, JSONObject body, int readTimeoutMs)
            throws IOException, JSONException {
        URL url = new URL("http://127.0.0.1:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(readTimeoutMs);
            conn.setUseCaches(false);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int status = conn.getResponseCode();
            InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = is != null ? readAll(is) : "";
            if (status == 403) {
                throw new IOException("Kernel rejected the session token");
            }
            if (status >= 400) {
                throw new IOException("Kernel error " + status + ": " + text);
            }
            return new JSONObject(text);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        try (InputStream in = is) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
