package com.pynb.app.kernel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable snapshot of the kernel connection (Termux or SSH), published through LiveData. */
public final class KernelState {

    public enum Status { DISCONNECTED, CONNECTING, IDLE, BUSY, ERROR }

    @NonNull public final Status status;
    @Nullable public final String message;
    @Nullable public final String pythonVersion;
    @Nullable public final String venvPath;
    /** Where the kernel runs: "Termux" or "user@host". */
    @Nullable public final String target;
    /** Set with {@link Status#ERROR} when an SSH server's host key needs the user's confirmation. */
    @Nullable public final KnownHosts.Prompt hostKeyPrompt;

    public KernelState(@NonNull Status status, @Nullable String message,
                       @Nullable String pythonVersion, @Nullable String venvPath) {
        this(status, message, pythonVersion, venvPath, "Termux", null);
    }

    public KernelState(@NonNull Status status, @Nullable String message, @Nullable String pythonVersion,
                       @Nullable String venvPath, @Nullable String target,
                       @Nullable KnownHosts.Prompt hostKeyPrompt) {
        this.status = status;
        this.message = message;
        this.pythonVersion = pythonVersion;
        this.venvPath = venvPath;
        this.target = target;
        this.hostKeyPrompt = hostKeyPrompt;
    }

    public static KernelState disconnected() {
        return new KernelState(Status.DISCONNECTED, null, null, null, null, null);
    }

    public boolean isConnected() {
        return status == Status.IDLE || status == Status.BUSY;
    }

    public boolean isRemote() {
        return target != null && !"Termux".equals(target);
    }

    public KernelState with(@NonNull Status newStatus) {
        return new KernelState(newStatus, null, pythonVersion, venvPath, target, null);
    }

    /** Short label for the toolbar subtitle. */
    @NonNull
    public String label() {
        String where = target != null ? target : "Termux";
        String python = "Python " + (pythonVersion != null ? pythonVersion : "");
        if (isRemote()) python = where + " · " + python;
        switch (status) {
            case CONNECTING: return where + ": connecting…";
            case IDLE: return python + " ● idle";
            case BUSY: return python + " ● busy";
            case ERROR: return where + ": not connected";
            default: return "";
        }
    }
}
