package com.stan.rideswitch;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything RideSwitch needs to know about one driver app: its package name and the
 * on-screen words that tell us what state it is in.
 *
 * Keyword rules (case-insensitive):
 *   go offline   -> matches any text that CONTAINS "go offline"
 *   =go          -> matches text that is EXACTLY "go"
 * Each list has English and Russian words, so it works whichever language Uber/Lyft is set to.
 * Every list can be edited in the app (Edit keywords), so it can be fixed after an
 * Uber/Lyft update without a new build.
 */
public final class AppProfile {

    public static final String ACTIVE = "active";
    public static final String IDLE = "idle";
    public static final String GO_OFFLINE = "go_offline";
    public static final String IS_OFFLINE = "is_offline";
    public static final String GO_ONLINE = "go_online";
    public static final String IS_ONLINE = "is_online";

    public static final String[] LISTS = {ACTIVE, IDLE, GO_OFFLINE, IS_OFFLINE, GO_ONLINE, IS_ONLINE};

    private static final String[] LABELS_EN = {
            "Trip in progress (shows after you accept a ride)",
            "Trip finished / waiting for requests",
            "Buttons to tap to go offline",
            "Text that shows when offline",
            "Buttons to tap to go online",
            "Text that shows when online",
    };

    private static final String[] LABELS_RU = {
            "Поездка идёт (появляется после принятия заказа)",
            "Поездка завершена / ожидание заказов",
            "Кнопки, чтобы уйти в офлайн",
            "Текст, когда вы не в сети",
            "Кнопки, чтобы выйти в сеть",
            "Текст, когда вы в сети",
    };

    public static String listLabel(Context c, int i) {
        return L.ru(c) ? LABELS_RU[i] : LABELS_EN[i];
    }

    public static final AppProfile UBER = new AppProfile("uber", "Uber", "com.ubercab.driver",
            new String[][]{
                    // ACTIVE
                    {"picking up", "swipe to start", "start trip", "start uber", "swipe to complete",
                            "complete trip", "complete uber", "dropping off", "i've arrived", "arrived at pickup",
                            "waiting for rider",
                            "начать поездку", "завершить поездку", "я на месте", "проведите, чтобы начать",
                            "проведите, чтобы завершить", "ожидание пассажира", "забрать пассажира",
                            "высадить пассажира"},
                    // IDLE
                    {"you're online", "finding trips", "finding requests", "rate your rider", "rate rider",
                            "how was your trip",
                            "вы в сети", "поиск поездок", "поиск заказов", "оцените пассажира",
                            "как прошла поездка"},
                    // GO_OFFLINE
                    {"go offline", "stop new requests",
                            "выйти из сети", "перейти в офлайн", "уйти в офлайн", "не принимать заказы"},
                    // IS_OFFLINE
                    {"you're offline", "go online", "=go",
                            "вы не в сети", "вы офлайн", "выйти в сеть"},
                    // GO_ONLINE
                    {"=go", "go online",
                            "выйти в сеть", "=в сеть"},
                    // IS_ONLINE
                    {"you're online", "finding trips", "finding requests",
                            "вы в сети", "поиск поездок", "поиск заказов"},
            });

    public static final AppProfile LYFT = new AppProfile("lyft", "Lyft", "com.lyft.android.driver",
            new String[][]{
                    // ACTIVE
                    {"arrived", "confirm pickup", "swipe to confirm", "picking up", "dropping off", "drop off",
                            "complete ride", "end ride", "waiting for passenger", "waiting for rider",
                            "прибыл", "подтвердить посадку", "забрать пассажира", "высадить пассажира",
                            "завершить поездку", "ожидание пассажира"},
                    // IDLE
                    {"you're online", "looking for rides", "finding rides", "rate your passenger", "rate your rider",
                            "rate rider",
                            "вы в сети", "поиск поездок", "оцените пассажира"},
                    // GO_OFFLINE
                    {"go offline",
                            "выйти из сети", "перейти в офлайн", "уйти в офлайн"},
                    // IS_OFFLINE
                    {"you're offline", "go online",
                            "вы не в сети", "вы офлайн", "выйти в сеть"},
                    // GO_ONLINE
                    {"go online", "=go",
                            "выйти в сеть", "=в сеть"},
                    // IS_ONLINE
                    {"you're online", "looking for rides", "finding rides",
                            "вы в сети", "поиск поездок"},
            });

    public static final AppProfile[] ALL = {UBER, LYFT};

    public final String id;
    public final String label;
    public final String defaultPkg;
    private final String[][] defaults;

    private AppProfile(String id, String label, String defaultPkg, String[][] defaults) {
        this.id = id;
        this.label = label;
        this.defaultPkg = defaultPkg;
        this.defaults = defaults;
    }

    public AppProfile other() {
        return this == UBER ? LYFT : UBER;
    }

    public String pkgKey() {
        return id + "_pkg";
    }

    public String listKey(String list) {
        return id + "_" + list;
    }

    public String pkg(Context c) {
        String p = Prefs.sp(c).getString(pkgKey(), defaultPkg);
        p = p == null ? "" : p.trim();
        return p.isEmpty() ? defaultPkg : p;
    }

    public String defaultText(String list) {
        return String.join("\n", defaults[indexOf(list)]);
    }

    public String text(Context c, String list) {
        return Prefs.sp(c).getString(listKey(list), defaultText(list));
    }

    public List<String> keywords(Context c, String list) {
        List<String> out = new ArrayList<>();
        for (String line : text(c, list).split("\n")) {
            boolean exact = line.trim().startsWith("=");
            String k = Nodes.norm(exact ? line.trim().substring(1) : line);
            if (!k.isEmpty()) out.add(exact ? "=" + k : k);
        }
        return out;
    }

    public static AppProfile forPackage(Context c, String pkg) {
        if (pkg == null) return null;
        for (AppProfile p : ALL) {
            if (p.pkg(c).equals(pkg)) return p;
        }
        return null;
    }

    private static int indexOf(String list) {
        for (int i = 0; i < LISTS.length; i++) {
            if (LISTS[i].equals(list)) return i;
        }
        throw new IllegalArgumentException(list);
    }
}
