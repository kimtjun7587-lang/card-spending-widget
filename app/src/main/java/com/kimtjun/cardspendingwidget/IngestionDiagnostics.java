package com.kimtjun.cardspendingwidget;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class IngestionDiagnostics {
    private static final String PREF = "ingestion_diagnostics";
    private static final String KEY_SMS = "last_sms";
    private static final String KEY_NOTIFICATION = "last_notification";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");

    private IngestionDiagnostics() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static void recordSms(Context context, String sender, String status, Long amount) {
        prefs(context).edit().putString(KEY_SMS, format("SMS", sender, status, amount)).apply();
    }

    static void recordNotification(Context context, String packageName, String status, Long amount) {
        prefs(context).edit().putString(KEY_NOTIFICATION, format("알림", packageName, status, amount)).apply();
    }

    static void recordListenerConnected(Context context, String packageName) {
        recordNotification(context, packageName, "알림 접근 연결됨", null);
    }

    static String summary(Context context) {
        SharedPreferences p = prefs(context);
        String sms = p.getString(KEY_SMS, "SMS: 아직 롯데카드 문자 감지 기록 없음");
        String notification = p.getString(KEY_NOTIFICATION, "알림: 아직 롯데카드 알림 감지 기록 없음");
        return sms + "\n" + notification;
    }

    private static String format(String source, String origin, String status, Long amount) {
        StringBuilder out = new StringBuilder();
        out.append(source).append(" ").append(LocalDateTime.now().format(TIME)).append(" · ").append(status);
        if (amount != null) out.append(" · ").append(String.format("%,d원", amount));
        if (origin != null && !origin.isEmpty()) out.append(" · ").append(origin);
        return out.toString();
    }
}
