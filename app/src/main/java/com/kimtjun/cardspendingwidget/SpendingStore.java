package com.kimtjun.cardspendingwidget;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class SpendingStore {
    private static final String PREF = "spending";
    private static final String KEY_GOAL = "goal";
    private static final String KEY_BASE = "base";
    private static final String KEY_APPROVED = "approved";
    private static final String KEY_LAST_FINGERPRINT = "last_fp";

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static long goal(Context c) { return prefs(c).getLong(KEY_GOAL, 1000000L); }
    public static void setGoal(Context c, long v) { prefs(c).edit().putLong(KEY_GOAL, Math.max(0,v)).apply(); }
    public static long base(Context c) { return prefs(c).getLong(KEY_BASE, 0L); }
    public static void setBase(Context c, long v) { prefs(c).edit().putLong(KEY_BASE, Math.max(0,v)).apply(); }
    public static long approved(Context c) { return prefs(c).getLong(KEY_APPROVED, 0L); }
    public static long total(Context c) { return base(c) + approved(c); }

    public static boolean isDuplicate(Context c, String fp) {
        String last = prefs(c).getString(KEY_LAST_FINGERPRINT, "");
        if (fp != null && fp.equals(last)) return true;
        prefs(c).edit().putString(KEY_LAST_FINGERPRINT, fp == null ? "" : fp).apply();
        return false;
    }

    public static void applyAmount(Context c, long amount) {
        long next = Math.max(0L, approved(c) + amount);
        prefs(c).edit().putLong(KEY_APPROVED, next).apply();
    }

    public static LocalDate periodStart(LocalDate today) {
        if (today.getDayOfMonth() >= 11) return today.withDayOfMonth(11);
        return today.minusMonths(1).withDayOfMonth(11);
    }

    public static LocalDate periodEnd(LocalDate today) {
        LocalDate start = periodStart(today);
        return start.plusMonths(1).minusDays(1);
    }

    public static long weeklyRecommended(Context c) {
        long remain = Math.max(0L, goal(c) - total(c));
        LocalDate today = LocalDate.now();
        LocalDate end = periodEnd(today);
        long days = Math.max(1L, ChronoUnit.DAYS.between(today, end) + 1L);
        double weeks = Math.max(1.0, days / 7.0);
        return Math.round(remain / weeks);
    }

    public static int usagePercent(Context c) {
        long g = goal(c);
        if (g <= 0) return 0;
        return (int)Math.min(999, Math.round(total(c) * 100.0 / g));
    }
}
