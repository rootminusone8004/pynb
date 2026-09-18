package com.pynb.app.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.pynb.app.model.Notebook;
import com.pynb.app.model.NotebookCell;
import com.pynb.app.parser.NotebookParser;
import com.pynb.app.util.RecentFilesManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for NotebookViewerActivity managing async notebook loading,
 * in-notebook cell search, and rendering settings.
 */
public class NotebookViewerViewModel extends AndroidViewModel {

    private final RecentFilesManager recentFilesManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Notebook> notebook = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    // Search state
    private final MutableLiveData<List<Integer>> searchMatches = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> currentMatchIndex = new MutableLiveData<>(-1);
    private final MutableLiveData<String> matchCountText = new MutableLiveData<>("0/0");

    // Formatting state
    private final MutableLiveData<Boolean> showLineNumbers = new MutableLiveData<>(true);
    private final MutableLiveData<Float> fontScale = new MutableLiveData<>(1.0f);

    public NotebookViewerViewModel(@NonNull Application application) {
        super(application);
        this.recentFilesManager = new RecentFilesManager(application);
    }

    public LiveData<Notebook> getNotebook() {
        return notebook;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<List<Integer>> getSearchMatches() {
        return searchMatches;
    }

    public LiveData<Integer> getCurrentMatchIndex() {
        return currentMatchIndex;
    }

    public LiveData<String> getMatchCountText() {
        return matchCountText;
    }

    public LiveData<Boolean> getShowLineNumbers() {
        return showLineNumbers;
    }

    public LiveData<Float> getFontScale() {
        return fontScale;
    }

    public void setShowLineNumbers(boolean show) {
        showLineNumbers.setValue(show);
    }

    public void setFontScale(float scale) {
        fontScale.setValue(Math.max(0.6f, Math.min(scale, 1.8f)));
    }

    public void loadNotebook(Uri targetUri, String targetAssetPath, String notebookTitle) {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                InputStream is;
                if (targetAssetPath != null) {
                    is = getApplication().getAssets().open(targetAssetPath);
                } else if (targetUri != null) {
                    is = getApplication().getContentResolver().openInputStream(targetUri);
                } else {
                    throw new IllegalArgumentException("No file URI or asset path provided");
                }

                if (is == null) {
                    throw new IllegalArgumentException("Could not open file input stream");
                }

                Notebook nb = NotebookParser.parse(is);
                if (notebookTitle != null) {
                    nb.setTitle(notebookTitle);
                }

                // Record in Recent Files
                String uriKey = targetAssetPath != null ? "asset:" + targetAssetPath : (targetUri != null ? targetUri.toString() : null);
                if (uriKey != null) {
                    recentFilesManager.addRecentItem(nb.getTitle(), uriKey, nb.getTotalCells(), targetAssetPath != null);
                }

                isLoading.postValue(false);
                notebook.postValue(nb);

            } catch (Exception e) {
                android.util.Log.e("NotebookViewer", "Error loading notebook", e);
                isLoading.postValue(false);
                errorMessage.postValue(e.getMessage() != null ? e.getMessage() : "Unknown error (" + e.getClass().getSimpleName() + ")");
            }
        });
    }

    public void search(String query) {
        Notebook nb = notebook.getValue();
        if (query == null || query.trim().isEmpty() || nb == null) {
            searchMatches.setValue(new ArrayList<>());
            currentMatchIndex.setValue(-1);
            matchCountText.setValue("0/0");
            return;
        }

        String lowerQuery = query.toLowerCase();
        List<NotebookCell> cells = nb.getCells();
        List<Integer> matches = new ArrayList<>();

        for (int i = 0; i < cells.size(); i++) {
            NotebookCell cell = cells.get(i);
            if (cell.getSource() != null && cell.getSource().toLowerCase().contains(lowerQuery)) {
                matches.add(i);
            }
        }

        searchMatches.setValue(matches);
        if (!matches.isEmpty()) {
            currentMatchIndex.setValue(0);
            matchCountText.setValue("1/" + matches.size());
        } else {
            currentMatchIndex.setValue(-1);
            matchCountText.setValue("0/0");
        }
    }

    public int nextMatch() {
        List<Integer> matches = searchMatches.getValue();
        if (matches == null || matches.isEmpty()) return -1;

        int current = currentMatchIndex.getValue() != null ? currentMatchIndex.getValue() : -1;
        int next = (current + 1) % matches.size();
        currentMatchIndex.setValue(next);
        matchCountText.setValue((next + 1) + "/" + matches.size());
        return matches.get(next);
    }

    public int prevMatch() {
        List<Integer> matches = searchMatches.getValue();
        if (matches == null || matches.isEmpty()) return -1;

        int current = currentMatchIndex.getValue() != null ? currentMatchIndex.getValue() : 0;
        int prev = (current - 1 + matches.size()) % matches.size();
        currentMatchIndex.setValue(prev);
        matchCountText.setValue((prev + 1) + "/" + matches.size());
        return matches.get(prev);
    }

    public void clearSearch() {
        searchMatches.setValue(new ArrayList<>());
        currentMatchIndex.setValue(-1);
        matchCountText.setValue("0/0");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
