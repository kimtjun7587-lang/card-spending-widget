package com.kimtjun.cardspendingwidget;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpendingStore {
    private static final String PREF = "spending";
    private static final String KEY_GOAL = "goal";
    private static final String KEY_BASE = "base";
    private static final String KEY_APPROVED = "approved";
    private static final String KEY_CYCLE = "cycle_key";
    private static final String KEY_WEEK = "week_key";
    private static final String KEY_WEEK_SPENT = "week_spent";
    private static final String KEY_LAST_EVENT = "last_event";
    private static final String KEY_LAST_EVENT_AT = "last_event_at";
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{2}/\\d{2})\\s+(\\d{2}:\\d{2})");
    private static final Pattern MERCHANT = Pattern.compile("(?m)^([^\\n]+)\\n\\s*[0-9,]+원\\s*(?:승인취소|취소|승인)");

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String cycleKey(LocalDate today) {
        return periodStart(today).toString() + "~" + periodEnd(today).toString();
    }

    private static String weekKey(LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.toString();
    }

    public static void ensureCurrentPeriod(Context c) {
        SharedPreferences p = prefs(c);
        LocalDate today = LocalDate.now();
        String currentCycle = cycleKey(today);
        String savedCycle = p.getString(KEY_CYCLE, "");

        if (savedCycle.isEmpty()) {
            // Upgrade migration: keep the amount already entered in the test version.
            p.edit().putString(KEY_CYCLE, currentCycle).apply();
        } else if (!currentCycle.equals(savedCycle)) {
            p.edit()
                    .putString(KEY_CYCLE, currentCycle)
                    .putLong(KEY_BASE, 0L)
                    .putLong(KEY_APPROVED, 0L)
                    .putLong(KEY_WEEK_SPENT, 0L)
                    .putString(KEY_WEEK, weekKey(today))
                    .remove(KEY_LAST_EVENT)
                    .remove(KEY_LAST_EVENT_AT)
                    .apply();
        }

        String currentWeek = weekKey(today);
        String savedWeek = p.getString(KEY_WEEK, "");
        if (!currentWeek.equals(savedWeek)) {
            p.edit().putString(KEY_WEEK, currentWeek).putLong(KEY_WEEK_SPENT, 0L).apply();
        }
    }

    public static long goal(Context c) {
        ensureCurrentPeriod(c);
        return prefs(c).getLong(KEY_GOAL, 1000000L);
    }

    public static void setGoal(Context c, long v) {
        ensureCurrentPeriod(c);
        prefs(c).edit().putLong(KEY_GOAL, Math.max(0, v)).apply();
    }

    public static long base(Context c) {
        ensureCurrentPeriod(c);
        return prefs(c).getLong(KEY_BASE, 0L);
    }

    public static void setBase(Context c, long v) {
        ensureCurrentPeriod(c);
        prefs(c).edit().putLong(KEY_BASE, Math.max(0, v)).apply();
    }

    public static long approved(Context c) {
        ensureCurrentPeriod(c);
        return prefs(c).getLong(KEY_APPROVED, 0L);
    }

    public static long total(Context c) {
        return base(c) + approved(c);
    }

    public static long weekSpent(Context c) {
        ensureCurrentPeriod(c);
        return prefs(c).getLong(KEY_WEEK_SPENT, 0L);
    }

    public static boolean isDuplicateEvent(Context c, long amount, String kind, String body) {
        String dateTime = "";
        Matcher dt = DATE_TIME.matcher(body == null ? "" : body);
        if (dt.find()) dateTime = dt.group(1) + " " + dt.group(2);

        String merchant = "";
        Matcher mm = MERCHANT.matcher(body == null ? "" : body);
        if (mm.find()) merchant = mm.group(1).trim();

        String core;
        if (!dateTime.isEmpty()) {
            core = amount + "|" + kind + "|" + dateTime + "|" + merchant;
        } else {
            String normalized = body == null ? "" : body.replaceAll("\\s+", " ").trim();
            core = amount + "|" + kind + "|" + normalized.hashCode();
        }

        SharedPreferences p = prefs(c);
        String last = p.getString(KEY_LAST_EVENT, "");
        long lastAt = p.getLong(KEY_LAST_EVENT_AT, 0L);
        long now = System.currentTimeMillis();
        if (core.equals(last) && now - lastAt < 120000L) return true;

        p.edit().putString(KEY_LAST_EVENT, core).putLong(KEY_LAST_EVENT_AT, now).apply();
        return false;
    }

    public static void applyAmount(Context c, long amount) {
        ensureCurrentPeriod(c);
        SharedPreferences p = prefs(c);
        long nextApproved = Math.max(0L, p.getLong(KEY_APPROVED, 0L) + amount);
        long nextWeek = Math.max(0L, p.getLong(KEY_WEEK_SPENT, 0L) + amount);
        p.edit().putLong(KEY_APPROVED, nextApproved).putLong(KEY_WEEK_SPENT, nextWeek).apply();
    }

    public static LocalDate periodStart(LocalDate today) {
        if (today.getDayOfMonth() >= 11) return today.withDayOfMonth(11);
        return today.minusMonths(1).withDayOfMonth(11);
    }

    public static LocalDate periodEnd(LocalDate today) {
        return periodStart(today).plusMonths(1).minusDays(1);
    }

    public static String periodMonthLabel(LocalDate today) {
        return periodEnd(today).getMonthValue() + "월 사용액";
    }

    public static String periodRangeLabel(LocalDate today) {
        LocalDate s = periodStart(today);
        LocalDate e = periodEnd(today);
        return s.getMonthValue() + "." + s.getDayOfMonth() + " ~ " + e.getMonthValue() + "." + e.getDayOfMonth();
    }

    public static long weeklyPlan(Context c) {
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
        return Math.round(g * (weekDays / (double) periodDays));
    }

    public static long weeklyAvailable(Context c) {
        long remain = Math.max(0L, goal(c) - total(c));
        long plannedLeft = Math.max(0L, weeklyPlan(c) - weekSpent(c));
        return Math.min(remain, plannedLeft);
    }

    public static int usagePercent(Context c) {
        long g = goal(c);
        if (g <= 0) return 0;
        return (int) Math.min(999, Math.round(total(c) * 100.0 / g));
    }
}
