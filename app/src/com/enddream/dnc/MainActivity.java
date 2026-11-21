package com.enddream.dnc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    static final String PREFS = "dnc_prefs";
    static final String KEY_LAST_LOADED = "last_loaded";

    private SharedPreferences prefs;
    private TextView statusValue;
    private TextView statusHint;
    private View statusDot;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(26));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextAppearance(R.style.TextTitle);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(R.string.xposed_description);
        sub.setTextAppearance(R.style.TextMuted);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(6);
        root.addView(sub, subLp);

        addSection(root, R.string.section_status);
        addCard(root, buildStatusCard());

        addSection(root, R.string.section_features);
        addCard(root, buildFeatureCard());

        addSection(root, R.string.section_usage);
        addCard(root, buildUsageCard());

        addSection(root, R.string.section_about);
        addCard(root, buildAboutCard());
        return scroll;
    }

    private void addSection(LinearLayout root, int res) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextAppearance(R.style.TextSection);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(22);
        root.addView(t, lp);
    }

    private void addCard(LinearLayout root, View card) {
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card_bg);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        return c;
    }

    private View buildStatusCard() {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        dotLp.rightMargin = dp(9);
        row.addView(statusDot, dotLp);

        statusValue = new TextView(this);
        statusValue.setTextAppearance(R.style.TextBody);
        row.addView(statusValue);
        c.addView(row);

        statusHint = new TextView(this);
        statusHint.setTextAppearance(R.style.TextMuted);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(8);
        c.addView(statusHint, hintLp);

        TextView open = new TextView(this);
        open.setText(R.string.action_open);
        open.setTextAppearance(R.style.TextBody);
        open.setTextColor(getColor(R.color.text_primary));
        open.setGravity(Gravity.CENTER);
        open.setBackgroundResource(R.drawable.button_bg);
        open.setPadding(dp(14), dp(13), dp(14), dp(13));
        open.setOnClickListener(v -> openDeviceNamePage());
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        openLp.topMargin = dp(16);
        c.addView(open, openLp);
        return c;
    }

    private View buildFeatureCard() {
        LinearLayout c = card();
        addFeature(c, R.string.feature_1);
        addDivider(c);
        addFeature(c, R.string.feature_2);
        addDivider(c);
        addFeature(c, R.string.feature_3);
        addDivider(c);
        addFeature(c, R.string.feature_4);
        return c;
    }

    private void addFeature(LinearLayout parent, int label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextAppearance(R.style.TextBody);
        t.setTextSize(14);
        t.setLineSpacing(dp(3), 1f);
        parent.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addDivider(LinearLayout parent) {
        View d = new View(this);
        d.setBackgroundColor(getColor(R.color.card_stroke));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
        lp.topMargin = dp(11);
        lp.bottomMargin = dp(11);
        parent.addView(d, lp);
    }

    private View buildUsageCard() {
        LinearLayout c = card();
        int[] steps = {R.string.usage_1, R.string.usage_2, R.string.usage_3};
        for (int i = 0; i < steps.length; i++) {
            TextView t = new TextView(this);
            t.setText((i + 1) + ". " + getString(steps[i]));
            t.setTextAppearance(R.style.TextBody);
            t.setTextSize(14);
            t.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.topMargin = dp(9);
            }
            c.addView(t, lp);
        }
        return c;
    }

    private View buildAboutCard() {
        LinearLayout c = card();
        addInfo(c, R.string.about_version, version(), null);
        addInfo(c, R.string.about_author, getString(R.string.author_value), null);
        addInfo(c, R.string.about_github, getString(R.string.github_value), v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.github_url))));
            } catch (Throwable t) {
                Toast.makeText(this, R.string.action_open_failed, Toast.LENGTH_SHORT).show();
            }
        });
        return c;
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1.0.0";
        }
    }

    private void addInfo(LinearLayout parent, int key, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView k = new TextView(this);
        k.setText(key);
        k.setTextAppearance(R.style.TextBody);
        k.setTextSize(14);
        k.setSingleLine(true);
        row.addView(k, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextAppearance(R.style.TextMuted);
        v.setTextSize(13);
        v.setGravity(Gravity.END);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        vLp.leftMargin = dp(16);
        row.addView(v, vLp);

        if (click != null) {
            row.setOnClickListener(click);
            row.setClickable(true);
            row.setFocusable(true);
        }
        parent.addView(row);
    }

    private void refreshStatus() {
        boolean on = prefs.getLong(KEY_LAST_LOADED, 0L) > 0L;
        statusValue.setText(on ? R.string.status_on : R.string.status_off);
        statusDot.setBackground(dot(on ? getColor(R.color.text_primary)
                : getColor(R.color.text_secondary)));
        statusHint.setText(on ? R.string.status_hint_on : R.string.status_hint_off);
    }

    private GradientDrawable dot(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private void openDeviceNamePage() {
        Intent i = new Intent("android.settings.DEVICE_NAME_EDIT");
        i.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            startActivity(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
            } catch (Throwable t2) {
                Toast.makeText(this, R.string.action_open_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
