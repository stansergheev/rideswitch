package com.stan.rideswitch;

import android.content.Context;
import android.content.SharedPreferences;

/** Small wrapper around the app's settings. */
public final class Prefs {
    private Prefs() {}

    static final String FILE = "rideswitch";
    static final String ENABLED = "enabled";
    static final String AUTO_ONLINE = "auto_online";
    static final String DEBUG_DUMP = "debug_dump";

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return sp(c).getBoolean(ENABLED, true);
    }

    static boolean autoOnline(Context c) {
        return sp(c).getBoolean(AUTO_ONLINE, true);
    }

    static boolean debugDump(Context c) {
        return sp(c).getBoolean(DEBUG_DUMP, false);
    }

    static void put(Context c, String key, boolean value) {
        sp(c).edit().putBoolean(key, value).apply();
    }
}
