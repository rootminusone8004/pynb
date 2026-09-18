package com.pynb.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.LruCache;

/**
 * In-memory LRU bitmap cache for decoded notebook output images.
 * Ensures butter-smooth RecyclerView scrolling without repetitive base64 decoding.
 */
public class ImageCache {

    private static ImageCache instance;
    private final LruCache<String, Bitmap> memoryCache;

    private ImageCache() {
        // Use 1/8th of available runtime memory for image cache
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;

        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public static synchronized ImageCache getInstance() {
        if (instance == null) {
            instance = new ImageCache();
        }
        return instance;
    }

    public Bitmap getBitmap(String key) {
        if (key == null) return null;
        return memoryCache.get(key);
    }

    public void putBitmap(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) {
            memoryCache.put(key, bitmap);
        }
    }

    public void clear() {
        memoryCache.evictAll();
    }

    /**
     * Safely decodes a base64 encoded image string into a Bitmap.
     */
    public static Bitmap decodeBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            // Strip data:image/png;base64, prefix if present
            String clean = base64;
            int commaIndex = clean.indexOf(",");
            if (commaIndex != -1 && clean.substring(0, commaIndex).contains("base64")) {
                clean = clean.substring(commaIndex + 1);
            }
            clean = clean.trim();
            byte[] decodedBytes = Base64.decode(clean, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
