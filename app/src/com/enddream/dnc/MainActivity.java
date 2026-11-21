package com.enddream.dnc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.util.Map;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class MainActivity extends Activity {

    private static final String ALIAS = "com.enddream.dnc.Launcher";

    private SharedPreferences prefs;
    private SharedPreferences remote;
    private XposedService service;
    private TextView statusValue;
    private TextView statusHint;
    private View statusDot;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            android.app.ActionBar ab = getActionBar();
            if (ab != null) {
                ab.hide();
            }
        } catch (Throwable ignored) {
        }
        prefs = openPrefs(this);
        bindService();
        if (prefs.getBoolean(Keys.AGREED, false)) {
            showMain();
        } else {
            showGate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusValue != null) {
            pushConfig();
            refreshStatus();
        }
    }

    static SharedPreferences openPrefs(Context c) {
        return c.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE);
    }

    private void bindService() {
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(XposedService s) {
                    service = s;
                    try {
                        remote = s.getRemotePreferences(Keys.PREFS);
                    } catch (Throwable t) {
                        remote = null;
                    }
                    pushAll();
                }

                @Override
                public void onServiceDied(XposedService s) {
                    service = null;
                    remote = null;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void pushAll() {
        if (remote == null) {
            return;
        }
        try {
            SharedPreferences.Editor e = remote.edit();
            for (Map.Entry<String, ?> en : prefs.getAll().entrySet()) {
                Object v = en.getValue();
                if (v instanceof Boolean) {
                    e.putBoolean(en.getKey(), (Boolean) v);
                }
            }
            e.apply();
        } catch (Throwable ignored) {
        }
    }

    private boolean flag(String key) {
        if (remote != null) {
            try {
                return remote.getBoolean(key, false);
            } catch (Throwable ignored) {
            }
        }
        return prefs.getBoolean(key, false);
    }

    private void put(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
        if (remote != null) {
            try {
                remote.edit().putBoolean(key, value).apply();
            } catch (Throwable ignored) {
            }
        }
        pushConfig();
        writeConf();
    }

    private void pushConfig() {
        try {
            Intent i = new Intent(Keys.CONFIG);
            i.setPackage("com.android.settings");
            for (String key : Keys.CONFIG_KEYS) {
                i.putExtra(key, prefs.getBoolean(key, false));
            }
            sendBroadcast(i);
        } catch (Throwable ignored) {
        }
        pushConfigRoot();
    }

    private void pushConfigRoot() {
        final StringBuilder sb = new StringBuilder();
        for (String key : Keys.CONFIG_KEYS) {
            sb.append("settings put global ")
                    .append(Keys.CFG).append(key).append(' ')
                    .append(prefs.getBoolean(key, false) ? '1' : '0')
                    .append("; ");
        }
        new Thread(() -> exec("su -c \"" + sb + "\"")).start();
    }

    private void showMain() {
        setContentView(build());
        refreshStatus();
    }

    static void applyInsets(final View v, final int l, final int t, final int r, final int b) {
        v.setFitsSystemWindows(false);
        v.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(l + left, t + top, r + right, b + bottom);
            return insets;
        });
        v.post(() -> {
            try {
                android.view.WindowInsets in = v.getRootWindowInsets();
                if (in != null) {
                    int left;
                    int top;
                    int right;
                    int bottom;
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets bars = in.getInsets(
                                android.view.WindowInsets.Type.systemBars()
                                        | android.view.WindowInsets.Type.displayCutout());
                        left = bars.left;
                        top = bars.top;
                        right = bars.right;
                        bottom = bars.bottom;
                    } else {
                        left = in.getSystemWindowInsetLeft();
                        top = in.getSystemWindowInsetTop();
                        right = in.getSystemWindowInsetRight();
                        bottom = in.getSystemWindowInsetBottom();
                    }
                    v.setPadding(l + left, t + top, r + right, b + bottom);
                }
            } catch (Throwable ignored) {
            }
        });
        v.requestApplyInsets();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void showGate() {
        docDialog(R.string.agree_user_title, R.string.agree_user_body,
                getString(R.string.agree_next),
                () -> docDialog(R.string.agree_privacy_title, R.string.agree_privacy_body,
                        getString(R.string.agree_accept), () -> {
                            put(Keys.AGREED, true);
                            writeConf();
                            showMain();
                        }, this::reject),
                this::reject);
    }

    private void reject() {
        Toast.makeText(this, R.string.agree_reject_toast, Toast.LENGTH_SHORT).show();
        finishAffinity();
    }

    private void docDialog(int titleRes, int bodyRes, String positive, Runnable onAccept,
                           Runnable onReject) {
        TextView body = new TextView(this);
        body.setText(bodyRes);
        body.setTextAppearance(R.style.TextBody);
        body.setTextSize(14);
        body.setLineSpacing(dp(4), 1f);
        body.setPadding(dp(20), dp(16), dp(20), dp(8));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(scroll)
                .setCancelable(false)
                .setPositiveButton(positive, (d, w) -> onAccept.run())
                .setNegativeButton(R.string.agree_reject, (d, w) -> onReject.run())
                .show();
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        scroll.setFillViewport(true);
        applyInsets(scroll, 0, 0, 0, 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextAppearance(R.style.TextTitle);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(6);
        tlp.leftMargin = dp(4);
        tlp.rightMargin = dp(4);
        root.addView(title, tlp);

        TextView sub = new TextView(this);
        sub.setText(R.string.xposed_description);
        sub.setTextAppearance(R.style.TextMuted);
        sub.setPadding(dp(4), dp(6), dp(4), 0);
        root.addView(sub);

        addSection(root, R.string.section_status);
        addCard(root, buildStatusCard());

        addSection(root, R.string.section_shortcut);
        addCard(root, buildShortcutCard());

        addSection(root, R.string.section_switch);
        addCard(root, buildSwitchCard());

        addSection(root, R.string.section_usage);
        addCard(root, buildUsageCard());

        addSection(root, R.string.section_settings);
        addCard(root, buildSettingsCard());

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
        lp.bottomMargin = dp(8);
        lp.leftMargin = dp(8);
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
        return c;
    }

    private View buildStatusCard() {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(18), dp(20), dp(4));

        statusDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        dotLp.rightMargin = dp(10);
        row.addView(statusDot, dotLp);

        statusValue = new TextView(this);
        statusValue.setTextAppearance(R.style.TextBody);
        row.addView(statusValue);
        c.addView(row);

        statusHint = new TextView(this);
        statusHint.setTextAppearance(R.style.TextMuted);
        statusHint.setPadding(dp(20), dp(6), dp(20), dp(18));
        c.addView(statusHint);
        return c;
    }

    private View buildShortcutCard() {
        LinearLayout c = card();
        addAction(c, R.string.restart, v -> restartSettings());
        addDivider(c);
        addAction(c, R.string.shortcut_device, v -> openSettings(
                new Intent("android.settings.DEVICE_NAME_EDIT"),
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
        return c;
    }

    private void openSettings(Intent first, Intent second) {
        try {
            first.addCategory(Intent.CATEGORY_DEFAULT);
            startActivity(first);
            return;
        } catch (Throwable ignored) {
        }
        try {
            startActivity(second);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.shortcut_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void addAction(LinearLayout parent, int label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextAppearance(R.style.TextBody);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(20), dp(17), dp(20), dp(17));
        t.setBackgroundResource(R.drawable.row_bg);
        t.setOnClickListener(l);
        parent.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View buildSwitchCard() {
        LinearLayout c = card();
        addSwitch(c, Keys.EN_DEVICE, R.string.sw_device);
        addDivider(c);
        addConfirmSwitch(c, Keys.EN_CLOUD, R.string.sw_cloud, Keys.CLOUD_TIP_OFF,
                R.string.cloud_dialog_title, R.string.cloud_dialog_body,
                R.string.cloud_dialog_ok, R.string.cloud_dialog_cancel, R.string.cloud_dialog_never);
        addDivider(c);
        addSwitch(c, Keys.EN_RESET, R.string.sw_reset);
        return c;
    }

    private void addDivider(LinearLayout parent) {
        View d = new View(this);
        d.setBackgroundColor(getColor(R.color.card_stroke));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
        lp.leftMargin = dp(20);
        parent.addView(d, lp);
    }

    private LinearLayout newRow(LinearLayout parent, int label, MiuixSwitch s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextAppearance(R.style.TextBody);
        t.setTextSize(16);
        t.setLineSpacing(dp(2), 1f);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(14);
        row.addView(s, slp);
        parent.addView(row);
        return row;
    }

    private void addSwitch(LinearLayout parent, String key, int label) {
        MiuixSwitch s = new MiuixSwitch(this);
        s.setChecked(flag(key), false);
        newRow(parent, label, s);
        s.setOnChangeListener(checked -> {
            put(key, checked);
            s.setChecked(checked);
            writeConf();
            toastState(label, checked);
        });
    }

    private void toastState(int label, boolean checked) {
        String name = getString(label).replace("\n", " ");
        Toast.makeText(this, getString(checked ? R.string.toast_on : R.string.toast_off, name),
                Toast.LENGTH_SHORT).show();
    }

    private void addConfirmSwitch(LinearLayout parent, String key, int label, String tipKey,
                                  int title, int body, int ok, int cancel, int never) {
        MiuixSwitch s = new MiuixSwitch(this);
        s.setChecked(flag(key), false);
        newRow(parent, label, s);
        final boolean[] suppress = {false};
        s.setOnChangeListener(want -> {
            if (suppress[0]) {
                return;
            }
            if (!want) {
                put(key, false);
                s.setChecked(false);
                writeConf();
                toastState(label, false);
                return;
            }
            if (prefs.getBoolean(tipKey, false)) {
                put(key, true);
                s.setChecked(true);
                writeConf();
                toastState(label, true);
                return;
            }
            suppress[0] = true;
            showConfirmDialog(tipKey, title, body, ok, cancel, never,
                    () -> {
                        suppress[0] = false;
                        put(key, true);
                        s.setChecked(true);
                        writeConf();
                        toastState(label, true);
                    },
                    () -> {
                        suppress[0] = false;
                        s.setChecked(false);
                    });
        });
    }

    private void showConfirmDialog(String tipKey, int title, int body,
                                   int ok, int cancel, int never,
                                   Runnable onAccept, Runnable onCancel) {
        showTimedDialog(title, body, ok, cancel, never, tipKey, onAccept, onCancel);
    }

    private void showTimedDialog(int title, int body, int ok, int cancel, int never,
                                 String tipKey, Runnable onAccept, Runnable onCancel) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(4));

        TextView tv = new TextView(this);
        tv.setText(body);
        tv.setTextAppearance(R.style.TextBody);
        tv.setTextSize(14);
        tv.setLineSpacing(dp(3), 1f);
        box.addView(tv);

        final CheckBox cb = new CheckBox(this);
        cb.setText(never);
        cb.setTextSize(14);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(10);
        box.addView(cb, nlp);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setNegativeButton(cancel, (d, w) -> onCancel.run())
                .setPositiveButton(ok, null)
                .create();
        dialog.setOnShowListener(d -> {
            final android.widget.Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            pos.setEnabled(false);
            final int[] left = {3};
            final Handler h = new Handler(Looper.getMainLooper());
            h.post(new Runnable() {
                @Override
                public void run() {
                    if (left[0] > 0) {
                        pos.setText(getString(R.string.dialog_wait, left[0]));
                        left[0]--;
                        h.postDelayed(this, 1000);
                    } else {
                        pos.setText(ok);
                        pos.setEnabled(true);
                    }
                }
            });
            pos.setOnClickListener(v -> {
                if (cb.isChecked() && tipKey != null) {
                    prefs.edit().putBoolean(tipKey, true).apply();
                }
                dialog.dismiss();
                onAccept.run();
            });
        });
        dialog.setOnCancelListener(d -> onCancel.run());
        dialog.show();
    }

    private View buildUsageCard() {
        LinearLayout c = card();
        int[] steps = {R.string.usage_1, R.string.usage_2, R.string.usage_3, R.string.usage_4};
        for (int i = 0; i < steps.length; i++) {
            TextView t = new TextView(this);
            t.setText((i + 1) + ". " + getString(steps[i]));
            t.setTextAppearance(R.style.TextBody);
            t.setTextSize(14);
            t.setLineSpacing(dp(3), 1f);
            t.setPadding(dp(20), dp(i == 0 ? 16 : 6), dp(20), dp(i == steps.length - 1 ? 16 : 6));
            c.addView(t);
        }
        return c;
    }

    private View buildSettingsCard() {
        LinearLayout c = card();
        MiuixSwitch s = new MiuixSwitch(this);
        s.setChecked(isIconHidden(), false);
        newRow(c, R.string.set_hide_icon, s);
        s.setOnChangeListener(checked -> {
            setIconHidden(checked);
            s.setChecked(checked);
            Toast.makeText(this, checked ? R.string.set_hide_icon_on : R.string.set_hide_icon_off,
                    Toast.LENGTH_SHORT).show();
        });
        TextView d = new TextView(this);
        d.setText(R.string.set_hide_icon_desc);
        d.setTextAppearance(R.style.TextMuted);
        d.setTextSize(12);
        d.setPadding(dp(20), 0, dp(20), dp(16));
        c.addView(d);
        return c;
    }

    private boolean isIconHidden() {
        try {
            int state = getPackageManager().getComponentEnabledSetting(new ComponentName(this, ALIAS));
            return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Throwable t) {
            return prefs.getBoolean(Keys.HIDE_ICON, false);
        }
    }

    private void setIconHidden(boolean hidden) {
        try {
            getPackageManager().setComponentEnabledSetting(new ComponentName(this, ALIAS),
                    hidden ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            prefs.edit().putBoolean(Keys.HIDE_ICON, hidden).apply();
        } catch (Throwable t) {
            Toast.makeText(this, R.string.shortcut_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private View buildAboutCard() {
        LinearLayout c = card();
        addInfo(c, R.string.about_version, version(), null, true);
        addInfo(c, R.string.about_author, getString(R.string.author_value), null, false);
        addInfo(c, R.string.about_agreement_user, getString(R.string.agree_view),
                v -> viewDoc(R.string.agree_user_title, R.string.agree_user_body), false);
        addInfo(c, R.string.about_agreement_privacy, getString(R.string.agree_view),
                v -> viewDoc(R.string.agree_privacy_title, R.string.agree_privacy_body), false);
        addInfo(c, R.string.about_project, getString(R.string.agree_view), v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.github_url))));
            } catch (Throwable t) {
                Toast.makeText(this, R.string.credits_failed, Toast.LENGTH_SHORT).show();
            }
        }, false);
        addInfo(c, R.string.about_credits, getString(R.string.agree_view),
                v -> showCreditsDialog(), true);
        return c;
    }

    private void showCreditsDialog() {
        final String[][] items = {
                {"libxposed/api 102.0.0", "Apache License 2.0", "https://github.com/libxposed/api"},
                {"Android SDK / Framework", "Apache License 2.0", "https://source.android.com"},
                {"Miuix", "Apache License 2.0", "https://github.com/miuix-kotlin-multiplatform/miuix"},
        };
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                View d = new View(this);
                d.setBackgroundColor(getColor(R.color.card_stroke));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
                lp.leftMargin = dp(20);
                box.addView(d, lp);
            }
            final String url = items[i][2];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(20), dp(12), dp(20), dp(12));
            row.setBackgroundResource(R.drawable.row_bg);

            TextView name = new TextView(this);
            name.setText(items[i][0]);
            name.setTextAppearance(R.style.TextBody);
            name.setTextSize(15);
            row.addView(name);

            TextView lic = new TextView(this);
            lic.setText(items[i][1]);
            lic.setTextAppearance(R.style.TextMuted);
            lic.setTextSize(12);
            lic.setPadding(0, dp(2), 0, 0);
            row.addView(lic);

            row.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Throwable t) {
                    Toast.makeText(this, R.string.credits_failed, Toast.LENGTH_SHORT).show();
                }
            });
            box.addView(row);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.credits_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void viewDoc(int titleRes, int bodyRes) {
        TextView body = new TextView(this);
        body.setText(bodyRes);
        body.setTextAppearance(R.style.TextBody);
        body.setTextSize(14);
        body.setLineSpacing(dp(4), 1f);
        body.setPadding(dp(20), dp(16), dp(20), dp(8));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return Keys.VERSION;
        }
    }

    private void addInfo(LinearLayout parent, int key, String value, View.OnClickListener click,
                         boolean first) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(15), dp(20), dp(15));
        row.setBackgroundResource(R.drawable.row_bg);

        TextView k = new TextView(this);
        k.setText(key);
        k.setTextAppearance(R.style.TextBody);
        k.setTextSize(16);
        k.setSingleLine(true);
        row.addView(k, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextAppearance(R.style.TextMuted);
        v.setTextSize(13);
        v.setGravity(Gravity.END);
        v.setSingleLine(true);
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
        if (!first) {
            View d = new View(this);
            d.setBackgroundColor(getColor(R.color.card_stroke));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
            lp.leftMargin = dp(20);
            parent.addView(d, lp);
        }
    }

    private void refreshStatus() {
        setStatus(moduleEnabled());
        pushConfig();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean on = beatFresh() || moduleEnabled();
            if (on) {
                prefs.edit().putLong(Keys.HOOK_TIME, System.currentTimeMillis()).apply();
            }
            setStatus(on);
        }, 500);
    }

    private boolean moduleEnabled() {
        try {
            if (HookEntry.sActive) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (service != null) {
                for (io.github.libxposed.service.HookedTarget t : service.getRunningTargets()) {
                    String n = t.getProcessName();
                    if (n != null && n.startsWith("com.android.settings")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private String global(String key) {
        try {
            return Settings.Global.getString(getContentResolver(), key);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean everHooked() {
        String v = global(Keys.HOOKED);
        if (v == null) {
            return false;
        }
        int i = v.lastIndexOf(':');
        if (i < 0) {
            return false;
        }
        try {
            return Long.parseLong(v.substring(i + 1)) >= bootTime() - 5000L;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean beatFresh() {
        try {
            String v = global(Keys.BEAT);
            return v != null && System.currentTimeMillis() - Long.parseLong(v) < 5000L;
        } catch (Throwable t) {
            return false;
        }
    }

    private long bootTime() {
        return System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
    }

    private void restartSettings() {
        final Activity self = this;
        new Thread(() -> {
            String out = exec("su -c id");
            final boolean root = out != null && out.contains("uid=0");
            if (!root) {
                runOnUiThread(() -> Toast.makeText(self, R.string.restart_no_root,
                        Toast.LENGTH_LONG).show());
                return;
            }
            exec("su -c am force-stop com.android.settings");
            runOnUiThread(() -> {
                Toast.makeText(self, R.string.restart_done, Toast.LENGTH_SHORT).show();
                refreshStatus();
            });
        }).start();
    }

    private String exec(String cmd) {
        java.io.BufferedReader r = null;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            p.waitFor();
            return sb.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (r != null) {
                    r.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void setStatus(boolean on) {
        statusValue.setText(on ? R.string.status_on : R.string.status_off);
        statusDot.setBackground(dot(on ? getColor(R.color.text_primary) : getColor(R.color.text_secondary)));
        statusHint.setText(on ? R.string.status_hint_on : R.string.status_hint_off);
    }

    private void writeConf() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) {
                sb.append(e.getKey()).append('=').append(v).append('\n');
            }
        }
        FileOutputStream out = null;
        android.os.ParcelFileDescriptor pfd = null;
        try {
            if (service != null) {
                pfd = service.openRemoteFile(Keys.CONF_FILE);
                out = new FileOutputStream(pfd.getFileDescriptor());
            } else {
                out = new FileOutputStream(new java.io.File(getFilesDir(), Keys.CONF_FILE), false);
            }
            out.write(sb.toString().getBytes("UTF-8"));
            out.flush();
            out.close();
            out = null;
            java.io.File ext = getExternalFilesDir(null);
            if (ext != null) {
                java.io.FileOutputStream eo = new java.io.FileOutputStream(
                        new java.io.File(ext, Keys.CONF_FILE), false);
                try {
                    eo.write(sb.toString().getBytes("UTF-8"));
                    eo.flush();
                } finally {
                    eo.close();
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (pfd != null) {
                    pfd.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private GradientDrawable dot(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }
}
