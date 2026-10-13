package com.stan.rideswitch;

import android.content.Context;

import java.util.Locale;

/** Tiny translation helper: L.t(ctx, "English", "Русский"). */
public final class L {
    private L() {}

    static final String KEY = "lang";       // "auto", "en", "ru"

    static String mode(Context c) {
        return Prefs.sp(c).getString(KEY, "auto");
    }

    static void setMode(Context c, String mode) {
        Prefs.sp(c).edit().putString(KEY, mode).apply();
    }

    static boolean ru(Context c) {
        String m = mode(c);
        if ("ru".equals(m)) return true;
        if ("en".equals(m)) return false;
        return "ru".equals(Locale.getDefault().getLanguage());
    }

    static String t(Context c, String en, String ru) {
        return ru(c) ? ru : en;
    }
}
