package com.rootminusone8004.pynb.ui;

import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rootminusone8004.pynb.R;
import com.rootminusone8004.pynb.databinding.DialogCellEditorBinding;
import com.rootminusone8004.pynb.kernel.KernelManager;
import com.rootminusone8004.pynb.model.Notebook;
import com.rootminusone8004.pynb.model.NotebookCell;
import com.rootminusone8004.pynb.parser.PythonSyntaxHighlighter;
import com.rootminusone8004.pynb.viewmodel.NotebookViewerViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Full-screen editor for a markdown or code cell.
 *
 * <p>Code cells get live Python highlighting, auto-indent (keeps the previous line's
 * indentation and adds a level after a trailing {@code :}) and a key bar with Tab / unindent
 * and the symbols that are hard to reach on phone keyboards. Markdown cells get a markdown key
 * bar. "Done" applies the edit; for code cells "Apply and run" also runs the cell.</p>
 */
public class CellEditorDialog extends DialogFragment {

    /** Implemented by the hosting activity. */
    public interface Host {
        void onRunEditedCell(int cellIndex);
    }

    private static final String ARG_CELL_INDEX = "cell_index";
    private static final String INDENT = "    ";
    private static final String[] CODE_KEYS = {"⇥", "⇤", ":", "(", ")", "[", "]", "{", "}", "\"", "'",
            "=", "#", "_", ".", ",", "+", "-", "*", "/", "<", ">", "!", "%", "@"};
    private static final String[] MARKDOWN_KEYS = {"#", "*", "_", "`", "$", "-", ">", "[", "]", "(", ")",
            "|", "!", "\\", "^", "{", "}", "⇥"};

    private DialogCellEditorBinding binding;
    private NotebookViewerViewModel viewModel;
    private int cellIndex;
    private boolean isCode;
    private String originalSource = "";
    private boolean isDark;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Object> highlightSpans = new ArrayList<>();
    private final Runnable highlightTask = this::applyHighlight;
    private boolean selfEdit = false;
    private int pendingIndentAt = -1;

    // Completions from the connected kernel (Termux or SSH). Nothing is computed on the phone.
    private static final long COMPLETE_DELAY_MS = 250;
    private static final int MAX_SUGGESTIONS = 30;
    private final ExecutorService completeExecutor = Executors.newSingleThreadExecutor();
    private final Runnable completeTask = this::requestCompletions;
    /** Incremented per request; replies for older requests are dropped. */
    private int completeGeneration;
    private boolean suppressCompletion;

