package com.example.examhelper;

import android.app.Activity;
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

    private int mResultCode;
    private Intent mResultData;

    // 悬浮按钮
    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams floatParams;

    // 截屏
    private MediaProjectionManager mpManager;
    private static final int SS_W = 720;
    private static final int SS_H = 1280;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mpManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        TextView tvStatus = findViewById(R.id.tvStatus);

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "❌ 请先授权悬浮窗权限", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mResultData == null) {
                Toast.makeText(this, "❌ 请先授权截屏权限", Toast.LENGTH_SHORT).show();
                return;
            }
            showFloatButton();
            Toast.makeText(this, "🖤 悬浮按钮已显示", Toast.LENGTH_SHORT).show();
        });

        // 更新状态
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
        boolean proj = mResultData != null;

        TextView tv = findViewById(R.id.tvStatus);
        StringBuilder sb = new StringBuilder();
        sb.append(overlay ? "✅ 悬浮窗权限" : "❌ 悬浮窗权限");
        sb.append("   ");
        sb.append(proj ? "✅ 截屏权限" : "❌ 截屏权限");
        tv.setText(sb.toString());

        findViewById(R.id.btnOpenOverlay).setEnabled(!overlay);
        findViewById(R.id.btnGrantProjection).setEnabled(!proj);
        findViewById(R.id.btnStart).setEnabled(overlay && proj);

        if (overlay && proj && floatView == null) {
            showFloatButton();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PROJECTION && resultCode == RESULT_OK && data != null) {
            mResultCode = resultCode;
            mResultData = data;
            updateStatus();
            Toast.makeText(this, "✅ 截屏权限已获取", Toast.LENGTH_SHORT).show();
        }
    }

    // ========== 悬浮按钮 ==========

    private void showFloatButton() {
        if (floatView != null) return;
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            floatParams = new WindowManager.LayoutParams(
                    160, 160, flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            floatParams.gravity = Gravity.TOP | Gravity.START;
            floatParams.x = 50;
            floatParams.y = 300;

            floatView = LayoutInflater.from(this).inflate(R.layout.float_button, null);

            floatView.setOnTouchListener(new View.OnTouchListener() {
                private int startX, startY;
                private float startTouchX, startTouchY;
                private boolean dragging;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = floatParams.x;
                            startY = floatParams.y;
                            startTouchX = event.getRawX();
                            startTouchY = event.getRawY();
                            dragging = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - startTouchX;
                            float dy = event.getRawY() - startTouchY;
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                dragging = true;
                                floatParams.x = startX + (int) dx;
                                floatParams.y = startY + (int) dy;
                                wm.updateViewLayout(floatView, floatParams);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (!dragging) {
                                // 点击
                                new Handler(Looper.getMainLooper()).post(() ->
                                    Toast.makeText(MainActivity.this, "📸 截图中...", Toast.LENGTH_SHORT).show());
                                takeScreenshot();
                            }
                            return true;
                    }
                    return false;
                }
            });

            wm.addView(floatView, floatParams);
        } catch (Exception e) {
            Toast.makeText(this, "悬浮窗失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ========== 截屏 ==========

    private void takeScreenshot() {
        if (mResultData == null) {
            Toast.makeText(this, "❌ 截屏权限失效，请重新授权", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            MediaProjection mp = null;
            ImageReader reader = null;
            VirtualDisplay vd = null;
            try {
                int density = getResources().getDisplayMetrics().densityDpi;
                reader = ImageReader.newInstance(SS_W, SS_H, PixelFormat.RGBA_8888, 2);
                mp = mpManager.getMediaProjection(mResultCode, mResultData);
                vd = mp.createVirtualDisplay("shot", SS_W, SS_H, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.getSurface(), null, null);
                Thread.sleep(500);

                Image image = reader.acquireLatestImage();
                if (image == null) {
                    showToast("❌ 截屏失败（无图像）");
                    return;
                }
                Bitmap bitmap = imageToBitmap(image);
                image.close();
                String base64 = bitmapToBase64(bitmap, 80);
                bitmap.recycle();

                // 发送 OCR
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
                    if (!answer.isEmpty()) {
                        showToast("💡 " + answer);
                    } else {
                        showToast("⚠️ 未识别到内容");
                    }
                } else {
                    showToast("❌ 服务器错误 " + code);
                }
                conn.disconnect();

            } catch (Exception e) {
                showToast("❌ " + e.getMessage());
            } finally {
                if (vd != null) try { vd.release(); } catch (Exception ignored) {}
                if (reader != null) try { reader.close(); } catch (Exception ignored) {}
                if (mp != null) try { mp.stop(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int ps = planes[0].getPixelStride();
        int rs = planes[0].getRowStride();
        int pad = rs - ps * SS_W;
        Bitmap bmp = Bitmap.createBitmap(SS_W + pad / ps, SS_H, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        return Bitmap.createBitmap(bmp, 0, 0, SS_W, SS_H);
    }

    private String bitmapToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private void showToast(final String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        if (floatView != null && wm != null) {
            try { wm.removeView(floatView); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
