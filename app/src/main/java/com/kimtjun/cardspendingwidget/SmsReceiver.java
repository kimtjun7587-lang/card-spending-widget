package com.kimtjun.cardspendingwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        try {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (messages == null || messages.length == 0) {
                IngestionDiagnostics.recordSms(context, "", "SMS 이벤트 수신, 메시지 해석 실패", null);
                return;
            }

            String sender = "";
            StringBuilder bodyBuilder = new StringBuilder();
            for (SmsMessage message : messages) {
                if (message == null) continue;
                if (sender.isEmpty()) sender = message.getOriginatingAddress();
                if (message.getMessageBody() != null) bodyBuilder.append(message.getMessageBody());
            }

            String body = bodyBuilder.toString();
            boolean lotteSender = CardMessageParser.isLotteSender(sender);
            boolean lotteBody = CardMessageParser.looksLikeLotteNotification(body);
            if (!lotteSender && !lotteBody) {
                IngestionDiagnostics.recordSms(
                        context,
                        CardMessageParser.normalizeSender(sender),
                        "SMS 수신, 롯데카드 거래로 판별되지 않음",
                        null);
                return;
            }

            CardMessageParser.Parsed parsed = CardMessageParser.parse(body);
            if (parsed == null) {
                IngestionDiagnostics.recordSms(
                        context,
                        CardMessageParser.normalizeSender(sender),
                        "롯데카드 문자 수신, 승인/취소 금액 인식 실패",
                        null);
                return;
            }

            boolean stored = SpendingStore.recordTransaction(context, parsed.amount, parsed.kind, body);
            IngestionDiagnostics.recordSms(
                    context,
                    CardMessageParser.normalizeSender(sender),
                    stored ? "저장 성공" : "중복 거래로 무시",
                    parsed.amount);
            if (stored) SpendingWidgetProvider.updateAll(context);
        } catch (Exception e) {
            IngestionDiagnostics.recordSms(context, "", "수신 처리 오류: " + e.getClass().getSimpleName(), null);
        }
    }
}
