package com.akscrobble;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.akscrobble.core.Const;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.Strings;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class QueueActivity extends Activity {
    private TextView queueText;
    private boolean active;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // manifest の android:label は固定文字列なので、表示名はここで言語に合わせる。
        setTitle(Ui.s(this, Strings.QUEUE_TITLE));
        LinearLayout content = Ui.screen(this, Ui.s(this, Strings.QUEUE_TITLE),
                Ui.backLink(this), true).body;
        content.addView(Ui.button(this, Ui.s(this, Strings.QUEUE_REFRESH), false, new View.OnClickListener() {
            @Override public void onClick(View view) { refresh(); }
        }));
        content.addView(Ui.button(this, Ui.s(this, Strings.QUEUE_SEND_NOW), true, new View.OnClickListener() {
            @Override public void onClick(View view) {
                startService(new Intent(QueueActivity.this, ScrobbleService.class)
                        .setAction(Const.ACTION_FLUSH));
            }
        }));
        content.addView(Ui.button(this, Ui.s(this, Strings.QUEUE_RETRY_DEAD), false, new View.OnClickListener() {
            @Override public void onClick(View view) {
                confirm(Ui.s(QueueActivity.this, Strings.QUEUE_CONFIRM_RETRY_DEAD), new Runnable() {
                    @Override public void run() { changeDead(true); }
                });
            }
        }));
        content.addView(Ui.button(this, Ui.s(this, Strings.QUEUE_DELETE_DEAD), false, new View.OnClickListener() {
            @Override public void onClick(View view) {
                confirm(Ui.s(QueueActivity.this, Strings.QUEUE_CONFIRM_DELETE_DEAD), new Runnable() {
                    @Override public void run() { changeDead(false); }
                });
            }
        }));

        queueText = Ui.mono(this, "");
        content.addView(queueText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        refresh();
    }

    @Override protected void onPause() {
        active = false;
        super.onPause();
    }

    private void refresh() {
        queueText.setText(Ui.s(this, Strings.QUEUE_LOADING));
        NetThread.post(new Runnable() {
            @Override public void run() {
                ScrobbleDb db = new ScrobbleDb(getApplicationContext());
                try {
                    final int pending = db.countPending();
                    final int dead = db.countDead();
                    final List<PendingScrobble> items = db.recent(200);
                    final String text = render(pending, dead, items);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { if (active) queueText.setText(text); }
                    });
                } catch (final Throwable error) {
                    Log.e(Const.TAG, "QueueActivity refresh failed", error);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (active) queueText.setText(Ui.s(QueueActivity.this,
                                    Strings.QUEUE_LOAD_FAILED,
                                    error.getClass().getSimpleName()));
                        }
                    });
                } finally { db.close(); }
            }
        });
    }

    private void changeDead(final boolean retry) {
        NetThread.post(new Runnable() {
            @Override public void run() {
                ScrobbleDb db = new ScrobbleDb(getApplicationContext());
                try { if (retry) db.retryDead(); else db.purgeDead(); }
                finally { db.close(); }
                runOnUiThread(new Runnable() {
                    @Override public void run() { if (active) refresh(); }
                });
            }
        });
    }

    private String render(int pending, int dead, List<PendingScrobble> items) {
        StringBuilder out = new StringBuilder();
        out.append("pending=").append(pending).append(" dead=").append(dead)
                .append(' ').append(Ui.s(this, Strings.QUEUE_TOTAL, pending + dead)).append('\n');
        SimpleDateFormat dates = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (PendingScrobble item : items) {
            out.append('\n').append('#').append(item.id).append(" [").append(nz(item.state))
                    .append("] a=").append(item.attempts).append(' ')
                    .append(dates.format(new Date(item.startedAtSec * 1000L))).append(' ')
                    .append(nz(item.artist)).append(" - ").append(nz(item.track)).append('\n')
                    .append("album=").append(nz(item.album)).append(" src=")
                    .append(nz(item.sourcePkg)).append(" err=").append(nz(item.lastError)).append('\n');
        }
        return out.toString();
    }

    private void confirm(String message, final Runnable action) {
        new AlertDialog.Builder(this).setMessage(message)
                .setNegativeButton(Ui.s(this, Strings.QUEUE_CANCEL), null)
                .setPositiveButton(Ui.s(this, Strings.QUEUE_EXECUTE), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { action.run(); }
                }).show();
    }

    private static String nz(String value) { return value == null ? "" : value; }
}
