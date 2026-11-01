package com.akscrobble;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
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
        Ui.Screen screen = Ui.screen(this, Ui.s(this, Strings.SAN_TITLE),
                Ui.backLink(this), true);

        LinearLayout enabledList = Ui.list(this);
        final Switch enabled = Ui.toggle(this, config.enabled);
        Ui.addRow(enabledList, Ui.row(this, Ui.s(this, Strings.SAN_ENABLED), null,
                null, enabled, null).view);
        enabled.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton button,
                    boolean checked) {
                SanitizeStore.save(SanitizeActivity.this, config.withEnabled(checked));
                recreate();
            }
        });
        screen.body.addView(enabledList);

        screen.body.addView(Ui.section(this, Ui.s(this, Strings.SAN_DEFAULT_RULES)));
        LinearLayout defaultRules = Ui.list(this);
        for (final SanitizeRule rule : Sanitizer.DEFAULT_RULES) {
            final CheckBox box = Ui.checkBox(this,
                    rule.id + "\n" + Ui.s(this, noteKey(rule.id)),
                    !config.disabledIds.contains(rule.id));
            box.setTextColor(Ui.FG);
            box.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    Set<String> disabled = new HashSet<String>(config.disabledIds);
                    if (((CheckBox) view).isChecked()) disabled.remove(rule.id);
                    else disabled.add(rule.id);
                    SanitizeStore.save(SanitizeActivity.this, config.withDisabled(disabled));
                    recreate();
                }
            });
            Ui.addRow(defaultRules, box);
        }
        screen.body.addView(defaultRules);

        screen.body.addView(Ui.section(this, Ui.s(this, Strings.SAN_CUSTOM_RULES)));
        LinearLayout customRules = Ui.list(this);
        for (final SanitizeRule rule : config.customRules) {
            addCustomRow(customRules, config, rule);
        }
        screen.body.addView(customRules);
        addForm(screen.body, config);
        addPreview(screen.body, config);

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
        android.widget.Button edit = Ui.button(this, Ui.s(this, Strings.SAN_EDIT), false,
                new View.OnClickListener() {
            @Override public void onClick(View view) { edit(rule); }
        });
        edit.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(edit);
        android.widget.Button delete = Ui.button(this, Ui.s(this, Strings.SAN_DELETE), false,
                new View.OnClickListener() {
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
        delete.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(delete);
        Ui.addRow(parent, row);
    }

    private void addForm(LinearLayout parent, final SanitizeConfig config) {
        parent.addView(Ui.section(this, Ui.s(this, Strings.SAN_FORM)));
        idInput = Ui.input(this, Ui.s(this, Strings.SAN_ID), "", false);
        parent.addView(idInput);
        scopeInput = scopeSpinner(); parent.addView(scopeInput);
        regexInput = Ui.input(this, Ui.s(this, Strings.SAN_REGEX), "", true);
        parent.addView(regexInput);
        replacementInput = Ui.input(this, Ui.s(this, Strings.SAN_REPLACEMENT), "", false);
        parent.addView(replacementInput);
        scopeInput.setBackground(Ui.rounded(this, Ui.SURFACE, 8));
        formError = Ui.text(this, "", 12, Ui.ACCENT); parent.addView(formError);
        parent.addView(Ui.button(this, Ui.s(this, Strings.SAN_SAVE), true,
                new View.OnClickListener() {
            @Override public void onClick(View view) {
                SanitizeConfig changed = configWithForm(config, true);
                if (changed == null) return;
                SanitizeStore.save(SanitizeActivity.this, changed);
                recreate();
            }
        }));
    }

    private void addPreview(LinearLayout parent, final SanitizeConfig config) {
        parent.addView(Ui.section(this, Ui.s(this, Strings.SAN_PREVIEW)));
        previewInput = Ui.input(this, Ui.s(this, Strings.SAN_PREVIEW_INPUT), "", false);
        parent.addView(previewInput);
        previewScope = scopeSpinner(); parent.addView(previewScope);
        previewScope.setBackground(Ui.rounded(this, Ui.SURFACE, 8));
        previewResult = Ui.mono(this, "");
        parent.addView(Ui.button(this, Ui.s(this, Strings.SAN_PREVIEW), false,
                new View.OnClickListener() {
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
