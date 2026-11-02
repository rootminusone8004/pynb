package com.pynb.app.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.pynb.app.kernel.TermuxKernel;
import com.pynb.app.model.CellOutput;
import com.pynb.app.model.Notebook;
import com.pynb.app.model.NotebookCell;
import com.pynb.app.parser.NotebookParser;
import com.pynb.app.parser.NotebookWriter;
import com.pynb.app.util.RecentFilesManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    // Execution & saving state
    private final ExecutorService runExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentLinkedQueue<Integer> changedCells = new ConcurrentLinkedQueue<>();
    private final MutableLiveData<Long> cellsChanged = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> dirty = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private volatile boolean cancelQueued = false;
    private long changeCounter = 0;

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

    /** Ticks whenever cells changed; drain the indices with {@link #drainChangedCells()}. */
    public LiveData<Long> getCellsChanged() {
        return cellsChanged;
    }

    public List<Integer> drainChangedCells() {
        List<Integer> out = new ArrayList<>();
        Integer i;
        while ((i = changedCells.poll()) != null) {
            if (!out.contains(i)) out.add(i);
        }
        return out;
    }

    /** True when executed outputs have not been saved yet. */
    public LiveData<Boolean> getDirty() {
        return dirty;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    /** One-shot user messages (toasts); call {@link #clearMessage()} after showing. */
    public LiveData<String> getMessage() {
        return message;
    }

    public void clearMessage() {
        message.setValue(null);
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

    // ------------------------------------------------------------------ execution

    /** Queues one code cell for execution on the connected Termux kernel. */
    public void runCell(int index) {
        runCells(Collections.singletonList(index), true);
    }

    /** Queues every code cell, in order; stops at the first error like Jupyter's "Run all". */
    public void runAll() {
        Notebook nb = notebook.getValue();
        if (nb == null) return;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < nb.getCells().size(); i++) {
            if (nb.getCells().get(i).isCode()) indices.add(i);
        }
        runCells(indices, true);
    }

    private void runCells(List<Integer> indices, boolean stopOnError) {
        Notebook nb = notebook.getValue();
        if (nb == null || indices.isEmpty()) return;
        cancelQueued = false;
        for (int index : indices) {
            NotebookCell cell = nb.getCells().get(index);
            if (cell.getRunState() == NotebookCell.RunState.IDLE) {
                cell.setRunState(NotebookCell.RunState.QUEUED);
                markChanged(index);
            }
        }
        runExecutor.execute(() -> {
            for (int k = 0; k < indices.size(); k++) {
                int index = indices.get(k);
                NotebookCell cell = nb.getCells().get(index);
                if (cancelQueued) {
                    cell.setRunState(NotebookCell.RunState.IDLE);
                    markChanged(index);
                    continue;
                }
                cell.setRunState(NotebookCell.RunState.RUNNING);
                markChanged(index);
                boolean ok = executeCell(cell);
                cell.setRunState(NotebookCell.RunState.IDLE);
                markChanged(index);
                if (!ok && stopOnError) {
                    cancelQueued = true;
                }
            }
        });
    }

    private boolean executeCell(NotebookCell cell) {
        try {
            JSONObject reply = TermuxKernel.get(getApplication()).execute(cell.getSource());
            List<CellOutput> outputs = new ArrayList<>();
            JSONArray array = reply.optJSONArray("outputs");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject out = array.optJSONObject(i);
                    if (out != null) outputs.add(NotebookParser.parseOutput(out));
                }
            }
            cell.setOutputs(outputs);
            cell.setExecutionCount(reply.has("execution_count") ? reply.optInt("execution_count") : null);
            cell.setModified(true);
            dirty.postValue(true);
            return "ok".equals(reply.optString("status"));
        } catch (Exception e) {
            android.util.Log.w("NotebookViewer", "Cell execution failed", e);
            message.postValue("Run failed: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return false;
        }
    }

    // ------------------------------------------------------------------ editing

    /** Replaces a cell's source after editing; outputs are kept, like in Jupyter. */
    public void updateCellSource(int index, String newSource) {
        Notebook nb = notebook.getValue();
        if (nb == null || index < 0 || index >= nb.getCells().size()) return;
        NotebookCell cell = nb.getCells().get(index);
        String source = newSource != null ? newSource : "";
        if (source.equals(cell.getSource())) return;
        cell.setSource(source);
        cell.setSourceModified(true);
        dirty.setValue(true);
        markChanged(index);
    }

    /** Interrupts the running cell and drops the queued ones. */
    public void interrupt() {
        cancelQueued = true;
        TermuxKernel.get(getApplication()).interrupt();
    }

    private void markChanged(int index) {
        changedCells.add(index);
        synchronized (this) {
            cellsChanged.postValue(++changeCounter);
        }
    }

    // ------------------------------------------------------------------ saving

    /** Writes the notebook (with executed outputs) to {@code uri}. */
    public void save(Uri uri, @androidx.annotation.Nullable Runnable onSuccess) {
        Notebook nb = notebook.getValue();
        if (nb == null || uri == null) return;
        saving.setValue(true);
        executor.execute(() -> {
            try {
                String json = NotebookWriter.write(nb);
                try (OutputStream os = getApplication().getContentResolver().openOutputStream(uri, "wt")) {
                    if (os == null) throw new IllegalStateException("Could not open the file for writing");
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                for (NotebookCell cell : nb.getCells()) {
                    if (cell.isModified() || cell.isSourceModified()) {
                        cell.setRawJson(NotebookWriter.cellJson(cell));
                        cell.setModified(false);
                        cell.setSourceModified(false);
                    }
                }
                dirty.postValue(false);
                message.postValue("Notebook saved");
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
            } catch (Exception e) {
                android.util.Log.e("NotebookViewer", "Save failed", e);
                message.postValue("Could not save: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            } finally {
                saving.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
        runExecutor.shutdown();
    }
}
