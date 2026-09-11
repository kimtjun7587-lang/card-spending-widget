package com.kimtjun.cardspendingwidget;
import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;
public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction()))return;
        SmsMessage[] messages=Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if(messages==null||messages.length==0)return;
        String sender=messages[0].getOriginatingAddress();
        if(!LotteSmsParser.senderMatches(sender))return;
        StringBuilder body=new StringBuilder();
        for(SmsMessage sms:messages){if(!LotteSmsParser.senderMatches(sms.getOriginatingAddress()))return;body.append(sms.getMessageBody()==null?"":sms.getMessageBody());}
        Object[] objects=(Object[])intent.getSerializableExtra("pdus");if(objects==null||objects.length==0)return;
        byte[][] pdus=new byte[objects.length][];for(int i=0;i<objects.length;i++){if(!(objects[i] instanceof byte[]))return;pdus[i]=(byte[])objects[i];}
        String key=SmsIdentity.key("15888100",intent.getStringExtra("format"),pdus);
        TrialStore.get(context).receive(key,body.toString(),messages[0].getTimestampMillis());
        SpendingWidgetProvider.updateAll(context);
    }
}
