package com.akscrobble;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.akscrobble.core.ComponentFilter;
import com.akscrobble.core.Strings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdbActivity extends Activity {
    private static final String FACTORY_PACKAGE = "com.iriver.tester.factorytool";
    private TextView log;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(Ui.s(this, Strings.ADB_TITLE));

        LinearLayout content = Ui.screen(this, Ui.s(this, Strings.ADB_TITLE),
                Ui.backLink(this), true).body;

        TextView adbState = Ui.mono(this, "adb_enabled=" + setting("adb_enabled")
                + " development_settings_enabled=" + setting("development_settings_enabled"));
        adbState.setTextIsSelectable(true);
        content.addView(adbState);

        List<ComponentName> components = discover();
        Map<String, ComponentName> byName = new LinkedHashMap<String, ComponentName>();
        List<String> names = new ArrayList<String>();
        for (ComponentName component : components) {
            names.add(component.getClassName());
            byName.put(component.getClassName(), component);
        }
        ComponentFilter.Split split = ComponentFilter.classify(names);

        TextView primaryHelp = Ui.text(this, Ui.s(this, Strings.ADB_PRIMARY_HELP), 16f, Ui.FG);
        content.addView(primaryHelp);
        for (String name : split.primary) addComponentButton(content, byName.get(name), true);

        final LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.VERTICAL);
        secondary.setVisibility(View.GONE);
        Button secondaryToggle = Ui.button(this, Ui.s(this, Strings.ADB_SECONDARY_HEADER),
                false, null);
        secondaryToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                secondary.setVisibility(secondary.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
            }
        });
        content.addView(secondaryToggle);
        for (String name : split.secondary) addComponentButton(secondary, byName.get(name), false);
        content.addView(secondary);

        content.addView(Ui.text(this, Ui.s(this, Strings.ADB_BATTERY_HELP), 16f, Ui.FG));
        TextView command = Ui.mono(this, "adb shell dumpsys deviceidle whitelist +" + getPackageName());
        command.setTextIsSelectable(true);
        content.addView(command);

        content.addView(Ui.text(this, Ui.s(this, Strings.ADB_LOG_HEADER), 14f, Ui.FG));
        log = Ui.mono(this, components.isEmpty() ? Ui.s(this, Strings.ADB_NONE_FOUND) : "");
        log.setTextIsSelectable(true);
        content.addView(log, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int setting(String key) {
        try { return Settings.Global.getInt(getContentResolver(), key, -1); }
        catch (Throwable ignored) { return -1; }
    }

    private List<ComponentName> discover() {
        List<ComponentName> result = new ArrayList<ComponentName>();
        PackageManager manager = getPackageManager();
        try {
            addExported(result, manager.getPackageInfo(
                    FACTORY_PACKAGE, PackageManager.GET_ACTIVITIES));
        } catch (PackageManager.NameNotFoundException ignored) {
        } catch (Throwable ignored) {
        }
        if (!result.isEmpty()) return result;
        try {
            for (PackageInfo info : manager.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
                String name = info.packageName == null ? ""
                        : info.packageName.toLowerCase(Locale.US);
                if (name.contains("iriver") || name.contains("tester") || name.contains("factory"))
                    addExported(result, info);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static void addExported(List<ComponentName> target, PackageInfo info) {
        if (info == null || info.activities == null) return;
        for (ActivityInfo activity : info.activities) {
            if (activity != null && activity.exported && activity.name != null) {
                String packageName = activity.packageName == null
                        ? info.packageName : activity.packageName;
                if (packageName != null) target.add(new ComponentName(packageName, activity.name));
            }
        }
    }

    private void addComponentButton(LinearLayout parent, final ComponentName component,
            boolean primary) {
        if (component == null) return;
        // primary は「これを押せば ADB が有効になる」ボタンなので、完全修飾名だけだと
        // 何のボタンか分からない。平易なラベルを主にし、実際に起動する
        // コンポーネント名を副として併記する（機種差で名前が変わりうるため隠さない）。
        Button launch = Ui.button(this, primary
                ? Ui.s(this, Strings.ADB_PRIMARY_LABEL, component.getClassName())
                : component.getClassName(), primary, null);
        if (primary) {
            launch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            launch.setPadding(Ui.dp(this, 20), Ui.dp(this, 20),
                    Ui.dp(this, 20), Ui.dp(this, 20));
        }
        launch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { launch(component); }
        });
        parent.addView(launch);
    }

    private void launch(ComponentName component) {
        String name = component.flattenToShortString();
        try {
            Intent intent = new Intent().setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            append(Ui.s(this, Strings.ADB_LAUNCHED, name));
        } catch (ActivityNotFoundException error) {
            append(Ui.s(this, Strings.ADB_NOT_FOUND, name));
        } catch (SecurityException error) {
            append(Ui.s(this, Strings.ADB_DENIED, name));
        } catch (Throwable error) {
            append(Ui.s(this, Strings.ADB_FAILED, name, error.getClass().getSimpleName()));
        }
    }

    private void append(String message) {
        if (log == null) return;
        if (log.length() == 0) log.setText(message); else log.append("\n" + message);
    }
}
