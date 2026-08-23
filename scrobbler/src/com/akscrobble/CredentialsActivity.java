package com.akscrobble;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.akscrobble.core.Strings;

public final class CredentialsActivity extends Activity {
    private static final String ACCOUNT_URL = "https://www.last.fm/api/account/create";
    private EditText apiKey;
    private EditText apiSecret;
    private TextView message;
    private TextView keySource;
    private TextView secretSource;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(Ui.s(this, Strings.CRED_TITLE));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(12, 12, 12, 12);

        TextView help = text(Ui.s(this, Strings.CRED_HELP) + "\n" + ACCOUNT_URL);
        help.setTextIsSelectable(true);
        content.addView(help);

        Prefs prefs = new Prefs(this);
        apiKey = input(Ui.s(this, Strings.CRED_API_KEY), prefs.getString(Prefs.API_KEY));
        content.addView(apiKey);
        keySource = text("");
        content.addView(keySource);

        apiSecret = input(Ui.s(this, Strings.CRED_API_SECRET),
                prefs.getString(Prefs.API_SECRET));
        apiSecret.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiSecret.setTransformationMethod(PasswordTransformationMethod.getInstance());
        content.addView(apiSecret);
        secretSource = text("");
        content.addView(secretSource);

        CheckBox showSecret = new CheckBox(this);
        showSecret.setText(Ui.s(this, Strings.CRED_SHOW_SECRET));
        showSecret.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int cursor = apiSecret.getSelectionStart();
                apiSecret.setTransformationMethod(((CheckBox) view).isChecked()
                        ? null : PasswordTransformationMethod.getInstance());
                apiSecret.setSelection(Math.max(0, cursor));
            }
        });
        content.addView(showSecret);

        Button save = button(Ui.s(this, Strings.CRED_SAVE));
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { save(); }
        });
        content.addView(save);

        Button authenticate = button(Ui.s(this, Strings.CRED_AUTH));
        authenticate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(CredentialsActivity.this, AuthActivity.class));
            }
        });
        content.addView(authenticate);

        message = text("");
        content.addView(message);
        updateSources();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void save() {
        String key = apiKey.getText().toString().trim();
        String secret = apiSecret.getText().toString().trim();
        if (key.length() == 0 || secret.length() == 0) {
            message.setText(Ui.s(this, Strings.CRED_EMPTY));
            return;
        }
        Prefs prefs = new Prefs(this);
        prefs.putString(Prefs.API_KEY, key);
        prefs.putString(Prefs.API_SECRET, secret);
        apiKey.setText(key);
        apiSecret.setText(secret);
        apiSecret.setSelection(secret.length());
        message.setText(Ui.s(this, Strings.CRED_SAVED));
        updateSources();
    }

    private void updateSources() {
        keySource.setText(Ui.s(this, Strings.CRED_SOURCE,
                source("api_key", Prefs.API_KEY, "API_KEY")));
        secretSource.setText(Ui.s(this, Strings.CRED_SOURCE,
                source("api_secret", Prefs.API_SECRET, "API_SECRET")));
    }

    private String source(String configName, String prefName, String builtinName) {
        try {
            String config = new ConfigStore(this).load().get(configName);
            if (config != null && config.trim().length() > 0) return "config";
        } catch (Throwable ignored) {}
        if (new Prefs(this).getString(prefName).length() > 0) return "prefs";
        try {
            Object value = Class.forName("com.akscrobble.core.BuildSecrets")
                    .getField(builtinName).get(null);
            if (value instanceof String && ((String) value).length() > 0) return "builtin";
        } catch (Throwable ignored) {}
        return "unset";
    }

    private EditText input(String hint, String value) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setText(value);
        view.setSingleLine(true);
        return view;
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setPadding(8, 8, 8, 8);
        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }
}
