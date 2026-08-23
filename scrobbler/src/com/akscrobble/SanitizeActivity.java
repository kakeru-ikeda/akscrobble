package com.akscrobble;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.akscrobble.core.SanitizeConfig;
import com.akscrobble.core.SanitizeRule;
import com.akscrobble.core.Sanitizer;
import com.akscrobble.core.Strings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class SanitizeActivity extends Activity {
    private EditText idInput;
    private Spinner scopeInput;
    private EditText regexInput;
    private EditText replacementInput;
    private TextView formError;
    private EditText previewInput;
    private Spinner previewScope;
    private TextView previewResult;
    private String editingId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // manifest の android:label は固定文字列なので、表示名はここで言語に合わせる。
        setTitle(Ui.s(this, Strings.SAN_TITLE));
        final SanitizeConfig config = SanitizeStore.load(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(12, 12, 12, 12);

        CheckBox enabled = new CheckBox(this);
        enabled.setText(Ui.s(this, Strings.SAN_ENABLED));
        enabled.setChecked(config.enabled);
        enabled.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                SanitizeStore.save(SanitizeActivity.this,
                        config.withEnabled(((CheckBox) view).isChecked()));
                recreate();
            }
        });
        content.addView(enabled);

        addHeading(content, Strings.SAN_DEFAULT_RULES);
        for (final SanitizeRule rule : Sanitizer.DEFAULT_RULES) {
            CheckBox box = new CheckBox(this);
            box.setText(rule.id + "\n" + Ui.s(this, noteKey(rule.id)));
            box.setChecked(!config.disabledIds.contains(rule.id));
            box.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    Set<String> disabled = new HashSet<String>(config.disabledIds);
                    if (((CheckBox) view).isChecked()) disabled.remove(rule.id);
                    else disabled.add(rule.id);
                    SanitizeStore.save(SanitizeActivity.this, config.withDisabled(disabled));
                    recreate();
                }
            });
            content.addView(box);
        }

        addHeading(content, Strings.SAN_CUSTOM_RULES);
        for (final SanitizeRule rule : config.customRules) addCustomRow(content, config, rule);
        addForm(content, config);
        addPreview(content, config);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void addCustomRow(LinearLayout parent, final SanitizeConfig config,
            final SanitizeRule rule) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        CheckBox enabled = new CheckBox(this);
        // 既定ルール側と同じく、チェックボックス自身にルールの説明を持たせる。
        // 「有効」ラベルと別 TextView に分けると文字が詰まって見え、
        // 説明がボタンに押されて置換先が見切れる（実機で確認）。
        enabled.setText(rule.id + "\n[" + rule.scope.name() + "] /"
                + rule.pattern.pattern() + "/ -> " + rule.replacement);
        enabled.setChecked(!config.disabledIds.contains(rule.id));
        enabled.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Set<String> disabled = new HashSet<String>(config.disabledIds);
                if (((CheckBox) view).isChecked()) disabled.remove(rule.id);
                else disabled.add(rule.id);
                SanitizeStore.save(SanitizeActivity.this, config.withDisabled(disabled));
                recreate();
            }
        });
        row.addView(enabled, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button edit = button(Ui.s(this, Strings.SAN_EDIT), new View.OnClickListener() {
            @Override public void onClick(View view) { edit(rule); }
        });
        row.addView(edit);
        Button delete = button(Ui.s(this, Strings.SAN_DELETE), new View.OnClickListener() {
            @Override public void onClick(View view) {
                List<SanitizeRule> rules = new ArrayList<SanitizeRule>(config.customRules);
                removeRule(rules, rule.id);
                Set<String> disabled = new HashSet<String>(config.disabledIds);
                disabled.remove(rule.id);
                SanitizeStore.save(SanitizeActivity.this,
                        new SanitizeConfig(config.enabled, disabled, rules));
                recreate();
            }
        });
        row.addView(delete);
        parent.addView(row);
    }

    private void addForm(LinearLayout parent, final SanitizeConfig config) {
        addHeading(parent, Strings.SAN_FORM);
        idInput = editText(Strings.SAN_ID); parent.addView(idInput);
        scopeInput = scopeSpinner(); parent.addView(scopeInput);
        regexInput = editText(Strings.SAN_REGEX); parent.addView(regexInput);
        replacementInput = editText(Strings.SAN_REPLACEMENT); parent.addView(replacementInput);
        formError = new TextView(this); parent.addView(formError);
        parent.addView(button(Ui.s(this, Strings.SAN_SAVE), new View.OnClickListener() {
            @Override public void onClick(View view) {
                SanitizeConfig changed = configWithForm(config, true);
                if (changed == null) return;
                SanitizeStore.save(SanitizeActivity.this, changed);
                recreate();
            }
        }));
    }

    private void addPreview(LinearLayout parent, final SanitizeConfig config) {
        addHeading(parent, Strings.SAN_PREVIEW);
        previewInput = editText(Strings.SAN_PREVIEW_INPUT); parent.addView(previewInput);
        previewScope = scopeSpinner(); parent.addView(previewScope);
        previewResult = new TextView(this);
        previewResult.setTypeface(Typeface.MONOSPACE);
        parent.addView(button(Ui.s(this, Strings.SAN_PREVIEW), new View.OnClickListener() {
            @Override public void onClick(View view) {
                SanitizeConfig temporary = configWithForm(config, false);
                if (temporary == null) temporary = config;
                SanitizeRule.Scope scope = selectedScope(previewScope);
                String input = previewInput.getText().toString();
                Sanitizer sanitizer = Sanitizer.from(temporary);
                StringBuilder text = new StringBuilder();
                for (String change : sanitizer.explain(scope, input)) {
                    text.append(change).append('\n');
                }
                text.append(Ui.s(SanitizeActivity.this, Strings.SAN_PREVIEW_RESULT,
                        sanitizer.apply(scope, input)));
                previewResult.setText(text.toString());
            }
        }));
        parent.addView(previewResult);
    }

    private SanitizeConfig configWithForm(SanitizeConfig config, boolean showErrors) {
        String id = idInput.getText().toString().trim();
        String regex = regexInput.getText().toString();
        String replacement = replacementInput.getText().toString();
        boolean completelyEmpty = id.length() == 0 && regex.length() == 0
                && replacement.length() == 0 && editingId == null;
        if (!showErrors && completelyEmpty) return config;
        if (id.length() == 0) return fail(Strings.SAN_ERROR_ID_EMPTY, showErrors);
        for (SanitizeRule rule : Sanitizer.DEFAULT_RULES) {
            if (rule.id.equals(id)) return fail(Strings.SAN_ERROR_ID_DEFAULT, showErrors);
        }
        for (SanitizeRule rule : config.customRules) {
            if (rule.id.equals(id) && !rule.id.equals(editingId)) {
                return fail(Strings.SAN_ERROR_ID_DUPLICATE, showErrors);
            }
        }
        try { Pattern.compile(regex); }
        catch (PatternSyntaxException error) {
            return fail(Strings.SAN_ERROR_REGEX, showErrors, error.getDescription());
        }
        List<SanitizeRule> rules = new ArrayList<SanitizeRule>(config.customRules);
        boolean wasDisabled = editingId != null && config.disabledIds.contains(editingId);
        if (editingId != null) removeRule(rules, editingId);
        rules.add(new SanitizeRule(id, selectedScope(scopeInput), regex, replacement, true, ""));
        Set<String> disabled = new HashSet<String>(config.disabledIds);
        if (editingId != null) disabled.remove(editingId);
        if (wasDisabled) disabled.add(id);
        if (showErrors) formError.setText("");
        return new SanitizeConfig(config.enabled, disabled, rules);
    }

    private SanitizeConfig fail(String key, boolean show, Object... args) {
        if (show) formError.setText(Ui.s(this, key, args));
        return null;
    }

    private void edit(SanitizeRule rule) {
        editingId = rule.id;
        idInput.setText(rule.id);
        scopeInput.setSelection(rule.scope.ordinal());
        regexInput.setText(rule.pattern.pattern());
        replacementInput.setText(rule.replacement);
        formError.setText("");
        idInput.requestFocus();
    }

    private Spinner scopeSpinner() {
        Spinner spinner = new Spinner(this);
        SanitizeRule.Scope[] values = SanitizeRule.Scope.values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].name();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private static SanitizeRule.Scope selectedScope(Spinner spinner) {
        return SanitizeRule.Scope.values()[spinner.getSelectedItemPosition()];
    }

    private EditText editText(String hintKey) {
        EditText edit = new EditText(this);
        edit.setHint(Ui.s(this, hintKey));
        edit.setSingleLine(true);
        return edit;
    }

    private void addHeading(LinearLayout parent, String key) {
        TextView heading = new TextView(this);
        heading.setText(Ui.s(this, key));
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        parent.addView(heading);
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private static void removeRule(List<SanitizeRule> rules, String id) {
        for (int i = rules.size() - 1; i >= 0; i--) {
            if (rules.get(i).id.equals(id)) rules.remove(i);
        }
    }

    private static String noteKey(String ruleId) {
        if ("strip-zero-width".equals(ruleId)) return Strings.SAN_NOTE_STRIP_ZERO_WIDTH;
        if ("fullwidth-space".equals(ruleId)) return Strings.SAN_NOTE_FULLWIDTH_SPACE;
        if ("album-ep".equals(ruleId)) return Strings.SAN_NOTE_ALBUM_EP;
        if ("album-single".equals(ruleId)) return Strings.SAN_NOTE_ALBUM_SINGLE;
        if ("album-deluxe".equals(ruleId)) return Strings.SAN_NOTE_ALBUM_DELUXE;
        if ("album-remaster".equals(ruleId)) return Strings.SAN_NOTE_ALBUM_REMASTER;
        if ("album-bonus".equals(ruleId)) return Strings.SAN_NOTE_ALBUM_BONUS;
        if ("collapse-spaces".equals(ruleId)) return Strings.SAN_NOTE_COLLAPSE_SPACES;
        if ("trim-ends".equals(ruleId)) return Strings.SAN_NOTE_TRIM_ENDS;
        return ruleId;
    }
}
