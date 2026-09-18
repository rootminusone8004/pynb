package com.pynb.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages recently opened notebook files using SharedPreferences.
 */
public class RecentFilesManager {

    private static final String PREF_NAME = "pynb_recent_files";
    private static final String KEY_RECENT = "recent_list";
    private static final int MAX_RECENT = 25;

    public static class RecentItem implements Serializable {
        private String title;
        private String uriString;
        private int cellCount;
        private long timestamp;
        private boolean isSample;

        public RecentItem(String title, String uriString, int cellCount, long timestamp, boolean isSample) {
            this.title = title;
            this.uriString = uriString;
            this.cellCount = cellCount;
            this.timestamp = timestamp;
            this.isSample = isSample;
        }

        public String getTitle() { return title; }
        public String getUriString() { return uriString; }
        public int getCellCount() { return cellCount; }
        public long getTimestamp() { return timestamp; }
        public boolean isSample() { return isSample; }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("title", title);
                obj.put("uriString", uriString);
                obj.put("cellCount", cellCount);
                obj.put("timestamp", timestamp);
                obj.put("isSample", isSample);
                return obj;
            } catch (Exception e) {
                return null;
            }
        }

        public static RecentItem fromJson(JSONObject obj) {
            try {
                return new RecentItem(
                        obj.optString("title", "Untitled"),
                        obj.optString("uriString", ""),
                        obj.optInt("cellCount", 0),
                        obj.optLong("timestamp", System.currentTimeMillis()),
                        obj.optBoolean("isSample", false)
                );
            } catch (Exception e) {
                return null;
            }
        }
    }

    private final SharedPreferences prefs;

    public RecentFilesManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<RecentItem> getRecentItems() {
        List<RecentItem> list = new ArrayList<>();
        String json = prefs.getString(KEY_RECENT, null);
        if (json == null || json.isEmpty()) return list;

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                RecentItem item = RecentItem.fromJson(obj);
                if (item != null && !item.getUriString().isEmpty()) {
                    list.add(item);
                }
            }
        } catch (Exception ignored) {}

        return list;
    }

    public void addRecentItem(String title, String uriString, int cellCount, boolean isSample) {
        if (uriString == null || uriString.isEmpty()) return;

        List<RecentItem> current = getRecentItems();
        // Remove existing item with same uri
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getUriString().equals(uriString)) {
                current.remove(i);
                break;
            }
        }

        // Add to front
        current.add(0, new RecentItem(title, uriString, cellCount, System.currentTimeMillis(), isSample));

        // Trim to MAX_RECENT
        while (current.size() > MAX_RECENT) {
            current.remove(current.size() - 1);
        }

        saveList(current);
    }

    public void removeRecentItem(String uriString) {
        List<RecentItem> current = getRecentItems();
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getUriString().equals(uriString)) {
                current.remove(i);
                saveList(current);
                break;
            }
        }
    }

    public void clearAll() {
        prefs.edit().remove(KEY_RECENT).apply();
    }

    private void saveList(List<RecentItem> items) {
        JSONArray array = new JSONArray();
        for (RecentItem item : items) {
            JSONObject obj = item.toJson();
            if (obj != null) {
                array.put(obj);
            }
        }
        prefs.edit().putString(KEY_RECENT, array.toString()).apply();
    }
}
