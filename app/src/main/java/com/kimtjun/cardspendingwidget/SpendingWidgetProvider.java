package com.kimtjun.cardspendingwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class SpendingWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName name = new ComponentName(context, SpendingWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(name);
        for (int id : ids) updateOne(context, manager, id);
    }

    private static String won(long v) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원";
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_spending);
        long total = SpendingStore.total(context);
        long goal = SpendingStore.goal(context);
        long remain = Math.max(0, goal - total);
        int percent = SpendingStore.usagePercent(context);
        long weekly = SpendingStore.weeklyRecommended(context);
        LocalDate today = LocalDate.now();
        LocalDate end = SpendingStore.periodEnd(today);
        long dday = Math.max(0, ChronoUnit.DAYS.between(today, end));

        rv.setTextViewText(R.id.w_amount, won(total));
        rv.setTextViewText(R.id.w_goal, "목표 " + won(goal));
        rv.setTextViewText(R.id.w_remain, "남은 " + won(remain));
        rv.setTextViewText(R.id.w_week, "이번 주 권장 " + won(weekly));
        rv.setTextViewText(R.id.w_percent, "사용률 " + percent + "%");
        rv.setTextViewText(R.id.w_day, "결제일 10일 · D-" + dday);

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.widget_root, pi);
        manager.updateAppWidget(id, rv);
    }
}
