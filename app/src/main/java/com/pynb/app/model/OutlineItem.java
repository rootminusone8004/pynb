package com.pynb.app.model;

import java.io.Serializable;

/**
 * Represents a heading in the table of contents / outline.
 */
public class OutlineItem implements Serializable {
    private final int level; // 1 to 6
    private final String title;
    private final int cellIndex;

    public OutlineItem(int level, String title, int cellIndex) {
        this.level = level;
        this.title = title;
        this.cellIndex = cellIndex;
    }

    public int getLevel() {
        return level;
    }

    public String getTitle() {
        return title;
    }

    public int getCellIndex() {
        return cellIndex;
    }
}
