package com.pynb.app.util;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.style.AlignmentSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;

import androidx.annotation.NonNull;

import org.commonmark.node.Block;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

import java.util.regex.Pattern;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonPlugin;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.ext.latex.JLatexMathBlock;
import io.noties.markwon.ext.latex.JLatexMathNode;
import io.noties.markwon.inlineparser.InlineProcessor;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import ru.noties.jlatexmath.JLatexMathAndroid;
import ru.noties.jlatexmath.JLatexMathDrawable;

/**
 * Custom Markwon plugin for synchronous, high-performance LaTeX formula rendering.
 * Avoids AsynchronousDrawableScheduler race conditions in RecyclerView.
 */
public class SynchronousLatexPlugin extends AbstractMarkwonPlugin {

    private static final String TAG = "SynchronousLatexPlugin";

    public interface TextSizeProvider {
        float getTextSizePx();
    }

    public interface ColorProvider {
        int getTextColor();
    }

    private final TextSizeProvider inlineTextSizeProvider;
    private final TextSizeProvider blockTextSizeProvider;
    private final ColorProvider colorProvider;

    public static SynchronousLatexPlugin create(
            @NonNull Context context,
            @NonNull TextSizeProvider inlineTextSizeProvider,
            @NonNull TextSizeProvider blockTextSizeProvider,
            @NonNull ColorProvider colorProvider) {
        try {
            JLatexMathAndroid.init(context.getApplicationContext());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init JLatexMathAndroid in plugin factory", t);
        }
        return new SynchronousLatexPlugin(inlineTextSizeProvider, blockTextSizeProvider, colorProvider);
    }

    private SynchronousLatexPlugin(
            @NonNull TextSizeProvider inlineTextSizeProvider,
            @NonNull TextSizeProvider blockTextSizeProvider,
            @NonNull ColorProvider colorProvider) {
        this.inlineTextSizeProvider = inlineTextSizeProvider;
        this.blockTextSizeProvider = blockTextSizeProvider;
        this.colorProvider = colorProvider;
    }

    @Override
    public void configure(@NonNull MarkwonPlugin.Registry registry) {
        registry.require(MarkwonInlineParserPlugin.class)
                .factoryBuilder()
                .addInlineProcessor(new SynchronousLatexInlineProcessor());
    }

    @Override
    public void configureParser(@NonNull Parser.Builder builder) {
        builder.customBlockParserFactory(new LatexBlockParser.Factory());
    }

    @Override
    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
        // Handle inline math nodes
        builder.on(JLatexMathNode.class, (visitor, node) -> {
            String latex = node.latex();
            if (latex == null || latex.trim().isEmpty()) return;

            boolean isDisplay = isDisplayMath(latex);
            renderMath(visitor, latex.trim(), isDisplay);
        });

