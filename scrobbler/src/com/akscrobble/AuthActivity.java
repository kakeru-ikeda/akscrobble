package com.akscrobble;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.Strings;
import java.net.URLEncoder;

public final class AuthActivity extends Activity {
    private TextView status;
    private String token;
    private String approvalUrl;
    private boolean tokenRequestInProgress;
    private LastfmClient client;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        client = new LastfmClient(this, "", "");
        // manifest の android:label は固定文字列なので、表示名はここで言語に合わせる。
        setTitle(Ui.s(this, Strings.AUTH_TITLE));
        Ui.Screen screen = Ui.screen(this, Ui.s(this, Strings.AUTH_TITLE),
                Ui.backLink(this), false);
        LinearLayout body = screen.body;
        body.addView(Ui.button(this, Ui.s(this, Strings.AUTH_COPY_APPROVAL_URL), true,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { copyApprovalUrl(); }
                }));
        body.addView(Ui.button(this, Ui.s(this, Strings.AUTH_GET_SESSION), false,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { requestSession(false); }
                }));

        status = Ui.text(this, "", 12, Ui.FG);
        status.setTextIsSelectable(true);
        body.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new ConfigStore(this).writeTemplateIfAbsent();
    }

    private void copyApprovalUrl() {
        if (!blank(token) && !blank(approvalUrl)) {
            showApprovalUrl();
            return;
        }
        if (tokenRequestInProgress) return;
        requestToken();
    }

    private void requestToken() {
        tokenRequestInProgress = true;
        show(Ui.s(this, Strings.AUTH_GETTING_TOKEN));
        NetThread.post(new Runnable() {
            @Override public void run() {
                final LastfmResponse response = client.getToken();
                if (!response.isOk() || blank(response.token)) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            tokenRequestInProgress = false;
                            show(Ui.s(AuthActivity.this, Strings.AUTH_TOKEN_FAILED, reason(response)));
                        }
                    });
                    return;
                }
                final String newToken = response.token;
                final String newApprovalUrl = buildApprovalUrl(response.apiKey, newToken);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        token = newToken;
                        approvalUrl = newApprovalUrl;
                        tokenRequestInProgress = false;
                        showApprovalUrl();
                    }
                });
            }
        });
    }

    private void requestSession(final boolean silentFailure) {
        if (blank(token)) { if (!silentFailure) show(Ui.s(this, Strings.AUTH_TOKEN_REQUIRED)); return; }
        NetThread.post(new Runnable() {
            @Override public void run() {
                LastfmResponse response = client.getSession(token);
                if (!response.isOk() || blank(response.sessionKey)) {
                    if (!silentFailure) showFromWorker(Ui.s(AuthActivity.this,
                            Strings.AUTH_SESSION_FAILED, reason(response)));
                    return;
                }
                saveSession(response.sessionKey, response.sessionName, response.apiKey);
            }
        });
    }

    private void saveSession(String session, String user, String apiKey) {
        Prefs prefs = new Prefs(this);
        prefs.putString(Prefs.SESSION_KEY, session);
        prefs.putString(Prefs.SESSION_API_KEY, clean(apiKey));
        prefs.putString(Prefs.USER_NAME, clean(user));
        completeAuthentication();
    }

    private void completeAuthentication() {
        Prefs prefs = new Prefs(this);
        prefs.putBoolean(Prefs.AUTH_INVALID, false);
        prefs.putBoolean(Prefs.SIGNER_UNAVAILABLE, false);
        prefs.putBoolean(Prefs.SERVICE_PROBLEM, false);
        prefs.putBoolean(Prefs.MIGRATION_NOTICE, false);
        prefs.putBoolean(Prefs.SETUP_DISMISSED, true);
        Intent flush = new Intent(this, ScrobbleService.class);
        flush.setAction(com.akscrobble.core.Const.ACTION_FLUSH);
        startService(flush);
        runOnUiThread(new Runnable() { @Override public void run() { finish(); } });
    }

    private void showApprovalUrl() {
        boolean copied = copyToClipboard(approvalUrl);
        String message = copied ? Strings.AUTH_AUTHORIZATION_INSTRUCTIONS
                : Strings.AUTH_COPY_UNAVAILABLE;
        show(Ui.s(this, message) + "\n\n" + approvalUrl);
    }

    private boolean copyToClipboard(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return false;
            clipboard.setPrimaryClip(ClipData.newPlainText("Last.fm approval URL", text));
            return true;
        } catch (RuntimeException unavailable) { return false; }
    }
    private void show(final String text) { status.setText(text); }
    private void showFromWorker(final String text) {
        runOnUiThread(new Runnable() { @Override public void run() { show(text); } });
    }
    private static String buildApprovalUrl(String key, String token) {
        try { return "https://www.last.fm/api/auth/?api_key=" + URLEncoder.encode(key, "UTF-8")
                + "&token=" + URLEncoder.encode(token, "UTF-8"); }
        catch (Exception impossible) { return ""; }
    }
    private static String reason(LastfmResponse response) {
        return response.errorMessage == null ? "unknown error" : response.errorMessage;
    }
    private static boolean blank(String value) { return clean(value).length() == 0; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
