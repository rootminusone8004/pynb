package com.pynb.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages recently opened folder workspaces using SharedPreferences.
 */
public class RecentFoldersManager {

    private static final String PREF_NAME = "pynb_recent_folders";
    private static final String KEY_FOLDERS = "recent_folder_list";
    private static final int MAX_FOLDERS = 15;

    public static class RecentFolder implements Serializable {
        private final String name;
        private final String uriString;
        private final long timestamp;

        public RecentFolder(String name, String uriString, long timestamp) {
            this.name = name;
            this.uriString = uriString;
            this.timestamp = timestamp;
        }

        public String getName() { return name; }
        public String getUriString() { return uriString; }
        public long getTimestamp() { return timestamp; }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("name", name);
                obj.put("uriString", uriString);
                obj.put("timestamp", timestamp);
                return obj;
            } catch (Exception e) {
                return null;
            }
        }

        public static RecentFolder fromJson(JSONObject obj) {
            try {
                return new RecentFolder(
                        obj.optString("name", "Folder"),
                        obj.optString("uriString", ""),
                        obj.optLong("timestamp", System.currentTimeMillis())
                );
            } catch (Exception e) {
                return null;
            }
        }
    }

    private final SharedPreferences prefs;

    public RecentFoldersManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<RecentFolder> getRecentFolders() {
        List<RecentFolder> list = new ArrayList<>();
        String json = prefs.getString(KEY_FOLDERS, null);
        if (json == null || json.isEmpty()) return list;

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                RecentFolder folder = RecentFolder.fromJson(obj);
                if (folder != null && !folder.getUriString().isEmpty()) {
                    list.add(folder);
                }
            }
        } catch (Exception ignored) {}

        return list;
    }

    public void addRecentFolder(String name, String uriString) {
        if (uriString == null || uriString.isEmpty()) return;

        List<RecentFolder> current = getRecentFolders();
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getUriString().equals(uriString)) {
                current.remove(i);
                break;
            }
        }

        current.add(0, new RecentFolder(name, uriString, System.currentTimeMillis()));

        while (current.size() > MAX_FOLDERS) {
            current.remove(current.size() - 1);
        }

        saveList(current);
    }

    public void removeRecentFolder(String uriString) {
        List<RecentFolder> current = getRecentFolders();
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getUriString().equals(uriString)) {
                current.remove(i);
                saveList(current);
                break;
            }
        }
    }

    public void clearAll() {
        prefs.edit().remove(KEY_FOLDERS).apply();
    }

    private void saveList(List<RecentFolder> items) {
        JSONArray array = new JSONArray();
        for (RecentFolder item : items) {
            JSONObject obj = item.toJson();
            if (obj != null) {
                array.put(obj);
            }
        }
        prefs.edit().putString(KEY_FOLDERS, array.toString()).apply();
    }
}
