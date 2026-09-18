package com.pynb.app.adapter;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pynb.app.R;
import com.pynb.app.databinding.ItemCellCodeBinding;
import com.pynb.app.databinding.ItemCellHeaderBinding;
import com.pynb.app.databinding.ItemCellMarkdownBinding;
import com.pynb.app.databinding.ItemCellRawBinding;
import com.pynb.app.model.CellOutput;
import com.pynb.app.model.Notebook;
import com.pynb.app.model.NotebookCell;
import com.pynb.app.parser.AnsiParser;
import com.pynb.app.parser.PythonSyntaxHighlighter;
import com.pynb.app.util.FileUtils;
import com.pynb.app.util.ImageCache;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;

import java.util.ArrayList;
import java.util.List;

public class NotebookAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnImageClickListener {
        void onImageClick(Bitmap bitmap, String title);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MARKDOWN = 1;
    private static final int TYPE_CODE = 2;
    private static final int TYPE_RAW = 3;

    private Notebook notebook;
    private final List<NotebookCell> cells = new ArrayList<>();
    private final Markwon markwon;
    private final boolean isDarkTheme;
    private float fontScale = 1.0f;
    private boolean showLineNumbers = true;
    private final OnImageClickListener imageClickListener;

    public NotebookAdapter(Context context, OnImageClickListener imageClickListener) {
        this.imageClickListener = imageClickListener;

        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        this.isDarkTheme = (nightMode == Configuration.UI_MODE_NIGHT_YES);

        this.markwon = Markwon.builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(HtmlPlugin.create())
                .build();
    }

    public void setNotebook(Notebook notebook) {
        this.notebook = notebook;
        this.cells.clear();
        if (notebook != null && notebook.getCells() != null) {
            this.cells.addAll(notebook.getCells());
        }
        notifyDataSetChanged();
    }

    public void setFontScale(float scale) {
        this.fontScale = Math.max(0.75f, Math.min(1.8f, scale));
        notifyDataSetChanged();
    }

    public float getFontScale() {
        return fontScale;
    }

    public void setShowLineNumbers(boolean show) {
        this.showLineNumbers = show;
        notifyDataSetChanged();
    }

