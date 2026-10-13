package com.stan.rideswitch;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        col.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("RideSwitch");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        TextView blurb = text("Accept a ride on Uber or Lyft and RideSwitch takes the other app offline for you.", 14);
        col.addView(blurb);

        statusView = text("", 15);
        statusView.setPadding(0, dp(12), 0, dp(4));
        col.addView(statusView);

        col.addView(button("Open Accessibility settings",
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        col.addView(button("Open RideSwitch app info (for \"Allow restricted settings\")", v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }));

        col.addView(header("Settings"));
        col.addView(toggle("RideSwitch on", Prefs.ENABLED, true));
        col.addView(toggle("Bring the other app back online after the trip", Prefs.AUTO_ONLINE, true));
        col.addView(toggle("Screen dump (logs on-screen text, for fixing keywords)", Prefs.DEBUG_DUMP, false));

        col.addView(header("Test it (while parked)"));
        col.addView(row(
                button("Uber offline", v -> test(AppProfile.UBER, true)),
                button("Lyft offline", v -> test(AppProfile.LYFT, true))));
        col.addView(row(
                button("Uber online", v -> test(AppProfile.UBER, false)),
                button("Lyft online", v -> test(AppProfile.LYFT, false))));
        col.addView(row(
                button("Reset trip state", v -> {
                    RideSwitchService s = RideSwitchService.instance;
                    if (s != null) s.resetState();
                    refresh();
                }),
                button("Edit keywords", v -> startActivity(new Intent(this, KeywordsActivity.class)))));

        col.addView(header("Log"));
        col.addView(row(
                button("Copy log", v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("RideSwitch log", LogBuffer.text()));
                    Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
                }),
                button("Clear log", v -> LogBuffer.clear())));

        logView = text("", 12);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(8), 0, 0);
        col.addView(logView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogBuffer.setListener(() -> handler.post(this::refresh));
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogBuffer.setListener(null);
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        if (isServiceEnabled()) {
            sb.append("✅ Accessibility permission is on\n");
        } else {
            sb.append("⚠️ Turn on RideSwitch in Accessibility settings.\n")
              .append("If it's greyed out: App info → ⋮ menu → Allow restricted settings, then try again.\n");
        }
        for (AppProfile p : AppProfile.ALL) {
            boolean installed = getPackageManager().getLaunchIntentForPackage(p.pkg(this)) != null;
            sb.append(installed ? "✅ " : "❌ ").append(p.label).append(" Driver ")
              .append(installed ? "found" : "not found (" + p.pkg(this) + ")").append('\n');
        }
        RideSwitchService s = RideSwitchService.instance;
        if (s != null) sb.append('\n').append(s.describeState());
        statusView.setText(sb.toString().trim());
        logView.setText(LogBuffer.text());
    }

    private void test(AppProfile p, boolean offline) {
        RideSwitchService s = RideSwitchService.instance;
        if (s == null) {
            Toast.makeText(this, "Turn on RideSwitch in Accessibility settings first", Toast.LENGTH_LONG).show();
            return;
        }
        s.runTest(p, offline);
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName cn = new ComponentName(this, RideSwitchService.class);
        return enabled.contains(cn.flattenToString()) || enabled.contains(cn.flattenToShortString());
    }

    // ---------------------------------------------------------------- tiny UI helpers

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(20), 0, dp(6));
        return t;
    }

    private Button button(String label, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(onClick);
        return b;
    }

    private LinearLayout row(View a, View b) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        r.addView(a, lp);
        r.addView(b, new LinearLayout.LayoutParams(lp));
        return r;
    }

    private Switch toggle(String label, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setChecked(Prefs.sp(this).getBoolean(key, def));
        sw.setOnCheckedChangeListener((v, checked) -> Prefs.put(this, key, checked));
        return sw;
    }
}
