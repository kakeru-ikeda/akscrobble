package com.akscrobble;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.akscrobble.core.Strings;

import java.util.Locale;

public final class Ui {
    public static final int BG = 0xFF0F0F10;
    public static final int SURFACE = 0xFF1A1A1C;
    public static final int SURFACE2 = 0xFF242427;
    public static final int LINE = 0xFF2E2E32;
    public static final int FG = 0xFFF2F1EF;
    public static final int MUTED = 0xFF8F8E8B;
    public static final int ACCENT = 0xFFE0281C;
    public static final int ACCENT_PRESSED = 0xFFB81F15;
    public static final int OK = 0xFF3FB27F;
    public static final int WARN = 0xFFE0A526;
    public static final int OFF = 0xFF4A4A4F;
    public static final int BANNER_BG = 0xFF3A1512;
    public static final int BANNER_LINE = 0xFF6B241E;
    public static final int BANNER_FG = 0xFFFFD6D1;

    public static Strings.Lang lang(Context context) {
        String language = context == null ? "" : new Prefs(context).getString(Prefs.LANGUAGE);
        if (language.length() == 0) language = Locale.getDefault().getLanguage();
        return Strings.pick(language);
    }

    public static String s(Context context, String key, Object... args) {
        return Strings.get(lang(context), key, args);
    }

    public static int dp(Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(Context c, int fill, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(c, radiusDp));
        return drawable;
    }

    public static GradientDrawable rounded(Context c, int fill, float radiusDp,
            int stroke, boolean dashed) {
        GradientDrawable drawable = rounded(c, fill, radiusDp);
        int width = Math.max(1, dp(c, 1));
        if (dashed) drawable.setStroke(width, stroke, dp(c, 4), dp(c, 3));
        else drawable.setStroke(width, stroke);
        return drawable;
    }

