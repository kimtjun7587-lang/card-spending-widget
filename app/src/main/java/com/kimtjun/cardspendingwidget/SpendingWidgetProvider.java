package com.kimtjun.cardspendingwidget;
import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.widget.RemoteViews;
import java.time.*;
import java.time.format.DateTimeFormatter;
public class SpendingWidgetProvider extends AppWidgetProvider {
    private static final String REFRESH="com.kimtjun.cardspendingwidget.trial.REFRESH";
    public static void updateAll(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);for(int id:m.getAppWidgetIds(new ComponentName(c,SpendingWidgetProvider.class)))render(c,m,id);}
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)render(c,m,id);}
    @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);if(REFRESH.equals(i.getAction()))updateAll(c);}
    public static void render(Context c,AppWidgetManager m,int id){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_spending);
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root,open);
        PendingIntent refresh=PendingIntent.getBroadcast(c,1,new Intent(c,SpendingWidgetProvider.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_refresh,refresh);
        TrialStore store=TrialStore.get(c);BudgetEngine.Snapshot s=store.snapshot();
        if(s==null){v.setTextViewText(R.id.widget_amount,"설정 시작하기");v.setTextViewText(R.id.widget_details,"눌러서 예산과 시작일을 설정하세요");v.setTextViewText(R.id.widget_status,"기존 앱과 별도로 저장됩니다");}
        else{
            v.setTextViewText(R.id.widget_amount,MainActivity.money(s.todayAvailable));
            v.setTextViewText(R.id.widget_details,"오늘 순지출 "+MainActivity.money(s.todaySpent)+"   ·   "+s.days+"일 남음\n주기 잔액 "+MainActivity.money(s.remaining)+"\n이번 주 남음 "+MainActivity.money(s.weekAvailable)+" / 배정 "+MainActivity.money(s.weekBudget));
            boolean ready=store.config().consent&&c.checkSelfPermission(Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED;
            String status=!ready?"자동 수집 꺼짐 · 앱에서 권한 확인":store.pendingCount()>0?"확인할 문자 "+store.pendingCount()+"건 · 앱에서 확인":"거래 반영 "+store.lastApplied();
            v.setTextViewText(R.id.widget_status,status+"\n화면 갱신 "+LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM.dd HH:mm")));
        }
        m.updateAppWidget(id,v);
    }
}
