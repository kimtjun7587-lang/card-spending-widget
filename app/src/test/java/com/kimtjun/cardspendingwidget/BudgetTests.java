package com.kimtjun.cardspendingwidget;
import org.junit.Test;
public class BudgetTests {
 @Test public void daily(){CoreChecks.daily();}
 @Test public void dates(){CoreChecks.boundaries();}
 @Test public void weekly(){CoreChecks.weekly();}
 @Test public void refunds(){CoreChecks.cancellations();}
 @Test public void initial(){CoreChecks.initial();}
 @Test public void sms(){CoreChecks.parser();}
 @Test public void identity(){CoreChecks.identity();}
 @Test public void invariants(){CoreChecks.invariants();}
}
