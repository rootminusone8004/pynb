package com.pynb.app.model;

import android.net.Uri;
import android.text.format.DateUtils;

import androidx.documentfile.provider.DocumentFile;

import com.pynb.app.util.FileUtils;

import java.io.Serializable;

/**
 * Represents a file or subfolder in the Folder Workspace Explorer.
 */
public class FolderItem implements Serializable {

    private final String name;
    private final Uri uri;
    private final boolean isDirectory;
    private final boolean isParent;
    private final boolean isNotebook;
    private final long size;
    private final long lastModified;
    private transient DocumentFile documentFile;

    public FolderItem(String name, Uri uri, boolean isDirectory, boolean isParent,
                      boolean isNotebook, long size, long lastModified, DocumentFile documentFile) {
        this.name = name != null ? name : "Untitled";
        this.uri = uri;
        this.isDirectory = isDirectory;
        this.isParent = isParent;
        this.isNotebook = isNotebook;
        this.size = size;
        this.lastModified = lastModified;
        this.documentFile = documentFile;
    }

    public static FolderItem createParentItem() {
        return new FolderItem("..", null, true, true, false, 0, 0, null);
    }

    public static FolderItem fromDocumentFile(DocumentFile file) {
        if (file == null) return null;
        String name = file.getName();
        boolean isDir = file.isDirectory();
        boolean isNb = name != null && name.toLowerCase().endsWith(".ipynb");
        long size = isDir ? 0 : file.length();
        long lastMod = file.lastModified();
        return new FolderItem(name, file.getUri(), isDir, false, isNb, size, lastMod, file);
    }

    public String getName() {
        return name;
    }

    public Uri getUri() {
        return uri;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public boolean isParent() {
        return isParent;
    }

    public boolean isNotebook() {
        return isNotebook;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public DocumentFile getDocumentFile() {
        return documentFile;
    }

    public void setDocumentFile(DocumentFile documentFile) {
        this.documentFile = documentFile;
    }

    public String getFormattedSize() {
        if (isDirectory) return "";
        return FileUtils.formatFileSize(size);
    }

    public String getFormattedDate() {
        if (lastModified <= 0) return "";
        return DateUtils.getRelativeTimeSpanString(
                lastModified,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString();
    }
}
