package com.kimtjun.cardspendingwidget;

import java.time.*;
import java.time.temporal.*;
import java.util.*;

/** Pure date-based allocation: no persisted carry-over balances to double count. */
public final class BudgetEngine {
    public static final long MAX_AMOUNT = 1_000_000_000_000L;
    public static final class Entry {
        public final LocalDate day; public final long amount;
        public Entry(LocalDate day,long amount){this.day=day;this.amount=amount;}
    }
    public static final class Snapshot {
        public LocalDate start,end,weekStart,weekEnd;
        public long spent,remaining,todaySpent,todayBudget,todayAvailable,weekSpent,weekBudget,weekAvailable;
        public int days;
    }
    public static LocalDate start(LocalDate date,int startDay){
        if(startDay<1 || startDay>31) throw new IllegalArgumentException("시작일은 1~31일입니다.");
        YearMonth m=YearMonth.from(date);
        LocalDate candidate=m.atDay(Math.min(startDay,m.lengthOfMonth()));
        if(date.isBefore(candidate)){m=m.minusMonths(1);candidate=m.atDay(Math.min(startDay,m.lengthOfMonth()));}
        return candidate;
    }
    public static LocalDate end(LocalDate cycleStart,int startDay){
        YearMonth m=YearMonth.from(cycleStart).plusMonths(1);
        return m.atDay(Math.min(startDay,m.lengthOfMonth())).minusDays(1);
    }
    public static Snapshot calculate(LocalDate today,int startDay,long goal,List<Entry> entries){
        if(goal<=0 || goal>MAX_AMOUNT)throw new IllegalArgumentException("예산 범위를 확인하세요.");
        Snapshot s=new Snapshot();s.start=start(today,startDay);s.end=end(s.start,startDay);
        LocalDate monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        s.weekStart=monday.isBefore(s.start)?s.start:monday;
        LocalDate sunday=monday.plusDays(6);s.weekEnd=sunday.isAfter(s.end)?s.end:sunday;
        long beforeToday=0,beforeWeek=0;
        for(Entry e:entries){
            if(e.day.isBefore(s.start)||e.day.isAfter(today))continue;
            s.spent=Math.addExact(s.spent,e.amount);
            if(e.day.equals(today))s.todaySpent=Math.addExact(s.todaySpent,e.amount);
            else beforeToday=Math.addExact(beforeToday,e.amount);
            if(!e.day.isBefore(s.weekStart))s.weekSpent=Math.addExact(s.weekSpent,e.amount);
            else beforeWeek=Math.addExact(beforeWeek,e.amount);
        }
        s.remaining=goal-s.spent;s.days=(int)ChronoUnit.DAYS.between(today,s.end)+1;
        s.todayBudget=Math.max(0,goal-beforeToday)/s.days;
        s.todayAvailable=Math.min(Math.max(0,s.remaining),Math.max(0,s.todayBudget-s.todaySpent));
        long weekDays=ChronoUnit.DAYS.between(s.weekStart,s.weekEnd)+1;
        long fromWeek=ChronoUnit.DAYS.between(s.weekStart,s.end)+1;
        s.weekBudget=Math.max(0,goal-beforeWeek)*weekDays/fromWeek;
        s.weekAvailable=Math.min(Math.max(0,s.remaining),Math.max(0,s.weekBudget-s.weekSpent));
        return s;
    }
    public static List<Entry> initial(LocalDate today,int startDay,long total,long week,long day){
        if(day<0||week<day||total<week||total>MAX_AMOUNT)
            throw new IllegalArgumentException("초기 사용액은 소비주기 ≥ 이번 주 ≥ 오늘이어야 합니다.");
        LocalDate cycle=start(today,startDay);
        LocalDate ws=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if(ws.isBefore(cycle))ws=cycle;
        if(cycle.equals(ws)&&total!=week)throw new IllegalArgumentException("이번 주에 소비주기가 시작됐으므로 주기 사용액과 주 사용액이 같아야 합니다.");
        if(ws.equals(today)&&week!=day)throw new IllegalArgumentException("오늘이 주간 시작일이므로 주 사용액과 오늘 사용액이 같아야 합니다.");
        List<Entry> out=new ArrayList<>();
        if(total>week)out.add(new Entry(cycle,total-week));
        if(week>day)out.add(new Entry(ws,week-day));
        if(day>0)out.add(new Entry(today,day));
        return out;
    }
}
