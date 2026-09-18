package com.pynb.app.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.pynb.app.model.FolderItem;
import com.pynb.app.util.RecentFoldersManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for FolderBrowserActivity managing directory traversal,
 * file filtering, search queries, and breadcrumbs.
 */
public class FolderBrowserViewModel extends AndroidViewModel {

    private final RecentFoldersManager recentFoldersManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Uri rootTreeUri;
    private final List<DocumentFile> dirStack = new ArrayList<>();
    private final List<FolderItem> allLoadedItems = new ArrayList<>();

    private final MutableLiveData<List<FolderItem>> displayItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<DocumentFile>> breadcrumbs = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> currentTitle = new MutableLiveData<>("Workspace");
    private final MutableLiveData<String> currentSubtitle = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> showNotebooksOnly = new MutableLiveData<>(true);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    public FolderBrowserViewModel(@NonNull Application application) {
        super(application);
        this.recentFoldersManager = new RecentFoldersManager(application);
    }

    public LiveData<List<FolderItem>> getDisplayItems() {
        return displayItems;
    }

    public LiveData<List<DocumentFile>> getBreadcrumbs() {
        return breadcrumbs;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getCurrentTitle() {
        return currentTitle;
    }

    public LiveData<String> getCurrentSubtitle() {
        return currentSubtitle;
    }

    public LiveData<Boolean> getShowNotebooksOnly() {
        return showNotebooksOnly;
    }

    public LiveData<String> getSearchQuery() {
        return searchQuery;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public Uri getRootTreeUri() {
        return rootTreeUri;
    }

    public void initRootFolder(Uri treeUri) {
        if (treeUri == null) {
            errorMessage.setValue("Invalid folder URI");
            return;
        }

        this.rootTreeUri = treeUri;
        DocumentFile rootDoc = DocumentFile.fromTreeUri(getApplication(), treeUri);
        if (rootDoc == null || !rootDoc.exists()) {
            errorMessage.setValue("Folder is not accessible");
            return;
        }

        dirStack.clear();
        dirStack.add(rootDoc);

        // Record in Recent Folders
        String folderName = rootDoc.getName() != null ? rootDoc.getName() : "Folder Workspace";
        recentFoldersManager.addRecentFolder(folderName, treeUri.toString());

        loadCurrentDirectory();
    }

    public void switchFolder(Uri newTreeUri) {
        initRootFolder(newTreeUri);
    }

    public void openSubFolder(DocumentFile folder) {
        if (folder != null) {
            dirStack.add(folder);
            loadCurrentDirectory();
        }
    }

    /**
     * Handles back navigation in directory hierarchy.
     * @return true if ascended to parent directory; false if already at root (activity can finish).
     */
    public boolean navigateUp() {
        if (dirStack.size() > 1) {
            dirStack.remove(dirStack.size() - 1);
            loadCurrentDirectory();
            return true;
        }
        return false;
    }

    public void navigateToBreadcrumb(int index) {
        if (index >= 0 && index < dirStack.size() - 1) {
            while (dirStack.size() > index + 1) {
                dirStack.remove(dirStack.size() - 1);
            }
            loadCurrentDirectory();
        }
    }

    public void toggleFilterNotebooksOnly() {
        boolean current = Boolean.TRUE.equals(showNotebooksOnly.getValue());
        showNotebooksOnly.setValue(!current);
        applyFilterAndSearch();
    }

    public void setShowNotebooksOnly(boolean notebooksOnly) {
        showNotebooksOnly.setValue(notebooksOnly);
        applyFilterAndSearch();
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query != null ? query.trim().toLowerCase() : "");
        applyFilterAndSearch();
    }

    public void refresh() {
        loadCurrentDirectory();
    }

    private void loadCurrentDirectory() {
        if (dirStack.isEmpty()) return;

        DocumentFile currentDoc = dirStack.get(dirStack.size() - 1);
        String name = currentDoc.getName() != null ? currentDoc.getName() : "Workspace";
        currentTitle.setValue(name);
        breadcrumbs.setValue(new ArrayList<>(dirStack));

        isLoading.setValue(true);

        executor.execute(() -> {
            List<FolderItem> folderItems = new ArrayList<>();
            List<FolderItem> notebookItems = new ArrayList<>();
            List<FolderItem> otherItems = new ArrayList<>();

            try {
                DocumentFile[] files = currentDoc.listFiles();
                for (DocumentFile df : files) {
                    FolderItem item = FolderItem.fromDocumentFile(df);
                    if (item == null) continue;

                    if (item.isDirectory()) {
                        folderItems.add(item);
                    } else if (item.isNotebook()) {
                        notebookItems.add(item);
                    } else {
                        otherItems.add(item);
                    }
                }

                Collections.sort(folderItems, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(notebookItems, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                Collections.sort(otherItems, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            } catch (Exception ignored) {}

            List<FolderItem> combined = new ArrayList<>();
            combined.addAll(folderItems);
            combined.addAll(notebookItems);
            combined.addAll(otherItems);

            int dirCount = folderItems.size();
            int nbCount = notebookItems.size();
            String sub = dirCount + " folders • " + nbCount + " notebooks";

            // Post back to LiveData on main thread
            isLoading.postValue(false);
            currentSubtitle.postValue(sub);

            synchronized (allLoadedItems) {
                allLoadedItems.clear();
                allLoadedItems.addAll(combined);
            }

            postFilterAndSearch();
        });
    }

    private void postFilterAndSearch() {
        boolean notebooksOnly = Boolean.TRUE.equals(showNotebooksOnly.getValue());
        String query = searchQuery.getValue() != null ? searchQuery.getValue() : "";

        List<FolderItem> displayList = new ArrayList<>();
        if (dirStack.size() > 1 && query.isEmpty()) {
            displayList.add(FolderItem.createParentItem());
        }

        synchronized (allLoadedItems) {
            for (FolderItem item : allLoadedItems) {
                if (!query.isEmpty() && !item.getName().toLowerCase().contains(query)) {
                    continue;
                }
                if (notebooksOnly && !item.isDirectory() && !item.isNotebook()) {
                    continue;
                }
                displayList.add(item);
            }
        }

        displayItems.postValue(displayList);
    }

    private void applyFilterAndSearch() {
        boolean notebooksOnly = Boolean.TRUE.equals(showNotebooksOnly.getValue());
        String query = searchQuery.getValue() != null ? searchQuery.getValue() : "";

        List<FolderItem> displayList = new ArrayList<>();
        if (dirStack.size() > 1 && query.isEmpty()) {
            displayList.add(FolderItem.createParentItem());
        }

        synchronized (allLoadedItems) {
            for (FolderItem item : allLoadedItems) {
                if (!query.isEmpty() && !item.getName().toLowerCase().contains(query)) {
                    continue;
                }
                if (notebooksOnly && !item.isDirectory() && !item.isNotebook()) {
                    continue;
                }
                displayList.add(item);
            }
        }

        displayItems.setValue(displayList);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
