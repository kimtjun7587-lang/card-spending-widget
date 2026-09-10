package com.kimtjun.cardspendingwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class Widget4x1Provider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) SpendingWidgetProvider.render(context, manager, id, R.layout.widget_4x1);
    }
}
