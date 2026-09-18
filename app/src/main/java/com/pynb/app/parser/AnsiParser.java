package com.pynb.app.parser;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ANSI escape sequences into Android Spannable text with colors and styles.
 * Jupyter notebooks use ANSI codes extensively in stderr and tracebacks.
 */
public class AnsiParser {

    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[([0-9;]*)m");

    // Terminal colors adapted for high readability on both dark and light surfaces
    private static final int COLOR_BLACK = 0xFF282C34;
    private static final int COLOR_RED = 0xFFE06C75;
    private static final int COLOR_GREEN = 0xFF98C379;
    private static final int COLOR_YELLOW = 0xFFE5C07B;
    private static final int COLOR_BLUE = 0xFF61AFEF;
    private static final int COLOR_MAGENTA = 0xFFC678DD;
    private static final int COLOR_CYAN = 0xFF56B6C2;
    private static final int COLOR_WHITE = 0xFFABB2BF;
    private static final int COLOR_GRAY = 0xFF5C6370;

    /**
     * Converts a raw ANSI string into a SpannableStringBuilder with appropriate spans.
     */
    public static CharSequence parse(String raw, boolean isDarkTheme) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        if (!raw.contains("\u001B")) {
            return raw;
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        Matcher matcher = ANSI_PATTERN.matcher(raw);

        int lastIndex = 0;
        Integer currentColor = null;
        boolean isBold = false;
        int activeSpanStart = 0;

        while (matcher.find()) {
            int matchStart = matcher.start();
            int matchEnd = matcher.end();

            // Append text prior to this escape code
            if (matchStart > lastIndex) {
                String segment = raw.substring(lastIndex, matchStart);
                int segStart = sb.length();
                sb.append(segment);
                int segEnd = sb.length();

                if (currentColor != null) {
                    sb.setSpan(new ForegroundColorSpan(currentColor), segStart, segEnd, 0);
                }
                if (isBold) {
                    sb.setSpan(new StyleSpan(Typeface.BOLD), segStart, segEnd, 0);
                }
            }

            // Parse codes inside \u001b[...m
            String codesStr = matcher.group(1);
            if (codesStr == null || codesStr.isEmpty()) {
                codesStr = "0"; // reset
            }

            String[] codes = codesStr.split(";");
            for (String codeStr : codes) {
                try {
                    int code = Integer.parseInt(codeStr);
                    if (code == 0) {
                        // Reset
                        currentColor = null;
                        isBold = false;
                    } else if (code == 1) {
                        isBold = true;
                    } else if (code >= 30 && code <= 37) {
                        currentColor = getStandardColor(code - 30, isDarkTheme);
                    } else if (code == 39) {
                        // Default color
                        currentColor = null;
                    } else if (code >= 90 && code <= 97) {
                        currentColor = getBrightColor(code - 90, isDarkTheme);
                    }
                } catch (NumberFormatException ignored) {}
            }

            lastIndex = matchEnd;
        }

        // Append remaining text
        if (lastIndex < raw.length()) {
            String segment = raw.substring(lastIndex);
            int segStart = sb.length();
            sb.append(segment);
            int segEnd = sb.length();

            if (currentColor != null) {
                sb.setSpan(new ForegroundColorSpan(currentColor), segStart, segEnd, 0);
            }
            if (isBold) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), segStart, segEnd, 0);
            }
        }

        return sb;
    }

    private static int getStandardColor(int index, boolean isDarkTheme) {
        switch (index) {
            case 0: return isDarkTheme ? COLOR_GRAY : COLOR_BLACK;
            case 1: return COLOR_RED;
            case 2: return isDarkTheme ? COLOR_GREEN : 0xFF2E7D32;
            case 3: return isDarkTheme ? COLOR_YELLOW : 0xFFB78103;
            case 4: return isDarkTheme ? COLOR_BLUE : 0xFF1565C0;
            case 5: return COLOR_MAGENTA;
            case 6: return isDarkTheme ? COLOR_CYAN : 0xFF00838F;
            case 7: return isDarkTheme ? COLOR_WHITE : 0xFF424242;
            default: return isDarkTheme ? Color.WHITE : Color.BLACK;
        }
    }

    private static int getBrightColor(int index, boolean isDarkTheme) {
        switch (index) {
            case 0: return 0xFF7F848E;
            case 1: return 0xFFFF6B6B;
            case 2: return 0xFF69DB7C;
            case 3: return 0xFFFFD43B;
            case 4: return 0xFF4DABF7;
            case 5: return 0xFFDA77F2;
            case 6: return 0xFF38D9A9;
            case 7: return Color.WHITE;
            default: return isDarkTheme ? Color.WHITE : Color.BLACK;
        }
    }

    /**
     * Helper to strip all ANSI codes from a string.
     */
    public static String stripAnsi(String input) {
        if (input == null) return "";
        return ANSI_PATTERN.matcher(input).replaceAll("");
    }
}
