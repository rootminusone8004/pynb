package com.pynb.app.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for normalizing mathematical formulas and LaTeX syntax in Jupyter Notebooks.
 * Converts single-dollar inline math ($...$), parenthesis delimiters (\(...\), \[...\]),
 * and LaTeX environments into Markwon/JLatexMath-compatible format ($$...$$).
 */
public class MathFormatter {

    // Matches code blocks (fenced or inline) to ensure math inside code is untouched
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(```[\\s\\S]*?```|`[^`\n]+`)");

    // Matches LaTeX inline delimiters \( ... \)
    private static final Pattern PAREN_INLINE_PATTERN = Pattern.compile("\\\\\\(([\\s\\S]+?)\\\\\\)");

    // Matches LaTeX display delimiters \[ ... \]
    private static final Pattern BRACKET_DISPLAY_PATTERN = Pattern.compile("\\\\\\[([\\s\\S]+?)\\\\\\]");

    // Matches LaTeX environment blocks like \begin{equation}...\end{equation}
    private static final Pattern LATEX_ENV_PATTERN = Pattern.compile(
            "\\\\begin\\{(equation|align|gather|flalign|multline|matrix|pmatrix|bmatrix|vmatrix|Vmatrix|aligned|array|split)\\*?\\}([\\s\\S]+?)\\\\end\\{\\1\\*?\\}");

    // Matches single dollar inline math: $formula$, avoiding escaped \$, double $$, and currency
    private static final Pattern SINGLE_DOLLAR_PATTERN = Pattern.compile(
            "(?<![\\\\\\$])\\$(?!\\$|\\s)([^\\n\\$]+?)(?<!\\s|\\\\)\\$(?!\\$)");

    // Matches pure numbers/currency like $100, $5.50, $20,000
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^\\d[\\d,.]*$");

    public static String formatMath(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        // Fast check: if no math indicator present, return as is
        if (!markdown.contains("$") && !markdown.contains("\\(") && !markdown.contains("\\[") && !markdown.contains("\\begin{")) {
            return markdown;
        }

        StringBuilder sb = new StringBuilder();
        Matcher codeMatcher = CODE_BLOCK_PATTERN.matcher(markdown);
        int lastEnd = 0;

        while (codeMatcher.find()) {
            // Process non-code text before this code block
            String textBefore = markdown.substring(lastEnd, codeMatcher.start());
            sb.append(processNonCodeSegment(textBefore));

            // Append code block verbatim
            sb.append(codeMatcher.group());
            lastEnd = codeMatcher.end();
        }

        // Process remaining text after the last code block
        if (lastEnd < markdown.length()) {
            sb.append(processNonCodeSegment(markdown.substring(lastEnd)));
        }

        return sb.toString();
    }

    private static String processNonCodeSegment(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 1. Convert \( ... \) to $$ ... $$
        Matcher parenMatcher = PAREN_INLINE_PATTERN.matcher(text);
        StringBuffer parenBuf = new StringBuffer();
        while (parenMatcher.find()) {
            parenMatcher.appendReplacement(parenBuf, Matcher.quoteReplacement("$$" + parenMatcher.group(1) + "$$"));
        }
        parenMatcher.appendTail(parenBuf);
        text = parenBuf.toString();

        // 2. Convert \[ ... \] to \n$$\n...\n$$\n
        Matcher bracketMatcher = BRACKET_DISPLAY_PATTERN.matcher(text);
        StringBuffer bracketBuf = new StringBuffer();
        while (bracketMatcher.find()) {
            bracketMatcher.appendReplacement(bracketBuf, Matcher.quoteReplacement("\n$$\n" + bracketMatcher.group(1) + "\n$$\n"));
        }
        bracketMatcher.appendTail(bracketBuf);
        text = bracketBuf.toString();

        // 3. Convert \begin{env}...\end{env} to \n$$\n\begin{env}...\end{env}\n$$\n
        Matcher envMatcher = LATEX_ENV_PATTERN.matcher(text);
        StringBuffer envBuffer = new StringBuffer();
        while (envMatcher.find()) {
            String fullEnv = envMatcher.group(0);
            envMatcher.appendReplacement(envBuffer, Matcher.quoteReplacement("\n$$\n" + fullEnv + "\n$$\n"));
        }
        envMatcher.appendTail(envBuffer);
        text = envBuffer.toString();

        // 4. Convert single dollar $formula$ to $$formula$$, ignoring currency
        Matcher dollarMatcher = SINGLE_DOLLAR_PATTERN.matcher(text);
        StringBuffer dollarBuffer = new StringBuffer();
        while (dollarMatcher.find()) {
            String content = dollarMatcher.group(1);
            if (content != null && CURRENCY_PATTERN.matcher(content.trim()).matches()) {
                // Keep currency untouched
                dollarMatcher.appendReplacement(dollarBuffer, Matcher.quoteReplacement(dollarMatcher.group(0)));
            } else {
                dollarMatcher.appendReplacement(dollarBuffer, Matcher.quoteReplacement("$$" + content + "$$"));
            }
        }
        dollarMatcher.appendTail(dollarBuffer);
        text = dollarBuffer.toString();

        return text;
    }
}