    public boolean isShowLineNumbers() {
        return showLineNumbers;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_HEADER;
        }
        NotebookCell cell = cells.get(position - 1);
        if (cell.isMarkdown()) return TYPE_MARKDOWN;
        if (cell.isCode()) return TYPE_CODE;
        return TYPE_RAW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderViewHolder(ItemCellHeaderBinding.inflate(inflater, parent, false));
            case TYPE_MARKDOWN:
                return new MarkdownViewHolder(ItemCellMarkdownBinding.inflate(inflater, parent, false));
            case TYPE_CODE:
                return new CodeViewHolder(ItemCellCodeBinding.inflate(inflater, parent, false));
            case TYPE_RAW:
            default:
                return new RawViewHolder(ItemCellRawBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(notebook);
        } else if (holder instanceof MarkdownViewHolder) {
            ((MarkdownViewHolder) holder).bind(cells.get(position - 1), position - 1);
        } else if (holder instanceof CodeViewHolder) {
            ((CodeViewHolder) holder).bind(cells.get(position - 1), position - 1);
        } else if (holder instanceof RawViewHolder) {
            ((RawViewHolder) holder).bind(cells.get(position - 1), position - 1);
        }
    }

    @Override
    public int getItemCount() {
        return notebook == null ? 0 : cells.size() + 1;
    }

    // ================= HEADER VIEWHOLDER =================
    class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemCellHeaderBinding binding;

        HeaderViewHolder(ItemCellHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Notebook nb) {
            if (nb == null) return;
            binding.tvHeaderTitle.setText(nb.getTitle());
            binding.chipKernel.setText(nb.getMetadata().getKernelDisplayName());
            binding.chipTotalCells.setText(nb.getTotalCells() + " cells");
            binding.chipStats.setText(nb.getCodeCellCount() + " code • " + nb.getMarkdownCellCount() + " markdown");
        }
    }

    // ================= MARKDOWN VIEWHOLDER =================
    class MarkdownViewHolder extends RecyclerView.ViewHolder {
        private final ItemCellMarkdownBinding binding;
        private boolean isShowingRaw = false;

        MarkdownViewHolder(ItemCellMarkdownBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NotebookCell cell, int index) {
            binding.tvMdCellNumber.setText("#" + (index + 1));
            String source = cell.getSource();

            // Font size adjustment
            binding.tvMarkdownRendered.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * fontScale);
            binding.tvMarkdownRaw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * fontScale);

            // Render Markdown
            markwon.setMarkdown(binding.tvMarkdownRendered, source);
            binding.tvMarkdownRaw.setText(source);

            // Toggle source / rendered view
            binding.btnToggleSource.setOnClickListener(v -> {
                isShowingRaw = !isShowingRaw;
                if (isShowingRaw) {
                    binding.tvMarkdownRendered.setVisibility(View.GONE);
                    binding.tvMarkdownRaw.setVisibility(View.VISIBLE);
                    binding.btnToggleSource.setText(R.string.toggle_render);
                } else {
                    binding.tvMarkdownRendered.setVisibility(View.VISIBLE);
                    binding.tvMarkdownRaw.setVisibility(View.GONE);
                    binding.btnToggleSource.setText(R.string.toggle_raw);
                }
            });

            // Copy markdown source
            binding.btnCopyMarkdown.setOnClickListener(v -> {
                FileUtils.copyToClipboard(v.getContext(), "Markdown Cell #" + (index + 1), source);
            });
        }
    }

    // ================= CODE VIEWHOLDER =================
    class CodeViewHolder extends RecyclerView.ViewHolder {
        private final ItemCellCodeBinding binding;

        CodeViewHolder(ItemCellCodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NotebookCell cell, int index) {
            Context context = binding.getRoot().getContext();
            binding.tvCodeCellNumber.setText("#" + (index + 1));
            binding.tvInPrompt.setText("In " + cell.getExecutionPrompt() + ":");

            String source = cell.getSource();

            // Font size scaling
            binding.tvCodeSource.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * fontScale);
            binding.tvLineNumbers.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * fontScale);

            // Syntax highlighting
            CharSequence highlighted = PythonSyntaxHighlighter.highlight(source, isDarkTheme);
            binding.tvCodeSource.setText(highlighted);

            // Line numbers calculation
            if (showLineNumbers && source != null && !source.isEmpty()) {
                binding.tvLineNumbers.setVisibility(View.VISIBLE);
                binding.viewLineDivider.setVisibility(View.VISIBLE);
                int lineCount = countLines(source);
                StringBuilder lines = new StringBuilder();
                for (int i = 1; i <= lineCount; i++) {
                    lines.append(i).append("\n");
                }
                binding.tvLineNumbers.setText(lines.toString().trim());
            } else {
                binding.tvLineNumbers.setVisibility(View.GONE);
                binding.viewLineDivider.setVisibility(View.GONE);
            }

            // Collapse / Expand toggle
            binding.btnCollapseCode.setOnClickListener(v -> {
                boolean collapsed = !cell.isCollapsed();
                cell.setCollapsed(collapsed);
                updateCollapseState(cell);
            });
            updateCollapseState(cell);

            // Copy code
            binding.btnCopyCode.setOnClickListener(v -> {
                FileUtils.copyToClipboard(context, "Code Cell #" + (index + 1), source);
            });

            // Render Outputs
            renderOutputs(cell, index);
        }

        private void updateCollapseState(NotebookCell cell) {
            if (cell.isCollapsed()) {
                binding.scrollCodeContainer.setVisibility(View.GONE);
                binding.layoutOutputsContainer.setVisibility(View.GONE);
                binding.btnCollapseCode.setImageResource(R.drawable.ic_expand);
            } else {
                binding.scrollCodeContainer.setVisibility(View.VISIBLE);
                if (cell.hasOutputs()) {
                    binding.layoutOutputsContainer.setVisibility(View.VISIBLE);
                }
                binding.btnCollapseCode.setImageResource(R.drawable.ic_collapse);
            }
        }

        private void renderOutputs(NotebookCell cell, int cellIndex) {
            binding.layoutOutputsList.removeAllViews();

            if (!cell.hasOutputs()) {
                binding.layoutOutputsContainer.setVisibility(View.GONE);
                return;
            }

            if (!cell.isCollapsed()) {
                binding.layoutOutputsContainer.setVisibility(View.VISIBLE);
            }

            binding.tvOutPrompt.setText("Out " + cell.getExecutionPrompt() + ":");
            Context context = binding.getRoot().getContext();
            LayoutInflater inflater = LayoutInflater.from(context);

            for (int i = 0; i < cell.getOutputs().size(); i++) {
                CellOutput output = cell.getOutputs().get(i);
                String cacheKey = "cell_" + cellIndex + "_out_" + i;

                // 1. Image Output (PNG / JPEG)
                if (output.hasImage()) {
                    ImageView iv = new ImageView(context);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(0, 8, 0, 8);
                    iv.setLayoutParams(lp);
                    iv.setAdjustViewBounds(true);
                    iv.setMaxHeight((int) (400 * context.getResources().getDisplayMetrics().density));
                    iv.setBackgroundResource(R.drawable.bg_output_box);
                    iv.setPadding(8, 8, 8, 8);

                    Bitmap cachedBitmap = ImageCache.getInstance().getBitmap(cacheKey);
                    if (cachedBitmap == null) {
                        cachedBitmap = ImageCache.decodeBase64(output.getImageBase64());
                        if (cachedBitmap != null) {
                            ImageCache.getInstance().putBitmap(cacheKey, cachedBitmap);
                        }
                    }

                    if (cachedBitmap != null) {
                        final Bitmap bmp = cachedBitmap;
                        iv.setImageBitmap(bmp);
                        iv.setOnClickListener(v -> {
                            if (imageClickListener != null) {
                                imageClickListener.onImageClick(bmp, "Output Figure (Cell #" + (cellIndex + 1) + ")");
                            }
                        });
                        binding.layoutOutputsList.addView(iv);
                    }
                }

                // 2. Error Output (Traceback with ANSI escape codes)
                else if (output.isError()) {
                    LinearLayout errorLayout = new LinearLayout(context);
                    errorLayout.setOrientation(LinearLayout.VERTICAL);
                    errorLayout.setBackgroundResource(R.drawable.bg_error_box);
                    errorLayout.setPadding(16, 16, 16, 16);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(0, 8, 0, 8);
                    errorLayout.setLayoutParams(lp);

                    // Ename + Evalue
                    TextView tvErrTitle = new TextView(context);
                    tvErrTitle.setText(output.getEname() + ": " + output.getEvalue());
                    tvErrTitle.setTextColor(context.getColor(R.color.error_text_light));
                    tvErrTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    tvErrTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * fontScale);
                    errorLayout.addView(tvErrTitle);

                    // Traceback
                    if (output.getTraceback() != null && !output.getTraceback().isEmpty()) {
                        StringBuilder tbSb = new StringBuilder();
                        for (String tbLine : output.getTraceback()) {
                            tbSb.append(tbLine).append("\n");
                        }
                        CharSequence coloredTb = AnsiParser.parse(tbSb.toString(), isDarkTheme);

                        TextView tvTb = new TextView(context);
                        tvTb.setText(coloredTb);
                        tvTb.setTypeface(Typeface.MONOSPACE);
                        tvTb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * fontScale);
                        tvTb.setTextIsSelectable(true);
                        tvTb.setPadding(0, 8, 0, 0);
                        errorLayout.addView(tvTb);
                    }

                    binding.layoutOutputsList.addView(errorLayout);
                }

                // 3. Stream (stdout / stderr) or Plain Text
                else if (output.hasPlainText()) {
                    String plain = output.getPlainText();
                    if (plain != null && !plain.isEmpty()) {
                        TextView tvText = new TextView(context);
                        tvText.setBackgroundResource(R.drawable.bg_output_box);
                        tvText.setTypeface(Typeface.MONOSPACE);
                        tvText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * fontScale);
                        tvText.setTextColor(context.getColor(R.color.text_primary_light));
                        tvText.setTextIsSelectable(true);
                        tvText.setPadding(16, 12, 16, 12);

                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.setMargins(0, 4, 0, 4);
                        tvText.setLayoutParams(lp);

                        CharSequence ansiParsed = AnsiParser.parse(plain, isDarkTheme);
                        tvText.setText(ansiParsed);
                        binding.layoutOutputsList.addView(tvText);
                    }
                }
            }
        }

        private int countLines(String str) {
            if (str == null || str.isEmpty()) return 0;
            int lines = 1;
            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) == '\n') lines++;
            }
            return lines;
        }
    }

    // ================= RAW VIEWHOLDER =================
    class RawViewHolder extends RecyclerView.ViewHolder {
        private final ItemCellRawBinding binding;

        RawViewHolder(ItemCellRawBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NotebookCell cell, int index) {
            binding.tvRawCellNumber.setText("#" + (index + 1));
            String source = cell.getSource();
            binding.tvRawSource.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * fontScale);
            binding.tvRawSource.setText(source);

            binding.btnCopyRaw.setOnClickListener(v -> {
                FileUtils.copyToClipboard(v.getContext(), "Raw Cell #" + (index + 1), source);
            });
        }
    }
}
