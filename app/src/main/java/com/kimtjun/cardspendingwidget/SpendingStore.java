package com.kimtjun.cardspendingwidget;

import android.content.Context;
import android.content.SharedPreferences;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final String KEY_WEEK_BASE = "week_base";
    private static final String KEY_WEEK_BASE_WEEK = "week_base_week_v5";
    private static final String KEY_BASE_DATE = "base_date_v5";
    private static final String KEY_TODAY_BASE = "today_base_v5";
    private static final String KEY_TODAY_BASE_DATE = "today_base_date_v5";
    private static final String KEY_DAILY_ANCHOR_DATE = "daily_anchor_date_v5";
    private static final String KEY_LEDGER = "ledger_v3";
    private static final String KEY_MIGRATED = "ledger_migrated_v3";
    private static final String KEY_RULE_VERSION = "cycle_rule_version";
    private static final int RULE_VERSION = 5;
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

    private static LocalDate parseDate(String value, LocalDate fallback) {
        try { return LocalDate.parse(value); }
        catch (Exception ignored) { return fallback; }
    }

    public static void ensureCurrentPeriod(Context c) {
        SharedPreferences p = prefs(c);

        if (!p.getBoolean(KEY_MIGRATED, false)) {
            long legacyApproved = p.getLong("approved", 0L);
            long legacyWeek = p.getLong("week_spent", 0L);
            p.edit()
                    .putLong(KEY_BASE, p.getLong(KEY_BASE, 0L) + legacyApproved)
                    .putLong(KEY_WEEK_BASE, Math.max(p.getLong(KEY_WEEK_BASE, 0L), legacyWeek))
                    .putLong("approved", 0L)
                    .putLong("week_spent", 0L)
                    .putBoolean(KEY_MIGRATED, true)
                    .apply();
        }

        LocalDate today = LocalDate.now();
        String currentCycle = cycleKey(today);

        if (p.getInt(KEY_RULE_VERSION, 0) < RULE_VERSION) {
            SharedPreferences.Editor e = p.edit()
                    .putInt(KEY_RULE_VERSION, RULE_VERSION)
                    .putString(KEY_CYCLE, currentCycle);
            if (!p.contains(KEY_BASE_DATE)) e.putString(KEY_BASE_DATE, today.toString());
            if (!p.contains(KEY_WEEK_BASE_WEEK)) e.putString(KEY_WEEK_BASE_WEEK, weekKey(today));
            if (!p.contains(KEY_TODAY_BASE)) e.putLong(KEY_TODAY_BASE, 0L);
            if (!p.contains(KEY_TODAY_BASE_DATE)) e.putString(KEY_TODAY_BASE_DATE, today.toString());
            if (!p.contains(KEY_DAILY_ANCHOR_DATE)) e.putString(KEY_DAILY_ANCHOR_DATE, today.toString());
            e.apply();
            return;
        }

        String savedCycle = p.getString(KEY_CYCLE, "");
        if (savedCycle == null || savedCycle.isEmpty()) {
            p.edit()
                    .putString(KEY_CYCLE, currentCycle)
                    .putString(KEY_BASE_DATE, today.toString())
                    .putString(KEY_WEEK_BASE_WEEK, weekKey(today))
                    .putString(KEY_TODAY_BASE_DATE, today.toString())
                    .putString(KEY_DAILY_ANCHOR_DATE, today.toString())
                    .apply();
        } else if (!currentCycle.equals(savedCycle)) {
            p.edit()
                    .putString(KEY_CYCLE, currentCycle)
                    .putLong(KEY_BASE, 0L)
                    .putLong(KEY_WEEK_BASE, 0L)
                    .putLong(KEY_TODAY_BASE, 0L)
                    .putString(KEY_BASE_DATE, today.toString())
                    .putString(KEY_WEEK_BASE_WEEK, weekKey(today))
                    .putString(KEY_TODAY_BASE_DATE, today.toString())
                    .putString(KEY_DAILY_ANCHOR_DATE, today.toString())
                    .apply();
        }
    }

    public static long goal(Context c) {
        ensureCurrentPeriod(c);
        return prefs(c).getLong(KEY_GOAL, 1000000L);
    }

    public static void setGoal(Context c, long v) {
        ensureCurrentPeriod(c);
        prefs(c).edit().putLong(KEY_GOAL, Math.max(0L, v)).apply();
    }

    public static long base(Context c) {
        ensureCurrentPeriod(c);
        return prefs(c).getLong(KEY_BASE, 0L);
    }

    public static void setBase(Context c, long desiredCycleTotal) {
        ensureCurrentPeriod(c);
        LocalDate today = LocalDate.now();
        long automatic = sumBetween(c, periodStart(today), periodEnd(today));
        long correction = Math.max(0L, desiredCycleTotal) - automatic;
        prefs(c).edit()
                .putLong(KEY_BASE, correction)
                .putString(KEY_BASE_DATE, today.toString())
                .apply();
    }

    public static long weekBase(Context c) {
        ensureCurrentPeriod(c);
        SharedPreferences p = prefs(c);
        return weekKey(LocalDate.now()).equals(p.getString(KEY_WEEK_BASE_WEEK, ""))
                ? p.getLong(KEY_WEEK_BASE, 0L) : 0L;
    }

    public static void setWeekBase(Context c, long desiredWeekTotal) {
        ensureCurrentPeriod(c);
        LocalDate today = LocalDate.now();
        long automatic = sumBetween(c, currentWeekStart(today), currentWeekEnd(today));
        long correction = Math.max(0L, desiredWeekTotal) - automatic;
        prefs(c).edit()
                .putLong(KEY_WEEK_BASE, correction)
                .putString(KEY_WEEK_BASE_WEEK, weekKey(today))
                .apply();
    }

    public static long todayBase(Context c) {
        ensureCurrentPeriod(c);
        SharedPreferences p = prefs(c);
        String date = p.getString(KEY_TODAY_BASE_DATE, "");
        return LocalDate.now().toString().equals(date) ? p.getLong(KEY_TODAY_BASE, 0L) : 0L;
    }

    public static void setTodayBase(Context c, long desiredTodayTotal) {
        ensureCurrentPeriod(c);
        LocalDate today = LocalDate.now();
        long automatic = sumBetween(c, today, today);
        long correction = Math.max(0L, desiredTodayTotal) - automatic;
        prefs(c).edit()
                .putLong(KEY_TODAY_BASE, correction)
                .putString(KEY_TODAY_BASE_DATE, today.toString())
                .apply();
    }

    public static LocalDate periodStart(LocalDate today) {
        return today.getDayOfMonth() >= 27
                ? today.withDayOfMonth(27)
                : today.minusMonths(1).withDayOfMonth(27);
    }

    public static LocalDate periodEnd(LocalDate today) {
        return periodStart(today).plusMonths(1).minusDays(1);
    }

    public static String periodMonthLabel(LocalDate today) {
        return periodEnd(today).getMonthValue() + "월 사용액";
    }

    public static String periodRangeLabel(LocalDate today) {
        LocalDate s = periodStart(today), e = periodEnd(today);
        return s.getMonthValue() + "." + s.getDayOfMonth() + " ~ " + e.getMonthValue() + "." + e.getDayOfMonth();
    }

    private static class Entry {
        final long at;
        final long amount;
        final String key;

        Entry(long at, long amount, String key) {
            this.at = at;
            this.amount = amount;
            this.key = key;
        }
    }

    private static List<Entry> entries(Context c) {
        String raw = prefs(c).getString(KEY_LEDGER, "");
        List<Entry> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\\n")) {
            String[] p = line.split("\\|", 3);
            if (p.length != 3) continue;
            try {
                out.add(new Entry(Long.parseLong(p[0]), Long.parseLong(p[1]), p[2]));
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static long sumBetween(Context c, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) return 0L;
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

    private static LocalDate currentWeekStart(LocalDate today) {
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate cycleStart = periodStart(today);
        return start.isBefore(cycleStart) ? cycleStart : start;
    }

    private static LocalDate currentWeekEnd(LocalDate today) {
        LocalDate end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate cycleEnd = periodEnd(today);
        return end.isAfter(cycleEnd) ? cycleEnd : end;
    }

    private static long currentWeekInitial(Context c, LocalDate today) {
        SharedPreferences p = prefs(c);
        return weekKey(today).equals(p.getString(KEY_WEEK_BASE_WEEK, ""))
                ? p.getLong(KEY_WEEK_BASE, 0L) : 0L;
    }

    private static BigDecimal allocation(long goal, long days, long cycleDays) {
        if (goal <= 0L || days <= 0L || cycleDays <= 0L) return BigDecimal.ZERO;
        return BigDecimal.valueOf(goal)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(cycleDays), 12, RoundingMode.HALF_UP);
    }

    private static BigDecimal weeklyNeedExact(Context c) {
        ensureCurrentPeriod(c);
        LocalDate today = LocalDate.now();
        long remaining = Math.max(0L, goal(c) - total(c));
        long spent = weekSpent(c);
        if (remaining <= 0L) return BigDecimal.valueOf(spent);

        LocalDate ce = periodEnd(today);
        LocalDate we = currentWeekEnd(today);
        long remainingCycleDays = Math.max(1L, ChronoUnit.DAYS.between(today, ce) + 1L);
        long remainingWeekDays = Math.max(1L, ChronoUnit.DAYS.between(today, we) + 1L);

        BigDecimal futureWeekAllowance = allocation(remaining, remainingWeekDays, remainingCycleDays);
        return BigDecimal.valueOf(spent).add(futureWeekAllowance);
    }

    public static long weeklyPlan(Context c) {
        BigDecimal exact = weeklyNeedExact(c);
        if (exact.signum() <= 0) return 0L;
        return exact.setScale(0, RoundingMode.FLOOR).longValue();
    }

    public static long weekSpent(Context c) {
        ensureCurrentPeriod(c);
        LocalDate today = LocalDate.now();
        long correction = currentWeekInitial(c, today);
        return Math.max(0L, correction + sumBetween(c, currentWeekStart(today), currentWeekEnd(today)));
    }

    public static long weeklyRemaining(Context c) {
        return Math.max(0L, weeklyPlan(c) - weekSpent(c));
    }

    public static boolean weeklyOver(Context c) {
        return weekSpent(c) > weeklyPlan(c);
    }

    private static long datedTodayBaseBetween(Context c, LocalDate start, LocalDate end) {
        SharedPreferences p = prefs(c);
        LocalDate fallback = LocalDate.now();
        LocalDate d = parseDate(p.getString(KEY_TODAY_BASE_DATE, fallback.toString()), fallback);
        if (d.isBefore(start) || d.isAfter(end)) return 0L;
        return p.getLong(KEY_TODAY_BASE, 0L);
    }

    public static long todaySpent(Context c) {
        ensureCurrentPeriod(c);
        LocalDate today = LocalDate.now();
        return Math.max(0L, datedTodayBaseBetween(c, today, today) + sumBetween(c, today, today));
    }

    public static long todayAvailable(Context c) {
        ensureCurrentPeriod(c);
        LocalDate today = LocalDate.now();
        long spentToday = todaySpent(c);
        long spentBeforeToday = Math.max(0L, total(c) - spentToday);
        long startRemaining = Math.max(0L, goal(c) - spentBeforeToday);
        if (startRemaining <= 0L) return 0L;

        long remainingCycleDays = Math.max(1L, ChronoUnit.DAYS.between(today, periodEnd(today)) + 1L);
        long todayBudget = BigDecimal.valueOf(startRemaining)
                .divide(BigDecimal.valueOf(remainingCycleDays), 0, RoundingMode.FLOOR)
                .longValue();
        return Math.max(0L, todayBudget - spentToday);
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
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    public static synchronized boolean recordTransaction(Context c, long amount, String kind, String body) {
        ensureCurrentPeriod(c);
        long at = eventTime(body);
        String dateToken = CardMessageParser.dateTimeToken(body, at);
        String legacyKey = hash(amount + "|" + kind + "|" + dateToken);
        String key = "v6:" + hash(CardMessageParser.dedupMaterial(body, amount, kind, at));

        List<Entry> all = entries(c);
        for (Entry e : all) {
            if (e.key.startsWith("v6:")) {
                if (e.key.equals(key)) return false;
            } else if (e.key.equals(legacyKey)) {
                return false;
            }
        }

        long signed = kind != null && kind.contains("취소") ? -Math.abs(amount) : Math.abs(amount);
        all.add(new Entry(at, signed, key));
        if (all.size() > 500) all = new ArrayList<>(all.subList(all.size() - 500, all.size()));

        StringBuilder raw = new StringBuilder();
        for (Entry e : all) raw.append(e.at).append('|').append(e.amount).append('|').append(e.key).append('\n');
        prefs(c).edit().putString(KEY_LEDGER, raw.toString()).apply();
        return true;
    }

    public static long daysUntilClose(LocalDate today) {
        return Math.max(0L, ChronoUnit.DAYS.between(today, periodEnd(today)));
    }

    public static int usagePercent(Context c) {
        long g = goal(c);
        return g <= 0L ? 0 : (int)Math.min(999L, Math.round(total(c) * 100.0 / g));
    }
}
