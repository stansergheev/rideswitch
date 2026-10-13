package com.stan.rideswitch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Watches Uber Driver and Lyft Driver.
 *
 *  - When a trip becomes active in app A (you accepted a ride), it opens app B,
 *    taps "Go offline", and switches back to A.
 *  - When the trip in A ends, and "auto online" is on, it brings B back online —
 *    but only if RideSwitch was the one that took B offline.
 *
 * It never taps cancel/decline, and it backs off if B has its own trip in progress.
 */
public class RideSwitchService extends AccessibilityService {

    static volatile RideSwitchService instance;

    private static final long EVAL_THROTTLE_MS = 500;
    private static final long JOB_TIMEOUT_MS = 15000;
    private static final long POLL_MS = 600;
    private static final long MIN_TAP_GAP_MS = 1800;
    private static final int MAX_TAPS = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> tripActive = new HashMap<>();
    private final Set<String> wentOfflineByUs = new HashSet<>();
    private final Map<String, Long> lastEval = new HashMap<>();
    private final Set<String> evalPending = new HashSet<>();
    private final Map<String, Integer> lastDumpHash = new HashMap<>();

    private Job job;

    private static final class Job {
        final AppProfile target;
        final boolean goOffline;
        final String returnPkg;
        long started;
        long lastTap;
        int taps;
        boolean relaunched;

        Job(AppProfile target, boolean goOffline, String returnPkg) {
            this.target = target;
            this.goOffline = goOffline;
            this.returnPkg = returnPkg;
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        applyPackageFilter();
        LogBuffer.add(L.t(this, "Service connected — watching ", "Служба включена — слежу за ") + AppProfile.UBER.pkg(this) + L.t(this, " and ", " и ") + AppProfile.LYFT.pkg(this));
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        LogBuffer.add(L.t(this, "Service stopped", "Служба остановлена"));
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
    }

    /** Only receive events from the two driver apps (package names can be edited in settings). */
    void applyPackageFilter() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.packageNames = new String[]{AppProfile.UBER.pkg(this), AppProfile.LYFT.pkg(this)};
        setServiceInfo(info);
    }

    // ---------------------------------------------------------------- watching

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (job != null || !Prefs.enabled(this)) return;
        AppProfile p = AppProfile.forPackage(this, event.getPackageName().toString());
        if (p == null) return;

