package com.pynb.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.pynb.app.databinding.DialogImageViewerBinding;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Fullscreen dialog for inspecting plot images with pinch-to-zoom and pan.
 */
public class ImageViewerDialog extends Dialog {

    private final Bitmap bitmap;
    private final String title;
    private DialogImageViewerBinding binding;

    public ImageViewerDialog(@NonNull Context context, Bitmap bitmap, String title) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.bitmap = bitmap;
        this.title = title;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        binding = DialogImageViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        if (title != null) {
            binding.dialogImageToolbar.setTitle(title);
        }

        binding.dialogImageToolbar.setNavigationOnClickListener(v -> dismiss());

        if (bitmap != null) {
            binding.zoomableImageView.setImageBitmap(bitmap);
        }

        binding.btnShareImage.setOnClickListener(v -> shareImage());
    }

    private void shareImage() {
        if (bitmap == null) return;
        try {
            Context context = getContext();
            File cachePath = new File(context.getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "notebook_figure.png");
            try (FileOutputStream stream = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }

            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", imageFile);
            if (contentUri != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.setDataAndType(contentUri, context.getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.setType("image/png");
                context.startActivity(Intent.createChooser(shareIntent, "Share Figure"));
            }
        } catch (Exception ignored) {}
    }
}
