package com.akscrobble;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.akscrobble.core.Credentials;
import com.akscrobble.core.LastfmResponse;
import com.akscrobble.core.Strings;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public final class AuthActivity extends Activity {
    private static final String DESKTOP_UA = "Mozilla/5.0 (Linux; Android 9) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private TextView status;
    private WebView webView;
    private CheckBox replaceUa;
    private String token;
    private String approvalUrl;
    private boolean automaticSessionAttempted;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // manifest の android:label は固定文字列なので、表示名はここで言語に合わせる。
        setTitle(Ui.s(this, Strings.AUTH_TITLE));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        addButton(buttons, Ui.s(this, Strings.AUTH_OPEN_APPROVAL), new View.OnClickListener() {
            @Override public void onClick(View view) { requestToken(true, false); }
        });
        addButton(buttons, Ui.s(this, Strings.AUTH_GET_SESSION), new View.OnClickListener() {
            @Override public void onClick(View view) { requestSession(false); }
        });
        addButton(buttons, Ui.s(this, Strings.AUTH_SHOW_PC_URL), new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (approvalUrl == null) requestToken(false, true); else showApprovalUrl(true);
            }
        });
        addButton(buttons, Ui.s(this, Strings.AUTH_IMPORT_CONFIG), new View.OnClickListener() {
            @Override public void onClick(View view) { importConfig(); }
        });
        replaceUa = new CheckBox(this);
        replaceUa.setText(Ui.s(this, Strings.AUTH_USE_NEW_UA));
        replaceUa.setChecked(false);
        replaceUa.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { applyUserAgent(); }
        });
        buttons.addView(replaceUa);
        root.addView(buttons);

        status = new TextView(this);
        status.setTextIsSelectable(true);
        status.setTextColor(Color.BLACK);
        status.setPadding(4, 10, 4, 10);
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        try {
            webView = new WebView(this);
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
            root.addView(webView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } catch (Throwable error) {
            webView = null;
            status.setText(Ui.s(this, Strings.AUTH_WEBVIEW_UNAVAILABLE));
        }
        setContentView(root);
        new ConfigStore(this).writeTemplateIfAbsent();
    }

    private void requestToken(final boolean openWebView, final boolean copyUrl) {
        final Credentials credentials = credentials();
        if (credentials.apiKey.length() == 0 || credentials.apiSecret.length() == 0) {
            show(Ui.s(this, Strings.AUTH_CREDENTIALS_MISSING));
            return;
        }
        show(Ui.s(this, Strings.AUTH_GETTING_TOKEN));
        NetThread.post(new Runnable() {
            @Override public void run() {
                LastfmResponse response = new LastfmClient(credentials.apiKey,
                        credentials.apiSecret, "").getToken();
                if (!response.isOk() || blank(response.token)) {
                    showFromWorker(Ui.s(AuthActivity.this, Strings.AUTH_TOKEN_FAILED, reason(response)));
                    return;
                }
                token = response.token;
                approvalUrl = buildApprovalUrl(credentials.apiKey, token);
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
        final Credentials credentials = credentials();
        NetThread.post(new Runnable() {
            @Override public void run() {
                LastfmResponse response = new LastfmClient(credentials.apiKey,
                        credentials.apiSecret, "").getSession(token);
                if (!response.isOk() || blank(response.sessionKey)) {
                    if (!silentFailure) showFromWorker(Ui.s(AuthActivity.this,
                            Strings.AUTH_SESSION_FAILED, reason(response)));
                    return;
                }
                saveSession(response.sessionKey, response.sessionName);
            }
        });
    }

    private void importConfig() {
        Map<String, String> file = new ConfigStore(this).load();
        String session = clean(file.get("session_key"));
        if (session.length() == 0) {
            show(Ui.s(this, Strings.AUTH_CONFIG_SESSION_MISSING)); return;
        }
        Prefs prefs = new Prefs(this);
        prefs.putString(Prefs.SESSION_KEY, session);
        String user = clean(file.get("user_name"));
        if (user.length() > 0) prefs.putString(Prefs.USER_NAME, user);
        String key = clean(file.get("api_key"));
        String secret = clean(file.get("api_secret"));
        if (key.length() > 0) prefs.putString(Prefs.API_KEY, key);
        if (secret.length() > 0) prefs.putString(Prefs.API_SECRET, secret);
        completeAuthentication();
    }

    private Credentials credentials() {
        // 解決経路は ConfigStore.credentials に集約する。ここで prefs を直読みすると
        // 呼び出し元ごとに挙動がずれる（実機で ScrobbleService/Sender だけ api_key が
        // 空になる不具合を出した）。
        return ConfigStore.credentials(this);
    }

    private void saveSession(String session, String user) {
        Prefs prefs = new Prefs(this);
        prefs.putString(Prefs.SESSION_KEY, session);
        prefs.putString(Prefs.USER_NAME, clean(user));
        completeAuthentication();
    }

    private void completeAuthentication() {
        new Prefs(this).putBoolean(Prefs.AUTH_INVALID, false);
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
    private static void addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(parent.getContext()); button.setText(label);
        button.setOnClickListener(listener); parent.addView(button);
    }
}
