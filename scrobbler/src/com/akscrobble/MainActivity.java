package com.akscrobble;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.akscrobble.core.Const;
import com.akscrobble.core.LaunchRoute;
import com.akscrobble.core.NowPlaying;
import com.akscrobble.core.SourceStatus;
import com.akscrobble.core.Sources;
import com.akscrobble.core.Strings;
import com.akscrobble.core.update.UpdateDecision;
import com.akscrobble.core.update.UpdateManifest;
import com.akscrobble.update.UpdateChecker;
import com.akscrobble.update.UpdateFlow;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REQ_STORAGE = 1201;
    private final Handler handler = new Handler();
    private final String[] sourcePackages = Sources.displayOrder();
    private final boolean[] installed = new boolean[sourcePackages.length];
    private final TextView[] sourceSubtitles = new TextView[sourcePackages.length];
    private final Switch[] sourceToggles = new Switch[sourcePackages.length];
    private final String[] lastSourceStatus = new String[sourcePackages.length];
    private TextView banner;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private TextView nowHeading;
    private TextView nowTitle;
    private TextView nowDetail;
    private Switch serviceToggle;
    private Ui.Row queueRow;
    private boolean refreshing;
    private boolean changingServiceToggle;
    private boolean changingSourceToggles;
    private int tickCount;
    private int pendingCount;
    private int deadCount;
    private Strings.Lang createdLanguage;
    private String lastHeroSubtitle;
    private String lastNowTitle;
    private String lastNowDetail;
    private String lastQueueTitle;
    private String lastQueueStatus;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateStatus(StatusBus.snapshot());
            tickCount++;
            if (tickCount % 5 == 0) refreshQueueCounts();
            if (refreshing) handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        createdLanguage = Ui.lang(this);
        installCrashHandler();
        if (LaunchRoute.decide(ConfigStore.credentials(this)) == LaunchRoute.SETUP) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        Ui.Screen screen = Ui.screen(this, "AKScrobble", Ui.gear(this,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { open(SettingsActivity.class); }
                }), true);
        banner = Ui.banner(this, "", new View.OnClickListener() {
            @Override public void onClick(View view) { open(AuthActivity.class); }
        });
        banner.setVisibility(View.GONE);
        screen.body.addView(banner);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        hero.setBackground(Ui.rounded(this, Ui.SURFACE, 12));
        LinearLayout headline = new LinearLayout(this);
        headline.setOrientation(LinearLayout.HORIZONTAL);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headlineText = new LinearLayout(this);
        headlineText.setOrientation(LinearLayout.VERTICAL);
        heroTitle = Ui.text(this, "", 16, Ui.FG);
        heroTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        headlineText.addView(heroTitle);
        heroSubtitle = Ui.text(this, "", 12, Ui.MUTED);
        headlineText.addView(heroSubtitle);
        headline.addView(headlineText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        serviceToggle = Ui.toggle(this, false);
        headline.addView(serviceToggle);
        hero.addView(headline);
        serviceToggle.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(android.widget.CompoundButton button,
                            boolean checked) {
                        if (changingServiceToggle) return;
                        tintToggle(serviceToggle, checked);
                        showToast(AppActions.sendService(MainActivity.this,
                                checked ? Const.ACTION_START : Const.ACTION_STOP));
                        updateStatus(StatusBus.snapshot());
                    }
                });

        View heroDivider = new View(this);
        heroDivider.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1)));
        dividerParams.topMargin = Ui.dp(this, 12);
        dividerParams.bottomMargin = Ui.dp(this, 10);
        hero.addView(heroDivider, dividerParams);
        nowHeading = Ui.text(this, "", 11, Ui.MUTED);
        hero.addView(nowHeading);
        nowTitle = Ui.text(this, "", 14, Ui.FG);
        nowTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        hero.addView(nowTitle);
        nowDetail = Ui.text(this, "", 12, Ui.MUTED);
        hero.addView(nowDetail);
        screen.body.addView(hero);

        screen.body.addView(Ui.section(this, Ui.s(this, Strings.HOME_SEC_SOURCES)));
        LinearLayout sources = Ui.list(this);
        for (int i = 0; i < sourcePackages.length; i++) addSourceRow(sources, i);
        screen.body.addView(sources);

        screen.body.addView(Ui.section(this, Ui.s(this, Strings.HOME_SEC_QUEUE)));
        LinearLayout queue = Ui.list(this);
        queueRow = Ui.row(this, "", " ", null, null, new View.OnClickListener() {
            @Override public void onClick(View view) { open(QueueActivity.class); }
        });
        Ui.addRow(queue, queueRow.view);
        Ui.addRow(queue, Ui.button(this, Ui.s(this, Strings.MAIN_FLUSH), false,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showToast(AppActions.sendService(MainActivity.this, Const.ACTION_FLUSH));
                    }
                }));
        screen.body.addView(queue);

        screen.body.addView(Ui.section(this, Ui.s(this, Strings.HOME_SEC_DEVICE)));
        LinearLayout device = Ui.list(this);
        Ui.addRow(device, Ui.row(this, Ui.s(this, Strings.MAIN_BTN_ADB),
                Ui.s(this, Strings.HOME_ADB_SUB), null, null,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { open(AdbActivity.class); }
                }).view);
        screen.body.addView(device);

        AppActions.requestStorage(this, REQ_STORAGE);
        UpdateChecker.check(this, new UpdateChecker.Callback() {
            @Override public void onResult(UpdateDecision decision, final UpdateManifest manifest) {
                AppActions.showUpdatePrompt(MainActivity.this, decision, manifest);
            }
        });
    }

    private void addSourceRow(LinearLayout list, final int index) {
        final String pkg = sourcePackages[index];
        Switch toggle = Ui.toggle(this, new Prefs(this).getBoolean(Prefs.sourceEnabledKey(pkg)));
        sourceToggles[index] = toggle;
        Ui.Row row = Ui.row(this, Sources.label(pkg), " ", Ui.tag(this, Sources.tag(pkg),
                Sources.tagColor(pkg)), toggle, null);
        sourceSubtitles[index] = row.subtitle;
        toggle.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(android.widget.CompoundButton button,
                            boolean checked) {
                        if (changingSourceToggles) return;
                        tintToggle(sourceToggles[index], checked);
                        AppActions.setSourceEnabled(MainActivity.this, pkg, checked);
                        updateStatus(StatusBus.snapshot());
                    }
                });
        Ui.addRow(list, row.view);
    }

    @Override protected void onResume() {
        super.onResume();
        if (createdLanguage != Ui.lang(this)) {
            recreate();
            return;
        }
        resolveInstalledSources();
        refreshBanner(ConfigStore.credentials(this));
        changingSourceToggles = true;
        for (int i = 0; i < sourcePackages.length; i++) {
            boolean enabled = new Prefs(this).getBoolean(Prefs.sourceEnabledKey(sourcePackages[i]));
            sourceToggles[i].setChecked(enabled);
            tintToggle(sourceToggles[i], enabled);
        }
        changingSourceToggles = false;
        refreshQueueCounts();
        refreshing = true;
        tickCount = 0;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
        UpdateFlow.resumeIfPermitted(this);
    }

    @Override protected void onPause() {
        refreshing = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_STORAGE) showToast(AppActions.storageResult(this));
    }

    private void installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable error) {
                Log.e(Const.TAG, "Uncaught exception on " + thread.getName(), error);
            }
        });
    }

    private void open(Class<?> activity) {
        try { startActivity(new Intent(this, activity)); }
        catch (Throwable error) {
            Log.e(Const.TAG, "Unable to open screen", error);
            showToast(Ui.s(this, Strings.MAIN_FAILURE, Ui.s(this, Strings.MAIN_OP_OPEN_SCREEN),
                    error.getClass().getSimpleName()));
        }
    }

    private void refreshBanner(com.akscrobble.core.Credentials credentials) {
        Prefs prefs = new Prefs(this);
        boolean invalid = prefs.getBoolean(Prefs.AUTH_INVALID);
        boolean needsAuth = LaunchRoute.decide(credentials) == LaunchRoute.AUTH;
        if (invalid || needsAuth) {
            setTextIfChanged(banner, Ui.s(this, invalid
                    ? Strings.HOME_BANNER_AUTH_INVALID : Strings.HOME_BANNER_NOT_AUTHED));
            banner.setVisibility(View.VISIBLE);
        } else {
            banner.setVisibility(View.GONE);
        }
    }

    private void resolveInstalledSources() {
        for (int i = 0; i < sourcePackages.length; i++) {
            try {
                getPackageManager().getPackageInfo(sourcePackages[i], 0);
                installed[i] = true;
            } catch (PackageManager.NameNotFoundException missing) {
                installed[i] = false;
            }
        }
    }

    private void refreshQueueCounts() {
        ScrobbleDb db = new ScrobbleDb(this);
        try {
            pendingCount = db.countPending();
            deadCount = db.countDead();
        } catch (Throwable error) {
            Log.e(Const.TAG, "Unable to refresh queue counts", error);
        } finally {
            db.close();
        }
        updateQueueRow();
    }

    private void updateQueueRow() {
        String title = Ui.s(this, Strings.HOME_QUEUE_PENDING, pendingCount);
        if (!title.equals(lastQueueTitle)) {
            queueRow.title.setText(title);
            lastQueueTitle = title;
        }
        String sent = formatLastSent(new Prefs(this).getLong(Prefs.LAST_SENT_AT));
        if (sent.length() == 0) sent = Ui.s(this, Strings.HOME_NEVER_SENT);
        String detail = Ui.s(this, Strings.HOME_QUEUE_DETAIL, deadCount, sent);
        int color = deadCount > 0 ? Ui.WARN : Ui.OK;
        String rendered = detail + "|" + color;
        if (!rendered.equals(lastQueueStatus)) {
            Ui.setStatus(queueRow.subtitle, detail, color);
            lastQueueStatus = rendered;
        }
    }

    private String formatLastSent(long epochMillis) {
        if (epochMillis <= 0L) return "";
        Date sent = new Date(epochMillis);
        Date now = new Date();
        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.US);
        String format = day.format(sent).equals(day.format(now)) ? "HH:mm" : "MM/dd HH:mm";
        return new SimpleDateFormat(format, Locale.getDefault()).format(sent);
    }

    private void updateStatus(Map<String, String> snapshot) {
        if (heroTitle == null) return;
        Prefs prefs = new Prefs(this);
        boolean serviceEnabled = prefs.getBoolean(Prefs.SERVICE_ENABLED);
        boolean authenticated = prefs.getString(Prefs.USER_NAME).length() > 0;
        String title = Ui.s(this, serviceEnabled ? Strings.HOME_SCROBBLING : Strings.HOME_STOPPED);
        setTextIfChanged(heroTitle, title);
        String subtitle = authenticated
                ? Ui.s(this, Strings.HOME_SENDING_AS, prefs.getString(Prefs.USER_NAME))
                : Ui.s(this, Strings.MAIN_UNAUTHENTICATED);
        String subtitleKey = subtitle + "|" + (serviceEnabled && authenticated ? Ui.OK : Ui.OFF);
        if (!subtitleKey.equals(lastHeroSubtitle)) {
            Ui.setStatus(heroSubtitle, subtitle, serviceEnabled && authenticated ? Ui.OK : Ui.OFF);
            lastHeroSubtitle = subtitleKey;
        }
        changingServiceToggle = true;
        serviceToggle.setChecked(serviceEnabled);
        tintToggle(serviceToggle, serviceEnabled);
        changingServiceToggle = false;

        String playing = serviceEnabled ? NowPlaying.pick(sourcePackages, snapshot) : null;
        if (playing == null) {
            nowHeading.setVisibility(View.GONE);
            nowTitle.setVisibility(View.VISIBLE);
            nowDetail.setVisibility(View.GONE);
            setTextIfChanged(nowTitle, Ui.s(this, Strings.HOME_NOTHING_PLAYING));
            lastNowDetail = null;
        } else {
            nowHeading.setVisibility(View.VISIBLE);
            nowDetail.setVisibility(View.VISIBLE);
            setTextIfChanged(nowHeading, Ui.s(this, Strings.HOME_NOW_PLAYING, Sources.label(playing)));
            String key = Sources.statusKey(playing);
            String track = value(snapshot.get("np_title_" + key));
            String artist = value(snapshot.get("np_artist_" + key));
            String album = value(snapshot.get("np_album_" + key));
            String detail = artist.length() == 0 ? album
                    : album.length() == 0 ? artist : artist + " — " + album;
            setTextIfChanged(nowTitle, track);
            setTextIfChanged(nowDetail, detail);
            lastNowTitle = track;
            lastNowDetail = detail;
        }

        for (int i = 0; i < sourcePackages.length; i++) updateSourceStatus(i, serviceEnabled,
                prefs.getBoolean(Prefs.sourceEnabledKey(sourcePackages[i])), snapshot);
        updateQueueRow();
    }

    private void updateSourceStatus(int index, boolean serviceEnabled, boolean sourceEnabled,
            Map<String, String> snapshot) {
        String pkg = sourcePackages[index];
        String link = snapshot.get("link_" + Sources.statusKey(pkg));
        SourceStatus state = SourceStatus.of(serviceEnabled, sourceEnabled, installed[index], link);
        String text;
        int color;
        switch (state) {
            case CONNECTED: text = Ui.s(this, Strings.SRC_CONNECTED); color = Ui.OK; break;
            case CONNECTING: text = Ui.s(this, Strings.SRC_CONNECTING); color = Ui.WARN; break;
            case DISCONNECTED: text = Ui.s(this, Strings.SRC_DISCONNECTED); color = Ui.WARN; break;
            case DISABLED: text = Ui.s(this, Strings.SRC_DISABLED); color = Ui.OFF; break;
            case NOT_INSTALLED: text = Ui.s(this, Strings.SRC_NOT_INSTALLED); color = Ui.OFF; break;
            default: text = Ui.s(this, Strings.SRC_STOPPED); color = Ui.OFF; break;
        }
        String rendered = text + "|" + color;
        if (!rendered.equals(lastSourceStatus[index])) {
            Ui.setStatus(sourceSubtitles[index], text, color);
            lastSourceStatus[index] = rendered;
        }
        if (sourceToggles[index].isChecked() != sourceEnabled) {
            changingSourceToggles = true;
            sourceToggles[index].setChecked(sourceEnabled);
            tintToggle(sourceToggles[index], sourceEnabled);
            changingSourceToggles = false;
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static void tintToggle(Switch toggle, boolean checked) {
        if (Build.VERSION.SDK_INT >= 23) {
            toggle.setTrackTintList(ColorStateList.valueOf(checked ? Ui.OK : Ui.OFF));
        }
    }

    private static void setTextIfChanged(TextView view, String value) {
        if (!value.equals(view.getText().toString())) view.setText(value);
    }

    private static String value(String value) { return value == null ? "" : value; }
}
