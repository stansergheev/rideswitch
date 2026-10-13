package com.stan.rideswitch;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/** Lets you fix the on-screen words / package names without rebuilding the app. */
public class KeywordsActivity extends Activity {

    private final Map<String, EditText> fields = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        col.setPadding(pad, pad, pad, pad);

        col.addView(text("One phrase per line, not case-sensitive. A phrase matches any text that contains it. "
                + "Start a line with = to require an exact match (for example =go). "
                + "Turn on Screen dump to see the exact words each app shows.", 14, false));

        for (AppProfile p : AppProfile.ALL) {
            TextView h = text(p.label + " Driver", 20, true);
            h.setPadding(0, dp(24), 0, dp(4));
            col.addView(h);

            col.addView(text("Package name", 14, true));
            EditText pkg = field(p.pkg(this), false);
            fields.put(p.pkgKey(), pkg);
            col.addView(pkg);

            for (int i = 0; i < AppProfile.LISTS.length; i++) {
                String list = AppProfile.LISTS[i];
                TextView label = text(AppProfile.LIST_LABELS[i], 14, true);
                label.setPadding(0, dp(12), 0, 0);
                col.addView(label);
                EditText e = field(p.text(this, list), true);
                fields.put(p.listKey(list), e);
                col.addView(e);
            }
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(20), 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        Button save = new Button(this);
        save.setText("Save");
        save.setAllCaps(false);
        save.setOnClickListener(v -> save());
        buttons.addView(save, lp);

        Button reset = new Button(this);
        reset.setText("Reset to defaults");
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> resetDefaults());
        buttons.addView(reset, new LinearLayout.LayoutParams(lp));

        col.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
    }

    private void save() {
        SharedPreferences.Editor ed = Prefs.sp(this).edit();
        for (Map.Entry<String, EditText> e : fields.entrySet()) {
            ed.putString(e.getKey(), e.getValue().getText().toString().trim());
        }
        ed.apply();
        RideSwitchService s = RideSwitchService.instance;
        if (s != null) s.applyPackageFilter();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void resetDefaults() {
        for (AppProfile p : AppProfile.ALL) {
            fields.get(p.pkgKey()).setText(p.defaultPkg);
            for (String list : AppProfile.LISTS) {
                fields.get(p.listKey(list)).setText(p.defaultText(list));
            }
        }
        Toast.makeText(this, "Defaults restored — tap Save to keep them", Toast.LENGTH_SHORT).show();
    }

    private EditText field(String value, boolean multiline) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setMinLines(3);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setSingleLine(true);
        }
        return e;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
