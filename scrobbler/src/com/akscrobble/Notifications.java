package com.akscrobble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.akscrobble.core.Const;

public final class Notifications {
    public static Notification build(Context context, String title, String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(
                    Const.NOTIF_CHANNEL, "AKScrobble", NotificationManager.IMPORTANCE_LOW));
            builder = new Notification.Builder(context, Const.NOTIF_CHANNEL);
        } else {
            builder = new Notification.Builder(context);
        }

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT);
        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();
    }

    private Notifications() {}
}
