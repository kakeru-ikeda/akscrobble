package com.akscrobble;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import com.akscrobble.core.BackoffPolicy;
import com.akscrobble.core.Const;
import com.akscrobble.core.Credentials;
import com.akscrobble.core.LastfmSig;
import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.PendingScrobble;
import com.akscrobble.core.QueuePolicy;
import com.akscrobble.core.SanitizeRule;
import com.akscrobble.core.Sanitizer;
import com.akscrobble.core.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Sender {
    private final Context context;
    private final ScrobbleDb database;
    private final Handler scheduler;
    private final ConnectivityManager connectivity;
    private final PowerManager.WakeLock wakeLock;
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private volatile boolean networkAvailable;
    private boolean callbackRegistered;
    private volatile boolean closed;
    private boolean lastAuthInvalid;
    private int consecutiveFailures;

    private final Runnable retry = new Runnable() {
        @Override public void run() { requestDrain("retry"); }
    };

    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) {
            networkAvailable = true;
            requestDrain("network");
        }

        @Override public void onLost(Network network) {
            networkAvailable = false;
            StatusBus.put("network", "lost");
        }
    };

    public Sender(Context context, ScrobbleDb database, Handler scheduler) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.scheduler = scheduler;
        connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AKScrobble:sender");
        lastAuthInvalid = new Prefs(context).getBoolean(Prefs.AUTH_INVALID);
        if (Build.VERSION.SDK_INT >= 24 && connectivity != null) {
            networkAvailable = connectivity.getActiveNetwork() != null;
            try {
                connectivity.registerDefaultNetworkCallback(networkCallback);
                callbackRegistered = true;
            } catch (RuntimeException error) {
                Log.w(Const.TAG, "Could not register network callback", error);
            }
        }
    }

    public void requestDrain(final String reason) {
        if (closed) return;
        if (!draining.compareAndSet(false, true)) return;
        NetThread.post(new Runnable() {
            @Override public void run() { drain(reason); }
        });
    }

    /** Called by the service supervisor so clearing auth_invalid resumes promptly. */
    public void tick() {
        if (closed) return;
        boolean authInvalid = new Prefs(context).getBoolean(Prefs.AUTH_INVALID);
        if (lastAuthInvalid && !authInvalid) requestDrain("reauthenticated");
        lastAuthInvalid = authInvalid;
    }

    public void close() {
        closed = true;
        scheduler.removeCallbacks(retry);
        if (callbackRegistered && connectivity != null && Build.VERSION.SDK_INT >= 24) {
            try { connectivity.unregisterNetworkCallback(networkCallback); }
            catch (RuntimeException error) { Log.w(Const.TAG, "Network callback cleanup failed", error); }
            callbackRegistered = false;
        }
        if (wakeLock.isHeld()) wakeLock.release();
    }

    /** 認証情報の同一性を判定するための指紋。値そのものは保存しない。 */
    private static String credentialFingerprint(Credentials creds) {
        return LastfmSig.hexMd5(creds.apiKey + "|" + creds.sessionKey);
    }

    private void drain(String reason) {
        boolean shouldRetry = false;
        try {
            Prefs prefs = new Prefs(context);
            Credentials creds = ConfigStore.credentials(context);
            String fingerprint = credentialFingerprint(creds);
            if (prefs.getBoolean(Prefs.AUTH_INVALID)) {
                // 認証情報が当時と同じなら本当に再認証が要る。変わっていれば
                // 設定ファイル差し替えや再認証で直った可能性があるので一度試す。
                // これが無いと、一度でも認証エラーが出たらキューが永久に止まる。
                if (fingerprint.equals(prefs.getString(Prefs.AUTH_INVALID_FOR))) {
                    publishAuthBlocked();
                    return;
                }
                prefs.putBoolean(Prefs.AUTH_INVALID, false);
                lastAuthInvalid = false;
            }
            if (!creds.isComplete()) {
                // 認証情報が未設定なのは「認証拒否」ではなく設定漏れ。
                // AUTH_INVALID を立てると設定を直しても止まったままになる。
                StatusBus.put("sender", "credentials missing");
                shouldRetry = true;
                return;
            }
            if (!isConnected()) {
                StatusBus.put("sender", "offline");
                shouldRetry = true;
                return;
            }
            wakeLock.acquire(60_000L);
            StatusBus.put("sender", "draining (" + reason + ")");
            while (true) {
                List<PendingScrobble> batch = database.takeBatch(50);
                if (batch.isEmpty()) {
                    resetFailures();
                    StatusBus.put("sender", "idle");
                    return;
                }
                LastfmClient client = new LastfmClient(
                        creds.apiKey, creds.apiSecret, creds.sessionKey);
                LastfmResponse response = client.scrobble(sanitize(batch));
                QueuePolicy.Outcome outcome = QueuePolicy.classify(response, maxAttempts(batch));
                if (outcome == QueuePolicy.Outcome.AUTH_BLOCKED) {
                    prefs.putBoolean(Prefs.AUTH_INVALID, true);
                    prefs.putString(Prefs.AUTH_INVALID_FOR, fingerprint);
                    lastAuthInvalid = true;
                    publishAuthBlocked();
                    return;
                }
                if (outcome == QueuePolicy.Outcome.RETRY) {
                    database.markRetry(ids(batch), response.errorMessage);
                    shouldRetry = true;
                    return;
                }
                if (outcome == QueuePolicy.Outcome.DEAD) {
                    database.markDead(ids(batch), response.errorMessage);
                    resetFailures();
                    continue;
                }
                markSuccessfulBatch(batch, response);
                if (response.accepted > 0) {
                    prefs.putLong(Prefs.LAST_SENT_AT, System.currentTimeMillis());
                }
                // 送信が通った時点で認証は有効。古い AUTH_INVALID が残っていたら解除する。
                if (prefs.getBoolean(Prefs.AUTH_INVALID)) {
                    prefs.putBoolean(Prefs.AUTH_INVALID, false);
                    lastAuthInvalid = false;
                }
                resetFailures();
            }
        } catch (Throwable error) {
            Log.e(Const.TAG, "Sender drain failed", error);
            StatusBus.put("sender", "failed: " + error.getClass().getSimpleName());
            shouldRetry = true;
        } finally {
            if (wakeLock.isHeld()) wakeLock.release();
            draining.set(false);
            StatusBus.put("pending", Integer.toString(database.countPending()));
            if (shouldRetry && !closed) scheduleRetry();
        }
    }

    private boolean isConnected() {
        if (connectivity == null) return false;
        if (Build.VERSION.SDK_INT >= 24) return networkAvailable;
        NetworkInfo info = connectivity.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private void markSuccessfulBatch(List<PendingScrobble> batch, LastfmResponse response) {
        List<Long> sent = new ArrayList<Long>();
        List<Long> dead = new ArrayList<Long>();
        for (int i = 0; i < batch.size(); i++) {
            int code = i < response.ignoredCodes.size()
                    ? response.ignoredCodes.get(i).intValue() : 0;
            if (response.accepted < batch.size() && QueuePolicy.isIgnoredPermanent(code)) {
                dead.add(Long.valueOf(batch.get(i).id));
            } else {
                sent.add(Long.valueOf(batch.get(i).id));
            }
        }
        database.markSent(sent);
        database.markDead(dead, "Last.fm ignored scrobble permanently");
    }

    private List<PendingScrobble> sanitize(List<PendingScrobble> batch) {
        Sanitizer sanitizer = SanitizeStore.sanitizer(context);
        List<PendingScrobble> result = new ArrayList<PendingScrobble>(batch.size());
        for (PendingScrobble item : batch) {
            result.add(new PendingScrobble(item.id, item.sourcePkg,
                    sanitizer.apply(SanitizeRule.Scope.ARTIST, item.artist),
                    sanitizer.apply(SanitizeRule.Scope.TRACK, item.track),
                    sanitizer.apply(SanitizeRule.Scope.ALBUM, item.album),
                    sanitizer.apply(SanitizeRule.Scope.ALBUM_ARTIST, item.albumArtist),
                    item.durationMs, item.trackNumber, item.startedAtSec, item.attempts,
                    item.state, item.lastError));
        }
        return result;
    }

    private void scheduleRetry() {
        consecutiveFailures++;
        scheduler.removeCallbacks(retry);
        scheduler.postDelayed(retry, BackoffPolicy.nextDelayMs(consecutiveFailures));
    }

    private void resetFailures() {
        consecutiveFailures = 0;
        scheduler.removeCallbacks(retry);
    }

    private void publishAuthBlocked() {
        StatusBus.put("sender", "reauthentication required");
        StatusBus.put("notification", Ui.s(context, Strings.NOTIF_REAUTH));
    }

    private static int maxAttempts(List<PendingScrobble> batch) {
        int attempts = 0;
        for (PendingScrobble item : batch) attempts = Math.max(attempts, item.attempts);
        return attempts;
    }

    private static List<Long> ids(List<PendingScrobble> batch) {
        List<Long> ids = new ArrayList<Long>(batch.size());
        for (PendingScrobble item : batch) ids.add(Long.valueOf(item.id));
        return ids;
    }
}
