package com.akscrobble;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.akscrobble.core.LaunchRoute;
import com.akscrobble.core.Sources;
import com.akscrobble.core.Strings;

public final class SetupActivity extends Activity {
    private static final int REQ_STORAGE = 1301;
    private LinearLayout body;
    private LinearLayout stepsContainer;
    private LinearLayout authGroup;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(Ui.s(this, Strings.SETUP_TITLE));
        Ui.Screen screen = Ui.screen(this, "AKScrobble", null, true);
        body = screen.body;

        stepsContainer = new LinearLayout(this);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(stepsContainer, matchWrap());

        buildAuthGroup();

        addSourcePreview();
        addDeviceSection();
        refreshRoute();

        if (!AppActions.hasStorage(this)) AppActions.requestStorage(this, REQ_STORAGE);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshRoute();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions,
            int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_STORAGE) {
            LegacyMigration.run(this);
            refreshRoute();
        }
    }

    private void buildAuthGroup() {
        authGroup = new LinearLayout(this);
        authGroup.setOrientation(LinearLayout.VERTICAL);

        TextView heading = Ui.text(this, Ui.s(this, Strings.SETUP_HEADING_AUTH), 17, Ui.FG);
        heading.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        authGroup.addView(heading, matchWrap());
        if (new Prefs(this).getBoolean(Prefs.MIGRATION_NOTICE)) {
            TextView notice = Ui.text(this, Ui.s(this, Strings.SETUP_REAUTH_NOTICE),
                    12, Ui.WARN);
            authGroup.addView(notice, matchWrap());
        }
        authGroup.addView(Ui.text(this, Ui.s(this, Strings.SETUP_AUTH_HELP), 12, Ui.MUTED),
                matchWrap());
        authGroup.addView(Ui.button(this, Ui.s(this, Strings.CRED_AUTH), true,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        startActivity(new Intent(SetupActivity.this, AuthActivity.class));
                    }
                }), matchWrap());
        authGroup.addView(Ui.text(this, Ui.s(this, Strings.SETUP_SIGNING_NOTE), 11,
                Ui.MUTED), matchWrap());
        authGroup.addView(Ui.button(this, Ui.s(this, Strings.SETUP_LATER), false,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { goHome(); }
                }), matchWrap());
        authGroup.setVisibility(View.GONE);
        body.addView(authGroup, matchWrap());
    }

    private void addSourcePreview() {
        body.addView(Ui.section(this, Ui.s(this, Strings.HOME_SEC_SOURCES)), matchWrap());
        LinearLayout list = Ui.list(this);
        String[] sources = Sources.displayOrder();
        for (int i = 0; i < sources.length; i++) {
            String pkg = sources[i];
            TextView afterKeys = Ui.text(this, Ui.s(this, Strings.SETUP_AFTER_KEYS),
                    11, Ui.MUTED);
            Ui.Row row = Ui.row(this, Sources.label(pkg), null,
                    Ui.tag(this, Sources.tag(pkg), Sources.tagColor(pkg)), afterKeys, null);
            Ui.addRow(list, row.view);
        }
        list.setAlpha(0.4f);
        body.addView(list, matchWrap());
    }

    private void addDeviceSection() {
        body.addView(Ui.section(this, Ui.s(this, Strings.HOME_SEC_DEVICE)), matchWrap());
        LinearLayout device = Ui.list(this);
        Ui.addRow(device, Ui.row(this, Ui.s(this, Strings.MAIN_BTN_ADB),
                Ui.s(this, Strings.HOME_ADB_SUB), null, null,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        startActivity(new Intent(SetupActivity.this, AdbActivity.class));
                    }
                }).view);
        body.addView(device, matchWrap());
    }

    private void refreshRoute() {
        if (body == null) return;
        LaunchRoute route = route();
        if (route == LaunchRoute.HOME) {
            goHome();
            return;
        }
        stepsContainer.removeAllViews();
        String[] labels = new String[] {Ui.s(this, Strings.SETUP_STEP_AUTH),
                Ui.s(this, Strings.SETUP_STEP_SOURCES)};
        stepsContainer.addView(Ui.steps(this, 1, labels), matchWrap());
    }

    private LaunchRoute route() {
        return LaunchRoute.decide(ConfigStore.credentials(this),
                new Prefs(this).getBoolean(Prefs.SETUP_DISMISSED));
    }

    private void goHome() {
        new Prefs(this).putBoolean(Prefs.SETUP_DISMISSED, true);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
