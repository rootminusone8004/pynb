package com.pynb.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.pynb.app.adapter.NotebookOutlineAdapter;
import com.pynb.app.databinding.BottomSheetOutlineBinding;
import com.pynb.app.model.OutlineItem;

import java.io.Serializable;
import java.util.List;

public class OutlineBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_OUTLINE = "arg_outline";

    public interface OutlineSelectionListener {
        void onSectionSelected(int cellIndex);
    }

    private BottomSheetOutlineBinding binding;
    private OutlineSelectionListener listener;

    public static OutlineBottomSheet newInstance(List<OutlineItem> outline) {
        OutlineBottomSheet sheet = new OutlineBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_OUTLINE, (Serializable) outline);
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(OutlineSelectionListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetOutlineBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnCloseOutline.setOnClickListener(v -> dismiss());

        List<OutlineItem> outline = null;
        if (getArguments() != null) {
            outline = (List<OutlineItem>) getArguments().getSerializable(ARG_OUTLINE);
        }

        if (outline == null || outline.isEmpty()) {
            binding.tvEmptyOutline.setVisibility(View.VISIBLE);
            binding.rvOutline.setVisibility(View.GONE);
        } else {
            binding.tvEmptyOutline.setVisibility(View.GONE);
            binding.rvOutline.setVisibility(View.VISIBLE);
            binding.rvOutline.setLayoutManager(new LinearLayoutManager(getContext()));

            NotebookOutlineAdapter adapter = new NotebookOutlineAdapter(cellIndex -> {
                if (listener != null) {
                    listener.onSectionSelected(cellIndex);
                }
                dismiss();
            });
            adapter.setItems(outline);
            binding.rvOutline.setAdapter(adapter);
        }
    }
}
