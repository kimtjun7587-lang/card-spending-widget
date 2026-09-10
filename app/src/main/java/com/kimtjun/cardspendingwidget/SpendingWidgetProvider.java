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
import java.util.Locale;

public class SpendingWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id, R.layout.widget_spending);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, android.os.Bundle newOptions) {
        render(context, manager, appWidgetId, R.layout.widget_spending);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        updateComponent(context, manager, SpendingWidgetProvider.class, R.layout.widget_spending);
        updateComponent(context, manager, Widget4x1Provider.class, R.layout.widget_4x1);
        updateComponent(context, manager, Widget2x1Provider.class, R.layout.widget_2x1);
    }

    private static void updateComponent(Context context, AppWidgetManager manager, Class<?> cls, int layoutId) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, cls));
        for (int id : ids) render(context, manager, id, layoutId);
    }

    static String won(long v) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원";
    }

    static void render(Context context, AppWidgetManager manager, int id, int layoutId) {
        SpendingStore.ensureCurrentPeriod(context);
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutId);
        long total = SpendingStore.total(context);
        long goal = SpendingStore.goal(context);
        long remain = Math.max(0L, goal - total);
        long weekPlan = SpendingStore.weeklyPlan(context);
        long weekLeft = SpendingStore.weeklyRemaining(context);
        long todayAvailable = SpendingStore.todayAvailable(context);
        long todaySpent = SpendingStore.todaySpent(context);
        int percent = SpendingStore.usagePercent(context);
        LocalDate today = LocalDate.now();
        long daysLeft = SpendingStore.daysUntilClose(today);

        rv.setTextViewText(R.id.w_month, SpendingStore.periodMonthLabel(today));
        rv.setTextViewText(R.id.w_period, SpendingStore.periodRangeLabel(today));
        rv.setTextViewText(R.id.w_amount, won(total));
        rv.setTextViewText(R.id.w_goal, "목표 금액  " + won(goal));
        rv.setTextViewText(R.id.w_remain, "남은 금액  " + won(remain));
        rv.setTextViewText(R.id.w_week_plan, won(weekPlan));
        rv.setTextViewText(R.id.w_week_left, SpendingStore.weeklyOver(context) ? "0원 · 초과" : won(weekLeft));
        rv.setTextViewText(R.id.w_today_available, won(todayAvailable));
        rv.setTextViewText(R.id.w_today_spent, won(todaySpent));
        rv.setTextViewText(R.id.w_percent, "사용률  " + percent + "%");
        rv.setTextViewText(R.id.w_day, "마감일 26일  |  " + daysLeft + "일 남음");
        rv.setProgressBar(R.id.w_progress, 100, Math.max(0, Math.min(100, percent)), false);

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.widget_root, pi);
        manager.updateAppWidget(id, rv);
    }
}
