package com.example.examhelper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_MEDIA_PROJECTION = 1001;

    private TextView tvStatus;
    private Button btnGrantProjection;
    private Button btnOpenSettings;
    private Button btnRefreshApps;
    private Button btnAddManual;
    private RecyclerView rvApps;
    private AppListAdapter adapter;
    private List<AppInfo> appList;
    private MonitorPrefs monitorPrefs;
    private String myPackageName;
    private AnswerService answerService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnGrantProjection = findViewById(R.id.btnGrantProjection);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        btnRefreshApps = findViewById(R.id.btnRefreshApps);
        btnAddManual = findViewById(R.id.btnAddManual);
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

        btnGrantProjection.setOnClickListener(v -> {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        });

        btnRefreshApps.setOnClickListener(v -> loadInstalledApps());

        btnAddManual.setOnClickListener(v -> {
            String pkg = "com.qny.qnex";
            getSharedPreferences("monitor_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("monitor_" + pkg, true)
                    .apply();
            loadInstalledApps();
            tvStatus.setText("✅ 已添加: " + pkg);
            tvStatus.setTextColor(0xFF2E7D32);
        });

        loadInstalledApps();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            // 把录屏权限传给 AnswerService
            AnswerService service = getAnswerService();
            if (service != null) {
                service.setProjection(resultCode, data);
            }
            // 保存以便服务重启后使用
            getSharedPreferences("exam_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("projection_result_code", resultCode)
                    .putString("projection_data_action", data.getAction())
                    .apply();
            Toast.makeText(this, "✅ 截屏权限已获取", Toast.LENGTH_SHORT).show();
        }
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
            int count = monitorPrefs.getMonitoredPackages().size();
            String hint = hasProjectionPermission() ? "📷" : "⚠️ 未授权截屏";
            tvStatus.setText("✅ 服务已启动 [" + hint + "] - 监听 " + count + " 个应用");
            tvStatus.setTextColor(0xFF2E7D32);
        } else {
            tvStatus.setText("❌ 服务未启动");
            tvStatus.setTextColor(0xFFC62828);
        }
    }

    private boolean hasProjectionPermission() {
        return getSharedPreferences("exam_prefs", MODE_PRIVATE).contains("projection_result_code");
    }

    private AnswerService getAnswerService() {
        if (answerService != null) return answerService;
        // 尝试从已绑定的无障碍服务获取
        android.accessibilityservice.AccessibilityServiceInfo info = null;
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo si : services) {
            if (si.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) {
                // AnswerService 实例无法直接从 info 获取，用全局引用
                break;
            }
        }
        return null;
    }

    /** AnswerService 启动时调用此方法注册自身 */
    public void registerAnswerService(AnswerService service) {
        this.answerService = service;
        // 恢复之前保存的投影权限
        if (hasProjectionPermission()) {
            int code = getSharedPreferences("exam_prefs", MODE_PRIVATE).getInt("projection_result_code", 0);
            // data 无法序列化保存，需要重新请求
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

    /** 由 AnswerService 调用，更新界面显示的题目 */
    public void onQuestionDetected(String question) {
        // 界面已简化，此回调暂不更新UI
    }
}
