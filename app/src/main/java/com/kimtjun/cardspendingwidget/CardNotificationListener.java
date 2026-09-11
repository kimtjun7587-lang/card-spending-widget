package com.kimtjun.cardspendingwidget;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
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

            String body = collectBody(extras);
            String packageName = sbn.getPackageName() == null ? "" : sbn.getPackageName();
            boolean samsungMessages = packageName.equals("com.samsung.android.messaging")
                    || packageName.equals("com.google.android.apps.messaging");
            boolean looksLikeLotte = CardMessageParser.looksLikeLotteNotification(body)
                    || (samsungMessages && CardMessageParser.looksLikeCardApprovalBody(body));
            if (!looksLikeLotte) return;

            CardMessageParser.Parsed parsed = CardMessageParser.parseLast(body);
            if (parsed == null) {
                IngestionDiagnostics.recordNotification(this, packageName, "카드 관련 알림, 승인/취소 금액 인식 실패", null);
                return;
            }

            boolean stored = SpendingStore.recordTransaction(this, parsed.amount, parsed.kind, body);
            IngestionDiagnostics.recordNotification(
                    this,
                    packageName,
                    stored ? "저장 성공" : "중복 거래로 무시",
                    parsed.amount);
            if (stored) SpendingWidgetProvider.updateAll(this);
        } catch (Exception e) {
            IngestionDiagnostics.recordNotification(this, sbn.getPackageName(), "알림 처리 오류: " + e.getClass().getSimpleName(), null);
        }
    }

    private static String collectBody(Bundle extras) {
        StringBuilder out = new StringBuilder();
        append(out, text(extras, Notification.EXTRA_TITLE));
        append(out, text(extras, Notification.EXTRA_CONVERSATION_TITLE));
        append(out, text(extras, Notification.EXTRA_TEXT));
        append(out, text(extras, Notification.EXTRA_BIG_TEXT));
        append(out, text(extras, Notification.EXTRA_SUB_TEXT));

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) if (line != null) append(out, line.toString());
        }

        Parcelable[] bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (bundles != null) {
            for (Notification.MessagingStyle.Message message : Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)) {
                if (message != null && message.getText() != null) append(out, message.getText().toString());
            }
        }
        return out.toString();
    }

    private static void append(StringBuilder out, String value) {
        if (value == null || value.isEmpty()) return;
        if (out.length() > 0) out.append('\n');
        out.append(value);
    }

    private static String text(Bundle extras, String key) {
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString();
    }
}
