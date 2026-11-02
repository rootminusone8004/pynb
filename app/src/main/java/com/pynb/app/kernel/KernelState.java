package com.pynb.app.kernel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable snapshot of the Termux kernel connection, published through LiveData. */
public final class KernelState {

    public enum Status { DISCONNECTED, CONNECTING, IDLE, BUSY, ERROR }

    @NonNull public final Status status;
    @Nullable public final String message;
    @Nullable public final String pythonVersion;
    @Nullable public final String venvPath;

    public KernelState(@NonNull Status status, @Nullable String message,
                       @Nullable String pythonVersion, @Nullable String venvPath) {
        this.status = status;
        this.message = message;
        this.pythonVersion = pythonVersion;
        this.venvPath = venvPath;
    }

    public static KernelState disconnected() {
        return new KernelState(Status.DISCONNECTED, null, null, null);
    }

    public boolean isConnected() {
        return status == Status.IDLE || status == Status.BUSY;
    }

    public KernelState with(@NonNull Status newStatus) {
        return new KernelState(newStatus, null, pythonVersion, venvPath);
    }

    /** Short label for the toolbar subtitle. */
    @NonNull
    public String label() {
        switch (status) {
            case CONNECTING: return "Termux: connecting…";
            case IDLE: return "Python " + (pythonVersion != null ? pythonVersion : "") + " ● idle";
            case BUSY: return "Python " + (pythonVersion != null ? pythonVersion : "") + " ● busy";
            case ERROR: return "Termux: not connected";
            default: return "";
        }
    }
}
