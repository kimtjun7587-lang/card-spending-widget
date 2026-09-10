package com.kimtjun.cardspendingwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DateChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            SpendingWidgetProvider.updateAll(context);
        }
    }
}
