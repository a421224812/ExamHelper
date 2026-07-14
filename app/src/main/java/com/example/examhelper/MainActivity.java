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
    private Button btnCaptureNow;
    private Button btnOpenSettings;
    private Button btnRefreshApps;
    private Button btnAddManual;
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
        btnGrantProjection = findViewById(R.id.btnGrantProjection);
        btnCaptureNow = findViewById(R.id.btnCaptureNow);
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

        btnCaptureNow.setOnClickListener(v -> {
            if (AnswerService.hasProjection()) {
                Intent intent = new Intent(this, AnswerService.class);
                intent.setAction("com.example.examhelper.CAPTURE_NOW");
                startService(intent);
                Toast.makeText(this, "📸 正在截屏识别...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "⚠️ 请先授权截屏权限", Toast.LENGTH_SHORT).show();
            }
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
            // 静态方式传给 AnswerService（无需拿到 Service 实例）
            AnswerService.setProjection(resultCode, data);
            Toast.makeText(this, "✅ 截屏权限已获取", Toast.LENGTH_SHORT).show();
            btnCaptureNow.setEnabled(true);
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
            String hint = AnswerService.hasProjection() ? "📷" : "⚠️";
            tvStatus.setText("✅ 服务已启动 [" + hint + "] - 监听 " + count + " 个应用");
            tvStatus.setTextColor(0xFF2E7D32);
            btnCaptureNow.setEnabled(AnswerService.hasProjection());
        } else {
            tvStatus.setText("❌ 服务未启动");
            tvStatus.setTextColor(0xFFC62828);
            btnCaptureNow.setEnabled(false);
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
