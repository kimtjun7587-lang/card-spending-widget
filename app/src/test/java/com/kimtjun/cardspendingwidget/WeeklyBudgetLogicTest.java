package com.kimtjun.cardspendingwidget;

import static org.junit.Assert.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Random;
import org.junit.Test;

public class WeeklyBudgetLogicTest {
    private static long plan(LocalDate date, long goal, long total, long weekSpent, long todayAvailable) {
        return SpendingStore.weeklyPlanFor(date, goal, total, weekSpent, todayAvailable);
    }

    @Test
    public void reproducedSep12Sep13BugNowMovesInCorrectDirection() {
        LocalDate sep12 = LocalDate.of(2026, 9, 12);
        LocalDate sep13 = LocalDate.of(2026, 9, 13);

        long p12 = plan(sep12, 700_000L, 538_400L, 73_600L, 9_840L);
        long p13 = plan(sep13, 700_000L, 538_400L, 73_600L, 11_542L);

        assertEquals(83_440L, p12);
        assertEquals(85_142L, p13);
        assertTrue("No spending occurred, so weekly plan must not shrink", p13 >= p12);
        assertEquals(9_840L, p12 - 73_600L);
        assertEquals(11_542L, p13 - 73_600L);
    }

    @Test
    public void mondayStartsWithZeroWeekSpentAndPlanEqualsRemaining() {
        LocalDate monday = LocalDate.of(2026, 9, 14);
        long p = plan(monday, 700_000L, 538_400L, 0L, 12_430L);
        assertEquals(87_015L, p);
        assertEquals(p, p - 0L);
    }

    @Test
    public void cycleBoundaryUsesOnlyCurrentCycleDays() {
        LocalDate monday = LocalDate.of(2026, 9, 21);
        long p = plan(monday, 700_000L, 600_000L, 0L, 16_666L);
        // Current cycle ends Sep 26, so only six days (Mon-Sat) are budgeted.
        assertEquals(100_000L, p);
    }

    @Test
    public void noBudgetWhenGoalAlreadyExceeded() {
        LocalDate d = LocalDate.of(2026, 9, 13);
        assertEquals(450_000L, plan(d, 400_000L, 450_000L, 450_000L, 0L));
    }

    @Test
    public void todayAllowanceIsAlwaysCoveredByWeeklyRemainderAcrossManyCases() {
        Random r = new Random(20260913L);
        LocalDate start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < 500; i++) {
            LocalDate d = start.plusDays(r.nextInt(900));
            long goal = 100_000L + r.nextInt(1_900_001);
            long total = r.nextInt((int)Math.min(Integer.MAX_VALUE, goal + 200_001L));
            long spent = r.nextInt(150_001);
            long today = r.nextInt(50_001);
            long p = plan(d, goal, total, spent, today);
            long remaining = Math.max(0L, goal - total);
            long weeklyRemaining = Math.max(0L, p - spent);

            if (remaining > 0L) {
                assertTrue("weekly remainder must cover today's allowance", weeklyRemaining >= today);
            }
            assertTrue(p >= spent || remaining == 0L);
        }
    }

    @Test
    public void noSpendDateAdvanceWithinSameWeekNeverShrinksWhenTodayAllowanceDoesNotShrink() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        long goal = 700_000L;
        long total = 538_400L;
        long spent = 73_600L;
        long[] todayAvailable = {8_000L, 8_500L, 9_000L, 9_200L, 9_500L, 9_840L, 11_542L};

        long previous = -1L;
        for (int i = 0; i < 7; i++) {
            long p = plan(monday.plusDays(i), goal, total, spent, todayAvailable[i]);
            if (previous >= 0L) assertTrue(p >= previous);
            previous = p;
        }
    }

    @Test
    public void spendingMoreDoesNotCreateExtraWeeklyHeadroom() {
        LocalDate d = LocalDate.of(2026, 9, 10);
        long goal = 700_000L;

        long basePlan = plan(d, goal, 500_000L, 40_000L, 10_000L);
        long baseRemaining = basePlan - 40_000L;

        long afterPlan = plan(d, goal, 520_000L, 60_000L, 0L);
        long afterRemaining = afterPlan - 60_000L;

        assertTrue(afterRemaining <= baseRemaining);
    }

    @Test
    public void weekRunsMondayThroughSunday() {
        for (int i = 0; i < 200; i++) {
            LocalDate d = LocalDate.of(2026, 1, 1).plusDays(i);
            LocalDate monday = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate sunday = d.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            assertFalse(d.isBefore(monday));
            assertFalse(d.isAfter(sunday));
            assertEquals(DayOfWeek.MONDAY, monday.getDayOfWeek());
            assertEquals(DayOfWeek.SUNDAY, sunday.getDayOfWeek());
        }
    }

    @Test
    public void oneHundredFortyFourStructuredDateAmountCasesStayConsistent() {
        long[] goals = {100_000L, 300_000L, 400_000L, 600_000L, 700_000L, 1_000_000L};
        long[] totals = {0L, 25_000L, 80_000L, 150_000L};
        LocalDate[] dates = {
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 13),
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 26),
                LocalDate.of(2026, 9, 27)
        };

        int cases = 0;
        for (LocalDate date : dates) {
            for (long goal : goals) {
                for (long usedSeed : totals) {
                    long total = Math.min(goal + 10_000L, usedSeed);
                    long spent = Math.min(total, 30_000L);
                    long remaining = Math.max(0L, goal - total);
                    long today = remaining == 0L ? 0L : Math.min(12_345L, remaining);
                    long p = plan(date, goal, total, spent, today);
                    long weekRemaining = Math.max(0L, p - spent);

                    assertTrue(p >= 0L);
                    if (remaining > 0L) assertTrue(weekRemaining >= today);
                    cases++;
                }
            }
        }
        assertEquals(144, cases);
    }
}
