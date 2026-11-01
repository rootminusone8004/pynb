package com.pynb.app.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes the math in Jupyter Markdown so it can be rendered reliably by
 * {@link SynchronousLatexPlugin}.
 *
 * <p>Like Jupyter's own {@code removeMath}, this scans the source once, left to right, and
 * recognises every math delimiter Jupyter/MathJax understands ({@code $...$}, {@code $$...$$},
 * {@code \(...\)}, {@code \[...\]} and bare {@code \begin{env}...\end{env}}), while leaving code
 * spans and fenced code blocks untouched. The output is canonical:</p>
 * <ul>
 *     <li>inline math becomes {@code $latex$} (on a single line);</li>
 *     <li>display math that sits on its own line(s) or spans several lines becomes a fenced
 *     block: a line with {@code $$}, the formula lines, and a closing {@code $$} line, indented
 *     to stay inside its list item / block quote. This shields the formula from Markdown block
 *     syntax ({@code -}, {@code #}, {@code =}, blank lines...) which would otherwise break it;</li>
 *     <li>display math written in the middle of a line becomes {@code $$latex$$};</li>
 *     <li>every other {@code $} (currency, stray dollars) is escaped as {@code \$} so that the
 *     inline parser never mistakes it for a delimiter.</li>
 * </ul>
 */
public final class MathFormatter {

    private static final Pattern MATH_ENV_START = Pattern.compile(
            "\\\\begin\\{(equation|align|alignat|gather|flalign|multline|eqnarray|displaymath"
                    + "|aligned|alignedat|gathered|split|cases|array"
                    + "|matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|smallmatrix)(\\*?)\\}");

    private static final Pattern CURRENCY = Pattern.compile("^\\d[\\d,.]*$");

    private static final Pattern LINE_INDENT = Pattern.compile(
            "^([ \\t>]*)(?:([-*+]|\\d{1,9}[.)])[ \\t]+)?");

    private MathFormatter() {
    }

    public static String formatMath(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        if (markdown.indexOf('$') < 0 && !markdown.contains("\\(") && !markdown.contains("\\[")
                && !markdown.contains("\\begin{")) {
            return markdown;
        }

        final String s = markdown;
        final int n = s.length();
        final StringBuilder out = new StringBuilder(n + 32);
        int i = 0;

        while (i < n) {
            char c = s.charAt(i);

            // Fenced code block (``` or ~~~) at the start of a line: copy verbatim.
            if ((c == '`' || c == '~') && isLineStartIgnoringIndent(s, i)) {
                int run = runLength(s, i, c);
                if (run >= 3) {
                    int end = findFenceEnd(s, i, c, run);
                    out.append(s, i, end);
                    i = end;
                    continue;
                }
            }

            // Inline code span: copy verbatim up to the matching backtick run.
            if (c == '`') {
                int run = runLength(s, i, '`');
                int close = findBacktickRun(s, i + run, run);
                if (close < 0) {
                    out.append(s, i, i + run);
                    i += run;
                } else {
                    out.append(s, i, close + run);
                    i = close + run;
                }
                continue;
            }

            if (c == '\\' && i + 1 < n) {
                char next = s.charAt(i + 1);
                if (next == '(') {
                    int close = findUnescaped(s, i + 2, "\\)");
                    if (close >= 0) {
                        emitInline(out, s.substring(i + 2, close));
                        i = close + 2;
                        continue;
                    }
                } else if (next == '[') {
                    int close = findUnescaped(s, i + 2, "\\]");
                    if (close >= 0) {
                        emitDisplay(out, s, i, close + 2, s.substring(i + 2, close));
                        i = close + 2;
                        continue;
                    }
                } else if (next == 'b') {
                    Matcher m = MATH_ENV_START.matcher(s);
                    m.region(i, n);
                    if (m.lookingAt()) {
                        String endTag = "\\end{" + m.group(1) + m.group(2) + "}";
                        int close = s.indexOf(endTag, m.end());
                        if (close >= 0) {
                            int end = close + endTag.length();
                            emitDisplay(out, s, i, end, s.substring(i, end));
                            i = end;
                            continue;
                        }
                    }
                }
                // Any other escape (\$, \\, \_ ...) is copied as a pair so the escaped
                // character is never treated as a delimiter.
                out.append(c).append(next);
                i += 2;
                continue;
            }

            if (c == '$') {
                if (i + 1 < n && s.charAt(i + 1) == '$') {
                    int close = findUnescaped(s, i + 2, "$$");
                    if (close >= 0 && !s.substring(i + 2, close).trim().isEmpty()) {
                        emitDisplay(out, s, i, close + 2, s.substring(i + 2, close));
                        i = close + 2;
                        continue;
                    }
                    out.append("\\$\\$");
                    i += 2;
                    continue;
                }
                int close = findInlineDollarClose(s, i);
                if (close >= 0) {
                    String content = s.substring(i + 1, close);
                    if (!CURRENCY.matcher(content.trim()).matches()) {
                        emitInline(out, content);
                        i = close + 1;
                        continue;
                    }
                }
                out.append("\\$");
                i++;
                continue;
            }

            out.append(c);
            i++;
        }

        return out.toString();
    }

    // ------------------------------------------------------------------ emitters

    private static void emitInline(StringBuilder out, String latex) {
        String flat = latex.replaceAll("\\s*\\n\\s*", " ").trim();
        if (flat.isEmpty()) {
            return;
        }
        out.append('$').append(flat).append('$');
    }

    /**
     * @param start index of the opening delimiter in {@code s}
     * @param end   index just past the closing delimiter in {@code s}
     */
    private static void emitDisplay(StringBuilder out, String s, int start, int end, String latex) {
        String body = latex.trim();
        if (body.isEmpty()) {
            return;
        }

        int lineStart = s.lastIndexOf('\n', start - 1) + 1;
        int lineEnd = s.indexOf('\n', end);
        if (lineEnd < 0) lineEnd = s.length();

        String prefix = s.substring(lineStart, start);
        String suffix = s.substring(end, lineEnd);
        boolean prefixBlank = prefix.matches("[ \\t>]*");
        boolean suffixBlank = suffix.trim().isEmpty();
        boolean multiLine = body.indexOf('\n') >= 0;

        if (!multiLine && !(prefixBlank && suffixBlank)) {
            // Display formula in the middle of a line of text.
            out.append("$$").append(body).append("$$");
            return;
        }

        String indent = continuationIndent(prefix);
        if (!prefixBlank) {
            out.append('\n').append(indent);
        }
        out.append("$$\n");
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            out.append(indent).append(t).append('\n');
        }
        out.append(indent).append("$$");
        if (!suffixBlank) {
            out.append('\n').append(indent);
        }
    }

    /** Indentation that keeps a new line inside the current list item / block quote. */
    private static String continuationIndent(String linePrefix) {
        Matcher m = LINE_INDENT.matcher(linePrefix);
        if (!m.lookingAt()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(m.group(1));
        int markerLen = m.end() - m.end(1);
        for (int k = 0; k < markerLen; k++) sb.append(' ');
        return sb.toString();
    }

    // ------------------------------------------------------------------ scanning helpers

    /** Pandoc / Jupyter rule for {@code $...$}: no space after the opener or before the closer. */
    private static int findInlineDollarClose(String s, int open) {
        int n = s.length();
        int j = open + 1;
        if (j >= n || Character.isWhitespace(s.charAt(j)) || s.charAt(j) == '$') {
            return -1;
        }
        while (j < n) {
            char c = s.charAt(j);
            if (c == '\\') {
                j += 2;
                continue;
            }
            if (c == '\n' && isBlankLineAhead(s, j + 1)) {
                return -1; // math never crosses a paragraph break
            }
            if (c == '$') {
                boolean doubled = j + 1 < n && s.charAt(j + 1) == '$';
                boolean spaceBefore = Character.isWhitespace(s.charAt(j - 1));
                boolean digitAfter = j + 1 < n && Character.isDigit(s.charAt(j + 1));
                if (!doubled && !spaceBefore && !digitAfter) {
                    return j;
                }
                return -1;
            }
            j++;
        }
        return -1;
    }

    /** Finds {@code needle}, skipping backslash escape pairs (an escaped dollar, a LaTeX line break...). */
    private static int findUnescaped(String s, int from, String needle) {
        int j = from;
        while (j < s.length()) {
            if (s.startsWith(needle, j)) {
                return j;
            }
            j += s.charAt(j) == '\\' ? 2 : 1;
        }
        return -1;
    }

    private static boolean isBlankLineAhead(String s, int from) {
        int j = from;
        while (j < s.length() && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) j++;
        return j >= s.length() || s.charAt(j) == '\n';
    }

    private static boolean isLineStartIgnoringIndent(String s, int i) {
        int j = i - 1;
        while (j >= 0 && (s.charAt(j) == ' ' || s.charAt(j) == '\t' || s.charAt(j) == '>')) j--;
        return j < 0 || s.charAt(j) == '\n';
    }

    private static int runLength(String s, int i, char c) {
        int j = i;
        while (j < s.length() && s.charAt(j) == c) j++;
        return j - i;
    }

    private static int findBacktickRun(String s, int from, int len) {
        int j = from;
        while (j < s.length()) {
            if (s.charAt(j) == '`') {
                int run = runLength(s, j, '`');
                if (run == len) return j;
                j += run;
            } else {
                j++;
            }
        }
        return -1;
    }

    /** Returns the index just past the closing fence line (or the end of the text). */
    private static int findFenceEnd(String s, int open, char c, int len) {
        int lineEnd = s.indexOf('\n', open);
        if (lineEnd < 0) return s.length();
        int pos = lineEnd + 1;
        while (pos < s.length()) {
            int next = s.indexOf('\n', pos);
            int end = next < 0 ? s.length() : next;
            String line = s.substring(pos, end).trim();
            if (line.length() >= len && runLength(line, 0, c) == line.length()) {
                return end;
            }
            if (next < 0) break;
            pos = next + 1;
        }
        return s.length();
    }

    // ------------------------------------------------------------------ LaTeX normalization

    private static final String[][] LATEX_REWRITES = {
            // Numbering / referencing commands that only make sense in a full document
            {"\\\\(?:label|require)\\s*\\{[^{}]*\\}", ""},
            {"\\\\(?:nonumber|notag)(?![a-zA-Z])", ""},
            {"\\\\tag\\*?\\s*\\{([^{}]*)\\}", "\\\\qquad\\\\text{($1)}"},
            {"\\\\eqref\\s*\\{([^{}]*)\\}", "\\\\text{($1)}"},
            {"\\\\ref\\s*\\{([^{}]*)\\}", "\\\\text{$1}"},
            // equation is just a wrapper; starred AMS environments map to their plain versions
            {"\\\\(?:begin|end)\\{equation\\*?\\}", ""},
            {"\\\\(begin|end)\\{(align|alignat|gather|multline|flalign|eqnarray)\\*\\}", "\\\\$1{$2}"},
            // MathJax/AMS macros JLatexMath lacks -> closest equivalents it has
            {"\\\\[lr]vert(?![a-zA-Z])", "\\\\vert"},
            {"\\\\coloneqq(?![a-zA-Z])", "\\\\colonequals"},
            {"\\\\eqqcolon(?![a-zA-Z])", "\\\\equalscolon"},
            {"\\\\bm(?![a-zA-Z])", "\\\\boldsymbol"},
            {"\\\\mathbbm(?![a-zA-Z])", "\\\\mathbb"},
            {"\\\\operatorname\\*", "\\\\operatorname"},
            {"\\\\emph(?![a-zA-Z])", "\\\\textit"},
            {"\\\\dotsm(?![a-zA-Z])", "\\\\cdots"},
            {"\\\\(?:b|x)?cancel(?![a-zA-Z])", ""},
            {"\\\\color\\s*\\{[^{}]*\\}", ""},
            {"\\\\displaylines(?![a-zA-Z])", ""},
    };

    private static final Pattern[] LATEX_PATTERNS = new Pattern[LATEX_REWRITES.length];

    static {
        for (int k = 0; k < LATEX_REWRITES.length; k++) {
            LATEX_PATTERNS[k] = Pattern.compile(LATEX_REWRITES[k][0]);
        }
    }

    /**
     * Rewrites MathJax-only constructs into syntax JLatexMath understands
     * (e.g. {@code \begin{align*}}, {@code \label}, {@code \tag}, {@code \lvert}, {@code \bm}).
     */
    public static String normalizeLatex(String latex) {
        if (latex == null) {
            return "";
        }
        String result = latex;
        if (result.indexOf('\\') >= 0) {
            for (int k = 0; k < LATEX_PATTERNS.length; k++) {
                result = LATEX_PATTERNS[k].matcher(result).replaceAll(LATEX_REWRITES[k][1]);
            }
        }
        // JLatexMath ignores line breaks, but a trailing "\\" creates an empty last row.
        result = result.trim().replaceAll("(\\\\\\\\\\s*)+$", "");
        return result.trim();
    }
}
