package com.example.examhelper;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PROJECTION = 1001;
    private static final String SERVER_URL = "http://120.78.231.177:8765";

    private static int sResultCode = 0;
    private static Intent sResultData;

    private TextView tvStatus;
    private boolean hasOverlay = false;

    // 悬浮按钮
    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams floatParams;
    private boolean floatAdded = false;

    // 截屏
    private MediaProjectionManager mpManager;
    private MediaProjection mp;
    private static final int SS_WIDTH = 720;
    private static final int SS_HEIGHT = 1280;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mpManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        tvStatus = findViewById(R.id.tvStatus);

        findViewById(R.id.btnOpenOverlay).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });

        findViewById(R.id.btnGrantProjection).setOnClickListener(v -> {
            startActivityForResult(mpManager.createScreenCaptureIntent(), REQ_PROJECTION);
        });

        findViewById(R.id.btnStart).setOnClickListener(v -> {
            if (!hasOverlay) {
                Toast.makeText(this, "❌ 请先开启悬浮窗权限", Toast.LENGTH_SHORT).show();
                return;
            }
            if (sResultData == null) {
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
            sResultCode = resultCode;
            sResultData = data;
            checkPermissions();
            Toast.makeText(this, "✅ 截屏权限已获取", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkPermissions() {
        hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);

        findViewById(R.id.btnOpenOverlay).setEnabled(!hasOverlay);
        findViewById(R.id.btnGrantProjection).setEnabled(sResultData == null);
        findViewById(R.id.btnStart).setEnabled(hasOverlay && sResultData != null);

        StringBuilder sb = new StringBuilder();
        sb.append(hasOverlay ? "✅ 悬浮窗权限" : "❌ 悬浮窗权限");
        sb.append("   ");
        sb.append(sResultData != null ? "✅ 截屏权限" : "❌ 截屏权限");
        tvStatus.setText(sb.toString());

        if (hasOverlay && sResultData != null) {
            showFloatButton();
        } else if (!hasOverlay) {
            hideFloatButton();
        }
    }

    /** 悬浮按钮点击 → 直接截屏+识别（不走Service，避免静态变量丢失） */
    private void takeScreenshot() {
        if (sResultData == null) {
            Toast.makeText(this, "❌ 截屏权限已失效，请重新授权", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "📸 正在截屏识别...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                int density = getResources().getDisplayMetrics().densityDpi;
                ImageReader reader = ImageReader.newInstance(SS_WIDTH, SS_HEIGHT, PixelFormat.RGBA_8888, 2);
                mp = mpManager.getMediaProjection(sResultCode, sResultData);
                VirtualDisplay vd = mp.createVirtualDisplay(
                        "screenshot", SS_WIDTH, SS_HEIGHT, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.getSurface(), null, null);
                Thread.sleep(500);

                Image image = reader.acquireLatestImage();
                if (image != null) {
                    Bitmap bitmap = imageToBitmap(image);
                    image.close();
                    String base64 = bitmapToBase64(bitmap, 80);
                    bitmap.recycle();

                    // 发到服务器
                    URL url = new URL(SERVER_URL + "/api/ocr");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);

                    String json = "{\"image\":\"" + base64 + "\"}";
                    OutputStream os = conn.getOutputStream();
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        java.io.InputStream is = conn.getInputStream();
                        Scanner s = new Scanner(is, "UTF-8").useDelimiter("\\A");
                        String answer = s.hasNext() ? s.next().trim() : "";
                        is.close();
                        String finalAnswer = answer;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!finalAnswer.isEmpty()) {
                                Toast.makeText(this, "💡 " + finalAnswer, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, "⚠️ 未识别到内容", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(this, "❌ 服务器错误 " + code, Toast.LENGTH_SHORT).show());
                    }
                    conn.disconnect();
                } else {
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(this, "❌ 截屏失败（无图像）", Toast.LENGTH_SHORT).show());
                }

                reader.close();
                vd.release();
                if (mp != null) { mp.stop(); mp = null; }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(this, "❌ " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * SS_WIDTH;
        Bitmap bitmap = Bitmap.createBitmap(SS_WIDTH + rowPadding / pixelStride, SS_HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return Bitmap.createBitmap(bitmap, 0, 0, SS_WIDTH, SS_HEIGHT);
    }

    private String bitmapToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    // ====== 悬浮按钮 ======

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
                    PixelFormat.TRANSLUCENT);
            floatParams.gravity = Gravity.TOP | Gravity.START;
            floatParams.x = 50;
            floatParams.y = 300;

            floatView = LayoutInflater.from(this).inflate(R.layout.float_button, null);

            // 直接在整个悬浮视图上处理点击和拖拽
            floatView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;
                private boolean isDragging;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = floatParams.x;
                            initialY = floatParams.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            isDragging = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - initialTouchX;
                            float dy = event.getRawY() - initialTouchY;
                            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                                isDragging = true;
                                floatParams.x = (int) (initialX + dx);
                                floatParams.y = (int) (initialY + dy);
                                wm.updateViewLayout(floatView, floatParams);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!isDragging) {
                                // 短点击 → 直接调截屏
                                takeScreenshot();
                            }
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
