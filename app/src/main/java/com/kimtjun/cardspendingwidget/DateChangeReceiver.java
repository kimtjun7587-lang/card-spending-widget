package com.kimtjun.cardspendingwidget;
import android.content.*;
public class DateChangeReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){String a=i.getAction();if(Intent.ACTION_DATE_CHANGED.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a)||Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a))SpendingWidgetProvider.updateAll(c);}
}
