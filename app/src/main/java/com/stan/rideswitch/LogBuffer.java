package com.stan.rideswitch;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/** In-memory activity log shown on the main screen (newest first). */
public final class LogBuffer {
    private LogBuffer() {}

    private static final int MAX = 400;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static volatile Runnable listener;

    public static void add(String msg) {
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        synchronized (LINES) {
            LINES.addLast(stamp + "  " + msg);
            while (LINES.size() > MAX) LINES.removeFirst();
        }
        Runnable l = listener;
        if (l != null) l.run();
    }

    public static String text() {
        StringBuilder sb = new StringBuilder();
        synchronized (LINES) {
            Iterator<String> it = LINES.descendingIterator();
            while (it.hasNext()) sb.append(it.next()).append('\n');
        }
        return sb.length() == 0 ? "(nothing yet)" : sb.toString();
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
        Runnable l = listener;
        if (l != null) l.run();
    }

    public static void setListener(Runnable r) {
        listener = r;
    }
}
