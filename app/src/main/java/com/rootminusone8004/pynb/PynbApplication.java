package com.rootminusone8004.pynb;

import android.app.Application;
import android.util.Log;

import ru.noties.jlatexmath.JLatexMathAndroid;

/**
 * Application class for Pynb.
 * Ensures critical native libraries and font assets (such as JLatexMath)
 * are initialized deterministically on startup.
 */
public class PynbApplication extends Application {

    private static final String TAG = "PynbApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            JLatexMathAndroid.init(this);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize JLatexMathAndroid", t);
        }
    }
}
