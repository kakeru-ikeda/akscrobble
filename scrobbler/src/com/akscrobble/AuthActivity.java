package com.akscrobble;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.Strings;
import java.net.URLEncoder;

public final class AuthActivity extends Activity {
    private static final String DESKTOP_UA = "Mozilla/5.0 (Linux; Android 9) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private TextView status;
    private WebView webView;
    private CheckBox replaceUa;
    private String token;
    private String approvalUrl;
    private boolean automaticSessionAttempted;
    private LastfmClient client;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        client = new LastfmClient(this, "", "");
        // manifest の android:label は固定文字列なので、表示名はここで言語に合わせる。
        setTitle(Ui.s(this, Strings.AUTH_TITLE));
        Ui.Screen screen = Ui.screen(this, Ui.s(this, Strings.AUTH_TITLE),
                Ui.backLink(this), false);
        LinearLayout body = screen.body;
        body.addView(Ui.button(this, Ui.s(this, Strings.AUTH_OPEN_APPROVAL), true,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { requestToken(true, false); }
                }));
        body.addView(Ui.button(this, Ui.s(this, Strings.AUTH_GET_SESSION), false,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { requestSession(false); }
                }));
        body.addView(Ui.button(this, Ui.s(this, Strings.AUTH_SHOW_PC_URL), false,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        if (approvalUrl == null) requestToken(false, true);
                        else showApprovalUrl(true);
                    }
                }));
        replaceUa = Ui.checkBox(this, Ui.s(this, Strings.AUTH_USE_NEW_UA), false);
        replaceUa.setChecked(false);
        replaceUa.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { applyUserAgent(); }
        });
        body.addView(replaceUa);

        status = Ui.text(this, "", 12, Ui.FG);
        status.setTextIsSelectable(true);
        body.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        try {
            webView = new WebView(this);
            webView.setBackgroundColor(Ui.BG);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    if (!automaticSessionAttempted && token != null && url != null
                            && url.contains("/api/auth")) {
                        automaticSessionAttempted = true;
                        requestSession(true);
                    }
                }
            });
            body.addView(webView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } catch (Throwable error) {
            webView = null;
            status.setText(Ui.s(this, Strings.AUTH_WEBVIEW_UNAVAILABLE));
        }
        new ConfigStore(this).writeTemplateIfAbsent();
    }

    private void requestToken(final boolean openWebView, final boolean copyUrl) {
        show(Ui.s(this, Strings.AUTH_GETTING_TOKEN));
        NetThread.post(new Runnable() {
            @Override public void run() {
                LastfmResponse response = client.getToken();
                if (!response.isOk() || blank(response.token)) {
                    showFromWorker(Ui.s(AuthActivity.this, Strings.AUTH_TOKEN_FAILED, reason(response)));
                    return;
                }
                token = response.token;
                automaticSessionAttempted = false;
                approvalUrl = buildApprovalUrl(response.apiKey, token);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        showApprovalUrl(copyUrl);
                        if (openWebView) {
                            if (webView == null) show(Ui.s(AuthActivity.this,
                                    Strings.AUTH_OPEN_ON_PC, approvalUrl));
                            else { applyUserAgent(); webView.loadUrl(approvalUrl); }
                        }
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

    private void applyUserAgent() {
        if (webView == null) return;
        WebSettings settings = webView.getSettings();
        settings.setUserAgentString(replaceUa.isChecked() ? DESKTOP_UA : null);
    }

    private void showApprovalUrl(boolean copy) {
        show(Ui.s(this, Strings.AUTH_APPROVAL_URL, approvalUrl));
        if (copy && copyToClipboard(approvalUrl)) {
            show(Ui.s(this, Strings.AUTH_APPROVAL_URL_COPIED, approvalUrl));
        }
    }
    private boolean copyToClipboard(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
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