    public static CellEditorDialog newInstance(int cellIndex) {
        CellEditorDialog dialog = new CellEditorDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_CELL_INDEX, cellIndex);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_Pynb_FullScreenDialog);
        cellIndex = requireArguments().getInt(ARG_CELL_INDEX);
        viewModel = new ViewModelProvider(requireActivity()).get(NotebookViewerViewModel.class);
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        isDark = night == Configuration.UI_MODE_NIGHT_YES;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog instanceof ComponentDialog) {
            ((ComponentDialog) dialog).getOnBackPressedDispatcher().addCallback(this,
                    new OnBackPressedCallback(true) {
                        @Override
                        public void handleOnBackPressed() {
                            closeWithConfirmation();
                        }
                    });
        } else {
            dialog.setOnKeyListener((d, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    closeWithConfirmation();
                    return true;
                }
                return keyCode == android.view.KeyEvent.KEYCODE_BACK;
            });
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogCellEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Notebook nb = viewModel.getNotebook().getValue();
        if (nb == null || cellIndex < 0 || cellIndex >= nb.getCells().size()) {
            dismissAllowingStateLoss();
            return;
        }
        NotebookCell cell = nb.getCells().get(cellIndex);
        isCode = cell.isCode();
        originalSource = cell.getSource();

        binding.editorToolbar.setTitle(getString(isCode ? R.string.editor_title_code
                : R.string.editor_title_markdown, cellIndex + 1));
        binding.editorToolbar.setNavigationOnClickListener(v -> closeWithConfirmation());
        binding.editorToolbar.inflateMenu(R.menu.menu_cell_editor);
        binding.editorToolbar.getMenu().findItem(R.id.action_editor_run).setVisible(isCode);
        binding.editorToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_editor_done) {
                apply(false);
                return true;
            } else if (item.getItemId() == R.id.action_editor_run) {
                apply(true);
                return true;
            }
            return false;
        });

        EditText editor = binding.etCellSource;
        Float scale = viewModel.getFontScale().getValue();
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * (scale != null ? scale : 1f));
        editor.setTypeface(isCode ? Typeface.MONOSPACE : Typeface.DEFAULT);
        editor.setHorizontallyScrolling(false);
        if (savedInstanceState == null) {
            editor.setText(originalSource);
            editor.setSelection(editor.length());
        }
        editor.addTextChangedListener(watcher);
        if (isCode) {
            applyHighlight();
        }
        buildKeyBar(isCode ? CODE_KEYS : MARKDOWN_KEYS);
        editor.requestFocus();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(highlightTask);
        handler.removeCallbacks(completeTask);
        completeGeneration++;
        binding = null;
        super.onDestroyView();
    }

    // ------------------------------------------------------------------ apply / close

    private String currentText() {
        return binding.etCellSource.getText() != null ? binding.etCellSource.getText().toString() : "";
    }

    private void apply(boolean run) {
        viewModel.updateCellSource(cellIndex, currentText());
        if (run && getActivity() instanceof Host) {
            ((Host) getActivity()).onRunEditedCell(cellIndex);
        }
        dismiss();
    }

    private void closeWithConfirmation() {
        if (binding == null || currentText().equals(originalSource)) {
            dismiss();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editor_discard_title)
                .setMessage(R.string.editor_discard_message)
                .setPositiveButton(R.string.discard, (d, w) -> dismiss())
                .setNegativeButton(R.string.editor_keep_editing, null)
                .show();
    }

    // ------------------------------------------------------------------ key bar

    private void buildKeyBar(String[] keys) {
        LinearLayout bar = binding.layoutKeyBar;
        bar.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (String key : keys) {
            MaterialButton button = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(key);
            button.setAllCaps(false);
            button.setTypeface(Typeface.MONOSPACE);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setInsetTop(0);
            button.setInsetBottom(0);
            int padH = Math.round(12 * density);
            button.setPadding(padH, 0, padH, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(36 * density));
            lp.setMargins(Math.round(3 * density), 0, Math.round(3 * density), 0);
            button.setLayoutParams(lp);
            button.setOnClickListener(v -> onKey(key));
            bar.addView(button);
        }
    }

    private void onKey(String key) {
        EditText editor = binding.etCellSource;
        Editable text = editor.getText();
        if (text == null) return;
        int start = Math.max(0, Math.min(editor.getSelectionStart(), editor.getSelectionEnd()));
        int end = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        if ("⇥".equals(key)) {
            if (spansLines(text, start, end)) {
                shiftLines(text, start, end, true);
            } else {
                text.replace(start, end, INDENT);
            }
        } else if ("⇤".equals(key)) {
            shiftLines(text, start, end, false);
        } else {
            text.replace(start, end, key);
        }
    }

    private static boolean spansLines(CharSequence text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == '\n') return true;
        }
        return false;
    }

    /** Indents or unindents every line touched by the selection. */
    private void shiftLines(Editable text, int start, int end, boolean indent) {
        int lineStart = lastNewline(text, start) + 1;
        List<Integer> starts = new ArrayList<>();
        int pos = lineStart;
        while (true) {
            starts.add(pos);
            int nl = indexOf(text, '\n', pos);
            if (nl < 0 || nl >= end || (nl + 1 == end && end > start)) break;
            pos = nl + 1;
        }
        selfEdit = true;
        try {
            for (int i = starts.size() - 1; i >= 0; i--) {
                int ls = starts.get(i);
                if (indent) {
                    text.insert(ls, INDENT);
                } else {
                    int remove = 0;
                    if (ls < text.length() && text.charAt(ls) == '\t') {
                        remove = 1;
                    } else {
                        while (remove < INDENT.length() && ls + remove < text.length()
                                && text.charAt(ls + remove) == ' ') remove++;
                    }
                    if (remove > 0) text.delete(ls, ls + remove);
                }
            }
        } finally {
            selfEdit = false;
        }
        scheduleHighlight();
    }

    private static int lastNewline(CharSequence text, int before) {
        for (int i = before - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') return i;
        }
        return -1;
    }

    private static int indexOf(CharSequence text, char c, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) == c) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ auto-indent & highlight

    private final TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!selfEdit && isCode && before == 0 && count == 1 && s.charAt(start) == '\n') {
                pendingIndentAt = start + 1;
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (pendingIndentAt >= 0) {
                int at = pendingIndentAt;
                pendingIndentAt = -1;
                String indent = indentFor(s, at - 1);
                if (!indent.isEmpty()) {
                    selfEdit = true;
                    try {
                        s.insert(at, indent);
                    } finally {
                        selfEdit = false;
                    }
                }
            }
            if (isCode) {
                scheduleHighlight();
                if (!selfEdit) scheduleCompletion();
            }
        }
    };

    /** Indentation for a new line after the newline at {@code newlineIndex}. */
    static String indentFor(CharSequence text, int newlineIndex) {
        int lineStart = lastNewline(text, newlineIndex) + 1;
        StringBuilder indent = new StringBuilder();
        int i = lineStart;
        while (i < newlineIndex && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            indent.append(text.charAt(i));
            i++;
        }
        String line = text.subSequence(lineStart, newlineIndex).toString();
        int hash = line.indexOf('#');
        String code = (hash >= 0 ? line.substring(0, hash) : line).trim();
        if (code.endsWith(":")) {
            indent.append(INDENT);
        }
        return indent.toString();
    }

    // ------------------------------------------------------------------ completions

    @Override
    public void onDestroy() {
        completeExecutor.shutdownNow();
        super.onDestroy();
    }

    private void scheduleCompletion() {
        handler.removeCallbacks(completeTask);
        if (suppressCompletion) {
            // A suggestion was just inserted: no new suggestions, but keep the parameter hint.
            suppressCompletion = false;
            hideSuggestions();
            return;
        }
        if (!KernelManager.get(requireContext()).currentState().isConnected()) {
            hideSuggestions();
            hideSignature();
            return;
        }
        handler.postDelayed(completeTask, COMPLETE_DELAY_MS);
    }

    private void requestCompletions() {
        if (binding == null) return;
        EditText editor = binding.etCellSource;
        int cursor = editor.getSelectionStart();
        if (cursor != editor.getSelectionEnd() || cursor <= 0) {
            hideSuggestions();
            hideSignature();
            return;
        }
        String code = currentText();
        // Name suggestions only right after a name or '.', but the parameter hint is asked for
        // after every edit, so it updates on '(' and ',' and disappears after ')'.
        char before = code.charAt(cursor - 1);
        boolean wantMatches = Character.isJavaIdentifierPart(before) || before == '.';
        if (!wantMatches) hideSuggestions();
        int generation = ++completeGeneration;
        int cursorCodePoints = code.codePointCount(0, cursor);
        KernelManager kernel = KernelManager.get(requireContext());
        completeExecutor.execute(() -> {
            JSONObject reply = kernel.complete(code, cursorCodePoints);
            handler.post(() -> {
                if (binding == null || generation != completeGeneration) return;
                // Only show results for the text and cursor they were computed for.
                if (!code.equals(currentText()) || binding.etCellSource.getSelectionStart() != cursor) return;
                showSignature(reply != null ? reply.optJSONObject("signature") : null);
                if (wantMatches) showSuggestions(reply, code, cursor);
            });
        });
    }

    private void showSuggestions(@Nullable JSONObject reply, String code, int cursor) {
        JSONArray matches = reply != null ? reply.optJSONArray("matches") : null;
        int prefixCodePoints = reply != null ? reply.optInt("prefix_length", 0) : 0;
        if (matches == null || matches.length() == 0) {
            hideSuggestions();
            return;
        }
        int start;
        try {
            start = Character.offsetByCodePoints(code, cursor, -prefixCodePoints);
        } catch (IndexOutOfBoundsException e) {
            hideSuggestions();
            return;
        }
        String typed = code.substring(start, cursor);
        if (matches.length() == 1 && typed.equals(matches.optJSONObject(0).optString("text"))) {
            hideSuggestions(); // already fully typed
            return;
        }

        LinearLayout bar = binding.layoutSuggestions;
        bar.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int muted = ContextCompat.getColor(requireContext(), R.color.text_muted_light);
        for (int i = 0; i < Math.min(matches.length(), MAX_SUGGESTIONS); i++) {
            JSONObject m = matches.optJSONObject(i);
            if (m == null) continue;
            String name = m.optString("text");
            if (name.isEmpty()) continue;
            SpannableStringBuilder label = new SpannableStringBuilder(name);
            String tag = kindTag(m.optString("type"));
            if (!tag.isEmpty()) {
                int at = label.length();
                label.append("  ").append(tag);
                label.setSpan(new ForegroundColorSpan(muted), at, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                label.setSpan(new RelativeSizeSpan(0.8f), at, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            MaterialButton chip = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.borderlessButtonStyle);
            chip.setText(label);
            chip.setAllCaps(false);
            chip.setTypeface(Typeface.MONOSPACE);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setInsetTop(0);
            chip.setInsetBottom(0);
            int padH = Math.round(10 * density);
            chip.setPadding(padH, 0, padH, 0);
            chip.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(36 * density)));
            chip.setOnClickListener(v -> insertCompletion(code, start, cursor, name));
            bar.addView(chip);
        }
        binding.suggestionScroll.scrollTo(0, 0);
        binding.suggestionScroll.setVisibility(View.VISIBLE);
    }

    /** Replaces the typed prefix with {@code name}, if the text hasn't changed since. */
    private void insertCompletion(String code, int start, int cursor, String name) {
        if (binding == null) return;
        EditText editor = binding.etCellSource;
        Editable text = editor.getText();
        if (text == null || !code.equals(text.toString()) || editor.getSelectionStart() != cursor) {
            hideSuggestions();
            return;
        }
        suppressCompletion = true; // don't immediately suggest again for the inserted name
        text.replace(start, cursor, name);
        editor.setSelection(start + name.length());
        hideSuggestions();
    }

    /** Shows {@code range(start, stop[, step])} with the parameter being typed highlighted. */
    private void showSignature(@Nullable JSONObject sig) {
        if (binding == null) return;
        String label = sig != null ? sig.optString("label", "") : "";
        if (label.isEmpty()) {
            hideSignature();
            return;
        }
        SpannableStringBuilder text = new SpannableStringBuilder(label);
        JSONArray params = sig.optJSONArray("params");
        int active = sig.optInt("active", -1);
        JSONArray span = params != null && active >= 0 ? params.optJSONArray(active) : null;
        if (span != null) {
            int start = span.optInt(0, -1);
            int end = span.optInt(1, -1);
            if (start >= 0 && end > start && end <= label.length()) {
                // Offsets are Python string indexes (code points); convert for Java's UTF-16 string.
                start = label.offsetByCodePoints(0, Math.min(start, label.codePointCount(0, label.length())));
                end = label.offsetByCodePoints(0, Math.min(end, label.codePointCount(0, label.length())));
                text.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.primaryOrange)),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        String doc = sig.optString("doc", "");
        if (!doc.isEmpty()) {
            int at = text.length();
            text.append('\n').append(doc);
            text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_muted_light)),
                    at, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(0.85f), at, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        binding.tvSignature.setText(text);
        binding.tvSignature.setVisibility(View.VISIBLE);
    }

    private void hideSignature() {
        if (binding == null) return;
        binding.tvSignature.setVisibility(View.GONE);
    }

    private void hideSuggestions() {
        if (binding == null) return;
        binding.suggestionScroll.setVisibility(View.GONE);
        binding.layoutSuggestions.removeAllViews();
    }

    private static String kindTag(String type) {
        switch (type) {
            case "function": return "ƒ";
            case "class": return "class";
            case "module": return "mod";
            case "keyword": return "kw";
            default: return "";
        }
    }

    private void scheduleHighlight() {
        handler.removeCallbacks(highlightTask);
        handler.postDelayed(highlightTask, 120);
    }

    private void applyHighlight() {
        if (binding == null || !isCode) return;
        Editable text = binding.etCellSource.getText();
        if (text == null) return;
        for (Object span : highlightSpans) {
            text.removeSpan(span);
        }
        highlightSpans.clear();
        CharSequence highlighted = PythonSyntaxHighlighter.highlight(text.toString(), isDark);
        if (!(highlighted instanceof Spanned)) return;
        Spanned spanned = (Spanned) highlighted;
        for (Object span : spanned.getSpans(0, spanned.length(), Object.class)) {
            Object copy = copySpan(span);
            if (copy == null) continue;
            int s = spanned.getSpanStart(span);
            int e = spanned.getSpanEnd(span);
            if (s < 0 || e > text.length() || s >= e) continue;
            text.setSpan(copy, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            highlightSpans.add(copy);
        }
    }

    private static Object copySpan(Object span) {
        if (span instanceof ForegroundColorSpan) {
            return new ForegroundColorSpan(((ForegroundColorSpan) span).getForegroundColor());
        }
        if (span instanceof StyleSpan) {
            return new StyleSpan(((StyleSpan) span).getStyle());
        }
        return null;
    }
}
