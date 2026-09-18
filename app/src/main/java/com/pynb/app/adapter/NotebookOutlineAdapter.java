package com.pynb.app.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pynb.app.databinding.ItemOutlineBinding;
import com.pynb.app.model.OutlineItem;

import java.util.ArrayList;
import java.util.List;

public class NotebookOutlineAdapter extends RecyclerView.Adapter<NotebookOutlineAdapter.ViewHolder> {

    public interface OnOutlineClickListener {
        void onOutlineClick(int cellIndex);
    }

    private final List<OutlineItem> items = new ArrayList<>();
    private final OnOutlineClickListener listener;

    public NotebookOutlineAdapter(OnOutlineClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<OutlineItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOutlineBinding binding = ItemOutlineBinding.inflate(
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
        private final ItemOutlineBinding binding;

        ViewHolder(ItemOutlineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(OutlineItem item) {
            binding.tvHeadingLevel.setText("H" + item.getLevel());
            binding.tvHeadingTitle.setText(item.getTitle());
            binding.tvHeadingCellTarget.setText("Cell #" + (item.getCellIndex() + 1));

            // Indentation based on heading level
            int indentDp = (item.getLevel() - 1) * 16;
            float density = itemView.getContext().getResources().getDisplayMetrics().density;
            int indentPx = (int) (indentDp * density);
            binding.layoutOutlineItem.setPadding(indentPx + (int)(8 * density),
                    binding.layoutOutlineItem.getPaddingTop(),
                    binding.layoutOutlineItem.getPaddingRight(),
                    binding.layoutOutlineItem.getPaddingBottom());

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOutlineClick(item.getCellIndex());
                }
            });
        }
    }
}
