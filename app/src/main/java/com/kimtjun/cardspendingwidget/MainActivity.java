package com.kimtjun.cardspendingwidget;

import android.Manifest;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.text.NumberFormat;
import java.util.*;

public class MainActivity extends Activity {
    private TrialStore store;
    private LinearLayout root;
    private int setupStep=0;
    private String goal="600000",startDay="27",total="0",week="0",today="0";
    private boolean consent=false,showAll=false,showPending=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){if(store.config()!=null&&!isFinishing()&&!dialogOpen){showHome();}handler.postDelayed(this,15000);}};
    private boolean dialogOpen=false;
    private static final int NAVY=Color.rgb(14,26,43),CARD=Color.rgb(27,45,69),WHITE=Color.rgb(246,249,255),MUTED=Color.rgb(184,202,223),PINK=Color.rgb(255,164,188);
    @Override public void onCreate(Bundle state){super.onCreate(state);store=TrialStore.get(this);
        if(state!=null){setupStep=state.getInt("step");goal=state.getString("goal","600000");startDay=state.getString("start","27");total=state.getString("total","0");week=state.getString("week","0");today=state.getString("today","0");consent=state.getBoolean("consent");}
        show();
    }
    @Override protected void onResume(){super.onResume();if(store!=null)show();handler.removeCallbacks(refresh);handler.postDelayed(refresh,15000);}
    @Override protected void onPause(){super.onPause();handler.removeCallbacks(refresh);}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("step",setupStep);out.putString("goal",goal);out.putString("start",startDay);out.putString("total",total);out.putString("week",week);out.putString("today",today);out.putBoolean("consent",consent);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    static String money(long n){return NumberFormat.getIntegerInstance(Locale.KOREA).format(n)+"원";}
    private void show(){if(store.config()==null)showSetup();else showHome();}
    private void page(String title){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(NAVY);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(20),dp(20),dp(28));scroll.addView(root);
        // API 35 edge-to-edge: keep controls outside status/navigation bars.
        scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(scroll);text(root,title,24,WHITE,true);text(root,"개인 개발 무료 시험판 · 롯데카드 공식 앱 아님",12,MUTED,false);
    }
    private TextView text(LinearLayout parent,String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(7),0,dp(7));if(bold)t.setTypeface(null,Typeface.BOLD);parent.addView(t);return t;}
    private LinearLayout card(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(10),dp(16),dp(14));GradientDrawable bg=new GradientDrawable();bg.setColor(CARD);bg.setCornerRadius(dp(18));box.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(16);root.addView(box,lp);return box;}
    private Button button(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setMinHeight(dp(48));parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());return b;}
    private EditText input(LinearLayout parent,String label,String value,boolean numeric){text(parent,label,15,parent==root?WHITE:Color.DKGRAY,true);EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setTextSize(18);e.setTextColor(parent==root?WHITE:Color.BLACK);e.setContentDescription(label);e.setInputType(numeric?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT);e.setSelectAllOnFocus(true);e.setMinHeight(dp(48));parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private long amount(EditText e){String s=e.getText().toString().trim().replace(",","");if(s.isEmpty())throw new IllegalArgumentException("빈 금액을 입력해 주세요.");try{long v=Long.parseLong(s);if(v<0||v>BudgetEngine.MAX_AMOUNT)throw new NumberFormatException();return v;}catch(NumberFormatException ex){throw new IllegalArgumentException("금액은 0~1조 원 사이 정수로 입력하세요.");}}
    private void error(Exception ex){new AlertDialog.Builder(this).setTitle("입력을 확인해 주세요").setMessage(ex.getMessage()==null?"입력한 값으로 저장하지 못했습니다.":ex.getMessage()).setPositiveButton("확인",null).show();}
    private void showSetup(){
        page("하루여유 시험판");text(root,(setupStep+1)+" / 3 · 처음 한 번 설정",18,PINK,true);
        if(setupStep==0){
            text(root,"월급일부터 다음 월급일 전날까지, 오늘 쓸 수 있는 돈을 확인하세요. 기존 앱과 기록은 공유하지 않습니다.",16,WHITE,false);
            EditText g=input(root,"소비주기 예산 (원)",goal,true),s=input(root,"매월 소비주기 시작일 (1~31)",startDay,true);
            text(root,"예: 27일 → 이번 달 27일부터 다음 달 26일까지. 없는 날짜는 그 달 마지막 날을 사용합니다. 고정비를 제외한 생활비 예산을 권장합니다.",14,MUTED,false);
            button(root,"다음 · 이미 쓴 금액",()->{try{long n=amount(g);long d=amount(s);if(n<=0||d<1||d>31)throw new IllegalArgumentException("예산은 1원 이상, 시작일은 1~31일입니다.");goal=Long.toString(n);startDay=Long.toString(d);setupStep=1;showSetup();}catch(Exception e){error(e);}});
        }else if(setupStep==1){
            LocalDate now=LocalDate.now(),cs=BudgetEngine.start(now,Integer.parseInt(startDay));
            text(root,"현재 소비주기: "+cs+" ~ "+BudgetEngine.end(cs,Integer.parseInt(startDay)),15,WHITE,true);
            text(root,"세 금액은 포함 관계입니다. 주기 10만원 중 이번 주 3만원, 그중 오늘 5천원이면 100000 / 30000 / 5000을 입력합니다. 합계로 더하지 않습니다.",15,MUTED,false);
            EditText t=input(root,"지금까지 소비주기 전체 사용액 (원)",total,true),w=input(root,"그중 이번 주 사용액 (월요일~오늘, 원)",week,true),d=input(root,"그중 오늘 사용액 (원)",today,true);
            text(root,"새 소비주기가 이번 주에 시작했다면 이번 주 금액에도 이번 주기 사용분만 입력하세요. 과거 문자를 읽어 오지 않으므로 현재까지 금액을 직접 맞춰 주세요.",14,MUTED,false);
            button(root,"다음 · 자동 문자 반영",()->{try{long tt=amount(t),ww=amount(w),dd=amount(d);BudgetEngine.initial(LocalDate.now(),Integer.parseInt(startDay),tt,ww,dd);total=""+tt;week=""+ww;today=""+dd;setupStep=2;showSetup();}catch(Exception e){error(e);}});
            button(root,"이전",()->{total=t.getText().toString();week=w.getText().toString();today=d.getText().toString();setupStep=0;showSetup();});
        }else{
            text(root,"문자를 휴대폰 안에서 처리합니다",20,WHITE,true);
            text(root,"동의하면 앞으로 수신하는 1588-8100 문자의 발신번호·내용·수신 정보를 확인합니다. 인식한 승인·취소는 자동 반영하며, 불확실한 문자는 확인 대기 목록에 둡니다. 금융 기록과 해당 문자 내용은 이 시험판의 휴대폰 내부 저장공간에만 보관합니다.",16,WHITE,false);
            text(root,"카드사 로그인·계좌 연결·서버 전송·알림 접근은 없습니다. Android 문자 권한은 특정 번호만 허용하는 방식이 아니므로 앱 내부에서 발신번호를 제한합니다. 동의하지 않아도 직접 입력으로 이용할 수 있습니다. 설정에서 언제든 수집을 중지할 수 있습니다.",14,MUTED,false);
            CheckBox check=new CheckBox(this);check.setText("1588-8100 문자 자동 처리에 동의합니다");check.setTextColor(WHITE);check.setChecked(consent);root.addView(check);check.setOnCheckedChangeListener((b,c)->consent=c);
            button(root,"시험판 시작",()->{try{store.setup(Long.parseLong(goal),Integer.parseInt(startDay),Long.parseLong(total),Long.parseLong(week),Long.parseLong(today),check.isChecked());showHome();SpendingWidgetProvider.updateAll(this);if(check.isChecked())requestSms();}catch(Exception e){error(e);}});
            button(root,"이전",()->{setupStep=1;showSetup();});
        }
    }
    private void showHome(){
        if(dialogOpen)return;
        BudgetEngine.Snapshot s=store.snapshot();if(s==null){showSetup();return;}
        page("하루여유 시험판");
        LinearLayout primary=card();text(primary,"오늘 사용 가능",16,MUTED,true);text(primary,money(s.todayAvailable),38,WHITE,true);
        text(primary,"오늘 순지출 "+money(s.todaySpent)+"  ·  아침 배정 "+money(s.todayBudget),14,MUTED,false);
        if(s.todaySpent>s.todayBudget)text(primary,"오늘 배정 초과 "+money(s.todaySpent-s.todayBudget)+" · 다음 날 예산에 반영됩니다",14,PINK,true);
        text(primary,"소비주기 잔액 "+money(s.remaining),19,WHITE,true);
        if(s.remaining<0)text(primary,"주기 예산을 초과했습니다. 실제 초과액도 잔액에 표시합니다.",14,PINK,false);
        text(primary,s.start+" ~ "+s.end+" · 오늘 포함 "+s.days+"일",13,MUTED,false);
        LinearLayout stats=card();text(stats,"이번 주 배정 예산 "+money(s.weekBudget),17,WHITE,true);text(stats,"이번 주 남은 예산 "+money(s.weekAvailable)+"\n이번 주 순지출 "+money(s.weekSpent),15,WHITE,false);
        text(stats,s.weekStart+" ~ "+s.weekEnd+" · 월요일 시작, 소비주기 안의 날짜만 계산",12,MUTED,false);
        if(s.weekSpent>s.weekBudget)text(stats,"이번 주 배정 초과 "+money(s.weekSpent-s.weekBudget),14,PINK,true);
        long g=store.config().goal; text(stats,"주기 순지출 "+money(s.spent)+" / 예산 "+money(g)+"\n사용률 "+String.format(Locale.KOREA,"%.1f",100.0*s.spent/g)+"%",15,MUTED,false);
        LinearLayout status=card();boolean granted=checkSelfPermission(Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED;
        text(status,"자동 반영: "+(store.config().consent?(granted?"문자 수신 대기 중":"문자 권한 필요"):"동의 꺼짐 · 직접 입력"),16,WHITE,true);
        text(status,"마지막 거래 반영: "+store.lastApplied()+"\n마지막 대상 문자: "+store.lastStatus(),12,MUTED,false);
        if(store.config().consent&&!granted)button(status,"문자 권한 허용",this::requestSms);
        if(store.pendingCount()>0)button(status,"확인할 문자 "+store.pendingCount()+"건 보기",()->{showPending=!showPending;showHome();});
        button(root,"누락 지출·취소 직접 추가",()->edit(null));
        button(root,"예산·주기·동의 설정",this::settings);
        button(root,"홈 화면 위젯 추가",()->{AppWidgetManager manager=getSystemService(AppWidgetManager.class);if(manager.isRequestPinAppWidgetSupported())manager.requestPinAppWidget(new ComponentName(this,SpendingWidgetProvider.class),null,null);else new AlertDialog.Builder(this).setMessage("홈 화면의 빈 곳을 길게 누른 뒤 위젯 → 하루여유 시험판을 선택하세요.").setPositiveButton("확인",null).show();});
        button(root,"계산 방식·설치 후 확인",this::help);
        text(root,showPending?"확인 대기 · 금액에 아직 미반영":"최근 내역 · 눌러 수정/삭제",19,WHITE,true);
        List<TrialStore.Row> rows=store.rows(showAll||showPending);int count=0;
        for(TrialStore.Row row:rows){if(showPending&&!row.status.equals("pending"))continue;count++;
            String line=row.day+"  "+(row.status.equals("pending")?"[확인 필요]":money(row.amount))+"\n"+row.label;
            Button b=button(root,line,()->edit(row));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setTextSize(14);
        }
        if(count==0)text(root,"아직 내역이 없습니다.",14,MUTED,false);
        button(root,showPending?"전체 내역으로 돌아가기":(showAll?"최근 40건만 보기":"과거 내역 모두 보기"),()->{if(showPending)showPending=false;else showAll=!showAll;showHome();});
    }
    private void requestSms(){if(checkSelfPermission(Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED)return;
        new AlertDialog.Builder(this).setTitle("새 결제 문자 자동 반영").setMessage("동의한 1588-8100 문자 자동 처리를 위해 Android 문자 수신 권한을 요청합니다. 거절해도 직접 입력은 가능합니다. 이전에 차단했다면 다음 화면의 앱 권한에서 SMS를 허용하세요.").setPositiveButton("권한 요청",(d,w)->requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS},10)).setNeutralButton("앱 권한 설정",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("나중에",null).show();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);show();SpendingWidgetProvider.updateAll(this);}
    private LinearLayout dialogBody(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(8));return box;}
    private void edit(TrialStore.Row row){
        dialogOpen=true;LinearLayout box=dialogBody();
        EditText date=input(box,"날짜 (YYYY-MM-DD)",row==null?LocalDate.now().toString():row.day.toString(),false);
        EditText value=input(box,"금액 (원, 양수)",row==null?"":Long.toString(Math.abs(row.amount)),true);
        CheckBox refund=new CheckBox(this);refund.setText("취소·부분 취소 (잔액에 돌려받기)");refund.setChecked(row!=null&&row.amount<0);box.addView(refund);
        EditText label=input(box,"메모",row==null?"직접 입력":row.label,false);
        text(box,"부분 취소는 돌려받은 금액만 입력하세요. 취소는 취소 날짜의 음수 지출로 처리합니다. 다른 소비주기 결제의 취소도 취소된 주기에 반영됩니다.",13,Color.DKGRAY,false);
        if(row!=null&&!row.raw.isEmpty()){TextView raw=text(box,"원문 (휴대폰 내부 보관)\n"+row.raw,12,Color.DKGRAY,false);raw.setTextIsSelectable(true);}
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(row==null?"누락 내역 추가":(row.status.equals("pending")?"확인 후 반영":"내역 수정")).setView(scroll).setPositiveButton("저장",null).setNegativeButton("닫기",null).create();
        if(row!=null)dialog.setButton(AlertDialog.BUTTON_NEUTRAL,"삭제",(d,w)->new AlertDialog.Builder(this).setTitle("이 내역을 제외할까요?").setMessage("금액에서 제외합니다. 같은 문자가 재전달되어도 다시 반영하지 않습니다.").setPositiveButton("삭제",(a,b)->{store.delete(row.id);SpendingWidgetProvider.updateAll(this);showHome();}).setNegativeButton("유지",null).show());
        dialog.setOnDismissListener(d->{dialogOpen=false;showHome();});dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{long n=amount(value);store.save(row==null?null:row.id,LocalDate.parse(date.getText().toString().trim()),refund.isChecked()?-n:n,label.getText().toString().trim());SpendingWidgetProvider.updateAll(this);dialog.dismiss();}catch(Exception e){error(e);}});
    }
    private void settings(){
        dialogOpen=true;TrialStore.Config c=store.config();LinearLayout box=dialogBody();
        EditText g=input(box,"소비주기 예산 (원)",""+c.goal,true),d=input(box,"매월 시작일 (1~31)",""+c.startDay,true);
        CheckBox check=new CheckBox(this);check.setText("1588-8100 문자 자동 처리 동의");check.setChecked(c.consent);box.addView(check);
        text(box,"시작일 변경 시 보관된 내역을 새 날짜 범위로 다시 계산합니다. 초기 사용액은 설정 당시 날짜의 합산 기록으로 남으므로 주기 변경 후 내역을 확인하세요. 동의를 끄면 이후 수집만 중지되며 기존 시험판 기록은 유지됩니다.",13,Color.DKGRAY,false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("시험판 설정").setView(box).setPositiveButton("저장",null).setNegativeButton("닫기",null).create();dialog.setOnDismissListener(x->{dialogOpen=false;showHome();});dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{long day=amount(d);if(day<1||day>31)throw new IllegalArgumentException("시작일은 1~31입니다.");store.settings(amount(g),(int)day,check.isChecked());SpendingWidgetProvider.updateAll(this);dialog.dismiss();if(check.isChecked())requestSms();}catch(Exception e){error(e);}});
    }
    private void help(){new AlertDialog.Builder(this).setTitle("계산과 시험판 안내").setMessage("오늘 사용 가능 = 오늘 아침 잔액 ÷ 오늘 포함 남은 날짜 − 오늘 순지출 (원 미만 버림, 최소 0, 전체 잔액 이하).\n\n10일 남고 잔액 10만원이면 오늘 1만원. 오늘 5천원을 쓰면 내일 95,000÷9=10,555원. 오늘 1만5천원을 쓰면 내일 85,000÷9=9,444원. 이월액을 별도로 더하지 않습니다.\n\n이번 주 배정 = 주 시작 때 잔액 × 이번 주기 안의 주간 날짜 수 ÷ 주 시작부터 주기 끝까지 날짜 수. 주중에는 배정을 유지하고 실제 지출을 차감합니다. 다음 주에 재배분합니다. 배정액은 의무 소비액이 아닙니다.\n\n새 소비주기에는 새 예산을 적용하며 이전 잔액을 넘기지 않습니다. 과거 기록은 보존합니다. 취소는 취소 날짜의 음수 지출입니다.\n\nSMS만 수집합니다. 앱 알림·RCS·MMS·해외 결제는 자동 처리를 보장하지 않습니다. 문자 알림 서비스가 없는 결제는 직접 추가하세요. 첫 설정 이전 문자는 확인 대기로 둡니다. 같은 원본 문자 재전달은 제외하나, 문자 자체가 새 시각으로 재발송되면 확인이 필요합니다.\n\n홈 화면을 길게 누르기 → 위젯 → 하루여유 시험판. 권한 허용 후 앱을 한 번 열어 주세요. 강제 종료 상태에서는 수신되지 않을 수 있습니다. 위젯은 문자·수정 시 즉시 요청하고 날짜 변경·정기 갱신도 사용하지만 절전 상태에서 지연될 수 있습니다. 위젯 ↻를 눌러 새로 고칠 수 있습니다.\n\n시험판은 기존 앱과 별도입니다. 기존 앱을 삭제하지 마세요. 시험판 삭제 시 시험판 기록만 사라집니다. 이 버전에는 서버 백업·내보내기가 없습니다.").setPositiveButton("확인",null).show();}
}
