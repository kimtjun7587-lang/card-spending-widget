package com.kimtjun.cardspendingwidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView summary;
    private EditText goalInput;
    private EditText baseInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        summary = findViewById(R.id.summary);
        goalInput = findViewById(R.id.goal_input);
        baseInput = findViewById(R.id.base_input);
        Button save = findViewById(R.id.save_button);
        Button access = findViewById(R.id.access_button);

        save.setOnClickListener(v -> {
            try { SpendingStore.setGoal(this, Long.parseLong(goalInput.getText().toString().replace(",", ""))); } catch(Exception ignored) {}
            try { SpendingStore.setBase(this, Long.parseLong(baseInput.getText().toString().replace(",", ""))); } catch(Exception ignored) {}
            SpendingWidgetProvider.updateAll(this);
            refresh();
        });
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private String won(long v) { return NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원"; }

    private void refresh() {
        long total = SpendingStore.total(this);
        long goal = SpendingStore.goal(this);
        long remain = Math.max(0, goal - total);
        LocalDate start = SpendingStore.periodStart(LocalDate.now());
        LocalDate end = SpendingStore.periodEnd(LocalDate.now());
        summary.setText("현재 소비주기 " + start + " ~ " + end + "\n\n" +
                "사용액  " + won(total) + "\n" +
                "목표  " + won(goal) + "\n" +
                "남은 금액  " + won(remain) + "\n" +
                "사용률  " + SpendingStore.usagePercent(this) + "%\n" +
                "이번 주 권장  " + won(SpendingStore.weeklyRecommended(this)));
        goalInput.setText(String.valueOf(goal));
        baseInput.setText(String.valueOf(SpendingStore.base(this)));
    }
}