        // Handle block math nodes
        builder.on(JLatexMathBlock.class, (visitor, node) -> {
            String latex = node.latex();
            if (latex == null || latex.trim().isEmpty()) return;

            renderMath(visitor, latex.trim(), true);
        });
    }

    private boolean isDisplayMath(String latex) {
        if (latex == null) return false;
        String trimmed = latex.trim();
        return trimmed.contains("\n")
                || trimmed.startsWith("\\begin{")
                || trimmed.startsWith("\\displaystyle");
    }

    private void renderMath(MarkwonVisitor visitor, String latex, boolean isBlock) {
        float textSize = isBlock ? blockTextSizeProvider.getTextSizePx() : inlineTextSizeProvider.getTextSizePx();
        int textColor = colorProvider.getTextColor();

        try {
            JLatexMathDrawable drawable = JLatexMathDrawable.builder(latex)
                    .textSize(textSize)
                    .color(textColor)
                    .align(isBlock ? JLatexMathDrawable.ALIGN_CENTER : JLatexMathDrawable.ALIGN_LEFT)
                    .build();

            drawable.setBounds(0, 0, Math.max(1, drawable.getIntrinsicWidth()), Math.max(1, drawable.getIntrinsicHeight()));

            if (isBlock) {
                visitor.ensureNewLine();
                int start = visitor.length();
                visitor.builder().append('\uFFFC');
                visitor.setSpans(start, new LatexMathSpan(drawable, true));
                visitor.setSpans(start, new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER));
                visitor.ensureNewLine();
            } else {
                int start = visitor.length();
                visitor.builder().append('\uFFFC');
                visitor.setSpans(start, new LatexMathSpan(drawable, false));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to render LaTeX formula: `" + latex + "`", t);
            // Monospace fallback so equations are never rendered as blank or invisible
            if (isBlock) {
                visitor.ensureNewLine();
                int start = visitor.length();
                visitor.builder().append(latex);
                visitor.setSpans(start, new TypefaceSpan("monospace"));
                visitor.setSpans(start, new StyleSpan(Typeface.ITALIC));
                visitor.setSpans(start, new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER));
                visitor.ensureNewLine();
            } else {
                int start = visitor.length();
                visitor.builder().append("$").append(latex).append("$");
                visitor.setSpans(start, new TypefaceSpan("monospace"));
                visitor.setSpans(start, new StyleSpan(Typeface.ITALIC));
            }
        }
    }

    /**
     * InlineProcessor that matches double dollar $$...$$ and single dollar $...$ formulas.
     */
    public static class SynchronousLatexInlineProcessor extends InlineProcessor {
        private static final Pattern DOUBLE_DOLLAR = Pattern.compile("(\\${2})([\\s\\S]+?)\\1");
        private static final Pattern SINGLE_DOLLAR = Pattern.compile("(\\$)((?!\\$)[^\\n]+?)(?<!\\\\)\\$");
        private static final Pattern CURRENCY_PATTERN = Pattern.compile("^\\d[\\d,.]*$");

        @Override
        public char specialCharacter() {
            return '$';
        }

        @Override
        protected Node parse() {
            // 1. Double dollar $$...$$
            String doubleMatch = match(DOUBLE_DOLLAR);
            if (doubleMatch != null) {
                String content = doubleMatch.substring(2, doubleMatch.length() - 2);
                JLatexMathNode node = new JLatexMathNode();
                node.latex(content);
                return node;
            }

            // 2. Single dollar $...$
            int prevIndex = index;
            String singleMatch = match(SINGLE_DOLLAR);
            if (singleMatch != null) {
                String content = singleMatch.substring(1, singleMatch.length() - 1).trim();
                if (CURRENCY_PATTERN.matcher(content).matches()) {
                    // Currency like $100 or $5.50 - rollback index
                    index = prevIndex;
                    return null;
                }
                JLatexMathNode node = new JLatexMathNode();
                node.latex(content);
                return node;
            }

            return null;
        }
    }

    /**
     * Block parser for standalone multiline display equations wrapped in $$ ... $$.
     */
    public static class LatexBlockParser extends AbstractBlockParser {
        private final JLatexMathBlock block = new JLatexMathBlock();
        private final StringBuilder builder = new StringBuilder();
        private final int signs;

        LatexBlockParser(int signs) {
            this.signs = signs;
        }

        @Override
        public Block getBlock() {
            return block;
        }

        @Override
        public BlockContinue tryContinue(ParserState state) {
            int nextNonSpace = state.getNextNonSpaceIndex();
            CharSequence line = state.getLine();
            int length = line.length();
            if (state.getIndent() < 4) {
                int match = consume('$', line, nextNonSpace, length);
                if (match == signs) {
                    int after = skip(' ', line, nextNonSpace + signs, length);
                    if (after == length) {
                        return BlockContinue.finished();
                    }
                }
            }
            return BlockContinue.atIndex(state.getIndex());
        }

        @Override
        public void addLine(CharSequence line) {
            builder.append(line).append('\n');
        }

        @Override
        public void closeBlock() {
            block.latex(builder.toString());
        }

        public static class Factory extends AbstractBlockParserFactory {
            @Override
            public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
                if (state.getIndent() >= 4) {
                    return BlockStart.none();
                }
                int nextNonSpace = state.getNextNonSpaceIndex();
                CharSequence line = state.getLine();
                int length = line.length();
                int match = consume('$', line, nextNonSpace, length);
                if (match < 2) {
                    return BlockStart.none();
                }
                int after = skip(' ', line, nextNonSpace + match, length);
                if (after != length) {
                    return BlockStart.none();
                }
                return BlockStart.of(new LatexBlockParser(match)).atIndex(length + 1);
            }
        }

        private static int consume(char c, CharSequence s, int start, int end) {
            int i = start;
            while (i < end && s.charAt(i) == c) {
                i++;
            }
            return i - start;
        }

        private static int skip(char c, CharSequence s, int start, int end) {
            int i = start;
            while (i < end && s.charAt(i) == c) {
                i++;
            }
            return i;
        }
    }
}
