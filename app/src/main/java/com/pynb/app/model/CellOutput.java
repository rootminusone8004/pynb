package com.pynb.app.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single output from a Jupyter code cell.
 * Handles stream (stdout/stderr), execute_result, display_data, and error.
 */
public class CellOutput implements Serializable {
    private String outputType;
    private String name; // e.g. "stdout" or "stderr"
    private String text;
    private Integer executionCount;
    private String ename;
    private String evalue;
    private List<String> traceback;
    private Map<String, String> data = new HashMap<>();

    public CellOutput() {}

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getExecutionCount() {
        return executionCount;
    }

    public void setExecutionCount(Integer executionCount) {
        this.executionCount = executionCount;
    }

    public String getEname() {
        return ename;
    }

    public void setEname(String ename) {
        this.ename = ename;
    }

    public String getEvalue() {
        return evalue;
    }

    public void setEvalue(String evalue) {
        this.evalue = evalue;
    }

    public List<String> getTraceback() {
        return traceback;
    }

    public void setTraceback(List<String> traceback) {
        this.traceback = traceback;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public boolean isStream() {
        return "stream".equalsIgnoreCase(outputType);
    }

    public boolean isError() {
        return "error".equalsIgnoreCase(outputType);
    }

    public boolean isExecuteResult() {
        return "execute_result".equalsIgnoreCase(outputType);
    }

    public boolean isDisplayData() {
        return "display_data".equalsIgnoreCase(outputType);
    }

    public boolean hasImage() {
        if (data == null) return false;
        return data.containsKey("image/png") || data.containsKey("image/jpeg") || data.containsKey("image/webp");
    }

    public String getImageBase64() {
        if (data == null) return null;
        if (data.containsKey("image/png")) return data.get("image/png");
        if (data.containsKey("image/jpeg")) return data.get("image/jpeg");
        if (data.containsKey("image/webp")) return data.get("image/webp");
        return null;
    }

    public String getImageMimeType() {
        if (data == null) return null;
        if (data.containsKey("image/png")) return "image/png";
        if (data.containsKey("image/jpeg")) return "image/jpeg";
        if (data.containsKey("image/webp")) return "image/webp";
        return null;
    }

    public boolean hasPlainText() {
        if (isStream() && text != null && !text.isEmpty()) return true;
        return data != null && data.containsKey("text/plain");
    }

    public String getPlainText() {
        if (isStream()) return text != null ? text : "";
        if (data != null && data.containsKey("text/plain")) {
            return data.get("text/plain");
        }
        return text != null ? text : "";
    }

    public boolean hasHtml() {
        return data != null && data.containsKey("text/html");
    }

    public String getHtmlText() {
        return data != null ? data.get("text/html") : null;
    }
}
