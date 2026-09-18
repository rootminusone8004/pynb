package com.pynb.app.ui;

import android.content.Context;
import android.view.LayoutInflater;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pynb.app.databinding.DialogNotebookInfoBinding;
import com.pynb.app.model.Notebook;

public class NotebookInfoDialog {

    public static void show(Context context, Notebook notebook) {
        if (context == null || notebook == null) return;

        DialogNotebookInfoBinding binding = DialogNotebookInfoBinding.inflate(LayoutInflater.from(context));

        binding.tvInfoKernel.setText("Kernel: " + notebook.getMetadata().getKernelDisplayName() + " (" + notebook.getMetadata().getKernelName() + ")");
        String lang = notebook.getMetadata().getLanguageName();
        if (notebook.getMetadata().getLanguageVersion() != null && !notebook.getMetadata().getLanguageVersion().isEmpty()) {
            lang += " " + notebook.getMetadata().getLanguageVersion();
        }
        binding.tvInfoLanguage.setText("Language: " + lang);
        binding.tvInfoFormat.setText("Format: nbformat v" + notebook.getNbformat() + "." + notebook.getNbformatMinor());
        binding.tvInfoCells.setText("Cells: " + notebook.getTotalCells() + " total (" +
                notebook.getCodeCellCount() + " code, " +
                notebook.getMarkdownCellCount() + " markdown)");

        new MaterialAlertDialogBuilder(context)
                .setView(binding.getRoot())
                .setPositiveButton("Close", null)
                .show();
    }
}
