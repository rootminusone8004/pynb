package com.pynb.app.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * Touch-enabled ImageView with pinch-to-zoom, pan, and double-tap zoom capabilities.
 */
public class ZoomableImageView extends AppCompatImageView {

    private final Matrix matrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;

    private final PointF lastTouch = new PointF();
    private final PointF startTouch = new PointF();

    private float minScale = 1f;
    private float maxScale = 5f;
    private float saveScale = 1f;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public ZoomableImageView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        super.setClickable(true);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(curr);
                startTouch.set(lastTouch);
                mode = DRAG;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG && saveScale > minScale) {
                    float deltaX = curr.x - lastTouch.x;
                    float deltaY = curr.y - lastTouch.y;
                    matrix.postTranslate(deltaX, deltaY);
                    fixTranslation();
                    lastTouch.set(curr.x, curr.y);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
        }

        setImageMatrix(matrix);
        invalidate();
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = detector.getScaleFactor();
            float origScale = saveScale;
            saveScale *= mScaleFactor;

            if (saveScale > maxScale) {
                saveScale = maxScale;
                mScaleFactor = maxScale / origScale;
            } else if (saveScale < minScale) {
                saveScale = minScale;
                mScaleFactor = minScale / origScale;
            }

            if (getDrawable() != null) {
                matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
                fixTranslation();
            }
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (saveScale > minScale) {
                // Reset to normal
                saveScale = minScale;
                fitImageToView();
            } else {
                // Zoom in 2.5x
                saveScale = 2.5f;
                matrix.postScale(2.5f, 2.5f, e.getX(), e.getY());
                fixTranslation();
            }
            setImageMatrix(matrix);
            invalidate();
            return true;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        fitImageToView();
    }

    public void resetZoom() {
        saveScale = minScale;
        fitImageToView();
        setImageMatrix(matrix);
        invalidate();
    }

    private void fitImageToView() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;

        int bmWidth = getDrawable().getIntrinsicWidth();
        int bmHeight = getDrawable().getIntrinsicHeight();

        if (bmWidth <= 0 || bmHeight <= 0) return;

        float scaleX = (float) getWidth() / (float) bmWidth;
        float scaleY = (float) getHeight() / (float) bmHeight;
        float scale = Math.min(scaleX, scaleY);

        matrix.setScale(scale, scale);

        float redundantYSpace = (float) getHeight() - (scale * (float) bmHeight);
        float redundantXSpace = (float) getWidth() - (scale * (float) bmWidth);
        redundantYSpace /= 2;
        redundantXSpace /= 2;

        matrix.postTranslate(redundantXSpace, redundantYSpace);
        saveScale = 1f;
        setImageMatrix(matrix);
    }

    private void fixTranslation() {
        if (getDrawable() == null) return;
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        float fixTransX = getFixTranslation(transX, getWidth(), getDrawable().getIntrinsicWidth() * saveScale);
        float fixTransY = getFixTranslation(transY, getHeight(), getDrawable().getIntrinsicHeight() * saveScale);

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private float getFixTranslation(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;
        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans) return -trans + minTrans;
        if (trans > maxTrans) return -trans + maxTrans;
        return 0;
    }
}
