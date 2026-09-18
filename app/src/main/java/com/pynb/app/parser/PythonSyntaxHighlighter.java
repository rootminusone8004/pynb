package com.pynb.app.parser;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast, lightweight syntax highlighter for Python code cells.
 * Runs efficiently on native Android without WebView overhead.
 */
public class PythonSyntaxHighlighter {

    private static final Pattern PATTERN_KEYWORDS = Pattern.compile(
            "\\b(and|as|assert|async|await|break|class|continue|def|del|elif|else|except|finally|" +
            "for|from|global|if|import|in|is|lambda|nonlocal|not|or|pass|raise|return|try|while|with|yield)\\b"
    );

    private static final Pattern PATTERN_CONSTANTS = Pattern.compile(
            "\\b(True|False|None|self|cls)\\b"
    );

    private static final Pattern PATTERN_BUILTINS = Pattern.compile(
            "\\b(abs|all|any|bin|bool|bytearray|bytes|callable|chr|classmethod|compile|complex|delattr|" +
            "dict|dir|divmod|enumerate|eval|exec|filter|float|format|frozenset|getattr|globals|hasattr|" +
            "hash|help|hex|id|input|int|isinstance|issubclass|iter|len|list|locals|map|max|memoryview|" +
            "min|next|object|oct|open|ord|pow|print|property|range|repr|reversed|round|set|setattr|" +
            "slice|sorted|staticmethod|str|sum|super|tuple|type|vars|zip)\\b"
    );

    private static final Pattern PATTERN_DECORATOR = Pattern.compile("(?m)^\\s*@[a-zA-Z_][a-zA-Z0-9_.]*");

    private static final Pattern PATTERN_DEF_CLASS = Pattern.compile(
            "\\b(def|class)\\s+([a-zA-Z_][a-zA-Z0-9_]*)"
    );

    private static final Pattern PATTERN_NUMBERS = Pattern.compile(
            "\\b(0[xX][0-9a-fA-F]+|0[bB][01]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?)\\b"
    );

    // Multi-line and single-line strings
    private static final Pattern PATTERN_STRINGS = Pattern.compile(
            "(\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')"
    );

    private static final Pattern PATTERN_COMMENTS = Pattern.compile("(?m)#.*$");

    public static CharSequence highlight(String code, boolean isDarkTheme) {
        if (code == null || code.isEmpty()) {
            return "";
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        // Color palettes
        int colorKeyword = isDarkTheme ? 0xFFC678DD : 0xFFA626A4;
        int colorConstant = isDarkTheme ? 0xFFE5C07B : 0xFFB76B00;
        int colorBuiltin = isDarkTheme ? 0xFF56B6C2 : 0xFF0184BC;
        int colorDef = isDarkTheme ? 0xFF61AFEF : 0xFF4078F2;
        int colorNumber = isDarkTheme ? 0xFFD19A66 : 0xFF986801;
        int colorString = isDarkTheme ? 0xFF98C379 : 0xFF50A14F;
        int colorComment = isDarkTheme ? 0xFF7F848E : 0xFFA0A1A7;
        int colorDecorator = isDarkTheme ? 0xFFE5C07B : 0xFFC18401;

        // 1. Numbers
        Matcher mNum = PATTERN_NUMBERS.matcher(code);
        while (mNum.find()) {
            ssb.setSpan(new ForegroundColorSpan(colorNumber), mNum.start(), mNum.end(), 0);
        }

        // 2. Built-ins
        Matcher mBuiltins = PATTERN_BUILTINS.matcher(code);
        while (mBuiltins.find()) {
            ssb.setSpan(new ForegroundColorSpan(colorBuiltin), mBuiltins.start(), mBuiltins.end(), 0);
        }

        // 3. Constants
        Matcher mConst = PATTERN_CONSTANTS.matcher(code);
        while (mConst.find()) {
            ssb.setSpan(new ForegroundColorSpan(colorConstant), mConst.start(), mConst.end(), 0);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), mConst.start(), mConst.end(), 0);
        }

        // 4. Keywords
        Matcher mKeywords = PATTERN_KEYWORDS.matcher(code);
        while (mKeywords.find()) {
            ssb.setSpan(new ForegroundColorSpan(colorKeyword), mKeywords.start(), mKeywords.end(), 0);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), mKeywords.start(), mKeywords.end(), 0);
        }

        // 5. Def / Class function names
        Matcher mDefClass = PATTERN_DEF_CLASS.matcher(code);
        while (mDefClass.find()) {
            int nameStart = mDefClass.start(2);
            int nameEnd = mDefClass.end(2);
            ssb.setSpan(new ForegroundColorSpan(colorDef), nameStart, nameEnd, 0);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), nameStart, nameEnd, 0);
        }

        // 6. Decorators
        Matcher mDeco = PATTERN_DECORATOR.matcher(code);
        while (mDeco.find()) {
            ssb.setSpan(new ForegroundColorSpan(colorDecorator), mDeco.start(), mDeco.end(), 0);
        }

        // 7. Strings (overlaying on top of keywords)
        Matcher mStr = PATTERN_STRINGS.matcher(code);
        while (mStr.find()) {
            ssb.setSpan(new ForegroundColorSpan(colorString), mStr.start(), mStr.end(), 0);
        }

        // 8. Comments (highest priority, overrides strings and keywords)
        Matcher mComments = PATTERN_COMMENTS.matcher(code);
        while (mComments.find()) {
            ssb.setSpan(new ForegroundColorSpan(colorComment), mComments.start(), mComments.end(), 0);
            ssb.setSpan(new StyleSpan(Typeface.ITALIC), mComments.start(), mComments.end(), 0);
        }

        return ssb;
    }
}
