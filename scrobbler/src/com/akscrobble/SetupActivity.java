package com.akscrobble;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.akscrobble.core.LaunchRoute;
import com.akscrobble.core.Sources;
import com.akscrobble.core.Strings;

import java.io.File;

public final class SetupActivity extends Activity {
    private static final int REQ_STORAGE = 1301;
    private LinearLayout body;
    private LinearLayout stepsContainer;
    private LinearLayout keysGroup;
    private LinearLayout authGroup;
    private LinearLayout inputPane;
    private LinearLayout filePane;
    private EditText apiKey;
    private EditText apiSecret;
    private TextView message;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(Ui.s(this, Strings.SETUP_TITLE));
        Ui.Screen screen = Ui.screen(this, "AKScrobble", null, true);
        body = screen.body;

        stepsContainer = new LinearLayout(this);
        stepsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(stepsContainer, matchWrap());

        buildKeysGroup();
        buildAuthGroup();

        message = Ui.text(this, "", 12, Ui.FG);
        body.addView(message, matchWrap());
        addSourcePreview();
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
        if (code == REQ_STORAGE) refreshRoute();
    }

    private void buildKeysGroup() {
        keysGroup = new LinearLayout(this);
        keysGroup.setOrientation(LinearLayout.VERTICAL);

        TextView heading = Ui.text(this, Ui.s(this, Strings.SETUP_HEADING_KEYS), 17, Ui.FG);
        heading.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        keysGroup.addView(heading, matchWrap());

        TextView account = Ui.text(this,
                Ui.s(this, Strings.SETUP_GET_KEY_AT, "last.fm/api/account/create"),
                12, Ui.MUTED);
        account.setTextIsSelectable(true);
        keysGroup.addView(account, matchWrap());

        Ui.Segment segment = Ui.segment(this,
                new String[] {Ui.s(this, Strings.SETUP_TAB_INPUT),
                        Ui.s(this, Strings.SETUP_TAB_FILE)}, 0,
                new Ui.SegmentListener() {
                    @Override public void onSelected(int index) {
                        inputPane.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
                        filePane.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
                    }
                });
        keysGroup.addView(segment.view, matchWrap());

        inputPane = new LinearLayout(this);
        inputPane.setOrientation(LinearLayout.VERTICAL);
        addInputField(inputPane, "API Key", false);
        addInputField(inputPane, "API Secret", true);

        CheckBox showSecret = Ui.checkBox(this, Ui.s(this, Strings.CRED_SHOW_SECRET), false);
        showSecret.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int cursor = apiSecret.getSelectionStart();
                apiSecret.setTransformationMethod(((CheckBox) view).isChecked()
                        ? null : PasswordTransformationMethod.getInstance());
                apiSecret.setSelection(Math.max(0, cursor));
            }
        });
        inputPane.addView(showSecret, matchWrap());
        inputPane.addView(Ui.button(this, Ui.s(this, Strings.SETUP_SAVE_AND_AUTH), true,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { saveAndProceed(); }
                }), matchWrap());
        keysGroup.addView(inputPane, matchWrap());

        filePane = new LinearLayout(this);
        filePane.setOrientation(LinearLayout.VERTICAL);
        filePane.setVisibility(View.GONE);
        filePane.addView(Ui.text(this, Ui.s(this, Strings.SETUP_FILE_HELP), 12, Ui.MUTED),
                matchWrap());
        String path = "/sdcard/" + com.akscrobble.core.Const.CONFIG_DIR + "/"
                + com.akscrobble.core.Const.CONFIG_FILE;
        filePane.addView(Ui.pathBox(this, path), matchWrap());
        filePane.addView(Ui.text(this, Ui.s(this, Strings.SETUP_FILE_NOTE), 12, Ui.MUTED),
                matchWrap());
        filePane.addView(Ui.button(this, Ui.s(this, Strings.SETUP_MAKE_TEMPLATE), true,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { makeTemplate(); }
                }), matchWrap());
        filePane.addView(Ui.button(this, Ui.s(this, Strings.SETUP_LOAD_FILE), false,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { loadFile(); }
                }), matchWrap());
        keysGroup.addView(filePane, matchWrap());
        body.addView(keysGroup, matchWrap());
    }

    private void addInputField(LinearLayout parent, String label, boolean secret) {
        parent.addView(Ui.text(this, label, 11, Ui.MUTED), matchWrap());
        EditText field = Ui.input(this, Ui.s(this, Strings.SETUP_KEY_HINT), "", true);
        if (secret) {
            field.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            field.setTransformationMethod(PasswordTransformationMethod.getInstance());
            apiSecret = field;
        } else {
            apiKey = field;
        }
        Prefs prefs = new Prefs(this);
        field.setText(prefs.getString(secret ? Prefs.API_SECRET : Prefs.API_KEY));
        parent.addView(field, matchWrap());
    }

    private void buildAuthGroup() {
        authGroup = new LinearLayout(this);
        authGroup.setOrientation(LinearLayout.VERTICAL);

        TextView heading = Ui.text(this, Ui.s(this, Strings.SETUP_HEADING_AUTH), 17, Ui.FG);
        heading.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        authGroup.addView(heading, matchWrap());
        authGroup.addView(Ui.text(this, Ui.s(this, Strings.SETUP_AUTH_HELP), 12, Ui.MUTED),
                matchWrap());
        authGroup.addView(Ui.button(this, Ui.s(this, Strings.CRED_AUTH), true,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        startActivity(new Intent(SetupActivity.this, AuthActivity.class));
                    }
                }), matchWrap());
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

    private void saveAndProceed() {
        String key = apiKey.getText().toString().trim();
        String secret = apiSecret.getText().toString().trim();
        if (key.length() == 0 || secret.length() == 0) {
            showMessage(Ui.s(this, Strings.CRED_EMPTY));
            return;
        }
        Prefs prefs = new Prefs(this);
        prefs.putString(Prefs.API_KEY, key);
        prefs.putString(Prefs.API_SECRET, secret);
        proceed();
    }

    private void makeTemplate() {
        if (!AppActions.hasStorage(this)) {
            AppActions.requestStorage(this, REQ_STORAGE);
            showMessage(Ui.s(this, Strings.SETUP_STORAGE_NEEDED));
            return;
        }
        ConfigStore store = new ConfigStore(this);
        File file = store.configFile();
        boolean exists = file.exists();
        store.writeTemplateIfAbsent();
        showMessage(Ui.s(this, exists ? Strings.SETUP_TEMPLATE_EXISTS
                : Strings.SETUP_TEMPLATE_CREATED, file.getAbsolutePath()));
    }

    private void loadFile() {
        if (!AppActions.hasStorage(this)) {
            AppActions.requestStorage(this, REQ_STORAGE);
            showMessage(Ui.s(this, Strings.SETUP_STORAGE_NEEDED));
            return;
        }
        AppActions.importConfig(this);
        if (route() == LaunchRoute.SETUP) {
            showMessage(Ui.s(this, Strings.SETUP_FILE_KEYS_MISSING));
        } else {
            proceed();
        }
    }

    private void proceed() {
        LaunchRoute route = route();
        if (route == LaunchRoute.AUTH) {
            startActivity(new Intent(this, AuthActivity.class));
        } else if (route == LaunchRoute.HOME) {
            goHome();
        }
    }

    private void refreshRoute() {
        if (body == null) return;
        LaunchRoute route = route();
        if (route == LaunchRoute.HOME) {
            goHome();
            return;
        }
        boolean needsKeys = route == LaunchRoute.SETUP;
        keysGroup.setVisibility(needsKeys ? View.VISIBLE : View.GONE);
        authGroup.setVisibility(needsKeys ? View.GONE : View.VISIBLE);
        stepsContainer.removeAllViews();
        String[] labels = new String[] {Ui.s(this, Strings.SETUP_STEP_KEYS),
                Ui.s(this, Strings.SETUP_STEP_AUTH), Ui.s(this, Strings.SETUP_STEP_SOURCES)};
        stepsContainer.addView(Ui.steps(this, needsKeys ? 1 : 2, labels), matchWrap());
    }

    private LaunchRoute route() {
        return LaunchRoute.decide(ConfigStore.credentials(this));
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private void showMessage(String value) {
        message.setText(value);
        message.setVisibility(value.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
