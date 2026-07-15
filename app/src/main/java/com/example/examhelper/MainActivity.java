package com.example.examhelper;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PROJECTION = 1001;
    private TextView tvStatus;
    private boolean hasOverlay = false;
    private boolean hasProjection = false;

    // 悬浮按钮
    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams floatParams;
    private boolean floatAdded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);

        findViewById(R.id.btnOpenOverlay).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        findViewById(R.id.btnGrantProjection).setOnClickListener(v -> {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
        });

        findViewById(R.id.btnStart).setOnClickListener(v -> {
            if (!hasOverlay) {
                Toast.makeText(this, "❌ 请先开启悬浮窗权限", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!hasProjection) {
                Toast.makeText(this, "❌ 请先授权截屏权限", Toast.LENGTH_SHORT).show();
                return;
            }
            showFloatButton();
            Toast.makeText(this, "🖤 悬浮按钮已显示", Toast.LENGTH_SHORT).show();
        });

        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROJECTION && resultCode == RESULT_OK && data != null) {
            AnswerService.setProjection(resultCode, data);
            checkPermissions();
            Toast.makeText(this, "✅ 截屏权限已获取", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkPermissions() {
        hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        hasProjection = AnswerService.hasProjection();

        findViewById(R.id.btnOpenOverlay).setEnabled(!hasOverlay);
        findViewById(R.id.btnGrantProjection).setEnabled(!hasProjection);
        findViewById(R.id.btnStart).setEnabled(hasOverlay && hasProjection);

        StringBuilder sb = new StringBuilder();
        sb.append(hasOverlay ? "✅ 悬浮窗权限" : "❌ 悬浮窗权限");
        sb.append("   ");
        sb.append(hasProjection ? "✅ 截屏权限" : "❌ 截屏权限");
        tvStatus.setText(sb.toString());

        // 权限都有了 → 自动显示悬浮按钮
        if (hasOverlay && hasProjection) {
            showFloatButton();
        } else if (!hasOverlay) {
            hideFloatButton();
        }
    }

    private void showFloatButton() {
        if (floatAdded) return;
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            floatParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.TRANSLUCENT);
            floatParams.gravity = Gravity.TOP | Gravity.START;
            floatParams.x = 50;
            floatParams.y = 300;

            floatView = LayoutInflater.from(this).inflate(R.layout.float_button, null);
            ImageButton btn = floatView.findViewById(R.id.btnFloatCapture);
            btn.setOnClickListener(v -> {
                if (AnswerService.hasProjection()) {
                    Intent intent = new Intent(this, AnswerService.class);
                    intent.setAction("com.example.examhelper.CAPTURE_NOW");
                    startForegroundService(intent);
                    Toast.makeText(this, "📸 正在截屏识别...", Toast.LENGTH_SHORT).show();
                }
            });

            // 拖拽
            floatView.setOnTouchListener(new View.OnTouchListener() {
                private int lastX, lastY;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastX = (int) event.getRawX();
                            lastY = (int) event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            int dx = (int) event.getRawX() - lastX;
                            int dy = (int) event.getRawY() - lastY;
                            floatParams.x += dx;
                            floatParams.y += dy;
                            lastX = (int) event.getRawX();
                            lastY = (int) event.getRawY();
                            wm.updateViewLayout(floatView, floatParams);
                            return true;
                    }
                    return false;
                }
            });

            wm.addView(floatView, floatParams);
            floatAdded = true;
        } catch (Exception e) {
            Toast.makeText(this, "悬浮窗失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void hideFloatButton() {
        if (floatView != null && floatAdded) {
            try { wm.removeView(floatView); } catch (Exception ignored) {}
            floatAdded = false;
        }
    }

    @Override
    protected void onDestroy() {
        hideFloatButton();
        super.onDestroy();
    }
}
