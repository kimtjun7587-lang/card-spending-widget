package com.kimtjun.cardspendingwidget;
import android.content.*;
import android.content.pm.*;
import android.database.sqlite.SQLiteDatabase;
import android.app.Activity;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class TrialIntegrationTest {
    TrialStore store;Context context;
    @Before public void clear(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();assertEquals("com.kimtjun.cardspendingwidget.trial",context.getPackageName());store=TrialStore.get(context);SQLiteDatabase db=store.getWritableDatabase();db.delete("ledger",null,null);db.delete("config",null,null);db.delete("status",null,null);}
    @Test public void smsLedgerIsIdempotentAndEditable(){
        store.setup(600000,1,0,0,0,true);long now=System.currentTimeMillis()+10;
        store.receive("pdu-first","롯데카드 승인 10,000원",now);
        store.receive("pdu-first","롯데카드 승인 10,000원",now);
        assertEquals(10000,store.snapshot().spent);
        // Same minute same value but independent source transport identity.
        store.receive("pdu-second","롯데카드 승인 10,000원",now+1);assertEquals(20000,store.snapshot().spent);
        store.receive("pdu-refund","롯데카드 부분취소 3,000원",now+2);assertEquals(17000,store.snapshot().spent);
        store.receive("pdu-full-refund","롯데카드 승인취소 10,000원",now+3);assertEquals(7000,store.snapshot().spent);
        long refund=store.rows(true).get(0).id;
        store.save(refund,LocalDate.now(),-9000,"수정한 취소");assertEquals(8000,store.snapshot().spent);
        store.delete(refund);assertEquals(17000,store.snapshot().spent);
        store.receive("pdu-full-refund","롯데카드 승인취소 10,000원",now+3);assertEquals(17000,store.snapshot().spent);
        store.save(null,LocalDate.now(),5000,"누락 추가");assertEquals(22000,store.snapshot().spent);
        store.close();assertEquals(22000,store.snapshot().spent); // persistence after reopening DB
    }
    @Test public void unknownAndOldSmsNeedReviewAndConsentCanStop(){
        store.setup(600000,1,0,0,0,true);long now=System.currentTimeMillis()+10;
        store.receive("unknown","롯데카드 승인 10,000원 20,000원",now);assertEquals(1,store.pendingCount());assertEquals(0,store.snapshot().spent);
        TrialStore.Row row=store.rows(true).get(0);store.save(row.id,LocalDate.now(),10000,"확인 후 반영");assertEquals(0,store.pendingCount());assertEquals(10000,store.snapshot().spent);
        store.receive("old","롯데카드 승인 10,000원",store.config().setupAt-1000);assertEquals(1,store.pendingCount());assertEquals(10000,store.snapshot().spent);
        store.settings(600000,1,false);store.receive("after-revoke","롯데카드 승인 10,000원",now);assertEquals(2,store.rows(true).size());
    }
    @Test public void manifestAndStorageAreIsolated() throws Exception {
        PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS|PackageManager.GET_SERVICES);
        assertEquals("com.kimtjun.cardspendingwidget.trial",info.packageName);
        assertTrue((info.applicationInfo.flags&ApplicationInfo.FLAG_ALLOW_BACKUP)==0);
        assertTrue(info.applicationInfo.dataDir.contains("com.kimtjun.cardspendingwidget.trial"));
        List<String> permissions=Arrays.asList(info.requestedPermissions);
        assertFalse(permissions.contains("android.permission.INTERNET"));assertFalse(permissions.contains("android.permission.READ_SMS"));
        assertTrue(info.services==null||info.services.length==0);
    }
    @Test public void onboardingAndConfiguredScreenLaunch(){
        android.app.Instrumentation ins=InstrumentationRegistry.getInstrumentation();
        Activity first=ins.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.runOnMainSync(()->{assertTrue(hasText(first.getWindow().getDecorView(),"소비주기 예산"));first.finish();});
        store.setup(600000,27,0,0,0,true);
        Activity next=ins.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.runOnMainSync(()->{assertTrue(hasText(next.getWindow().getDecorView(),"오늘 사용 가능"));assertTrue(hasText(next.getWindow().getDecorView(),"누락 지출"));next.finish();});
    }
    static boolean hasText(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(hasText(g.getChildAt(i),text))return true;}return false;}
}
