package com.example.examhelper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvLastQuestion;
    private Button btnOpenSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvLastQuestion = findViewById(R.id.tvLastQuestion);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        boolean isRunning = false;
        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) {
                isRunning = true;
                break;
            }
        }

        if (isRunning) {
            tvStatus.setText("✅ 服务已启动 - 正在监考考试应用");
            tvStatus.setTextColor(0xFF2E7D32);
        } else {
            tvStatus.setText("❌ 服务未启动 - 请点击上方按钮开启");
            tvStatus.setTextColor(0xFFC62828);
        }
    }

    /** 由 AnswerService 调用，更新界面显示的题目 */
    public void onQuestionDetected(String question) {
        runOnUiThread(() -> tvLastQuestion.setText("最近识别的题目:\n" + question));
    }
}
