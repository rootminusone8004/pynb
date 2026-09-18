package com.pynb.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pynb.app.adapter.RecentFilesAdapter;
import com.pynb.app.adapter.RecentFoldersAdapter;
import com.pynb.app.databinding.ActivityMainBinding;
import com.pynb.app.util.FileUtils;
import com.pynb.app.util.RecentFilesManager.RecentItem;
import com.pynb.app.util.RecentFoldersManager.RecentFolder;
import com.pynb.app.viewmodel.MainViewModel;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private RecentFilesAdapter recentAdapter;
    private RecentFoldersAdapter recentFoldersAdapter;

    // Folder Workspace Picker
    private final ActivityResultLauncher<Uri> openFolderLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (Exception ignored) {}

                    openFolderWorkspace(uri);
                }
            });

    // Single File Picker
    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}

                    openNotebookUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if opened via external intent (e.g. file manager clicked .ipynb)
        Intent intent = getIntent();
        if (intent != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_EDIT.equals(intent.getAction()))) {
            Uri data = intent.getData();
            if (data != null) {
                openNotebookUri(data);
                finish();
                return;
            }
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        setupButtons();
        setupRecentFoldersList();
        setupRecentFilesList();
        observeViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadRecents();
    }

    private void setupButtons() {
        // Choose Folder Workspace
        binding.btnChooseFolder.setOnClickListener(v -> openFolderLauncher.launch(null));

        // Open single notebook file
        binding.btnOpenNotebook.setOnClickListener(v -> {
            openDocumentLauncher.launch(new String[]{"*/*"});
        });

        // Sample 1: Data Science
        binding.cardSampleDataScience.setOnClickListener(v -> {
            openSampleNotebook("samples/data_analysis.ipynb", "Data Science & Matplotlib");
        });

        // Sample 2: ML & Diagnostics
        binding.cardSampleMl.setOnClickListener(v -> {
            openSampleNotebook("samples/machine_learning.ipynb", "Machine Learning & Diagnostics");
        });

        // Sample 3: Python Tutorial
        binding.cardSamplePython.setOnClickListener(v -> {
            openSampleNotebook("samples/python_tutorial.ipynb", "Python Fundamentals & Syntax");
        });

        // Clear Recent Notebooks
        binding.btnClearRecent.setOnClickListener(v -> viewModel.clearAllRecentFiles());

        // Clear Recent Folders
        binding.btnClearRecentFolders.setOnClickListener(v -> viewModel.clearAllRecentFolders());
    }

    private void setupRecentFoldersList() {
        recentFoldersAdapter = new RecentFoldersAdapter(new RecentFoldersAdapter.OnRecentFolderClickListener() {
            @Override
            public void onFolderClick(RecentFolder folder) {
                openFolderWorkspace(Uri.parse(folder.getUriString()));
            }

            @Override
            public void onFolderRemove(RecentFolder folder) {
                viewModel.removeRecentFolder(folder.getUriString());
            }
        });

        binding.rvRecentFolders.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRecentFolders.setAdapter(recentFoldersAdapter);
    }

    private void setupRecentFilesList() {
        recentAdapter = new RecentFilesAdapter(new RecentFilesAdapter.OnRecentItemClickListener() {
            @Override
            public void onItemClick(RecentItem item) {
                if (item.isSample() || item.getUriString().startsWith("asset:")) {
                    String assetPath = item.getUriString().replace("asset:", "");
                    openSampleNotebook(assetPath, item.getTitle());
                } else {
                    openNotebookUri(Uri.parse(item.getUriString()));
                }
            }

            @Override
            public void onItemRemove(RecentItem item) {
                viewModel.removeRecentFile(item.getUriString());
            }
        });

        binding.rvRecentFiles.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRecentFiles.setAdapter(recentAdapter);
    }

    private void observeViewModel() {
        viewModel.getRecentFolders().observe(this, this::renderRecentFolders);
        viewModel.getRecentFiles().observe(this, this::renderRecentFiles);
    }

    private void renderRecentFolders(List<RecentFolder> folders) {
        if (folders == null || folders.isEmpty()) {
            binding.layoutRecentFoldersHeader.setVisibility(View.GONE);
            binding.rvRecentFolders.setVisibility(View.GONE);
        } else {
            binding.layoutRecentFoldersHeader.setVisibility(View.VISIBLE);
            binding.rvRecentFolders.setVisibility(View.VISIBLE);
            recentFoldersAdapter.setItems(folders);
        }
    }

    private void renderRecentFiles(List<RecentItem> items) {
        if (items == null || items.isEmpty()) {
            binding.tvEmptyRecent.setVisibility(View.VISIBLE);
            binding.rvRecentFiles.setVisibility(View.GONE);
            binding.btnClearRecent.setVisibility(View.GONE);
        } else {
            binding.tvEmptyRecent.setVisibility(View.GONE);
            binding.rvRecentFiles.setVisibility(View.VISIBLE);
            binding.btnClearRecent.setVisibility(View.VISIBLE);
            recentAdapter.setItems(items);
        }
    }

    private void openFolderWorkspace(Uri treeUri) {
        Intent intent = new Intent(this, FolderBrowserActivity.class);
        intent.putExtra(FolderBrowserActivity.EXTRA_TREE_URI, treeUri.toString());
        startActivity(intent);
    }

    private void openNotebookUri(Uri uri) {
        String fileName = FileUtils.getFileName(this, uri);
        Intent intent = new Intent(this, NotebookViewerActivity.class);
        intent.setData(uri);
        intent.putExtra(NotebookViewerActivity.EXTRA_URI_STRING, uri.toString());
        intent.putExtra(NotebookViewerActivity.EXTRA_TITLE, fileName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void openSampleNotebook(String assetPath, String title) {
        Intent intent = new Intent(this, NotebookViewerActivity.class);
        intent.putExtra(NotebookViewerActivity.EXTRA_ASSET_PATH, assetPath);
        intent.putExtra(NotebookViewerActivity.EXTRA_TITLE, title);
        startActivity(intent);
    }
}
