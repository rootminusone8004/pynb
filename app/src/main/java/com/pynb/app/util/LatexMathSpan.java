package com.pynb.app.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ru.noties.jlatexmath.JLatexMathDrawable;

/**
 * A synchronous ReplacementSpan that renders a LaTeX formula (JLatexMathDrawable)
 * directly during text layout and rendering without asynchronous loaders, callbacks,
 * or attach/detach race conditions.
 *
 * <p>Inline formulas are placed so that the formula's own baseline sits on the text
 * baseline (using the TeX depth below the baseline), like MathJax does. Display formulas
 * get their own centred line with vertical padding. The drawable's bounds are expected to
 * already fit the available width (see {@link SynchronousLatexPlugin}).</p>
 */
public class LatexMathSpan extends ReplacementSpan {

    private final Drawable drawable;
    private final boolean isBlock;
    /** Distance (px) from the formula baseline to the bottom of the drawable. */
    private final int depth;

    public LatexMathSpan(@NonNull Drawable drawable, boolean isBlock, int depth) {
        this.drawable = drawable;
        this.isBlock = isBlock;
        if (drawable.getBounds().isEmpty()) {
            drawable.setBounds(0, 0, Math.max(1, drawable.getIntrinsicWidth()), Math.max(1, drawable.getIntrinsicHeight()));
        }
        this.depth = Math.max(0, Math.min(depth, drawable.getBounds().height()));
    }

    @NonNull
    public Drawable getDrawable() {
        return drawable;
    }

    public boolean isBlock() {
        return isBlock;
    }

    private int blockPadding(Paint paint) {
        return Math.max(8, Math.round(paint.getTextSize() * 0.35f));
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        Rect bounds = drawable.getBounds();
        int height = bounds.height();

        if (fm != null) {
            if (isBlock) {
                int pad = blockPadding(paint);
                fm.ascent = -height - pad;
                fm.descent = pad;
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            } else {
                int above = height - depth;
                if (-above < fm.ascent) fm.ascent = -above;
                if (depth > fm.descent) fm.descent = depth;
                fm.top = Math.min(fm.top, fm.ascent);
                fm.bottom = Math.max(fm.bottom, fm.descent);
            }
        }

        return bounds.width();
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        // Keep the formula colour in sync with the surrounding text (links, theme changes...)
        if (drawable instanceof JLatexMathDrawable) {
            try {
                ((JLatexMathDrawable) drawable).icon().setForeground(new ru.noties.jlatexmath.awt.Color(paint.getColor()));
            } catch (Throwable ignored) {
            }
        }

        int height = drawable.getBounds().height();
        float transY = isBlock ? y - height : y - (height - depth);

        canvas.save();
        canvas.translate(x, transY);
        drawable.draw(canvas);
        canvas.restore();
    }
}
