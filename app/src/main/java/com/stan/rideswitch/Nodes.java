package com.stan.rideswitch;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Helpers for reading the on-screen view tree of another app. */
public final class Nodes {
    private Nodes() {}

    private static final int MAX_NODES = 4000;

    /** Lowercase, straight apostrophes, ё→е, single spaces. */
    public static String norm(CharSequence s) {
        if (s == null) return "";
        return s.toString()
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace(' ', ' ')
                .toLowerCase(Locale.US)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static boolean matches(String normText, String keyword) {
        if (normText.isEmpty()) return false;
        if (keyword.startsWith("=")) return normText.equals(keyword.substring(1));
        return normText.contains(keyword);
    }

    public static boolean anyMatch(List<String> texts, List<String> keywords) {
        for (String t : texts) {
            for (String k : keywords) {
                if (matches(t, k)) return true;
            }
        }
        return false;
    }

    /** Every visible text and content description on the screen, normalized. */
    public static List<String> collectTexts(AccessibilityNodeInfo root) {
        List<String> out = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> stack = new ArrayDeque<>();
        stack.push(root);
        int seen = 0;
        while (!stack.isEmpty() && seen < MAX_NODES) {
            AccessibilityNodeInfo n = stack.pop();
            seen++;
            if (n.isVisibleToUser()) {
                String t = norm(n.getText());
                if (!t.isEmpty()) out.add(t);
                String d = norm(n.getContentDescription());
                if (!d.isEmpty() && !d.equals(t)) out.add(d);
            }
            for (int i = n.getChildCount() - 1; i >= 0; i--) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) stack.push(child);
            }
        }
        return out;
    }

    /** First visible node whose text or description matches one of the keywords. */
    public static AccessibilityNodeInfo findMatch(AccessibilityNodeInfo root, List<String> keywords) {
        ArrayDeque<AccessibilityNodeInfo> stack = new ArrayDeque<>();
        stack.push(root);
        int seen = 0;
        while (!stack.isEmpty() && seen < MAX_NODES) {
            AccessibilityNodeInfo n = stack.pop();
            seen++;
            if (n.isVisibleToUser()) {
                String t = norm(n.getText());
                String d = norm(n.getContentDescription());
                for (String k : keywords) {
                    if (matches(t, k) || matches(d, k)) return n;
                }
            }
            for (int i = n.getChildCount() - 1; i >= 0; i--) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) stack.push(child);
            }
        }
        return null;
    }

    public static String label(AccessibilityNodeInfo n) {
        String t = norm(n.getText());
        return t.isEmpty() ? norm(n.getContentDescription()) : t;
    }
}
