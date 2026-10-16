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
 */
public class LatexMathSpan extends ReplacementSpan {

    private final Drawable drawable;
    private final boolean isBlock;

    public LatexMathSpan(@NonNull Drawable drawable, boolean isBlock) {
        this.drawable = drawable;
        this.isBlock = isBlock;
        if (drawable.getBounds().isEmpty()) {
            drawable.setBounds(0, 0, Math.max(1, drawable.getIntrinsicWidth()), Math.max(1, drawable.getIntrinsicHeight()));
        }
    }

    @NonNull
    public Drawable getDrawable() {
        return drawable;
    }

    public boolean isBlock() {
        return isBlock;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        Rect bounds = drawable.getBounds();
        if (bounds.isEmpty()) {
            drawable.setBounds(0, 0, Math.max(1, drawable.getIntrinsicWidth()), Math.max(1, drawable.getIntrinsicHeight()));
            bounds = drawable.getBounds();
        }

        int drawableHeight = bounds.height();

        if (fm != null) {
            if (isBlock) {
                // Block formula: give adequate top and bottom padding like standard LaTeX display math
                int pad = Math.max(8, Math.round(paint.getTextSize() * 0.35f));
                fm.ascent = -drawableHeight - pad;
                fm.descent = pad;
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            } else {
                // Inline formula: vertically center with the text baseline
                int fontHeight = fm.descent - fm.ascent;
                if (drawableHeight > fontHeight) {
                    int extra = drawableHeight - fontHeight;
                    fm.ascent -= extra / 2;
                    fm.descent += (extra - extra / 2);
                    fm.top = Math.min(fm.top, fm.ascent);
                    fm.bottom = Math.max(fm.bottom, fm.descent);
                }
            }
        }

        return bounds.width();
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        canvas.save();

        // Update LaTeX foreground color dynamically to match the current Paint text color
        if (drawable instanceof JLatexMathDrawable) {
            try {
                ((JLatexMathDrawable) drawable).icon().setForeground(new ru.noties.jlatexmath.awt.Color(paint.getColor()));
            } catch (Throwable ignored) {
            }
        }

        Rect bounds = drawable.getBounds();
        int h = bounds.height();
        int w = bounds.width();

        float transY;
        if (isBlock) {
            int pad = Math.max(8, Math.round(paint.getTextSize() * 0.35f));
            transY = y - h - (pad / 2f);
        } else {
            // Center vertically within the line bounding box
            int lineHeight = bottom - top;
            transY = top + (lineHeight - h) / 2f;
        }

        // Auto-scale down if formula exceeds available canvas width
        int canvasWidth = canvas.getWidth();
        if (canvasWidth > 48 && w > (canvasWidth - 48)) {
            float scale = (float) (canvasWidth - 48) / (float) w;
            canvas.scale(scale, scale, x, transY);
        }

        canvas.translate(x, transY);
        drawable.draw(canvas);
        canvas.restore();
    }
}
