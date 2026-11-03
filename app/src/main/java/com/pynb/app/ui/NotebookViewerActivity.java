package com.pynb.app.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pynb.app.R;
import com.pynb.app.adapter.NotebookAdapter;
import com.pynb.app.databinding.ActivityNotebookViewerBinding;
import com.pynb.app.kernel.KernelState;
import com.pynb.app.kernel.KernelManager;
import com.pynb.app.kernel.KnownHosts;
import com.pynb.app.model.Notebook;
import com.pynb.app.model.NotebookCell;
import com.pynb.app.util.FileUtils;
import com.pynb.app.viewmodel.NotebookViewerViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class NotebookViewerActivity extends AppCompatActivity implements CellEditorDialog.Host {

    public static final String EXTRA_URI_STRING = "extra_uri_string";
    public static final String EXTRA_ASSET_PATH = "extra_asset_path";
    public static final String EXTRA_TITLE = "extra_title";

    private ActivityNotebookViewerBinding binding;
    private NotebookViewerViewModel viewModel;
    private NotebookAdapter adapter;

    private Uri targetUri;
    private String targetAssetPath;
    private String notebookTitle = "Notebook";

    private KernelManager kernel;
    /** Cell to run once the kernel connects (-1: none). */
    private int pendingRunCell = -1;
    private String pendingVenv;
    private String pendingWorkDir;
    private String lastShownKernelError;
    /** The open connect dialog, which receives the picked SSH key file. */
    private KernelConnectDialog connectDialog;
    private Runnable afterSave;

    private final ActivityResultLauncher<String> runCommandPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted) && pendingVenv != null) {
                    kernel.connect(pendingVenv, pendingWorkDir);
                } else if (!Boolean.TRUE.equals(granted)) {
                    pendingRunCell = -1;
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.kernel_connect_title)
                            .setMessage(R.string.kernel_permission_denied)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            });

    private final ActivityResultLauncher<String[]> sshKeyPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (connectDialog != null) connectDialog.onKeyFilePicked(uri);
            });

    private final ActivityResultLauncher<String> saveAsLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/x-ipynb+json"), uri -> {
                if (uri == null) {
                    afterSave = null;
                    return;
                }
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
                Runnable next = afterSave;
                afterSave = null;
                viewModel.save(uri, () -> {
                    targetUri = uri;
                    targetAssetPath = null;
                    notebookTitle = FileUtils.getFileName(this, uri);
                    binding.viewerToolbar.setTitle(notebookTitle);
                    Notebook nb = viewModel.getNotebook().getValue();
                    if (nb != null) nb.setTitle(notebookTitle);
                    if (next != null) next.run();
                });
            });

    private final OnBackPressedCallback unsavedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            new MaterialAlertDialogBuilder(NotebookViewerActivity.this)
                    .setTitle(R.string.unsaved_title)
                    .setMessage(R.string.unsaved_message)
                    .setPositiveButton(R.string.save, (d, w) -> save(NotebookViewerActivity.this::finish))
                    .setNegativeButton(R.string.discard, (d, w) -> finish())
                    .setNeutralButton(R.string.cancel, null)
                    .show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotebookViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(NotebookViewerViewModel.class);

        setSupportActionBar(binding.viewerToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        binding.viewerToolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, unsavedCallback);

        kernel = KernelManager.get(this);
        kernel.tryReattach();

        parseIntent();
        setupRecyclerView();
        setupSearch();
        observeViewModel();

        if (targetUri == null && targetAssetPath == null) {
            Toast.makeText(this, "No notebook file specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (savedInstanceState == null) {
            viewModel.loadNotebook(targetUri, targetAssetPath, notebookTitle);
        } else if (viewModel.getNotebook().getValue() != null) {
            binding.rvNotebook.setVisibility(View.VISIBLE);
            binding.layoutLoading.setVisibility(View.GONE);
            binding.layoutError.setVisibility(View.GONE);
        }
    }

    private void parseIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        if (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_EDIT.equals(intent.getAction())) {
            targetUri = intent.getData();
            if (targetUri != null) {
                notebookTitle = FileUtils.getFileName(this, targetUri);
            }
        } else {
            String uriStr = intent.getStringExtra(EXTRA_URI_STRING);
            if (uriStr != null) {
                targetUri = Uri.parse(uriStr);
            } else if (intent.getData() != null) {
                targetUri = intent.getData();
            }
            targetAssetPath = intent.getStringExtra(EXTRA_ASSET_PATH);
            if (intent.hasExtra(EXTRA_TITLE)) {
                notebookTitle = intent.getStringExtra(EXTRA_TITLE);
            } else if (targetUri != null) {
                notebookTitle = FileUtils.getFileName(this, targetUri);
            }
        }

        if (notebookTitle != null) {
            binding.viewerToolbar.setTitle(notebookTitle);
        }
    }

    private void setupRecyclerView() {
        adapter = new NotebookAdapter(this, (bitmap, title) -> {
            ImageViewerDialog dialog = new ImageViewerDialog(this, bitmap, title);
            dialog.show();
        });
        adapter.setOnEditCellListener(this::editCell);
        adapter.setOnRunCellListener(new NotebookAdapter.OnRunCellListener() {
            @Override
            public void onRunCell(int cellIndex) {
                runCell(cellIndex);
            }

            @Override
            public void onStopCell(int cellIndex) {
                viewModel.interrupt();
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvNotebook.setLayoutManager(layoutManager);
        binding.rvNotebook.setAdapter(adapter);
        binding.rvNotebook.setHasFixedSize(false);

        // Smooth scroll position listener for cell indicator pill
        binding.rvNotebook.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                Notebook nb = viewModel.getNotebook().getValue();
                if (nb == null || nb.getTotalCells() == 0) return;

                int firstVisible = layoutManager.findFirstVisibleItemPosition();
                if (firstVisible <= 0) {
                    binding.tvCellPositionIndicator.setVisibility(View.GONE);
                } else {
                    binding.tvCellPositionIndicator.setVisibility(View.VISIBLE);
                    int cellIndex = firstVisible; // position 0 is header, so position 1 is cell 1
                    int total = nb.getTotalCells();
                    binding.tvCellPositionIndicator.setText("Cell " + Math.min(cellIndex, total) + " of " + total);
                }
            }
        });
    }

    private void setupSearch() {
        binding.btnSearchClose.setOnClickListener(v -> closeSearch());

        binding.etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.search(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnSearchNext.setOnClickListener(v -> {
            int matchIndex = viewModel.nextMatch();
            if (matchIndex >= 0) {
                scrollToCell(matchIndex);
            }
        });

        binding.btnSearchPrev.setOnClickListener(v -> {
            int matchIndex = viewModel.prevMatch();
            if (matchIndex >= 0) {
                scrollToCell(matchIndex);
            }
        });
    }

    private void observeViewModel() {
        viewModel.getNotebook().observe(this, notebook -> {
            if (notebook != null) {
                binding.viewerToolbar.setTitle(notebook.getTitle());
                updateSubtitle();
                adapter.setNotebook(notebook);
                binding.rvNotebook.setVisibility(View.VISIBLE);
                binding.layoutLoading.setVisibility(View.GONE);
                binding.layoutError.setVisibility(View.GONE);
            }
        });

        viewModel.getIsLoading().observe(this, loading -> {
            if (Boolean.TRUE.equals(loading)) {
                binding.layoutLoading.setVisibility(View.VISIBLE);
                binding.rvNotebook.setVisibility(View.GONE);
                binding.layoutError.setVisibility(View.GONE);
            } else {
                binding.layoutLoading.setVisibility(View.GONE);
                if (viewModel.getNotebook().getValue() != null) {
                    binding.rvNotebook.setVisibility(View.VISIBLE);
                    binding.layoutError.setVisibility(View.GONE);
                }
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                binding.layoutLoading.setVisibility(View.GONE);
                binding.rvNotebook.setVisibility(View.GONE);
                binding.layoutError.setVisibility(View.VISIBLE);
                binding.tvErrorMessage.setText(getString(R.string.error_reading_notebook, error));
                binding.btnRetry.setOnClickListener(v ->
                        viewModel.loadNotebook(targetUri, targetAssetPath, notebookTitle));
            } else {
                binding.layoutError.setVisibility(View.GONE);
            }
        });

        viewModel.getMatchCountText().observe(this, text -> {
            binding.tvSearchMatches.setText(text);
        });

        viewModel.getSearchMatches().observe(this, matches -> {
            if (matches != null && !matches.isEmpty()) {
                scrollToCell(matches.get(0));
            }
        });

        viewModel.getShowLineNumbers().observe(this, show -> {
            adapter.setShowLineNumbers(show);
            invalidateOptionsMenu();
        });

        viewModel.getFontScale().observe(this, scale -> {
            adapter.setFontScale(scale);
        });

        viewModel.getCellsChanged().observe(this, tick -> {
            for (int index : viewModel.drainChangedCells()) {
                adapter.notifyCellChanged(index);
            }
        });

        viewModel.getDirty().observe(this, isDirty -> {
            unsavedCallback.setEnabled(Boolean.TRUE.equals(isDirty));
            updateSubtitle();
        });

        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                viewModel.clearMessage();
            }
        });

        kernel.getState().observe(this, state -> {
            updateSubtitle();
            invalidateOptionsMenu();
            if (state.status == KernelState.Status.CONNECTING) {
                lastShownKernelError = null;
            } else if (state.status == KernelState.Status.IDLE && pendingRunCell >= 0) {
                int index = pendingRunCell;
                pendingRunCell = -1;
                viewModel.runCell(index);
            } else if (state.status == KernelState.Status.ERROR && state.hostKeyPrompt != null
                    && !state.hostKeyPrompt.fingerprint.equals(lastShownKernelError)) {
                lastShownKernelError = state.hostKeyPrompt.fingerprint;
                showHostKeyPrompt(state.hostKeyPrompt, state.target);
            } else if (state.status == KernelState.Status.ERROR && state.hostKeyPrompt == null && state.message != null
                    && !state.message.equals(lastShownKernelError)) {
                lastShownKernelError = state.message;
                pendingRunCell = -1;
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.kernel_failed_title)
                        .setMessage(state.message)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(R.string.kernel_connect, (d, w) -> showConnectDialog())
                        .show();
            }
        });
    }

    private void updateSubtitle() {
        Notebook nb = viewModel.getNotebook().getValue();
        if (nb == null) return;
        StringBuilder sb = new StringBuilder();
        if (Boolean.TRUE.equals(viewModel.getDirty().getValue())) sb.append("● unsaved • ");
        KernelState state = kernel.currentState();
        String kernelLabel = state.label();
        if (kernelLabel.isEmpty()) {
            sb.append(nb.getMetadata().getKernelDisplayName()).append(" • ").append(nb.getTotalCells()).append(" cells");
        } else {
            sb.append(kernelLabel).append(" • ").append(nb.getTotalCells()).append(" cells");
        }
        binding.viewerToolbar.setSubtitle(sb.toString());
    }

    // ------------------------------------------------------------------ editing

    private void editCell(int cellIndex) {
        Notebook nb = viewModel.getNotebook().getValue();
        if (nb == null || cellIndex < 0 || cellIndex >= nb.getCells().size()) return;
        if (nb.getCells().get(cellIndex).getRunState() != NotebookCell.RunState.IDLE) {
            Toast.makeText(this, "Wait for the cell to finish running", Toast.LENGTH_SHORT).show();
            return;
        }
        if (getSupportFragmentManager().findFragmentByTag("cell_editor") == null) {
            CellEditorDialog.newInstance(cellIndex).show(getSupportFragmentManager(), "cell_editor");
        }
    }

    @Override
    public void onRunEditedCell(int cellIndex) {
        runCell(cellIndex);
    }

    // ------------------------------------------------------------------ kernel (Termux / SSH)

    private void runCell(int cellIndex) {
        if (kernel.currentState().isConnected()) {
            viewModel.runCell(cellIndex);
        } else {
            pendingRunCell = cellIndex;
            if (kernel.currentState().status != KernelState.Status.CONNECTING) {
                showConnectDialog();
            }
        }
    }

    private void showConnectDialog() {
        connectDialog = new KernelConnectDialog(this, kernel, new KernelConnectDialog.Host() {
            @Override
            public void requestKeyFile() {
                // Keys have no reliable MIME type (.pem, id_ed25519…), so allow any file.
                sshKeyPicker.launch(new String[]{"*/*"});
            }

            @Override
            public void connectTermux(@NonNull String venv, @NonNull String workDir) {
                connectDialog = null;
                lastShownKernelError = null;
                if (KernelManager.hasRunCommandPermission(NotebookViewerActivity.this)) {
                    kernel.connect(venv, workDir);
                } else {
                    pendingVenv = venv;
                    pendingWorkDir = workDir;
                    runCommandPermission.launch(KernelManager.PERMISSION_RUN_COMMAND);
                }
            }

            @Override
            public void onConnectCancelled() {
                connectDialog = null;
                pendingRunCell = -1;
            }
        });
        connectDialog.show();
    }

    /** Asks the user to verify an SSH server's host key (first connection, or a changed key). */
    private void showHostKeyPrompt(@NonNull KnownHosts.Prompt prompt, String target) {
        String name = target != null ? target : prompt.host;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        if (prompt.isChanged()) {
            builder.setTitle(R.string.ssh_changed_title)
                    .setMessage(getString(R.string.ssh_changed_message, name, prompt.previousFingerprint,
                            prompt.keyType, prompt.fingerprint))
                    .setPositiveButton(R.string.ssh_trust_new_key, (d, w) -> kernel.trustHostKeyAndRetry(prompt));
        } else {
            builder.setTitle(R.string.ssh_trust_title)
                    .setMessage(getString(R.string.ssh_trust_message, name, prompt.keyType, prompt.fingerprint))
                    .setPositiveButton(R.string.ssh_trust, (d, w) -> kernel.trustHostKeyAndRetry(prompt));
        }
        builder.setNegativeButton(R.string.cancel, (d, w) -> {
                    pendingRunCell = -1;
                    kernel.disconnect();
                })
                .setCancelable(false)
                .show();
    }

    // ------------------------------------------------------------------ saving

    private void save(Runnable onSuccess) {
        if (canOverwrite()) {
            viewModel.save(targetUri, onSuccess);
        } else {
            Toast.makeText(this, R.string.save_read_only, Toast.LENGTH_SHORT).show();
            saveAs(onSuccess);
        }
    }

    private void saveAs(Runnable onSuccess) {
        afterSave = onSuccess;
        String name = notebookTitle != null ? notebookTitle : "notebook.ipynb";
        if (!name.toLowerCase().endsWith(".ipynb")) name = name + ".ipynb";
        saveAsLauncher.launch(name);
    }

    private boolean canOverwrite() {
        if (targetAssetPath != null || targetUri == null) return false;
        if (!"content".equals(targetUri.getScheme())) return false;
        try {
            DocumentFile doc = DocumentFile.fromSingleUri(this, targetUri);
            return doc != null && doc.canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    private void toggleSearchBar() {
        if (binding.layoutSearch.getVisibility() == View.VISIBLE) {
            closeSearch();
        } else {
            binding.layoutSearch.setVisibility(View.VISIBLE);
            binding.etSearchQuery.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.etSearchQuery, InputMethodManager.SHOW_IMPLICIT);
            }
            viewModel.search(binding.etSearchQuery.getText().toString());
        }
    }

    private void closeSearch() {
        binding.layoutSearch.setVisibility(View.GONE);
        binding.etSearchQuery.setText("");
        viewModel.clearSearch();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.etSearchQuery.getWindowToken(), 0);
        }
    }

    private void scrollToCell(int cellIndex) {
        // Offset by 1 because position 0 is Header
        binding.rvNotebook.smoothScrollToPosition(cellIndex + 1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notebook_viewer, menu);
        KernelState state = kernel.currentState();
        boolean connected = state.isConnected();
        menu.findItem(R.id.action_kernel_connect).setTitle(connected
                ? "Reconnect / change kernel…" : "Connect to Python…");
        menu.findItem(R.id.action_kernel_interrupt).setVisible(connected);
        menu.findItem(R.id.action_kernel_restart).setVisible(connected);
        menu.findItem(R.id.action_kernel_disconnect).setVisible(connected || state.status == KernelState.Status.CONNECTING);
        MenuItem lineNumbersItem = menu.findItem(R.id.action_toggle_line_numbers);
        if (lineNumbersItem != null) {
            Boolean show = viewModel.getShowLineNumbers().getValue();
            lineNumbersItem.setChecked(Boolean.TRUE.equals(show));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_run_all) {
            if (kernel.currentState().isConnected()) {
                viewModel.runAll();
            } else {
                Toast.makeText(this, R.string.kernel_not_connected, Toast.LENGTH_SHORT).show();
                showConnectDialog();
            }
            return true;
        } else if (id == R.id.action_save) {
            save(null);
            return true;
        } else if (id == R.id.action_save_as) {
            saveAs(null);
            return true;
        } else if (id == R.id.action_kernel_connect) {
            showConnectDialog();
            return true;
        } else if (id == R.id.action_kernel_interrupt) {
            viewModel.interrupt();
            return true;
        } else if (id == R.id.action_kernel_restart) {
            viewModel.interrupt();
            kernel.restart(() -> runOnUiThread(() ->
                    Toast.makeText(this, R.string.kernel_restarted, Toast.LENGTH_SHORT).show()));
            return true;
        } else if (id == R.id.action_kernel_disconnect) {
            viewModel.interrupt();
            kernel.disconnect();
            return true;
        } else if (id == R.id.action_search) {
            toggleSearchBar();
            return true;
        } else if (id == R.id.action_toc) {
            showOutline();
            return true;
        } else if (id == R.id.action_font_larger) {
            float current = viewModel.getFontScale().getValue() != null ? viewModel.getFontScale().getValue() : 1.0f;
            float nextScale = current + 0.1f;
            viewModel.setFontScale(nextScale);
            Toast.makeText(this, "Font size: " + (int)(nextScale * 100) + "%", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_font_smaller) {
            float current = viewModel.getFontScale().getValue() != null ? viewModel.getFontScale().getValue() : 1.0f;
            float nextScale = current - 0.1f;
            viewModel.setFontScale(nextScale);
            Toast.makeText(this, "Font size: " + (int)(nextScale * 100) + "%", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_toggle_line_numbers) {
            boolean current = Boolean.TRUE.equals(viewModel.getShowLineNumbers().getValue());
            viewModel.setShowLineNumbers(!current);
            return true;
        } else if (id == R.id.action_info) {
            Notebook nb = viewModel.getNotebook().getValue();
            if (nb != null) {
                NotebookInfoDialog.show(this, nb);
            }
            return true;
        } else if (id == R.id.action_share) {
            shareNotebookSummary();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showOutline() {
        Notebook nb = viewModel.getNotebook().getValue();
        if (nb == null) return;
        OutlineBottomSheet sheet = OutlineBottomSheet.newInstance(nb.getOutline());
        sheet.setListener(this::scrollToCell);
        sheet.show(getSupportFragmentManager(), "outline_sheet");
    }

    private void shareNotebookSummary() {
        Notebook nb = viewModel.getNotebook().getValue();
        if (nb == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append(nb.getTitle()).append("\n");
        sb.append("Kernel: ").append(nb.getMetadata().getKernelDisplayName()).append("\n");
        sb.append("Cells: ").append(nb.getTotalCells()).append("\n\n");

        for (int i = 0; i < nb.getCells().size(); i++) {
            NotebookCell cell = nb.getCells().get(i);
            sb.append("--- [Cell ").append(i + 1).append(" (").append(cell.getCellType()).append(")] ---\n");
            sb.append(cell.getSource()).append("\n\n");
        }

        FileUtils.shareText(this, nb.getTitle(), sb.toString());
    }
}
