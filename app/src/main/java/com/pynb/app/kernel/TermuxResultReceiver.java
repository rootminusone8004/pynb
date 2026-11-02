package com.pynb.app.kernel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Receives the result Termux sends when the kernel process ends (through the PendingIntent
 * passed with RUN_COMMAND). While connecting this tells us why the kernel failed to start,
 * e.g. a wrong .venv path or {@code allow-external-apps} not enabled.
 */
public class TermuxResultReceiver extends BroadcastReceiver {

    static final String EXTRA_SESSION = "pynb_kernel_session";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle result = intent.getBundleExtra("result");
        String session = intent.getStringExtra(EXTRA_SESSION);
        if (result == null) {
            return;
        }
        String stdout = result.getString("stdout", "");
        String stderr = result.getString("stderr", "");
        int exitCode = result.getInt("exitCode", -1);
        int err = result.getInt("err", -1);
        String errmsg = result.getString("errmsg", "");
        TermuxKernel.get(context).onProcessFinished(session, exitCode, err, errmsg, stdout, stderr);
    }
}
