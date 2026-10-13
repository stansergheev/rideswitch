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
        build();
    }

    /** Builds the whole screen in the current language. */
    private void build() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        col.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("RideSwitch");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);

        col.addView(text(t("Accept a ride on Uber or Lyft and RideSwitch takes the other app offline for you.",
                "Примите заказ в Uber или Lyft — RideSwitch сам уведёт другое приложение в офлайн."), 14));

        col.addView(button(languageLabel(), v -> {
            String m = L.mode(this);
            L.setMode(this, "auto".equals(m) ? "en" : "en".equals(m) ? "ru" : "auto");
            build();
        }));

        statusView = text("", 15);
        statusView.setPadding(0, dp(12), 0, dp(4));
        col.addView(statusView);

        col.addView(button(t("Open Accessibility settings", "Открыть настройки спец. возможностей"),
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        col.addView(button(t("Open RideSwitch app info (for \"Allow restricted settings\")",
                "Сведения о приложении (для «Разрешить ограниченные настройки»)"), v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }));

        col.addView(header(t("Settings", "Настройки")));
        col.addView(toggle(t("RideSwitch on", "RideSwitch включён"), Prefs.ENABLED, true));
        col.addView(toggle(t("Bring the other app back online after the trip",
                "Возвращать другое приложение в сеть после поездки"), Prefs.AUTO_ONLINE, true));
        col.addView(toggle(t("Screen dump (logs on-screen text, for fixing keywords)",
                "Запись экрана (пишет текст с экрана в журнал, для настройки слов)"), Prefs.DEBUG_DUMP, false));

        col.addView(header(t("Test it (while parked)", "Проверка (на стоянке)")));
        col.addView(row(
                button(t("Uber offline", "Uber офлайн"), v -> test(AppProfile.UBER, true)),
                button(t("Lyft offline", "Lyft офлайн"), v -> test(AppProfile.LYFT, true))));
        col.addView(row(
                button(t("Uber online", "Uber в сеть"), v -> test(AppProfile.UBER, false)),
                button(t("Lyft online", "Lyft в сеть"), v -> test(AppProfile.LYFT, false))));
        col.addView(row(
                button(t("Reset trip state", "Сбросить состояние"), v -> {
                    RideSwitchService s = RideSwitchService.instance;
                    if (s != null) s.resetState();
                    refresh();
                }),
                button(t("Edit keywords", "Ключевые слова"),
                        v -> startActivity(new Intent(this, KeywordsActivity.class)))));

        col.addView(header(t("Log", "Журнал")));
        col.addView(row(
                button(t("Copy log", "Скопировать"), v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("RideSwitch log", LogBuffer.text()));
                    Toast.makeText(this, t("Log copied", "Журнал скопирован"), Toast.LENGTH_SHORT).show();
                }),
                button(t("Clear log", "Очистить"), v -> LogBuffer.clear())));

        logView = text("", 12);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(8), 0, 0);
        col.addView(logView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
        setTitle("RideSwitch");
        refresh();
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
        if (statusView == null) return;
        StringBuilder sb = new StringBuilder();
        if (isServiceEnabled()) {
            sb.append(t("✅ Accessibility permission is on\n", "✅ Доступ к спец. возможностям включён\n"));
        } else {
            sb.append(t("⚠️ Turn on RideSwitch in Accessibility settings.\n",
                    "⚠️ Включите RideSwitch в настройках спец. возможностей.\n"))
              .append(t("If it's greyed out: App info → ⋮ menu → Allow restricted settings, then try again.\n",
                    "Если переключатель серый: Сведения о приложении → меню ⋮ → Разрешить ограниченные настройки, затем снова.\n"));
        }
        for (AppProfile p : AppProfile.ALL) {
            boolean installed = getPackageManager().getLaunchIntentForPackage(p.pkg(this)) != null;
            sb.append(installed ? "✅ " : "❌ ").append(p.label).append(" Driver ")
              .append(installed ? t("found", "найден") : t("not found", "не найден") + " (" + p.pkg(this) + ")")
              .append('\n');
        }
        RideSwitchService s = RideSwitchService.instance;
        if (s != null) sb.append('\n').append(s.describeState());
        statusView.setText(sb.toString().trim());
        String log = LogBuffer.text();
        logView.setText(log.isEmpty() ? t("(nothing yet)", "(пока пусто)") : log);
    }

    private String languageLabel() {
        String m = L.mode(this);
        String current = "en".equals(m) ? "English" : "ru".equals(m) ? "Русский"
                : t("Auto (phone language)", "Авто (язык телефона)");
        return "🌐 " + t("Language: ", "Язык: ") + current;
    }

    private void test(AppProfile p, boolean offline) {
        RideSwitchService s = RideSwitchService.instance;
        if (s == null) {
            Toast.makeText(this, t("Turn on RideSwitch in Accessibility settings first",
                    "Сначала включите RideSwitch в настройках спец. возможностей"), Toast.LENGTH_LONG).show();
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

    private String t(String en, String ru) {
        return L.t(this, en, ru);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(String s, int sp) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return tv;
    }

    private TextView header(String s) {
        TextView tv = text(s, 18);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(20), 0, dp(6));
        return tv;
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
