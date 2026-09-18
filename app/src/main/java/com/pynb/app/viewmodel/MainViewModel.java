package com.pynb.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.pynb.app.util.RecentFilesManager;
import com.pynb.app.util.RecentFilesManager.RecentItem;
import com.pynb.app.util.RecentFoldersManager;
import com.pynb.app.util.RecentFoldersManager.RecentFolder;

import java.util.List;

/**
 * ViewModel for MainActivity managing state for recent files and recent folders.
 */
public class MainViewModel extends AndroidViewModel {

    private final RecentFilesManager recentFilesManager;
    private final RecentFoldersManager recentFoldersManager;

    private final MutableLiveData<List<RecentFolder>> recentFolders = new MutableLiveData<>();
    private final MutableLiveData<List<RecentItem>> recentFiles = new MutableLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.recentFilesManager = new RecentFilesManager(application);
        this.recentFoldersManager = new RecentFoldersManager(application);
        loadRecents();
    }

    public LiveData<List<RecentFolder>> getRecentFolders() {
        return recentFolders;
    }

    public LiveData<List<RecentItem>> getRecentFiles() {
        return recentFiles;
    }

    public void loadRecents() {
        recentFolders.setValue(recentFoldersManager.getRecentFolders());
        recentFiles.setValue(recentFilesManager.getRecentItems());
    }

    public void removeRecentFolder(String uriString) {
        recentFoldersManager.removeRecentFolder(uriString);
        recentFolders.setValue(recentFoldersManager.getRecentFolders());
    }

    public void removeRecentFile(String uriString) {
        recentFilesManager.removeRecentItem(uriString);
        recentFiles.setValue(recentFilesManager.getRecentItems());
    }

    public void clearAllRecentFolders() {
        recentFoldersManager.clearAll();
        recentFolders.setValue(recentFoldersManager.getRecentFolders());
    }

    public void clearAllRecentFiles() {
        recentFilesManager.clearAll();
        recentFiles.setValue(recentFilesManager.getRecentItems());
    }
}
