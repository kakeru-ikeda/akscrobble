package com.akscrobble;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.akscrobble.core.Credentials;
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

        Ui.Screen screen = Ui.screen(this, Ui.s(this, Strings.CRED_TITLE),
                Ui.backLink(this), true);
        LinearLayout content = screen.body;

        TextView help = Ui.text(this, Ui.s(this, Strings.CRED_HELP) + "\n" + ACCOUNT_URL,
                12, Ui.MUTED);
        help.setTextIsSelectable(true);
        content.addView(help);

        Prefs prefs = new Prefs(this);
        content.addView(Ui.text(this, Ui.s(this, Strings.CRED_API_KEY), 13, Ui.FG));
        apiKey = Ui.input(this, "", prefs.getString(Prefs.API_KEY), true);
        content.addView(apiKey);
        keySource = Ui.text(this, "", 11, Ui.MUTED);
        content.addView(keySource);

        content.addView(Ui.text(this, Ui.s(this, Strings.CRED_API_SECRET), 13, Ui.FG));
        apiSecret = Ui.input(this, "", prefs.getString(Prefs.API_SECRET), true);
        apiSecret.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiSecret.setTransformationMethod(PasswordTransformationMethod.getInstance());
        content.addView(apiSecret);
        secretSource = Ui.text(this, "", 11, Ui.MUTED);
        content.addView(secretSource);

        CheckBox showSecret = Ui.checkBox(this, Ui.s(this, Strings.CRED_SHOW_SECRET), false);
        showSecret.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int cursor = apiSecret.getSelectionStart();
                apiSecret.setTransformationMethod(((CheckBox) view).isChecked()
                        ? null : PasswordTransformationMethod.getInstance());
                apiSecret.setSelection(Math.max(0, cursor));
            }
        });
        content.addView(showSecret);

        Button save = Ui.button(this, Ui.s(this, Strings.CRED_SAVE), true,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { save(); }
                });
        content.addView(save);

        Button authenticate = Ui.button(this, Ui.s(this, Strings.CRED_AUTH), false,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        startActivity(new Intent(CredentialsActivity.this, AuthActivity.class));
                    }
                });
        content.addView(authenticate);

        message = Ui.text(this, "", 12, Ui.FG);
        content.addView(message);
        updateSources();
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
        Credentials credentials = ConfigStore.credentials(this);
        keySource.setText(Ui.s(this, Strings.CRED_SOURCE,
                credentials.source("api_key")));
        secretSource.setText(Ui.s(this, Strings.CRED_SOURCE,
                credentials.source("api_secret")));
    }
}
