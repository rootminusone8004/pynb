package com.pynb.app.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents an entire Jupyter Notebook (.ipynb) document.
 */
public class Notebook implements Serializable {
    private String title = "Untitled Notebook";
    private int nbformat = 4;
    private int nbformatMinor = 2;
    private NotebookMetadata metadata = new NotebookMetadata();
    private List<NotebookCell> cells = new ArrayList<>();

    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^(#{1,6})\\s+([^#\\n]+)");

    public Notebook() {}

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getNbformat() {
        return nbformat;
    }

    public void setNbformat(int nbformat) {
        this.nbformat = nbformat;
    }

    public int getNbformatMinor() {
        return nbformatMinor;
    }

    public void setNbformatMinor(int nbformatMinor) {
        this.nbformatMinor = nbformatMinor;
    }

    public NotebookMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(NotebookMetadata metadata) {
        this.metadata = metadata != null ? metadata : new NotebookMetadata();
    }

    public List<NotebookCell> getCells() {
        return cells;
    }

    public void setCells(List<NotebookCell> cells) {
        this.cells = cells != null ? cells : new ArrayList<>();
        // Assign cell indices
        for (int i = 0; i < this.cells.size(); i++) {
            this.cells.get(i).setCellIndex(i);
        }
    }

    public int getTotalCells() {
        return cells != null ? cells.size() : 0;
    }

    public int getCodeCellCount() {
        if (cells == null) return 0;
        int count = 0;
        for (NotebookCell cell : cells) {
            if (cell.isCode()) count++;
        }
        return count;
    }

    public int getMarkdownCellCount() {
        if (cells == null) return 0;
        int count = 0;
        for (NotebookCell cell : cells) {
            if (cell.isMarkdown()) count++;
        }
        return count;
    }

    /**
     * Extracts Table of Contents (outline) from all Markdown headings.
     */
    public List<OutlineItem> getOutline() {
        List<OutlineItem> outline = new ArrayList<>();
        if (cells == null) return outline;

        for (int i = 0; i < cells.size(); i++) {
            NotebookCell cell = cells.get(i);
            if (cell.isMarkdown() && cell.getSource() != null) {
                Matcher matcher = HEADING_PATTERN.matcher(cell.getSource());
                while (matcher.find()) {
                    String hashes = matcher.group(1);
                    String headingText = matcher.group(2);
                    int level = hashes != null ? hashes.length() : 1;
                    String cleanTitle = headingText != null ? headingText.trim() : "Section";
                    outline.add(new OutlineItem(level, cleanTitle, i));
                }
            }
        }
        return outline;
    }
}
