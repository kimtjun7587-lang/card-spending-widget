package com.kimtjun.cardspendingwidget;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

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
        long g = goal(c);
        if (g <= 0) return 0L;

        LocalDate today = LocalDate.now();
        LocalDate start = periodStart(today);
        LocalDate end = periodEnd(today);
        long periodDays = ChronoUnit.DAYS.between(start, end) + 1L;

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        if (weekStart.isBefore(start)) weekStart = start;
        if (weekEnd.isAfter(end)) weekEnd = end;

        long weekDays = Math.max(1L, ChronoUnit.DAYS.between(weekStart, weekEnd) + 1L);
        long weeklyPlan = Math.round(g * (weekDays / (double) periodDays));
        long remain = Math.max(0L, g - total(c));
        return Math.min(weeklyPlan, remain);
    }

    public static int usagePercent(Context c) {
        long g = goal(c);
        if (g <= 0) return 0;
        return (int)Math.min(999, Math.round(total(c) * 100.0 / g));
    }
}
