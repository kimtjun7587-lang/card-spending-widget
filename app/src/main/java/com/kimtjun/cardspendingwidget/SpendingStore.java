package com.kimtjun.cardspendingwidget;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpendingStore {
    private static final String PREF = "spending";
    private static final String KEY_GOAL = "goal";
    private static final String KEY_BASE = "base";
    private static final String KEY_CYCLE = "cycle_key";
    private static final String KEY_WEEK = "week_key";
    private static final String KEY_WEEK_BASE = "week_base";
    private static final String KEY_LEDGER = "ledger_v3";
    private static final String KEY_MIGRATED = "ledger_migrated_v3";
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{2})/(\\d{2})\\s+(\\d{2}):(\\d{2})");

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String cycleKey(LocalDate today) {
        return periodStart(today) + "~" + periodEnd(today);
    }

    private static String weekKey(LocalDate today) {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }

    public static void ensureCurrentPeriod(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(KEY_MIGRATED, false)) {
            long legacyApproved = p.getLong("approved", 0L);
            long legacyWeek = p.getLong("week_spent", 0L);
            p.edit()
                    .putLong(KEY_BASE, p.getLong(KEY_BASE, 0L) + legacyApproved)
                    .putLong(KEY_WEEK_BASE, legacyWeek)
                    .putLong("approved", 0L)
                    .putLong("week_spent", 0L)
                    .putBoolean(KEY_MIGRATED, true)
                    .apply();
        }

        LocalDate today = LocalDate.now();
        String currentCycle = cycleKey(today);
        String savedCycle = p.getString(KEY_CYCLE, "");
        if (savedCycle.isEmpty()) {
            p.edit().putString(KEY_CYCLE, currentCycle).putString(KEY_WEEK, weekKey(today)).apply();
        } else if (!currentCycle.equals(savedCycle)) {
            p.edit()
                    .putString(KEY_CYCLE, currentCycle)
                    .putString(KEY_WEEK, weekKey(today))
                    .putLong(KEY_BASE, 0L)
                    .putLong(KEY_WEEK_BASE, 0L)
                    .apply();
        }

        String currentWeek = weekKey(today);
        if (!currentWeek.equals(p.getString(KEY_WEEK, ""))) {
            p.edit().putString(KEY_WEEK, currentWeek).putLong(KEY_WEEK_BASE, 0L).apply();
        }
    }

    public static long goal(Context c) { ensureCurrentPeriod(c); return prefs(c).getLong(KEY_GOAL, 1000000L); }
    public static void setGoal(Context c, long v) { ensureCurrentPeriod(c); prefs(c).edit().putLong(KEY_GOAL, Math.max(0, v)).apply(); }
    public static long base(Context c) { ensureCurrentPeriod(c); return prefs(c).getLong(KEY_BASE, 0L); }
    public static void setBase(Context c, long v) { ensureCurrentPeriod(c); prefs(c).edit().putLong(KEY_BASE, Math.max(0, v)).apply(); }
    public static long weekBase(Context c) { ensureCurrentPeriod(c); return prefs(c).getLong(KEY_WEEK_BASE, 0L); }
    public static void setWeekBase(Context c, long v) { ensureCurrentPeriod(c); prefs(c).edit().putLong(KEY_WEEK_BASE, Math.max(0, v)).apply(); }

    public static LocalDate periodStart(LocalDate today) {
        return today.getDayOfMonth() >= 11 ? today.withDayOfMonth(11) : today.minusMonths(1).withDayOfMonth(11);
    }

    public static LocalDate periodEnd(LocalDate today) {
        return periodStart(today).plusMonths(1).minusDays(1);
    }

    public static String periodMonthLabel(LocalDate today) { return periodEnd(today).getMonthValue() + "월 사용액"; }

    public static String periodRangeLabel(LocalDate today) {
        LocalDate s = periodStart(today), e = periodEnd(today);
        return s.getMonthValue() + "." + s.getDayOfMonth() + " ~ " + e.getMonthValue() + "." + e.getDayOfMonth();
    }

    private static class Entry {
        final long at;
        final long amount;
        final String key;
        Entry(long at, long amount, String key) { this.at = at; this.amount = amount; this.key = key; }
    }

    private static List<Entry> entries(Context c) {
        String raw = prefs(c).getString(KEY_LEDGER, "");
        List<Entry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\\n")) {
            String[] p = line.split("\\|", 3);
            if (p.length != 3) continue;
            try { out.add(new Entry(Long.parseLong(p[0]), Long.parseLong(p[1]), p[2])); } catch (Exception ignored) {}
        }
        return out;
    }

    private static long sumBetween(Context c, LocalDate start, LocalDate end) {
        ZoneId zone = ZoneId.systemDefault();
        long sum = 0L;
        for (Entry e : entries(c)) {
            LocalDate d = Instant.ofEpochMilli(e.at).atZone(zone).toLocalDate();
            if (!d.isBefore(start) && !d.isAfter(end)) sum += e.amount;
        }
        return sum;
    }

    public static long total(Context c) {
        ensureCurrentPeriod(c);
        LocalDate today = LocalDate.now();
        return Math.max(0L, base(c) + sumBetween(c, periodStart(today), periodEnd(today)));
    }

    public static long weekSpent(Context c) {
        ensureCurrentPeriod(c);
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate cycleStart = periodStart(today), cycleEnd = periodEnd(today);
        if (start.isBefore(cycleStart)) start = cycleStart;
        if (end.isAfter(cycleEnd)) end = cycleEnd;
        return Math.max(0L, weekBase(c) + sumBetween(c, start, end));
    }

    private static long eventTime(String body) {
        LocalDateTime now = LocalDateTime.now();
        Matcher m = DATE_TIME.matcher(body == null ? "" : body);
        if (!m.find()) return System.currentTimeMillis();
        try {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            int hour = Integer.parseInt(m.group(3));
            int minute = Integer.parseInt(m.group(4));
            LocalDateTime candidate = LocalDateTime.of(now.getYear(), month, day, hour, minute);
            if (candidate.isAfter(now.plusDays(2))) candidate = candidate.minusYears(1);
            return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String hash(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte v : b) out.append(String.format("%02x", v));
            return out.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }

    public static boolean recordTransaction(Context c, long amount, String kind, String body) {
        ensureCurrentPeriod(c);
        long at = eventTime(body);
        Matcher dt = DATE_TIME.matcher(body == null ? "" : body);
        String dateTime = dt.find() ? dt.group(0) : Long.toString(at / 60000L);
        String key = hash(amount + "|" + kind + "|" + dateTime);
        List<Entry> all = entries(c);
        for (Entry e : all) if (e.key.equals(key)) return false;

        long signed = kind != null && kind.contains("취소") ? -Math.abs(amount) : Math.abs(amount);
        all.add(new Entry(at, signed, key));
        if (all.size() > 500) all = new ArrayList<>(all.subList(all.size() - 500, all.size()));
        StringBuilder raw = new StringBuilder();
        for (Entry e : all) raw.append(e.at).append('|').append(e.amount).append('|').append(e.key).append('\n');
        prefs(c).edit().putString(KEY_LEDGER, raw.toString()).apply();
        return true;
    }

    public static long weeklyPlan(Context c) {
        long g = goal(c);
        if (g <= 0) return 0L;
        LocalDate today = LocalDate.now();
        LocalDate cycleStart = periodStart(today), cycleEnd = periodEnd(today);
        long cycleDays = ChronoUnit.DAYS.between(cycleStart, cycleEnd) + 1L;
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        if (weekStart.isBefore(cycleStart)) weekStart = cycleStart;
        if (weekEnd.isAfter(cycleEnd)) weekEnd = cycleEnd;
        long overlap = Math.max(1L, ChronoUnit.DAYS.between(weekStart, weekEnd) + 1L);
        return Math.round(g * (overlap / (double) cycleDays));
    }

    public static long weeklyRemaining(Context c) { return Math.max(0L, weeklyPlan(c) - weekSpent(c)); }
    public static boolean weeklyOver(Context c) { return weekSpent(c) > weeklyPlan(c); }

    public static int usagePercent(Context c) {
        long g = goal(c);
        return g <= 0 ? 0 : (int)Math.min(999, Math.round(total(c) * 100.0 / g));
    }
}
