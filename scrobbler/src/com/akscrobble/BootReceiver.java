package com.akscrobble;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.akscrobble.core.Const;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || !new Prefs(context).getBoolean(Prefs.SERVICE_ENABLED)) {
            return;
        }
        Intent service = new Intent(context, ScrobbleService.class).setAction(Const.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }
}
