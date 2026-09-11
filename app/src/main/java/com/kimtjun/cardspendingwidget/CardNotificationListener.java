package com.kimtjun.cardspendingwidget;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class CardNotificationListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        IngestionDiagnostics.recordListenerConnected(this, getPackageName());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;

        try {
            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;
            if (extras == null) return;

            String title = text(extras, Notification.EXTRA_TITLE);
            String text = text(extras, Notification.EXTRA_TEXT);
            String big = text(extras, Notification.EXTRA_BIG_TEXT);
            String sub = text(extras, Notification.EXTRA_SUB_TEXT);
            String body = title + "\n" + text + "\n" + big + "\n" + sub;

            if (!CardMessageParser.looksLikeLotteNotification(body)) return;

            CardMessageParser.Parsed parsed = CardMessageParser.parse(body);
            if (parsed == null) {
                IngestionDiagnostics.recordNotification(this, sbn.getPackageName(), "롯데카드 관련 알림, 승인/취소 금액 인식 실패", null);
                return;
            }

            boolean stored = SpendingStore.recordTransaction(this, parsed.amount, parsed.kind, body);
            IngestionDiagnostics.recordNotification(
                    this,
                    sbn.getPackageName(),
                    stored ? "저장 성공" : "중복 거래로 무시",
                    parsed.amount);
            if (stored) SpendingWidgetProvider.updateAll(this);
        } catch (Exception e) {
            IngestionDiagnostics.recordNotification(this, sbn.getPackageName(), "알림 처리 오류: " + e.getClass().getSimpleName(), null);
        }
    }

    private static String text(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }
}
