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

    /** Maximum width (px) a formula may occupy; formulas wider than this are scaled down. */
    public interface WidthProvider {
        int getMaxWidthPx();
    }

    /** Inline node produced by {@code $$...$$} written in the middle of a line: rendered as display math. */
    public static class DisplayLatexNode extends JLatexMathNode {
    }

    private final TextSizeProvider inlineTextSizeProvider;
    private final TextSizeProvider blockTextSizeProvider;
    private final ColorProvider colorProvider;
    private final WidthProvider widthProvider;

    public static SynchronousLatexPlugin create(
            @NonNull Context context,
            @NonNull TextSizeProvider inlineTextSizeProvider,
            @NonNull TextSizeProvider blockTextSizeProvider,
            @NonNull ColorProvider colorProvider,
            @NonNull WidthProvider widthProvider) {
        try {
            JLatexMathAndroid.init(context.getApplicationContext());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init JLatexMathAndroid in plugin factory", t);
        }
        return new SynchronousLatexPlugin(inlineTextSizeProvider, blockTextSizeProvider, colorProvider, widthProvider);
    }

    private SynchronousLatexPlugin(
            @NonNull TextSizeProvider inlineTextSizeProvider,
            @NonNull TextSizeProvider blockTextSizeProvider,
            @NonNull ColorProvider colorProvider,
            @NonNull WidthProvider widthProvider) {
        this.inlineTextSizeProvider = inlineTextSizeProvider;
        this.blockTextSizeProvider = blockTextSizeProvider;
        this.colorProvider = colorProvider;
        this.widthProvider = widthProvider;
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
        // $...$ : inline math, sits on the text baseline
        builder.on(JLatexMathNode.class, (visitor, node) -> {
            String latex = node.latex();
            if (latex == null || latex.trim().isEmpty()) return;
            renderMath(visitor, latex, false);
        });

        // $$...$$ in the middle of a line: display math inside a paragraph
        builder.on(DisplayLatexNode.class, (visitor, node) -> {
            String latex = node.latex();
            if (latex == null || latex.trim().isEmpty()) return;
            renderMath(visitor, latex, true);
        });

        // Fenced $$ block on its own lines
        builder.on(JLatexMathBlock.class, (visitor, node) -> {
            String latex = node.latex();
            if (latex == null || latex.trim().isEmpty()) return;
            visitor.blockStart(node);
            renderMath(visitor, latex, true);
            visitor.blockEnd(node);
        });
    }

    private void renderMath(MarkwonVisitor visitor, String rawLatex, boolean isBlock) {
        String latex = MathFormatter.normalizeLatex(rawLatex);
        if (latex.isEmpty()) return;

        float textSize = isBlock ? blockTextSizeProvider.getTextSizePx() : inlineTextSizeProvider.getTextSizePx();
        int textColor = colorProvider.getTextColor();

        try {
            JLatexMathDrawable drawable = JLatexMathDrawable.builder(latex)
                    .textSize(textSize)
                    .color(textColor)
                    .align(isBlock ? JLatexMathDrawable.ALIGN_CENTER : JLatexMathDrawable.ALIGN_LEFT)
                    .build();

            int width = Math.max(1, drawable.getIntrinsicWidth());
            int height = Math.max(1, drawable.getIntrinsicHeight());
            int depth;
            try {
                depth = Math.max(0, Math.min(height, drawable.icon().getIconDepth()));
            } catch (Throwable t) {
                depth = height / 2;
            }

            // Scale wide formulas down to the available width (JLatexMathDrawable scales its
            // content to fit its bounds), instead of letting them overflow and get clipped.
            int maxWidth = widthProvider.getMaxWidthPx();
            if (maxWidth > 0 && width > maxWidth) {
                float scale = (float) maxWidth / width;
                width = maxWidth;
                height = Math.max(1, Math.round(height * scale));
                depth = Math.round(depth * scale);
            }
            drawable.setBounds(0, 0, width, height);

            int start;
            if (isBlock) {
                visitor.ensureNewLine();
                start = visitor.length();
                visitor.builder().append('\uFFFC');
                visitor.setSpans(start, new LatexMathSpan(drawable, true, depth));
                visitor.setSpans(start, new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER));
                visitor.ensureNewLine();
            } else {
                start = visitor.length();
                visitor.builder().append('\uFFFC');
                visitor.setSpans(start, new LatexMathSpan(drawable, false, depth));
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to render LaTeX formula: `" + latex + "`", t);
            // Monospace fallback so equations are never rendered as blank or invisible
            if (isBlock) {
                visitor.ensureNewLine();
                int start = visitor.length();
                visitor.builder().append(rawLatex.trim());
                visitor.setSpans(start, new TypefaceSpan("monospace"));
                visitor.setSpans(start, new StyleSpan(Typeface.ITALIC));
                visitor.setSpans(start, new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER));
                visitor.ensureNewLine();
            } else {
                int start = visitor.length();
                visitor.builder().append(rawLatex.trim());
                visitor.setSpans(start, new TypefaceSpan("monospace"));
                visitor.setSpans(start, new StyleSpan(Typeface.ITALIC));
            }
        }
    }

    /**
     * InlineProcessor for {@code $$...$$} (display) and {@code $...$} (inline) formulas.
     *
     * <p>Markwon's {@code match()} uses {@link java.util.regex.Matcher#find()}, so the patterns
     * must be anchored with {@code ^}; otherwise a match further along the paragraph would be
     * accepted and the text in between silently dropped. Non-math dollars have already been
     * escaped by {@link MathFormatter}, so every {@code $} reaching here is a delimiter.</p>
     */
    public static class SynchronousLatexInlineProcessor extends InlineProcessor {
        private static final Pattern DOUBLE_DOLLAR = Pattern.compile("^\\$\\$((?:\\\\[\\s\\S]|[^\\\\$])+?)\\$\\$");
        private static final Pattern SINGLE_DOLLAR = Pattern.compile("^\\$((?:\\\\[\\s\\S]|[^\\\\$])+?)\\$");

        @Override
        public char specialCharacter() {
            return '$';
        }

        @Override
        protected Node parse() {
            String doubleMatch = match(DOUBLE_DOLLAR);
            if (doubleMatch != null) {
                DisplayLatexNode node = new DisplayLatexNode();
                node.latex(doubleMatch.substring(2, doubleMatch.length() - 2));
                return node;
            }

            String singleMatch = match(SINGLE_DOLLAR);
            if (singleMatch != null) {
                JLatexMathNode node = new JLatexMathNode();
                node.latex(singleMatch.substring(1, singleMatch.length() - 1));
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