    public static StateListDrawable pressable(Context c, int normal, int pressed,
            float radiusDp) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[] { android.R.attr.state_pressed },
                rounded(c, pressed, radiusDp));
        drawable.addState(new int[0], rounded(c, normal, radiusDp));
        return drawable;
    }

    public static final class Screen {
        public final LinearLayout bar;
        public final TextView title;
        public final LinearLayout body;

        private Screen(LinearLayout bar, TextView title, LinearLayout body) {
            this.bar = bar;
            this.title = title;
            this.body = body;
        }
    }

    public static Screen screen(Activity a, String title, View trailing, boolean scroll) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        TextView heading = text(a, title, 15, FG);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(heading, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (trailing != null) bar.addView(trailing);
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View line = new View(a);
        line.setBackgroundColor(LINE);
        root.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(a, 1))));

        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14));
        GradientDrawable divider = new GradientDrawable();
        divider.setColor(0x00000000);
        divider.setSize(1, dp(a, 12));
        body.setDividerDrawable(divider);
        body.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        if (scroll) {
            ScrollView container = new ScrollView(a);
            container.setFillViewport(true);
            container.addView(body, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
            root.addView(container, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        } else {
            root.addView(body, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        }
        a.setContentView(root);
        return new Screen(bar, heading, body);
    }

    public static TextView backLink(final Activity a) {
        TextView view = text(a, s(a, Strings.UI_BACK), 13, MUTED);
        view.setPadding(dp(a, 8), 0, dp(a, 8), 0);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { a.finish(); }
        });
        return view;
    }

    public static View gear(Context c, View.OnClickListener listener) {
        GearView view = new GearView(c);
        view.setContentDescription(s(c, Strings.UI_SETTINGS));
        view.setBackground(pressable(c, 0, SURFACE2, 18));
        view.setOnClickListener(listener);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 36), dp(c, 36)));
        return view;
    }

    static final class GearView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GearView(Context context) { super(context); }

        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2.0f;
            float cy = getHeight() / 2.0f;
            float r = Math.min(getWidth(), getHeight()) * 0.33f;
            paint.setColor(FG);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, r, paint);
            canvas.save();
            for (int i = 0; i < 8; i++) {
                canvas.rotate(45, cx, cy);
                canvas.drawRect(cx - r * 0.225f, cy - r * 1.35f,
                        cx + r * 0.225f, cy - r, paint);
            }
            canvas.restore();
            paint.setColor(BG);
            canvas.drawCircle(cx, cy, r * 0.42f, paint);
        }
    }

    public static TextView section(Context c, String label) {
        TextView view = text(c, label, 11, MUTED);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(0.06f);
        view.setPadding(dp(c, 2), 0, 0, 0);
        return view;
    }

    public static LinearLayout list(Context c) {
        LinearLayout view = new LinearLayout(c);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(rounded(c, SURFACE, 10));
        return view;
    }

    public static void addRow(LinearLayout list, View row) {
        if (list.getChildCount() > 0) {
            View divider = new View(list.getContext());
            divider.setBackgroundColor(LINE);
            list.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.max(1, dp(list.getContext(), 0.5f))));
        }
        list.addView(row);
    }

    public static final class Row {
        public final LinearLayout view;
        public final TextView title;
        public final TextView subtitle;

        private Row(LinearLayout view, TextView title, TextView subtitle) {
            this.view = view;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    public static Row row(Context c, String title, String subtitle, View leading,
            View trailing, View.OnClickListener onClick) {
        LinearLayout container = new LinearLayout(c);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        if (onClick != null) {
            container.setBackground(pressable(c, 0, SURFACE2, 0));
            container.setOnClickListener(onClick);
        }
        if (leading != null) {
            addWithEndMargin(container, leading, dp(c, 12));
        }
        LinearLayout center = new LinearLayout(c);
        center.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(c, title, 14, FG);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        center.addView(titleView);
        TextView subtitleView = text(c, subtitle == null ? "" : subtitle, 11.5f, MUTED);
        if (subtitle == null || subtitle.length() == 0) subtitleView.setVisibility(View.GONE);
        else center.addView(subtitleView);
        container.addView(center, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        View right = trailing;
        if (right == null && onClick != null) {
            TextView chevron = text(c, "›", 18, MUTED);
            chevron.setGravity(Gravity.CENTER);
            right = chevron;
        }
        if (right != null) addWithStartMargin(container, right, dp(c, 12));
        return new Row(container, titleView, subtitleView);
    }

    private static void addWithEndMargin(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = margin;
        parent.addView(child, params);
    }

    private static void addWithStartMargin(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        parent.addView(child, params);
    }

    public static void setStatus(TextView subtitle, String text, int dotColor) {
        String value = dotColor == 0 ? text : "● " + text;
        if (dotColor == 0) {
            subtitle.setText(value);
            return;
        }
        SpannableString styled = new SpannableString(value);
        styled.setSpan(new ForegroundColorSpan(dotColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        subtitle.setText(styled);
    }

    public static TextView tag(Context c, String abbr, int color) {
        TextView view = text(c, abbr, 12, 0xFFFFFFFF);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackground(rounded(c, color, 8));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 34), dp(c, 34)));
        return view;
    }

    public static Switch toggle(Context c, boolean checked) {
        Switch view = new Switch(c);
        view.setChecked(checked);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            view.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
            view.setTrackTintList(ColorStateList.valueOf(checked ? OK : OFF));
        }
        view.setShowText(false);
        return view;
    }

    public static Button button(Context c, String label, boolean primary,
            View.OnClickListener listener) {
        Button view = new Button(c);
        view.setText(label);
        view.setAllCaps(false);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        view.setStateListAnimator(null);
        view.setBackground(primary ? pressable(c, ACCENT, ACCENT_PRESSED, 8)
                : pressable(c, SURFACE2, LINE, 8));
        view.setTextColor(primary ? 0xFFFFFFFF : FG);
        view.setOnClickListener(listener);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    public static EditText input(Context c, String hint, String value, boolean monospace) {
        EditText view = new EditText(c);
        view.setSingleLine(true);
        view.setBackground(rounded(c, SURFACE, 8, LINE, false));
        view.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        view.setTextColor(FG);
        view.setHintTextColor(OFF);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setHint(hint);
        view.setText(value);
        if (monospace) view.setTypeface(Typeface.MONOSPACE);
        return view;
    }

    public static TextView text(Context c, String value, float sp, int color) {
        TextView view = new TextView(c);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        return view;
    }

    public static TextView mono(Context c, String value) {
        TextView view = text(c, value, 10, FG);
        view.setTypeface(Typeface.MONOSPACE);
        view.setBackground(rounded(c, SURFACE, 8));
        view.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        view.setTextIsSelectable(true);
        return view;
    }

    public static TextView pathBox(Context c, String path) {
        TextView view = text(c, path, 11.5f, FG);
        view.setTypeface(Typeface.MONOSPACE);
        view.setBackground(rounded(c, SURFACE, 8, OFF, true));
        view.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        return view;
    }

    public static CheckBox checkBox(Context c, String label, boolean checked) {
        CheckBox view = new CheckBox(c);
        view.setText(label);
        view.setChecked(checked);
        view.setTextColor(MUTED);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setButtonTintList(ColorStateList.valueOf(checked ? ACCENT : OFF));
        return view;
    }

    public static LinearLayout steps(Context c, int current, String[] labels) {
        LinearLayout container = new LinearLayout(c);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bars = new LinearLayout(c);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            View bar = new View(c);
            bar.setBackgroundColor(i < current ? ACCENT : OFF);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(c, 3), 1);
            if (i > 0) params.leftMargin = dp(c, 4);
            bars.addView(bar, params);
        }
        container.addView(bars);
        LinearLayout names = new LinearLayout(c);
        names.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            TextView label = text(c, labels[i], 11, MUTED);
            label.setGravity(Gravity.CENTER);
            names.addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        container.addView(names);
        return container;
    }

    public interface SegmentListener { void onSelected(int index); }

    public static final class Segment {
        public final LinearLayout view;
        private final Context context;
        private final TextView[] labels;
        private int selected;

        private Segment(LinearLayout view, Context context, TextView[] labels, int selected) {
            this.view = view;
            this.context = context;
            this.labels = labels;
            this.selected = selected;
        }

        public void select(int index) {
            selected = index;
            for (int i = 0; i < labels.length; i++) {
                boolean active = i == selected;
                labels[i].setBackground(active ? rounded(context, SURFACE2, 6)
                        : rounded(context, 0, 6));
                labels[i].setTextColor(active ? FG : MUTED);
            }
        }
    }

    public static Segment segment(Context c, String[] labels, int selected,
            final SegmentListener listener) {
        LinearLayout container = new LinearLayout(c);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3));
        container.setBackground(rounded(c, SURFACE, 8));
        TextView[] views = new TextView[labels.length];
        final Segment segment = new Segment(container, c, views, selected);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView label = text(c, labels[i], 13, MUTED);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            label.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
            views[i] = label;
            container.addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            label.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    segment.select(index);
                    if (listener != null) listener.onSelected(index);
                }
            });
        }
        segment.select(selected);
        return segment;
    }

    public static TextView banner(Context c, String message, View.OnClickListener listener) {
        TextView view = text(c, message, 12, BANNER_FG);
        view.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        view.setBackground(rounded(c, BANNER_BG, 10, BANNER_LINE, false));
        if (listener != null) view.setOnClickListener(listener);
        return view;
    }

    private Ui() {}
}
