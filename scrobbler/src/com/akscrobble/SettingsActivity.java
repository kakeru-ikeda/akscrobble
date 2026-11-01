package com.akscrobble;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.akscrobble.core.Strings;
import com.akscrobble.core.update.UpdateDecision;
import com.akscrobble.core.update.UpdateManifest;
import com.akscrobble.update.UpdateChecker;

public final class SettingsActivity extends Activity {
    private static final int REQ_STORAGE = 1401;

    private TextView notice;
    private Ui.Row keyRow;
    private Ui.Row authRow;
    private Ui.Row storageRow;
    private String noticeText = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(Ui.s(this, Strings.UI_SETTINGS));
        Ui.Screen screen = Ui.screen(this, Ui.s(this, Strings.UI_SETTINGS),
                Ui.backLink(this), true);
        notice = Ui.text(this, "", 12, Ui.FG);
        notice.setBackground(Ui.rounded(this, Ui.SURFACE, 10));
        notice.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        notice.setTextIsSelectable(true);
        screen.body.addView(notice);
        updateNotice();

        addAccountSection(screen.body);
        addScrobbleSection(screen.body);
        addDeviceSection(screen.body);
        addVersionSection(screen.body);
        updateRows();
    }

    @Override protected void onResume() {
        super.onResume();
        updateRows();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            setNotice(AppActions.storageResult(this));
            updateRows();
        }
    }

    private void addAccountSection(LinearLayout body) {
        body.addView(Ui.section(this, Ui.s(this, Strings.SET_SEC_ACCOUNT)));
        LinearLayout list = Ui.list(this);
        keyRow = Ui.row(this, Ui.s(this, Strings.SET_API_KEY),
                Ui.s(this, Strings.SET_SOURCE_NONE), null, null,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { open(CredentialsActivity.class); }
                });
        Ui.addRow(list, keyRow.view);
        authRow = Ui.row(this, Ui.s(this, Strings.MAIN_AUTH),
                Ui.s(this, Strings.MAIN_UNAUTHENTICATED), null, null,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { open(AuthActivity.class); }
                });
        Ui.addRow(list, authRow.view);
        body.addView(list);
    }

    private void addScrobbleSection(LinearLayout body) {
        body.addView(Ui.section(this, Ui.s(this, Strings.SET_SEC_SCROBBLE)));
        LinearLayout list = Ui.list(this);
        addNavigationRow(list, Strings.SET_SANITIZE, SanitizeActivity.class);
        addNavigationRow(list, Strings.HOME_SEC_QUEUE, QueueActivity.class);
        final String[] languageKeys = new String[] { Strings.LANG_SYSTEM,
                Strings.LANG_JAPANESE, Strings.LANG_ENGLISH, Strings.LANG_KOREAN };
        String language = new Prefs(this).getString(Prefs.LANGUAGE);
        TextView selected = Ui.text(this, languageName(language), 13, Ui.MUTED);
        selected.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Ui.addRow(list, Ui.row(this, Ui.s(this, Strings.SET_LANGUAGE), null, null, selected,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { showLanguageDialog(languageKeys); }
                }).view);
        body.addView(list);
    }

    private void addDeviceSection(LinearLayout body) {
        body.addView(Ui.section(this, Ui.s(this, Strings.HOME_SEC_DEVICE)));
        LinearLayout list = Ui.list(this);
        addActionRow(list, Strings.MAIN_BATTERY_EXEMPTION, new Runnable() {
            @Override public void run() {
                String result = AppActions.requestBatteryExemption(SettingsActivity.this);
                if (result != null) setNotice(result);
            }
        });
        storageRow = Ui.row(this, Ui.s(this, Strings.SET_STORAGE),
                Ui.s(this, Strings.SET_NOT_GRANTED), null, null,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        if (!AppActions.hasStorage(SettingsActivity.this)) {
                            AppActions.requestStorage(SettingsActivity.this, REQ_STORAGE);
                        } else {
                            setNotice(AppActions.storageResult(SettingsActivity.this));
                        }
                    }
                });
        Ui.addRow(list, storageRow.view);
        addActionRow(list, Strings.MAIN_SAVE_DIAGNOSTIC, new Runnable() {
            @Override public void run() {
                setNotice(AppActions.saveDiagnosticDump(SettingsActivity.this));
            }
        });
        addActionRow(list, Strings.SET_STATUS, new Runnable() {
            @Override public void run() {
                String snapshot = StatusBus.snapshotText();
                if (snapshot.length() == 0) snapshot = "service: stopped/no status";
                new AlertDialog.Builder(SettingsActivity.this).setMessage(snapshot)
                        .setPositiveButton(android.R.string.ok, null).show();
            }
        });
        body.addView(list);
    }

    private void addVersionSection(LinearLayout body) {
        body.addView(Ui.section(this, Ui.s(this, Strings.SET_SEC_VERSION)));
        LinearLayout list = Ui.list(this);
        String version = "?";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (version == null || version.length() == 0) version = "?";
        } catch (Exception ignored) {}
        final Ui.Row row = Ui.row(this, version, Ui.s(this, Strings.SET_CHECK_UPDATE),
                null, null, new View.OnClickListener() {
                    @Override public void onClick(View view) { checkUpdates(); }
                });
        Ui.addRow(list, row.view);
        body.addView(list);
    }

    private void addNavigationRow(LinearLayout list, final String titleKey,
            final Class<?> activity) {
        Ui.addRow(list, Ui.row(this, Ui.s(this, titleKey), null, null, null,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { open(activity); }
                }).view);
    }

    private void addActionRow(LinearLayout list, final String titleKey, final Runnable action) {
        Ui.addRow(list, Ui.row(this, Ui.s(this, titleKey), null, null, null,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { action.run(); }
                }).view);
    }

    private void updateRows() {
        if (keyRow == null) return;
        String source = AppActions.apiKeySource(this);
        String sourceText;
        if ("config".equals(source)) sourceText = Ui.s(this, Strings.SET_SOURCE_CONFIG);
        else if ("prefs".equals(source)) sourceText = Ui.s(this, Strings.SET_SOURCE_PREFS);
        else if ("builtin".equals(source)) sourceText = Ui.s(this, Strings.SET_SOURCE_BUILTIN);
        else sourceText = Ui.s(this, Strings.SET_SOURCE_NONE);
        keyRow.subtitle.setVisibility(View.VISIBLE);
        keyRow.subtitle.setText("none".equals(source)
                ? sourceText : Ui.s(this, Strings.SET_KEY_FROM, sourceText));

        Prefs prefs = new Prefs(this);
        String user = prefs.getString(Prefs.USER_NAME);
        authRow.subtitle.setVisibility(View.VISIBLE);
        if (prefs.getBoolean(Prefs.AUTH_INVALID)) {
            Ui.setStatus(authRow.subtitle, Ui.s(this, Strings.SET_AUTH_INVALID), Ui.WARN);
        } else if (user.length() > 0) {
            Ui.setStatus(authRow.subtitle, Ui.s(this, Strings.SET_AUTHED, user), Ui.OK);
        } else {
            Ui.setStatus(authRow.subtitle, Ui.s(this, Strings.MAIN_UNAUTHENTICATED), Ui.OFF);
        }
        storageRow.subtitle.setVisibility(View.VISIBLE);
        storageRow.subtitle.setText(Ui.s(this, AppActions.hasStorage(this)
                ? Strings.SET_GRANTED : Strings.SET_NOT_GRANTED));
    }

    private void showLanguageDialog(String[] keys) {
        final String[] labels = new String[keys.length];
        for (int i = 0; i < keys.length; i++) labels[i] = Ui.s(this, keys[i]);
        String selected = new Prefs(this).getString(Prefs.LANGUAGE);
        final String[] values = new String[] { "", "ja", "en", "ko" };
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) checked = i;
        new AlertDialog.Builder(this).setTitle(Ui.s(this, Strings.SET_LANGUAGE))
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        new Prefs(SettingsActivity.this).putString(Prefs.LANGUAGE, values[which]);
                        dialog.dismiss();
                        recreate();
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private String languageName(String language) {
        if ("ja".equals(language)) return Ui.s(this, Strings.LANG_JAPANESE);
        if ("en".equals(language)) return Ui.s(this, Strings.LANG_ENGLISH);
        if ("ko".equals(language)) return Ui.s(this, Strings.LANG_KOREAN);
        return Ui.s(this, Strings.LANG_SYSTEM);
    }

    private void checkUpdates() {
        UpdateChecker.checkManually(this, new UpdateChecker.Callback() {
            @Override public void onResult(UpdateDecision decision, UpdateManifest manifest) {
                if (decision == UpdateDecision.OPTIONAL || decision == UpdateDecision.REQUIRED) {
                    AppActions.showUpdatePrompt(SettingsActivity.this, decision, manifest);
                } else if (decision == UpdateDecision.NONE) {
                    setNotice(Ui.s(SettingsActivity.this, Strings.SET_UP_TO_DATE));
                } else {
                    setNotice(Ui.s(SettingsActivity.this, Strings.SET_UPDATE_FAILED));
                }
            }
        });
    }

    private void open(Class<?> activity) {
        startActivity(new Intent(this, activity));
    }

    private void setNotice(String message) {
        noticeText = message == null ? "" : message;
        updateNotice();
    }

    private void updateNotice() {
        if (notice == null) return;
        notice.setText(noticeText);
        notice.setVisibility(noticeText.length() == 0 ? View.GONE : View.VISIBLE);
    }
}
