package com.pynb.app.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pynb.app.databinding.ItemRecentFileBinding;
import com.pynb.app.util.RecentFilesManager.RecentItem;

import java.util.ArrayList;
import java.util.List;

public class RecentFilesAdapter extends RecyclerView.Adapter<RecentFilesAdapter.ViewHolder> {

    public interface OnRecentItemClickListener {
        void onItemClick(RecentItem item);
        void onItemRemove(RecentItem item);
    }

    private final List<RecentItem> items = new ArrayList<>();
    private final OnRecentItemClickListener listener;

    public RecentFilesAdapter(OnRecentItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<RecentItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemRecentFileBinding binding = ItemRecentFileBinding.inflate(
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
        private final ItemRecentFileBinding binding;

        ViewHolder(ItemRecentFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(RecentItem item) {
            binding.tvRecentTitle.setText(item.getTitle());

            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    item.getTimestamp(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            );

            String subtitle = item.getCellCount() + " cells • " + timeAgo;
            binding.tvRecentSubtitle.setText(subtitle);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });

            binding.btnRemoveRecent.setOnClickListener(v -> {
                if (listener != null) listener.onItemRemove(item);
            });
        }
    }
}
