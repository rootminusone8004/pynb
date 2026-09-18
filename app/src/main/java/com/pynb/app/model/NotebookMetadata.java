package com.pynb.app.model;

import java.io.Serializable;

/**
 * Metadata about the Jupyter Notebook, including kernel and language info.
 */
public class NotebookMetadata implements Serializable {
    private String kernelName = "python3";
    private String kernelDisplayName = "Python 3";
    private String languageName = "python";
    private String languageVersion = "";

    public NotebookMetadata() {}

    public String getKernelName() {
        return kernelName;
    }

    public void setKernelName(String kernelName) {
        this.kernelName = kernelName;
    }

    public String getKernelDisplayName() {
        return kernelDisplayName;
    }

    public void setKernelDisplayName(String kernelDisplayName) {
        this.kernelDisplayName = kernelDisplayName;
    }

    public String getLanguageName() {
        return languageName;
    }

    public void setLanguageName(String languageName) {
        this.languageName = languageName;
    }

    public String getLanguageVersion() {
        return languageVersion;
    }

    public void setLanguageVersion(String languageVersion) {
        this.languageVersion = languageVersion;
    }
}
