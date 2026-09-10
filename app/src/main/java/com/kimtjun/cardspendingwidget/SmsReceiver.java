package com.kimtjun.cardspendingwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {
    private static final Pattern AMOUNT = Pattern.compile("([0-9,]+)원\\s*(승인취소|취소|승인)");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");
        if (pdus == null || pdus.length == 0) return;

        StringBuilder bodyBuilder = new StringBuilder();
        String sender = "";
        for (Object pdu : pdus) {
            SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (msg == null) continue;
            if (sender.isEmpty()) sender = msg.getOriginatingAddress();
            bodyBuilder.append(msg.getMessageBody());
        }

        String normalizedSender = sender == null ? "" : sender.replaceAll("[^0-9]", "");
        if (!normalizedSender.endsWith("15888100")) return;

        String body = bodyBuilder.toString();
        Matcher m = AMOUNT.matcher(body.replace(" ", ""));
        if (!m.find()) return;

        long amount;
        try {
            amount = Long.parseLong(m.group(1).replace(",", ""));
        } catch (Exception e) {
            return;
        }

        String kind = m.group(2);
        if (SpendingStore.isDuplicateEvent(context, amount, kind, body)) return;
        if (kind.contains("취소")) amount = -amount;

        SpendingStore.applyAmount(context, amount);
        SpendingWidgetProvider.updateAll(context);
    }
}
