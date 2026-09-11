package com.kimtjun.cardspendingwidget;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_SMS = 1001;
    private TextView summary;
    private TextView diagnostics;
    private EditText goalInput;
    private EditText baseInput;
    private EditText weekBaseInput;
    private EditText todayBaseInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        summary = findViewById(R.id.summary);
        diagnostics = findViewById(R.id.diagnostics);
        goalInput = findViewById(R.id.goal_input);
        baseInput = findViewById(R.id.base_input);
        weekBaseInput = findViewById(R.id.week_base_input);
        todayBaseInput = findViewById(R.id.today_base_input);
        Button save = findViewById(R.id.save_button);
        Button access = findViewById(R.id.access_button);

        save.setOnClickListener(v -> {
            try { SpendingStore.setGoal(this, parse(goalInput)); } catch(Exception ignored) {}
            try { SpendingStore.setBase(this, parse(baseInput)); } catch(Exception ignored) {}
            try { SpendingStore.setWeekBase(this, parse(weekBaseInput)); } catch(Exception ignored) {}
            try { SpendingStore.setTodayBase(this, parse(todayBaseInput)); } catch(Exception ignored) {}
            SpendingWidgetProvider.updateAll(this);
            refresh();
        });
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, REQ_SMS);
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private static long parse(EditText input) {
        return Long.parseLong(input.getText().toString().replace(",", "").trim());
    }

    private String won(long v) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원";
    }

    private void refresh() {
        SpendingStore.ensureCurrentPeriod(this);
        LocalDate today = LocalDate.now();
        long total = SpendingStore.total(this);
        long goal = SpendingStore.goal(this);
        long weekSpent = SpendingStore.weekSpent(this);
        long todaySpent = SpendingStore.todaySpent(this);
        long remain = Math.max(0L, goal - total);
        String weeklyLeft = SpendingStore.weeklyOver(this) ? "0원 (이번 주 예산 초과)" : won(SpendingStore.weeklyRemaining(this));

        summary.setText(
                SpendingStore.periodMonthLabel(today) + "   " + SpendingStore.periodRangeLabel(today) + "\n\n" +
                "사용액  " + won(total) + "\n" +
                "목표 금액  " + won(goal) + "\n" +
                "남은 금액  " + won(remain) + "\n" +
                "사용률  " + SpendingStore.usagePercent(this) + "%\n\n" +
                "이번 주 필요 소비 금액  " + won(SpendingStore.weeklyPlan(this)) + "\n" +
                "이번 주 남은 소비 금액  " + weeklyLeft + "\n" +
                "오늘 사용 가능 소비 금액  " + won(SpendingStore.todayAvailable(this)) + "\n" +
                "오늘 소비한 금액  " + won(todaySpent) + "\n" +
                "마감일 26일 | " + SpendingStore.daysUntilClose(today) + "일 남음");

        goalInput.setText(String.valueOf(goal));
        baseInput.setText(String.valueOf(total));
        weekBaseInput.setText(String.valueOf(weekSpent));
        todayBaseInput.setText(String.valueOf(todaySpent));

        String smsPermission = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                ? "SMS 권한: 허용" : "SMS 권한: 꺼짐";
        diagnostics.setText(smsPermission + "\n" + IngestionDiagnostics.summary(this));
    }
}
