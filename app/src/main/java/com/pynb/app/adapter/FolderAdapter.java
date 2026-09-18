package com.pynb.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pynb.app.R;
import com.pynb.app.databinding.ItemFolderFileBinding;
import com.pynb.app.model.FolderItem;

import java.util.ArrayList;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {

    public interface OnFolderItemClickListener {
        void onFolderClick(FolderItem item);
        void onNotebookClick(FolderItem item);
        void onParentClick();
    }

    private final List<FolderItem> items = new ArrayList<>();
    private final OnFolderItemClickListener listener;

    public FolderAdapter(OnFolderItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<FolderItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFolderFileBinding binding = ItemFolderFileBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemFolderFileBinding binding;

        ViewHolder(ItemFolderFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(FolderItem item) {
            if (item.isParent()) {
                binding.ivItemIcon.setImageResource(R.drawable.ic_folder_up);
                binding.tvItemName.setText(".. (Up to Parent)");
                binding.tvItemDetails.setText("Go back one directory");
                binding.tvItemBadge.setVisibility(View.GONE);
                binding.ivItemChevron.setVisibility(View.GONE);

                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) listener.onParentClick();
                });
            } else if (item.isDirectory()) {
                binding.ivItemIcon.setImageResource(R.drawable.ic_folder);
                binding.tvItemName.setText(item.getName());
                binding.tvItemDetails.setText("Directory");
                binding.tvItemBadge.setVisibility(View.GONE);
                binding.ivItemChevron.setVisibility(View.VISIBLE);

                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) listener.onFolderClick(item);
                });
            } else if (item.isNotebook()) {
                binding.ivItemIcon.setImageResource(R.drawable.ic_jupyter_notebook);
                binding.tvItemName.setText(item.getName());
                String info = item.getFormattedSize();
                String date = item.getFormattedDate();
                if (!date.isEmpty()) {
                    info = info.isEmpty() ? date : info + " • " + date;
                }
                binding.tvItemDetails.setText(info.isEmpty() ? "Jupyter Notebook" : info);
                binding.tvItemBadge.setVisibility(View.VISIBLE);
                binding.tvItemBadge.setText("IPYNB");
                binding.ivItemChevron.setVisibility(View.GONE);

                binding.getRoot().setOnClickListener(v -> {
                    if (listener != null) listener.onNotebookClick(item);
                });
            } else {
                binding.ivItemIcon.setImageResource(R.drawable.ic_file);
                binding.tvItemName.setText(item.getName());
                String info = item.getFormattedSize();
                binding.tvItemDetails.setText(info.isEmpty() ? "File" : info);
                binding.tvItemBadge.setVisibility(View.GONE);
                binding.ivItemChevron.setVisibility(View.GONE);

                binding.getRoot().setOnClickListener(null);
            }
        }
    }
}
