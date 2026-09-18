package com.pynb.app.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pynb.app.databinding.ItemRecentFolderBinding;
import com.pynb.app.util.RecentFoldersManager.RecentFolder;

import java.util.ArrayList;
import java.util.List;

public class RecentFoldersAdapter extends RecyclerView.Adapter<RecentFoldersAdapter.ViewHolder> {

    public interface OnRecentFolderClickListener {
        void onFolderClick(RecentFolder folder);
        void onFolderRemove(RecentFolder folder);
    }

    private final List<RecentFolder> items = new ArrayList<>();
    private final OnRecentFolderClickListener listener;

    public RecentFoldersAdapter(OnRecentFolderClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<RecentFolder> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecentFolderBinding binding = ItemRecentFolderBinding.inflate(
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
        private final ItemRecentFolderBinding binding;

        ViewHolder(ItemRecentFolderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(RecentFolder folder) {
            binding.tvRecentFolderName.setText(folder.getName());

            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    folder.getTimestamp(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            );
            binding.tvRecentFolderSubtitle.setText("Folder Workspace • " + timeAgo);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onFolderClick(folder);
            });

            binding.btnRemoveRecentFolder.setOnClickListener(v -> {
                if (listener != null) listener.onFolderRemove(folder);
            });
        }
    }
}
