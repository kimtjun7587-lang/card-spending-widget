package com.kimtjun.cardspendingwidget;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardNotificationListener extends NotificationListenerService {
    private static final Pattern AMOUNT = Pattern.compile("([0-9,]+)원\\s*(승인취소|취소|승인)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        String title = String.valueOf(e.getCharSequence(Notification.EXTRA_TITLE, ""));
        String text = String.valueOf(e.getCharSequence(Notification.EXTRA_TEXT, ""));
        String big = String.valueOf(e.getCharSequence(Notification.EXTRA_BIG_TEXT, ""));
        String sub = String.valueOf(e.getCharSequence(Notification.EXTRA_SUB_TEXT, ""));
        String body = title + "\n" + text + "\n" + big + "\n" + sub;

        boolean looksLikeLotte = body.contains("1588-8100") || body.contains("15888100") || body.contains("롯데카드") || body.contains("디지로카") || body.contains("London");
        if (!looksLikeLotte) return;

        Matcher m = AMOUNT.matcher(body.replace(" ", ""));
        if (!m.find()) return;
        try {
            long amount = Long.parseLong(m.group(1).replace(",", ""));
            String kind = m.group(2);
            if (SpendingStore.recordTransaction(this, amount, kind, body)) {
                SpendingWidgetProvider.updateAll(this);
            }
        } catch (Exception ignored) {}
    }
}
