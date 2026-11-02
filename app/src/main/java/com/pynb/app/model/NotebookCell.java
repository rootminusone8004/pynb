package com.pynb.app.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single cell (markdown, code, or raw) in a Jupyter Notebook.
 */
public class NotebookCell implements Serializable {
    private String cellType; // "markdown", "code", "raw"
    private String source = "";
    private Integer executionCount;
    private List<CellOutput> outputs = new ArrayList<>();
    private boolean isCollapsed = false;
    private int cellIndex = 0;

    /** Execution state while a Termux kernel runs this cell (not saved). */
    public enum RunState { IDLE, QUEUED, RUNNING }

    private transient RunState runState = RunState.IDLE;
    /** Original JSON of the cell, so saving keeps fields this app does not model. */
    private transient org.json.JSONObject rawJson;
    /** True once outputs / execution count were replaced by running the cell. */
    private transient boolean modified = false;
    /** True once the source was edited in the app. */
    private transient boolean sourceModified = false;

    public NotebookCell() {}

    public String getCellType() {
        return cellType;
    }

    public void setCellType(String cellType) {
        this.cellType = cellType;
    }

    public String getSource() {
        return source != null ? source : "";
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getExecutionCount() {
        return executionCount;
    }

    public void setExecutionCount(Integer executionCount) {
        this.executionCount = executionCount;
    }

    public List<CellOutput> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<CellOutput> outputs) {
        this.outputs = outputs != null ? outputs : new ArrayList<>();
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    public void setCollapsed(boolean collapsed) {
        isCollapsed = collapsed;
    }

    public int getCellIndex() {
        return cellIndex;
    }

    public void setCellIndex(int cellIndex) {
        this.cellIndex = cellIndex;
    }

    public boolean isCode() {
        return "code".equalsIgnoreCase(cellType);
    }

    public boolean isMarkdown() {
        return "markdown".equalsIgnoreCase(cellType);
    }

    public boolean isRaw() {
        return "raw".equalsIgnoreCase(cellType);
    }

    public boolean hasOutputs() {
        return outputs != null && !outputs.isEmpty();
    }

    public RunState getRunState() {
        return runState != null ? runState : RunState.IDLE;
    }

    public void setRunState(RunState runState) {
        this.runState = runState;
    }

    public org.json.JSONObject getRawJson() {
        return rawJson;
    }

    public void setRawJson(org.json.JSONObject rawJson) {
        this.rawJson = rawJson;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public boolean isSourceModified() {
        return sourceModified;
    }

    public void setSourceModified(boolean sourceModified) {
        this.sourceModified = sourceModified;
    }

    public String getExecutionPrompt() {
        if (getRunState() != RunState.IDLE) {
            return "[*]";
        }
        if (executionCount == null) {
            return "[ ]";
        }
        return "[" + executionCount + "]";
    }
}
