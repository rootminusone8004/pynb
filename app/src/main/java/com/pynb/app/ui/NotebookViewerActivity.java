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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pynb.app.R;
import com.pynb.app.adapter.NotebookAdapter;
import com.pynb.app.databinding.ActivityNotebookViewerBinding;
import com.pynb.app.model.Notebook;
import com.pynb.app.model.NotebookCell;
import com.pynb.app.util.FileUtils;
import com.pynb.app.viewmodel.NotebookViewerViewModel;

public class NotebookViewerActivity extends AppCompatActivity {

    public static final String EXTRA_URI_STRING = "extra_uri_string";
    public static final String EXTRA_ASSET_PATH = "extra_asset_path";
    public static final String EXTRA_TITLE = "extra_title";

    private ActivityNotebookViewerBinding binding;
    private NotebookViewerViewModel viewModel;
    private NotebookAdapter adapter;

    private Uri targetUri;
    private String targetAssetPath;
    private String notebookTitle = "Notebook";

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

        binding.viewerToolbar.setNavigationOnClickListener(v -> finish());

        parseIntent();
        setupRecyclerView();
        setupSearch();
        observeViewModel();

        if (savedInstanceState == null) {
            viewModel.loadNotebook(targetUri, targetAssetPath, notebookTitle);
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
            }
            targetAssetPath = intent.getStringExtra(EXTRA_ASSET_PATH);
            if (intent.hasExtra(EXTRA_TITLE)) {
                notebookTitle = intent.getStringExtra(EXTRA_TITLE);
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
                binding.viewerToolbar.setSubtitle(
                        notebook.getMetadata().getKernelDisplayName() + " • " + notebook.getTotalCells() + " cells");
                adapter.setNotebook(notebook);
            }
        });

        viewModel.getIsLoading().observe(this, loading -> {
            binding.layoutLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) {
                binding.rvNotebook.setVisibility(View.GONE);
                binding.layoutError.setVisibility(View.GONE);
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
            } else if (!Boolean.TRUE.equals(viewModel.getIsLoading().getValue())) {
                binding.layoutError.setVisibility(View.GONE);
                binding.rvNotebook.setVisibility(View.VISIBLE);
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
        if (id == R.id.action_search) {
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
