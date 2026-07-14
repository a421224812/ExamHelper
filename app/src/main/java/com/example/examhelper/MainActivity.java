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
import android.widget.EditText;
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
    private Button btnAddManual;
    private EditText etSearchPackage;
    private RecyclerView rvApps;
    private AppListAdapter adapter;
    private List<AppInfo> appList;
    private MonitorPrefs monitorPrefs;
    private String myPackageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvLastQuestion = findViewById(R.id.tvLastQuestion);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        btnRefreshApps = findViewById(R.id.btnRefreshApps);
        btnAddManual = findViewById(R.id.btnAddManual);
        etSearchPackage = findViewById(R.id.etSearchPackage);
        rvApps = findViewById(R.id.rvApps);

        monitorPrefs = new MonitorPrefs(this);
        myPackageName = getPackageName();
        appList = new ArrayList<>();
        adapter = new AppListAdapter(appList, getSharedPreferences("monitor_prefs", MODE_PRIVATE));

        rvApps.setLayoutManager(new LinearLayoutManager(this));
        rvApps.setAdapter(adapter);

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnRefreshApps.setOnClickListener(v -> loadInstalledApps());

        btnAddManual.setOnClickListener(v -> {
            String pkg = etSearchPackage.getText().toString().trim();
            if (pkg.isEmpty()) {
                pkg = "com.qny.qnex";
            }
            // 直接存入偏好设置，勾选上
            getSharedPreferences("monitor_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("monitor_" + pkg, true)
                    .apply();
            // 刷新列表并滚动到该项
            loadInstalledApps();
            String finalPkg = pkg;
            for (int i = 0; i < appList.size(); i++) {
                if (appList.get(i).packageName.equals(finalPkg)) {
                    rvApps.scrollToPosition(i);
                    break;
                }
            }
            tvStatus.setText("✅ 已添加: " + pkg);
            tvStatus.setTextColor(0xFF2E7D32);
        });

        // 搜索过滤
        etSearchPackage.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                filterApps(s.toString());
            }
        });

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
        List<android.content.pm.ApplicationInfo> packages = pm.getInstalledApplications(0);
        java.util.Set<String> foundPkgs = new java.util.HashSet<>();

        for (android.content.pm.ApplicationInfo ai : packages) {
            String packageName = ai.packageName;
            foundPkgs.add(packageName);
            if (packageName.startsWith("android.") || packageName.startsWith("com.android.")
                    || packageName.equals("com.android.shell")
                    || packageName.equals(myPackageName)) {
                continue;
            }
            String appName = pm.getApplicationLabel(ai).toString();
            Drawable icon = ai.loadIcon(pm);
            AppInfo info = new AppInfo(packageName, appName, icon);
            info.checked = monitorPrefs.isMonitored(packageName);
            appList.add(info);
        }

        // 添加已勾选但列表里没出现的 App（比如被系统过滤掉的）
        for (String monitoredPkg : monitorPrefs.getMonitoredPackages()) {
            if (!foundPkgs.contains(monitoredPkg)) {
                AppInfo info = new AppInfo(monitoredPkg, monitoredPkg, null);
                info.checked = true;
                appList.add(info);
                foundPkgs.add(monitoredPkg);
            }
        }

        Collections.sort(appList, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
        adapter.notifyDataSetChanged();
    }

    private void filterApps(String query) {
        if (query.isEmpty()) {
            // 显示完整列表
            for (AppInfo app : appList) {
                app.checked = monitorPrefs.isMonitored(app.packageName);
            }
            adapter.notifyDataSetChanged();
            return;
        }
        // 搜索过滤逻辑由 adapter 处理或直接外部过滤
        // 简单实现：只是用于提示用户点击"手动添加"按钮
    }

    /** 由 AnswerService 调用，更新界面显示的题目 */
    public void onQuestionDetected(String question) {
        runOnUiThread(() -> tvLastQuestion.setText("最近识别的题目:\n" + question));
    }
}
