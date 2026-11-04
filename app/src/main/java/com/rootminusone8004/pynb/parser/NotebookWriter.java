package com.rootminusone8004.pynb.parser;

import com.rootminusone8004.pynb.model.CellOutput;
import com.rootminusone8004.pynb.model.Notebook;
import com.rootminusone8004.pynb.model.NotebookCell;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Writes a notebook back to .ipynb (nbformat 4).
 *
 * <p>Only cells changed in the app are rewritten: edited cells
 * ({@link NotebookCell#isSourceModified()}) get their new {@code source}, executed cells
 * ({@link NotebookCell#isModified()}) get new {@code execution_count} / {@code outputs}.
 * Everything else is written from the original JSON, so metadata, attachments and fields this
 * app does not model are preserved.</p>
 *
 * <p>The text layout matches Jupyter's own writer ({@code json.dumps(nb, indent=1,
 * sort_keys=True, ensure_ascii=False)} with multi-line strings split into lists), so saving a
 * notebook here produces the same minimal diffs as saving it in Jupyter.</p>
 */
public final class NotebookWriter {

    private NotebookWriter() {
    }

    public static String write(Notebook notebook) throws JSONException {
        JSONObject root = notebook.getRawJson();
        if (root == null) {
            throw new JSONException("Notebook has no source JSON");
        }
        if (!root.has("cells")) {
            throw new JSONException("Only nbformat 4 notebooks can be saved (this file uses nbformat "
                    + root.optInt("nbformat", 3) + ")");
        }

        JSONObject out = shallowCopy(root);
        JSONArray cells = new JSONArray();
        for (NotebookCell cell : notebook.getCells()) {
            cells.put(cellJson(cell));
        }
        out.put("cells", cells);

        StringBuilder sb = new StringBuilder();
        writeValue(sb, out, 0);
        sb.append('\n');
        return sb.toString();
    }

    /** The JSON a cell is saved as (used to refresh its source JSON after saving). */
    public static JSONObject cellJson(NotebookCell cell) throws JSONException {
        boolean changed = cell.isModified() || cell.isSourceModified() || cell.getRawJson() == null;
        return changed ? updatedCell(cell) : cell.getRawJson();
    }

    private static JSONObject updatedCell(NotebookCell cell) throws JSONException {
        boolean isNew = cell.getRawJson() == null;
        JSONObject json = isNew ? new JSONObject() : shallowCopy(cell.getRawJson());
        if (isNew) {
            json.put("cell_type", cell.getCellType());
            json.put("metadata", new JSONObject());
        }
        if (isNew || cell.isSourceModified()) {
            json.put("source", splitLines(cell.getSource()));
        }
        if (cell.isCode() && (isNew || cell.isModified())) {
            json.put("execution_count", cell.getExecutionCount() != null ? cell.getExecutionCount() : JSONObject.NULL);
            JSONArray outputs = new JSONArray();
            for (CellOutput output : cell.getOutputs()) {
                if (output.getRawJson() != null) {
                    outputs.put(normalizeOutput(output.getRawJson()));
                }
            }
            json.put("outputs", outputs);
        }
        return json;
    }

    /** Applies nbformat's multi-line string convention to an output produced by the kernel. */
    static JSONObject normalizeOutput(JSONObject raw) throws JSONException {
        JSONObject out = shallowCopy(raw);
        Object text = out.opt("text");
        if (text instanceof String) {
            out.put("text", splitLines((String) text));
        }
        JSONObject data = out.optJSONObject("data");
        if (data != null) {
            JSONObject newData = shallowCopy(data);
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String mime = keys.next();
                Object value = data.opt(mime);
                if (value instanceof String && isSplitMime(mime)) {
                    newData.put(mime, splitLines((String) value));
                }
            }
            out.put("data", newData);
        }
        return out;
    }

    private static boolean isSplitMime(String mime) {
        return mime.startsWith("text/") || mime.equals("application/javascript") || mime.equals("image/svg+xml");
    }

    /** Equivalent of Python's {@code str.splitlines(keepends=True)} for '\n' line endings. */
    static JSONArray splitLines(String text) {
        JSONArray lines = new JSONArray();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        int start = 0;
        int nl;
        while ((nl = text.indexOf('\n', start)) >= 0) {
            lines.put(text.substring(start, nl + 1));
            start = nl + 1;
        }
        if (start < text.length()) {
            lines.put(text.substring(start));
        }
        return lines;
    }

    private static JSONObject shallowCopy(JSONObject source) throws JSONException {
        JSONObject copy = new JSONObject();
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            copy.put(key, source.opt(key));
        }
        return copy;
    }

    // ------------------------------------------------------------------ Python-style JSON

    private static void writeValue(StringBuilder sb, Object value, int indent) throws JSONException {
        if (value == null || value == JSONObject.NULL) {
            sb.append("null");
        } else if (value instanceof JSONObject) {
            writeObject(sb, (JSONObject) value, indent);
        } else if (value instanceof JSONArray) {
            writeArray(sb, (JSONArray) value, indent);
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean) {
            sb.append(((Boolean) value) ? "true" : "false");
        } else if (value instanceof Number) {
            sb.append(formatNumber((Number) value));
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeObject(StringBuilder sb, JSONObject obj, int indent) throws JSONException {
        List<String> keys = new ArrayList<>();
        Iterator<String> it = obj.keys();
        while (it.hasNext()) keys.add(it.next());
        if (keys.isEmpty()) {
            sb.append("{}");
            return;
        }
        Collections.sort(keys);
        sb.append("{\n");
        for (int i = 0; i < keys.size(); i++) {
            pad(sb, indent + 1);
            writeString(sb, keys.get(i));
            sb.append(": ");
            writeValue(sb, obj.opt(keys.get(i)), indent + 1);
            if (i < keys.size() - 1) sb.append(',');
            sb.append('\n');
        }
        pad(sb, indent);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, JSONArray array, int indent) throws JSONException {
        if (array.length() == 0) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < array.length(); i++) {
            pad(sb, indent + 1);
            writeValue(sb, array.opt(i), indent + 1);
            if (i < array.length() - 1) sb.append(',');
            sb.append('\n');
        }
        pad(sb, indent);
        sb.append(']');
    }

    private static void pad(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append(' ');
    }

    static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** Formats numbers the way Python's {@code json} module does. */
    static String formatNumber(Number n) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte
                || n instanceof java.math.BigInteger) {
            return n.toString();
        }
        double d = n.doubleValue();
        if (Double.isNaN(d)) return "NaN";
        if (Double.isInfinite(d)) return d > 0 ? "Infinity" : "-Infinity";
        if (n instanceof BigDecimal && ((BigDecimal) n).scale() <= 0) {
            return ((BigDecimal) n).toBigInteger().toString();
        }

        BigDecimal bd = new BigDecimal(Double.toString(d)).stripTrailingZeros();
        String digits = bd.unscaledValue().abs().toString();
        int exponent = digits.length() - bd.scale() - 1;
        String sign = d < 0 ? "-" : "";
        if (d == 0) {
            return (1 / d < 0) ? "-0.0" : "0.0";
        }
        if (exponent >= -4 && exponent < 16) {
            String plain = bd.abs().toPlainString();
            if (plain.indexOf('.') < 0) plain += ".0";
            return sign + plain;
        }
        String mantissa = digits.length() > 1 ? digits.charAt(0) + "." + digits.substring(1) : digits;
        String exp = String.format(Locale.ROOT, "%s%02d", exponent < 0 ? "-" : "+", Math.abs(exponent));
        return sign + mantissa + "e" + exp;
    }
}
