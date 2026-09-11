package com.kimtjun.cardspendingwidget;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.time.*;
import java.util.*;

/** Private per-application SQLite database; no legacy preferences or external storage. */
public final class TrialStore extends SQLiteOpenHelper {
    private static TrialStore instance;
    public static synchronized TrialStore get(Context c){if(instance==null)instance=new TrialStore(c.getApplicationContext());return instance;}
    private TrialStore(Context c){super(c,"spendable_trial_v1.db",null,1);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE config (id INTEGER PRIMARY KEY CHECK(id=1), goal INTEGER NOT NULL, start_day INTEGER NOT NULL, consent INTEGER NOT NULL, setup_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, amount INTEGER NOT NULL, label TEXT NOT NULL, raw TEXT NOT NULL DEFAULT '', status TEXT NOT NULL, source_key TEXT UNIQUE, received_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX ledger_day ON ledger(day,status)");
        db.execSQL("CREATE TABLE status (id INTEGER PRIMARY KEY CHECK(id=1), last_sms INTEGER NOT NULL, note TEXT NOT NULL)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Unsupported schema migration");}
    public static final class Config {public long goal,setupAt;public int startDay;public boolean consent;}
    public synchronized Config config(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT goal,start_day,consent,setup_at FROM config WHERE id=1",null)){
            if(!c.moveToFirst())return null;Config v=new Config();v.goal=c.getLong(0);v.startDay=c.getInt(1);v.consent=c.getInt(2)==1;v.setupAt=c.getLong(3);return v;
        }
    }
    public synchronized void setup(long goal,int startDay,long total,long week,long today,boolean consent){
        if(config()!=null)throw new IllegalArgumentException("이미 설정되었습니다.");
        if(goal<=0||goal>BudgetEngine.MAX_AMOUNT)throw new IllegalArgumentException("예산을 확인하세요.");
        List<BudgetEngine.Entry> initial=BudgetEngine.initial(LocalDate.now(),startDay,total,week,today);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{ContentValues c=new ContentValues();c.put("id",1);c.put("goal",goal);c.put("start_day",startDay);c.put("consent",consent?1:0);c.put("setup_at",System.currentTimeMillis());db.insertOrThrow("config",null,c);
            for(BudgetEngine.Entry e:initial)insert(db,e.day,e.amount,"초기 사용액 (합산 기록)","","active",null,System.currentTimeMillis());
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void settings(long goal,int day,boolean consent){
        if(goal<=0||goal>BudgetEngine.MAX_AMOUNT||day<1||day>31)throw new IllegalArgumentException("예산·시작일을 확인하세요.");
        ContentValues v=new ContentValues();v.put("goal",goal);v.put("start_day",day);v.put("consent",consent?1:0);getWritableDatabase().update("config",v,"id=1",null);
    }
    public static final class Row {public long id,amount,receivedAt;public LocalDate day;public String label,raw,status;}
    public synchronized List<Row> rows(boolean all){
        List<Row> rows=new ArrayList<>();
        String sql="SELECT id,day,amount,label,raw,status,received_at FROM ledger WHERE status != 'deleted' ORDER BY day DESC,id DESC"+(all?"":" LIMIT 40");
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext()){Row r=new Row();r.id=c.getLong(0);r.day=LocalDate.parse(c.getString(1));r.amount=c.getLong(2);r.label=c.getString(3);r.raw=c.getString(4);r.status=c.getString(5);r.receivedAt=c.getLong(6);rows.add(r);}}return rows;
    }
    public synchronized BudgetEngine.Snapshot snapshot(){
        Config c=config();if(c==null)return null;
        List<BudgetEngine.Entry> entries=new ArrayList<>();
        // Only the current cycle is queried; historical and deleted records are retained.
        LocalDate today=LocalDate.now(),start=BudgetEngine.start(today,c.startDay);
        try(Cursor cur=getReadableDatabase().rawQuery("SELECT day,amount FROM ledger WHERE status='active' AND day>=? AND day<=?",new String[]{start.toString(),today.toString()})){
            while(cur.moveToNext())entries.add(new BudgetEngine.Entry(LocalDate.parse(cur.getString(0)),cur.getLong(1)));
        }
        return BudgetEngine.calculate(today,c.startDay,c.goal,entries);
    }
    private long insert(SQLiteDatabase db,LocalDate day,long amount,String label,String raw,String state,String key,long received){
        ContentValues v=new ContentValues();v.put("day",day.toString());v.put("amount",amount);v.put("label",label);v.put("raw",raw);v.put("status",state);if(key!=null)v.put("source_key",key);v.put("received_at",received);v.put("updated_at",System.currentTimeMillis());
        return db.insertWithOnConflict("ledger",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    public synchronized void receive(String key,String body,long timestamp){
        Config cfg=config();if(cfg==null||!cfg.consent)return;
        long now=System.currentTimeMillis();LocalDate day=Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
        LotteSmsParser.Result parsed=LotteSmsParser.parse(body);
        boolean dateOk=timestamp>=cfg.setupAt&&timestamp<=now+300000;
        boolean accepted=parsed.accepted&&dateOk;
        String why=!dateOk?"설정 이전·미래 시각 문자: 초기 사용액과 중복인지 확인":parsed.reason;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            long id=insert(db,day,parsed.amount,why,body.length()>8000?body.substring(0,8000):body,accepted?"active":"pending",key,now);
            if(id!=-1){ContentValues v=new ContentValues();v.put("id",1);v.put("last_sms",now);v.put("note",accepted?"문자 자동 반영":"확인할 문자 있음");db.insertWithOnConflict("status",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void save(Long id,LocalDate day,long amount,String label){
        if(day.isAfter(LocalDate.now())||day.isBefore(LocalDate.of(2000,1,1)))throw new IllegalArgumentException("날짜는 2000년부터 오늘까지 입력하세요.");
        if(amount==0||amount>BudgetEngine.MAX_AMOUNT||amount< -BudgetEngine.MAX_AMOUNT)throw new IllegalArgumentException("금액을 확인하세요.");
        if(label.length()>100)throw new IllegalArgumentException("메모는 100자 이내로 입력하세요.");
        SQLiteDatabase db=getWritableDatabase();
        if(id==null){if(insert(db,day,amount,label,"","active",null,System.currentTimeMillis())==-1)throw new IllegalStateException("저장 실패");}
        else{ContentValues v=new ContentValues();v.put("day",day.toString());v.put("amount",amount);v.put("label",label);v.put("status","active");v.put("updated_at",System.currentTimeMillis());db.update("ledger",v,"id=?",new String[]{id.toString()});}
    }
    public synchronized void delete(long id){ContentValues v=new ContentValues();v.put("status","deleted");v.put("raw","");v.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("ledger",v,"id=?",new String[]{Long.toString(id)});}
    public synchronized int pendingCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM ledger WHERE status='pending'",null)){c.moveToFirst();return c.getInt(0);}}
    public synchronized String lastStatus(){try(Cursor c=getReadableDatabase().rawQuery("SELECT last_sms,note FROM status WHERE id=1",null)){if(!c.moveToFirst())return "문자 수신 기록 없음";return time(c.getLong(0))+" · "+c.getString(1);}}
    public synchronized String lastApplied(){try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(updated_at) FROM ledger WHERE status='active'",null)){c.moveToFirst();return c.isNull(0)?"반영 내역 없음":time(c.getLong(0));}}
    public static String time(long t){return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM.dd HH:mm:ss"));}
}
