package com.example.examhelper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvLastQuestion;
    private Button btnOpenSettings;
    private Button btnRefreshApps;
    private RecyclerView rvApps;
    private AppListAdapter adapter;
    private List<AppInfo> appList;
    private MonitorPrefs monitorPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvLastQuestion = findViewById(R.id.tvLastQuestion);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        btnRefreshApps = findViewById(R.id.btnRefreshApps);
        rvApps = findViewById(R.id.rvApps);

        monitorPrefs = new MonitorPrefs(this);
        appList = new ArrayList<>();
        adapter = new AppListAdapter(appList, getSharedPreferences("monitor_prefs", MODE_PRIVATE));

        rvApps.setLayoutManager(new LinearLayoutManager(this));
        rvApps.setAdapter(adapter);

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnRefreshApps.setOnClickListener(v -> loadInstalledApps());

        loadInstalledApps();
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
            // 显示当前监听的 App 数量
            int count = monitorPrefs.getMonitoredPackages().size();
            tvStatus.setText("✅ 服务已启动 - 正在监听 " + count + " 个应用");
            tvStatus.setTextColor(0xFF2E7D32);
        } else {
            tvStatus.setText("❌ 服务未启动 - 请点击上方按钮开启");
            tvStatus.setTextColor(0xFFC62828);
        }
    }

    private void loadInstalledApps() {
        appList.clear();

        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo ri : activities) {
            String packageName = ri.activityInfo.packageName;
            String appName = ri.loadLabel(pm).toString();
            Drawable icon = ri.loadIcon(pm);
            AppInfo info = new AppInfo(packageName, appName, icon);
            // 恢复已保存的选中状态
            info.checked = monitorPrefs.isMonitored(packageName);
            appList.add(info);
        }

        // 按 App 名称排序
        Collections.sort(appList, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
        adapter.notifyDataSetChanged();
    }

    /** 由 AnswerService 调用，更新界面显示的题目 */
    public void onQuestionDetected(String question) {
        runOnUiThread(() -> tvLastQuestion.setText("最近识别的题目:\n" + question));
    }
}