        // Throttle: the map in these apps fires events constantly.
        long now = SystemClock.uptimeMillis();
        Long last = lastEval.get(p.id);
        long wait = last == null ? 0 : EVAL_THROTTLE_MS - (now - last);
        if (wait <= 0) {
            evaluate(p);
        } else if (evalPending.add(p.id)) {
            handler.postDelayed(() -> {
                evalPending.remove(p.id);
                evaluate(p);
            }, wait);
        }
    }

    private void evaluate(AppProfile p) {
        lastEval.put(p.id, SystemClock.uptimeMillis());
        if (job != null || !Prefs.enabled(this)) return;

        AccessibilityNodeInfo root = findRoot(p.pkg(this));
        if (root == null) return;
        List<String> texts = Nodes.collectTexts(root);
        maybeDump(p, texts);

        boolean active = Nodes.anyMatch(texts, p.keywords(this, AppProfile.ACTIVE));
        boolean idle = Nodes.anyMatch(texts, p.keywords(this, AppProfile.IDLE));
        boolean wasActive = Boolean.TRUE.equals(tripActive.get(p.id));
        AppProfile other = p.other();

        if (active && !wasActive) {
            tripActive.put(p.id, true);
            LogBuffer.add(L.t(this, "Trip accepted on ", "Принят заказ в ") + p.label);
            if (Boolean.TRUE.equals(tripActive.get(other.id))) {
                LogBuffer.add(other.label + L.t(this, " also has a trip — leaving it alone", ": там тоже идёт поездка — не трогаю"));
                return;
            }
            startJob(new Job(other, true, p.pkg(this)));
        } else if (wasActive && idle && !active) {
            tripActive.put(p.id, false);
            LogBuffer.add(L.t(this, "Trip finished on ", "Поездка завершена в ") + p.label);
            if (Prefs.autoOnline(this) && wentOfflineByUs.contains(other.id)) {
                startJob(new Job(other, false, p.pkg(this)));
            }
        }
    }

    // ---------------------------------------------------------------- acting on the other app

    private void startJob(Job j) {
        job = j;
        j.started = SystemClock.uptimeMillis();
        String what = j.goOffline
                ? L.t(this, "Taking " + j.target.label + " offline", "Перевожу " + j.target.label + " в офлайн")
                : L.t(this, "Bringing " + j.target.label + " back online", "Возвращаю " + j.target.label + " в сеть");
        LogBuffer.add(what + "…");
        toast(what);
        if (!launch(j.target.pkg(this))) {
            finishJob(j.target.label + L.t(this, " Driver isn't installed (or the package name is wrong)", " Driver не установлен (или неверное имя пакета)"));
            return;
        }
        handler.postDelayed(this::stepJob, 1200);
    }

    private void stepJob() {
        Job j = job;
        if (j == null) return;
        long now = SystemClock.uptimeMillis();
        AppProfile t = j.target;

        if (now - j.started > JOB_TIMEOUT_MS) {
            finishJob(L.t(this, "Couldn't finish on " + t.label + " — turn on Screen dump and check the log for the button text",
                    "Не получилось в " + t.label + " — включите «Запись экрана» и найдите текст кнопки в журнале"));
            return;
        }

        AccessibilityNodeInfo root = findRoot(t.pkg(this));
        if (root == null) {
            if (!j.relaunched && now - j.started > 5000) {
                j.relaunched = true;
                launch(t.pkg(this));
            }
            handler.postDelayed(this::stepJob, POLL_MS);
            return;
        }

        List<String> texts = Nodes.collectTexts(root);
        maybeDump(t, texts);

        if (j.goOffline && Nodes.anyMatch(texts, t.keywords(this, AppProfile.ACTIVE))) {
            tripActive.put(t.id, true);
            finishJob(t.label + L.t(this, " has a trip in progress — left it alone", ": идёт поездка — не трогаю"));
            return;
        }

        String doneList = j.goOffline ? AppProfile.IS_OFFLINE : AppProfile.IS_ONLINE;
        if (Nodes.anyMatch(texts, t.keywords(this, doneList))) {
            if (j.goOffline) {
                if (j.taps > 0) wentOfflineByUs.add(t.id);
                finishJob(t.label + (j.taps > 0 ? L.t(this, " is now offline", " теперь не в сети") : L.t(this, " was already offline", " уже был не в сети")));
            } else {
                wentOfflineByUs.remove(t.id);
                finishJob(t.label + L.t(this, " is back online", " снова в сети"));
            }
            return;
        }

        if (j.taps < MAX_TAPS && now - j.lastTap > MIN_TAP_GAP_MS) {
            String buttonList = j.goOffline ? AppProfile.GO_OFFLINE : AppProfile.GO_ONLINE;
            AccessibilityNodeInfo button = Nodes.findMatch(root, t.keywords(this, buttonList));
            if (button != null) {
                boolean ok = tap(button);
                j.taps++;
                j.lastTap = now;
                LogBuffer.add(L.t(this, "Tapped \"", "Нажал «") + Nodes.label(button) + L.t(this, "\" in ", "» в ") + t.label
                        + (ok ? "" : L.t(this, " (tap failed)", " (не сработало)")));
            }
        }
        handler.postDelayed(this::stepJob, POLL_MS);
    }

    private void finishJob(String message) {
        Job j = job;
        job = null;
        LogBuffer.add(message);
        toast(message);
        if (j != null) {
            lastEval.put(j.target.id, SystemClock.uptimeMillis());
            if (j.returnPkg != null) {
                handler.postDelayed(() -> launch(j.returnPkg), 400);
            }
        }
    }

    // ---------------------------------------------------------------- called from the main screen

    void runTest(AppProfile target, boolean goOffline) {
        if (job != null) {
            toast(L.t(this, "Busy — try again in a few seconds", "Занят — попробуйте через пару секунд"));
            return;
        }
        startJob(new Job(target, goOffline, getPackageName()));
    }

    void resetState() {
        tripActive.clear();
        wentOfflineByUs.clear();
        LogBuffer.add(L.t(this, "Trip state reset", "Состояние поездок сброшено"));
    }

    String describeState() {
        StringBuilder sb = new StringBuilder();
        for (AppProfile p : AppProfile.ALL) {
            sb.append(p.label).append(": ")
                    .append(Boolean.TRUE.equals(tripActive.get(p.id)) ? L.t(this, "on a trip", "в поездке") : L.t(this, "no trip", "нет поездки"));
            if (wentOfflineByUs.contains(p.id)) sb.append(L.t(this, " (taken offline by RideSwitch)", " (отключён RideSwitch)"));
            sb.append('\n');
        }
        if (job != null) sb.append(L.t(this, "Working on ", "Работаю с ")).append(job.target.label).append("…\n");
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- helpers

    /** The largest on-screen window belonging to the package (skips Uber's small floating bubble). */
    private AccessibilityNodeInfo findRoot(String pkg) {
        AccessibilityNodeInfo best = null;
        long bestArea = -1;
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                Rect r = new Rect();
                for (AccessibilityWindowInfo w : windows) {
                    AccessibilityNodeInfo root = w.getRoot();
                    if (root == null || root.getPackageName() == null) continue;
                    if (!pkg.contentEquals(root.getPackageName())) continue;
                    w.getBoundsInScreen(r);
                    long area = (long) r.width() * r.height();
                    if (area > bestArea) {
                        bestArea = area;
                        best = root;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // window list can change mid-read
        }
        if (best != null) return best;
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && active.getPackageName() != null && pkg.contentEquals(active.getPackageName())) {
            return active;
        }
        return null;
    }

    private boolean launch(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            LogBuffer.add(L.t(this, "Couldn't open ", "Не удалось открыть ") + pkg + ": " + e.getMessage());
            return false;
        }
    }

    /** Click the node (or its nearest clickable parent); fall back to a real tap on its center. */
    private boolean tap(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        for (int i = 0; i < 6 && n != null; i++) {
            if (n.isClickable() && n.isEnabled()) {
                if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                break;
            }
            n = n.getParent();
        }
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.isEmpty()) return false;
        Path path = new Path();
        path.moveTo(r.exactCenterX(), r.exactCenterY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    private void maybeDump(AppProfile p, List<String> texts) {
        if (!Prefs.debugDump(this)) return;
        int hash = texts.hashCode();
        Integer last = lastDumpHash.get(p.id);
        if (last != null && last == hash) return;
        lastDumpHash.put(p.id, hash);
        String joined = String.join(" | ", texts);
        if (joined.length() > 1500) joined = joined.substring(0, 1500) + " …";
        LogBuffer.add("[" + p.label + L.t(this, " screen] ", " экран] ") + joined);
    }

    private void toast(String msg) {
        handler.post(() -> Toast.makeText(getApplicationContext(), "RideSwitch: " + msg, Toast.LENGTH_SHORT).show());
    }
}
