package com.example.examhelper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageButton;
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
                // 没授权则自动弹出授权请求
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
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

        // 启动时直接检查悬浮窗权限并显示按钮
        tryShowFloatButton();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            AnswerService.setProjection(resultCode, data);
            Toast.makeText(this, "✅ 截屏权限已获取", Toast.LENGTH_SHORT).show();
            btnCaptureNow.setEnabled(true);
            tryShowFloatButton();
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
            btnCaptureNow.setEnabled(true);
        } else {
            tvStatus.setText("❌ 服务未启动");
            tvStatus.setTextColor(0xFFC62828);
            btnCaptureNow.setEnabled(true);
        }
        // 不管无障碍服务状态，只要悬浮窗权限开了就显示按钮
        tryShowFloatButton();
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

    // 悬浮窗相关
    private WindowManager floatWindowManager;
    private View floatView;
    private WindowManager.LayoutParams floatParams;
    private boolean floatViewAdded = false;

    // 尝试显示悬浮按钮，自动跳过权限判断
    private void tryShowFloatButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            return; // 没权限就不显示
        }
        showFloatButton();
    }

    private void showFloatButton() {
        try {
            if (floatViewAdded) return;

            floatWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            int LAYOUT_FLAG = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            floatParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    LAYOUT_FLAG,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            floatParams.gravity = Gravity.TOP | Gravity.START;
            floatParams.x = 50;
            floatParams.y = 300;

            floatView = LayoutInflater.from(this).inflate(R.layout.float_button, null);
            ImageButton btn = floatView.findViewById(R.id.btnFloatCapture);
            btn.setOnClickListener(v -> {
                if (AnswerService.hasProjection()) {
                    Intent intent = new Intent(this, AnswerService.class);
                    intent.setAction("com.example.examhelper.CAPTURE_NOW");
                    startService(intent);
                    Toast.makeText(this, "📸 正在截屏识别...", Toast.LENGTH_SHORT).show();
                } else {
                    // 没授权则弹出授权请求
                    MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                    startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
                }
            });

            // 拖拽
            floatView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float touchX, touchY;
                private boolean isDragging = false;
                private long touchTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = floatParams.x;
                            initialY = floatParams.y;
                            touchX = event.getRawX();
                            touchY = event.getRawY();
                            touchTime = System.currentTimeMillis();
                            isDragging = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - touchX;
                            float dy = event.getRawY() - touchY;
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isDragging = true;
                                floatParams.x = initialX + (int) dx;
                                floatParams.y = initialY + (int) dy;
                                floatWindowManager.updateViewLayout(floatView, floatParams);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!isDragging && System.currentTimeMillis() - touchTime < 300) {
                                btn.performClick();
                            }
                            return true;
                    }
                    return false;
                }
            });

            floatWindowManager.addView(floatView, floatParams);
            floatViewAdded = true;
        } catch (Exception e) {
            Toast.makeText(this, "悬浮窗创建失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void hideFloatButton() {
        try {
            if (floatView != null && floatViewAdded) {
                floatWindowManager.removeView(floatView);
                floatViewAdded = false;
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        hideFloatButton();
        super.onDestroy();
    }
}
