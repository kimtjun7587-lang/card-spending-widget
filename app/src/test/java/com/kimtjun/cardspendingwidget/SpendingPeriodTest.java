package com.kimtjun.cardspendingwidget;

import static org.junit.Assert.*;
import java.time.LocalDate;
import org.junit.Test;

public class SpendingPeriodTest {
    @Test
    public void septemberCycleIsAug27ToSep26() {
        LocalDate d = LocalDate.of(2026, 9, 11);
        assertEquals(LocalDate.of(2026, 8, 27), SpendingStore.periodStart(d));
        assertEquals(LocalDate.of(2026, 9, 26), SpendingStore.periodEnd(d));
        assertEquals("9월 사용액", SpendingStore.periodMonthLabel(d));
        assertEquals("8.27 ~ 9.26", SpendingStore.periodRangeLabel(d));
    }

    @Test
    public void nextCycleSwitchesToOctober() {
        LocalDate d = LocalDate.of(2026, 9, 27);
        assertEquals(LocalDate.of(2026, 9, 27), SpendingStore.periodStart(d));
        assertEquals(LocalDate.of(2026, 10, 26), SpendingStore.periodEnd(d));
        assertEquals("10월 사용액", SpendingStore.periodMonthLabel(d));
        assertEquals("9.27 ~ 10.26", SpendingStore.periodRangeLabel(d));
    }
}
