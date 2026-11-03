package com.pynb.app.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a Markdown cell's source into plain Markdown parts and fenced
 * {@code ```mermaid} diagram blocks, in document order, so diagrams can be rendered
 * separately from the Markdown around them.
 */
public final class MermaidSplitter {

    /** One piece of a Markdown cell: either Markdown text or a Mermaid diagram's source. */
    public static final class Segment {
        public final boolean mermaid;
        public final String text;

        Segment(boolean mermaid, String text) {
            this.mermaid = mermaid;
            this.text = text;
        }

        @Override
        public String toString() {
            return (mermaid ? "mermaid:" : "md:") + text;
        }
    }

    // Opening fence: up to 3 spaces indent, 3+ backticks or tildes, info string starting with "mermaid".
    private static final Pattern OPEN = Pattern.compile("^ {0,3}(`{3,}|~{3,})\\s*mermaid(?:\\s.*)?$",
            Pattern.CASE_INSENSITIVE);
    // Any fence opener (used to skip over non-mermaid code blocks, which may contain "```mermaid" text).
    private static final Pattern ANY_OPEN = Pattern.compile("^ {0,3}(`{3,}|~{3,}).*$");

    private MermaidSplitter() {
    }

    /** Quick check used to keep the common no-diagram path cheap. */
    public static boolean containsMermaid(String source) {
        if (source == null || source.toLowerCase().indexOf("mermaid") < 0) return false;
        for (Segment s : split(source)) {
            if (s.mermaid) return true;
        }
        return false;
    }

    public static List<Segment> split(String source) {
        List<Segment> out = new ArrayList<>();
        if (source == null || source.isEmpty()) return out;

        String[] lines = source.split("\n", -1);
        StringBuilder md = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = stripCr(lines[i]);
            Matcher open = ANY_OPEN.matcher(line);
            if (!open.matches()) {
                appendLine(md, lines[i]);
                i++;
                continue;
            }

            String fence = open.group(1);
            boolean isMermaid = OPEN.matcher(line).matches();
            int close = findClose(lines, i + 1, fence);

            if (!isMermaid) {
                // Copy the whole non-mermaid code block through untouched.
                int end = close < 0 ? lines.length - 1 : close;
                for (int k = i; k <= end; k++) appendLine(md, lines[k]);
                i = end + 1;
                continue;
            }

            int end = close < 0 ? lines.length : close; // unclosed fence runs to end of cell
            StringBuilder diagram = new StringBuilder();
            for (int k = i + 1; k < end; k++) appendLine(diagram, stripCr(lines[k]));

            flushMarkdown(out, md);
            out.add(new Segment(true, diagram.toString().trim()));
            i = end + 1;
        }
        flushMarkdown(out, md);
        return out;
    }

    /** Closing fence: same char, at least as long as the opener, nothing but whitespace after. */
    private static int findClose(String[] lines, int from, String fence) {
        char c = fence.charAt(0);
        Pattern close = Pattern.compile("^ {0,3}" + Pattern.quote(String.valueOf(c)) + "{" + fence.length() + ",}\\s*$");
        for (int k = from; k < lines.length; k++) {
            if (close.matcher(stripCr(lines[k])).matches()) return k;
        }
        return -1;
    }

    private static void flushMarkdown(List<Segment> out, StringBuilder md) {
        String text = md.toString().trim();
        if (!text.isEmpty()) out.add(new Segment(false, text));
        md.setLength(0);
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(line);
    }

    private static String stripCr(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }
}
