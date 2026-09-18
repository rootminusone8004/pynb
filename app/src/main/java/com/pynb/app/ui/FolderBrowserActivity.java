package com.pynb.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.pynb.app.R;
import com.pynb.app.adapter.FolderAdapter;
import com.pynb.app.databinding.ActivityFolderBrowserBinding;
import com.pynb.app.model.FolderItem;
import com.pynb.app.viewmodel.FolderBrowserViewModel;

import java.util.List;

public class FolderBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_TREE_URI = "extra_tree_uri";

    private ActivityFolderBrowserBinding binding;
    private FolderBrowserViewModel viewModel;
    private FolderAdapter adapter;

    private final ActivityResultLauncher<Uri> switchFolderLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (Exception ignored) {}

                    viewModel.switchFolder(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFolderBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(FolderBrowserViewModel.class);

        setSupportActionBar(binding.toolbarFolder);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        binding.toolbarFolder.setNavigationOnClickListener(v -> handleBackNavigation());

        setupRecyclerView();
        setupSearchAndFilter();
        setupBackCallback();
        observeViewModel();

        if (savedInstanceState == null) {
            String uriStr = getIntent().getStringExtra(EXTRA_TREE_URI);
            if (uriStr != null) {
                viewModel.initRootFolder(Uri.parse(uriStr));
            } else {
                Toast.makeText(this, "Invalid folder URI", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void setupBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });
    }

    private void handleBackNavigation() {
        if (!viewModel.navigateUp()) {
            finish();
        }
    }

    private void setupRecyclerView() {
        adapter = new FolderAdapter(new FolderAdapter.OnFolderItemClickListener() {
            @Override
            public void onFolderClick(FolderItem item) {
                if (item.getDocumentFile() != null) {
                    viewModel.openSubFolder(item.getDocumentFile());
                }
            }

            @Override
            public void onNotebookClick(FolderItem item) {
                Intent intent = new Intent(FolderBrowserActivity.this, NotebookViewerActivity.class);
                intent.putExtra(NotebookViewerActivity.EXTRA_URI_STRING, item.getUri().toString());
                intent.putExtra(NotebookViewerActivity.EXTRA_TITLE, item.getName());
                startActivity(intent);
            }

            @Override
            public void onParentClick() {
                handleBackNavigation();
            }
        });

        binding.rvFolderItems.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFolderItems.setAdapter(adapter);
    }

    private void setupSearchAndFilter() {
        binding.btnFilterToggle.setOnClickListener(v -> viewModel.toggleFilterNotebooksOnly());

        binding.btnEmptyShowAll.setOnClickListener(v -> viewModel.setShowNotebooksOnly(false));

        binding.etFolderSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void observeViewModel() {
        viewModel.getCurrentTitle().observe(this, title -> binding.toolbarFolder.setTitle(title));

        viewModel.getCurrentSubtitle().observe(this, sub -> binding.toolbarFolder.setSubtitle(sub));

        viewModel.getIsLoading().observe(this, loading -> {
            binding.layoutFolderLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) {
                binding.rvFolderItems.setVisibility(View.GONE);
                binding.layoutFolderEmpty.setVisibility(View.GONE);
            }
        });

        viewModel.getShowNotebooksOnly().observe(this, notebooksOnly -> {
            binding.btnFilterToggle.setText(notebooksOnly ? ".ipynb Only" : "All Files");
        });

        viewModel.getBreadcrumbs().observe(this, this::renderBreadcrumbs);

        viewModel.getDisplayItems().observe(this, this::renderItems);

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void renderBreadcrumbs(List<DocumentFile> dirStack) {
        binding.layoutBreadcrumbs.removeAllViews();
        if (dirStack == null || dirStack.isEmpty()) return;

        for (int i = 0; i < dirStack.size(); i++) {
            final int index = i;
            DocumentFile doc = dirStack.get(i);
            String title = (i == 0) ? "📁 " + (doc.getName() != null ? doc.getName() : "Root") : doc.getName();

            Chip chip = new Chip(this, null, com.google.android.material.R.style.Widget_Material3_Chip_Assist);
            chip.setText(title);
            chip.setTextSize(12);
            chip.setChipMinHeight(32);
            chip.setCheckable(false);
            chip.setClickable(true);

            chip.setOnClickListener(v -> viewModel.navigateToBreadcrumb(index));

            binding.layoutBreadcrumbs.addView(chip);

            if (i < dirStack.size() - 1) {
                TextView separator = new TextView(this);
                separator.setText(" > ");
                separator.setTextSize(12);
                separator.setTextColor(getColor(R.color.text_muted_light));
                binding.layoutBreadcrumbs.addView(separator);
            }
        }

        binding.scrollBreadcrumbs.post(() -> binding.scrollBreadcrumbs.fullScroll(View.FOCUS_RIGHT));
    }

    private void renderItems(List<FolderItem> items) {
        if (Boolean.TRUE.equals(viewModel.getIsLoading().getValue())) {
            return;
        }

        adapter.setItems(items);

        if (items == null || items.isEmpty()) {
            binding.rvFolderItems.setVisibility(View.GONE);
            binding.layoutFolderEmpty.setVisibility(View.VISIBLE);

            String query = viewModel.getSearchQuery().getValue();
            boolean notebooksOnly = Boolean.TRUE.equals(viewModel.getShowNotebooksOnly().getValue());

            if (query != null && !query.isEmpty()) {
                binding.tvFolderEmptyTitle.setText("No Matches Found");
                binding.tvFolderEmptyDesc.setText("No items matched \"" + query + "\"");
                binding.btnEmptyShowAll.setVisibility(View.GONE);
            } else if (notebooksOnly) {
                binding.tvFolderEmptyTitle.setText("No Notebooks Found");
                binding.tvFolderEmptyDesc.setText("No .ipynb files were found in this directory.\nYou can toggle to view all files or open another folder.");
                binding.btnEmptyShowAll.setVisibility(View.VISIBLE);
            } else {
                binding.tvFolderEmptyTitle.setText("Directory is Empty");
                binding.tvFolderEmptyDesc.setText("This folder does not contain any files.");
                binding.btnEmptyShowAll.setVisibility(View.GONE);
            }
        } else {
            binding.rvFolderItems.setVisibility(View.VISIBLE);
            binding.layoutFolderEmpty.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_folder_browser, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh_folder) {
            viewModel.refresh();
            return true;
        } else if (id == R.id.action_switch_folder) {
            switchFolderLauncher.launch(viewModel.getRootTreeUri());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
