package com.example.examhelper;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.util.Base64;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class AnswerService extends AccessibilityService {

    private static final String SERVER_URL = "http://120.78.231.177:8765"; // 小黑服务器公网
    private long lastEventTime = 0;
    private static final long DEBOUNCE_MS = 2000;
    private MonitorPrefs monitorPrefs;
    private String myPackageName;
    private static final String TARGET_PACKAGE = "com.qny.qnex";

    // 截屏相关（静态，Activity 和 Service 共享）
    private static final int SCREENSHOT_WIDTH = 720;
    private static final int SCREENSHOT_HEIGHT = 1280;
    private MediaProjectionManager mpManager;
    private MediaProjection mp;
    private static int sResultCode = 0;
    private static Intent sResultData;

    // 轮询相关（无脑每3秒截屏一次）
    private Handler pollingHandler;
    private Runnable pollingTask;
    private static final long POLL_INTERVAL_MS = 3000;

    // 前台服务通知 ID
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        monitorPrefs = new MonitorPrefs(this);
        myPackageName = getPackageName();
        monitorPrefs.enableIfNot(TARGET_PACKAGE);
        mpManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // 启动无脑轮询截屏
        pollingHandler = new Handler(getMainLooper());
        pollingTask = new Runnable() {
            @Override
            public void run() {
                takeScreenshotAndSend();
                pollingHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        // 启动前台服务（Android 14 需要）
        String channelId = "exam_helper_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "考试助手", android.app.NotificationManager.IMPORTANCE_LOW);
            ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
        android.app.Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("考试助手")
                .setContentText("正在运行中...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        pollingHandler.postDelayed(pollingTask, POLL_INTERVAL_MS * 2); // 延迟6秒启动，给系统准备时间
    }

    /** 由 MainActivity 在获取到录屏权限后调用 */
    public static void setProjection(int code, Intent data) {
        sResultCode = code;
        sResultData = data;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不再依赖无障碍事件，改用轮询
    }

    private void takeScreenshotAndSend() {
        if (sResultData == null) {
            showToast("❌ 未获取截屏权限");
            return;
        }
        showToast("🔍 正在识别题目...");

        new Thread(() -> {
            try {
                // 释放旧投影
                if (mp != null) {
                    mp.stop();
                    mp = null;
                }

                int density = getResources().getDisplayMetrics().densityDpi;

                // 创建 ImageReader，格式 JPEG 以节省带宽
                ImageReader reader = ImageReader.newInstance(
                        SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT,
                        PixelFormat.RGBA_8888, 2);

                mp = mpManager.getMediaProjection(sResultCode, sResultData);
                VirtualDisplay vd = mp.createVirtualDisplay(
                        "examhelper_screenshot",
                        SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.getSurface(), null, null);

                // 等待一帧
                Thread.sleep(500);

                Image image = reader.acquireLatestImage();
                if (image != null) {
                    Bitmap bitmap = imageToBitmap(image);
                    image.close();
                    String base64 = bitmapToBase64(bitmap, 80);
                    bitmap.recycle();
                    sendScreenshotToServer(base64);
                } else {
                    showToast("❌ 截屏失败: 无法获取画面");
                }

                reader.close();
                vd.release();
                mp.stop();
                mp = null;

            } catch (Exception e) {
                showToast("❌ 截屏失败: " + e.getMessage());
            }
        }).start();
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * SCREENSHOT_WIDTH;

        Bitmap bitmap = Bitmap.createBitmap(
                SCREENSHOT_WIDTH + rowPadding / pixelStride,
                SCREENSHOT_HEIGHT, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        // 裁剪去掉填充
        return Bitmap.createBitmap(bitmap, 0, 0, SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT);
    }

    private String bitmapToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private void sendScreenshotToServer(String base64Image) {
        try {
            URL url = new URL(SERVER_URL + "/api/ocr");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            String json = "{\"image\":\"" + base64Image + "\"}";
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
                }
            } else {
                showToast("❌ 服务器返回: " + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            showToast("❌ 请求失败: " + e.getMessage());
        }
    }

    private void showToast(final String msg) {
        new Handler(getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onInterrupt() {
        if (mp != null) mp.stop();
    }
}
