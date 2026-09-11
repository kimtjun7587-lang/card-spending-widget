package com.kimtjun.cardspendingwidget;
import java.time.*;
import java.util.*;
public final class CoreChecks {
    static int assertions;
    static void eq(long expected,long actual){assertions++;if(expected!=actual)throw new AssertionError("Expected "+expected+", got "+actual);}
    static void eq(Object expected,Object actual){assertions++;if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
    static void yes(boolean value){assertions++;if(!value)throw new AssertionError("Condition failed");}
    static BudgetEngine.Entry e(String day,long n){return new BudgetEngine.Entry(LocalDate.parse(day),n);}
    static BudgetEngine.Snapshot calc(String date,int start,long goal,BudgetEngine.Entry... entries){return BudgetEngine.calculate(LocalDate.parse(date),start,goal,Arrays.asList(entries));}
    public static void daily(){
        // Sept 21~30 = 10 days, initial 200k already spent out of 300k.
        BudgetEngine.Snapshot s=calc("2026-09-21",1,300000,e("2026-09-01",200000));eq(10000,s.todayAvailable);eq(100000,s.remaining);
        s=calc("2026-09-21",1,300000,e("2026-09-01",200000),e("2026-09-21",5000));eq(5000,s.todayAvailable);eq(95000,s.remaining);
        s=calc("2026-09-22",1,300000,e("2026-09-01",200000),e("2026-09-21",5000));eq(10555,s.todayAvailable);
        s=calc("2026-09-22",1,300000,e("2026-09-01",200000),e("2026-09-21",15000));eq(9444,s.todayAvailable);eq(85000,s.remaining);
        s=calc("2026-09-21",1,300000,e("2026-09-01",200000),e("2026-09-21",15000));eq(0,s.todayAvailable);eq(10000,s.todayBudget);
        s=calc("2026-09-22",1,300000,e("2026-09-01",200000));eq(11111,s.todayAvailable); // No persisted additive carryover.
    }
    public static void boundaries(){
        eq(LocalDate.parse("2026-08-27"),BudgetEngine.start(LocalDate.parse("2026-09-26"),27));
        eq(LocalDate.parse("2026-09-27"),BudgetEngine.start(LocalDate.parse("2026-09-27"),27));
        eq(LocalDate.parse("2026-02-28"),BudgetEngine.start(LocalDate.parse("2026-02-28"),31));
        eq(LocalDate.parse("2026-03-30"),BudgetEngine.end(LocalDate.parse("2026-02-28"),31));
        eq(LocalDate.parse("2024-02-29"),BudgetEngine.start(LocalDate.parse("2024-02-29"),31));
        eq(LocalDate.parse("2025-12-27"),BudgetEngine.start(LocalDate.parse("2026-01-01"),27));
        BudgetEngine.Snapshot s=calc("2026-09-26",27,100000,e("2026-08-27",90000));eq(1,s.days);eq(10000,s.todayAvailable);
        s=calc("2026-09-27",27,100000,e("2026-08-27",90000));eq(0,s.spent);eq(100000,s.remaining);eq(30,s.days);
        // Date arithmetic remains calendar-based across DST and leap years.
        s=calc("2024-02-29",1,29000);eq(1,s.days);
        s=calc("2026-03-08",1,31000);eq(24,s.days);
    }
    public static void weekly(){
        BudgetEngine.Snapshot s=calc("2026-09-21",1,300000,e("2026-09-01",200000));eq(70000,s.weekBudget);eq(70000,s.weekAvailable);
        s=calc("2026-09-23",1,300000,e("2026-09-01",200000),e("2026-09-21",20000));eq(70000,s.weekBudget);eq(50000,s.weekAvailable);eq(10000,s.todayAvailable);
        s=calc("2026-09-28",1,300000,e("2026-09-01",200000),e("2026-09-21",20000));eq(80000,s.weekBudget);eq(80000,s.weekAvailable);
        s=calc("2026-09-27",27,300000);eq(10000,s.weekBudget);eq(s.weekBudget,s.todayBudget); // Sunday cycle start.
        s=calc("2026-09-26",27,300000,e("2026-09-25",10000));eq(290000,s.todayAvailable); // final day all remainder
    }
    public static void cancellations(){
        BudgetEngine.Snapshot s=calc("2026-09-21",1,300000,e("2026-09-01",200000),e("2026-09-21",10000),e("2026-09-21",-10000));eq(100000,s.remaining);eq(10000,s.todayAvailable);eq(0,s.todaySpent);
        s=calc("2026-09-21",1,300000,e("2026-09-01",200000),e("2026-09-21",10000),e("2026-09-21",-3000));eq(93000,s.remaining);eq(3000,s.todayAvailable);
        s=calc("2026-09-22",1,300000,e("2026-09-01",200000),e("2026-09-21",10000),e("2026-09-22",-3000));eq(93000,s.remaining);eq(13000,s.todayAvailable);
        s=calc("2026-09-28",27,100000,e("2026-09-26",10000),e("2026-09-28",-10000));eq(-10000,s.spent);eq(110000,s.remaining); // refund belongs to cancellation cycle
        s=calc("2026-09-22",1,100000,e("2026-09-21",110000),e("2026-09-22",-15000));eq(5000,s.remaining);eq(5000,s.todayAvailable); // never offer more than whole balance
        s=calc("2026-09-22",1,100000,e("2026-09-21",120000));eq(-20000,s.remaining);eq(0,s.todayAvailable);
    }
    public static void initial(){
        List<BudgetEngine.Entry> es=BudgetEngine.initial(LocalDate.parse("2026-09-23"),1,100000,30000,5000);
        BudgetEngine.Snapshot s=BudgetEngine.calculate(LocalDate.parse("2026-09-23"),1,300000,es);eq(100000,s.spent);eq(30000,s.weekSpent);eq(5000,s.todaySpent);
        boolean bad=false;try{BudgetEngine.initial(LocalDate.parse("2026-09-23"),1,100,200,50);}catch(IllegalArgumentException e){bad=true;}yes(bad);
        bad=false;try{BudgetEngine.initial(LocalDate.parse("2026-09-21"),1,100000,30000,5000);}catch(IllegalArgumentException e){bad=true;}yes(bad);
        es=BudgetEngine.initial(LocalDate.parse("2026-09-27"),27,5000,5000,5000);eq(1,es.size());
        bad=false;try{BudgetEngine.initial(LocalDate.parse("2026-09-27"),27,10000,5000,5000);}catch(IllegalArgumentException e){bad=true;}yes(bad);
    }
    public static void parser(){
        eq(12500,LotteSmsParser.parse("[Web발신] 롯데1234 승인 홍*동 12,500원 일시불 09/21 12:10 상점 누적 200,000원").amount);
        eq(12500,LotteSmsParser.parse("롯데카드 12,500원 승인").amount);
        eq(-12500,LotteSmsParser.parse("롯데카드 승인취소 12,500원").amount);
        eq(-3000,LotteSmsParser.parse("롯데카드 부분취소 3,000원 09/21 12:11").amount);
        yes(!LotteSmsParser.parse("승인 10,000원 20,000원").accepted);
        yes(!LotteSmsParser.parse("승인 거절 10,000원").accepted);
        yes(!LotteSmsParser.parse("취소 예정 10,000원").accepted);
        yes(!LotteSmsParser.parse("결제대금 10,000원 승인").accepted);
        yes(!LotteSmsParser.parse("승인 USD 10.00").accepted);
        yes(!LotteSmsParser.parse("해외 승인 10,000원").accepted);
        yes(!LotteSmsParser.parse("롯데카드 안내").accepted);
        yes(!LotteSmsParser.parse("승인 1,23원").accepted);
        yes(!LotteSmsParser.parse("취소 0원").accepted);
        yes(LotteSmsParser.senderMatches("1588-8100"));yes(LotteSmsParser.senderMatches("+8215888100"));yes(!LotteSmsParser.senderMatches("01015888100"));yes(!LotteSmsParser.senderMatches("롯데카드"));
    }
    public static void identity(){
        // Same amount/body/minute is not used as identity. Different seconds/transport bytes survive.
        String a=SmsIdentity.key("15888100","3gpp",new byte[][]{{1,2,3,10}});
        String b=SmsIdentity.key("15888100","3gpp",new byte[][]{{1,2,3,11}});
        yes(!a.equals(b));eq(a,SmsIdentity.key("15888100","3gpp",new byte[][]{{1,2,3,10}}));
        yes(!SmsIdentity.key("15888100","3gpp",new byte[][]{{1,2},{3}}).equals(SmsIdentity.key("15888100","3gpp",new byte[][]{{1},{2,3}})));
    }
    public static void invariants(){
        Random rng=new Random(20260911);
        for(int i=0;i<2000;i++){
            LocalDate date=LocalDate.of(2024,1,1).plusDays(rng.nextInt(1100));int d=1+rng.nextInt(31);long goal=100000;
            List<BudgetEngine.Entry> es=new ArrayList<>();
            for(int k=0;k<35;k++)es.add(new BudgetEngine.Entry(date.minusDays(k),rng.nextInt(20001)-3000));
            BudgetEngine.Snapshot s=BudgetEngine.calculate(date,d,goal,es);
            eq(goal-s.spent,s.remaining);yes(s.todayAvailable>=0&&s.todayAvailable<=Math.max(0,s.remaining));yes(s.weekAvailable>=0&&s.weekAvailable<=Math.max(0,s.remaining));
            yes(!s.start.isAfter(date)&&!s.end.isBefore(date)&&s.days>=1&&s.days<=31);
            long sum=0;for(BudgetEngine.Entry e:es)if(!e.day.isBefore(s.start)&&!e.day.isAfter(date))sum+=e.amount;eq(sum,s.spent);
            BudgetEngine.Snapshot again=BudgetEngine.calculate(date,d,goal,es);eq(s.todayAvailable,again.todayAvailable);
        }
    }
    public static void main(String[] args){daily();boundaries();weekly();cancellations();initial();parser();identity();invariants();System.out.println("PASS: "+assertions+" assertions across daily, cycle/date, weekly, refund, initial, SMS parser, identity and 2,000 randomized scenarios.");}
}
