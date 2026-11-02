package com.pynb.app.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CellEditorIndentTest {

    private static String indentAfter(String textBeforeNewline) {
        String text = textBeforeNewline + "\n";
        return CellEditorDialog.indentFor(text, text.length() - 1);
    }

    @Test
    public void keepsPreviousIndentation() {
        assertEquals("    ", indentAfter("def f():\n    x = 1"));
        assertEquals("", indentAfter("x = 1"));
    }

    @Test
    public void addsLevelAfterColon() {
        assertEquals("    ", indentAfter("for i in range(3):"));
        assertEquals("        ", indentAfter("def f():\n    if x:"));
    }

    @Test
    public void ignoresColonInsideTrailingComment() {
        assertEquals("    ", indentAfter("if x:  # note"));
        assertEquals("", indentAfter("x = 1  # todo:"));
    }
}
