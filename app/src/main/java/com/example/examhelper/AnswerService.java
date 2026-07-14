package com.example.examhelper;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class AnswerService extends AccessibilityService {

    private MonitorPrefs monitorPrefs;
    private String myPackageName;
    private static final String TARGET_PACKAGE = "com.qny.qnex";
    private static final String SERVER_URL = "http://120.78.231.177:8765";

    // 截屏相关（静态共享）
    private static final int SCREENSHOT_WIDTH = 720;
    private static final int SCREENSHOT_HEIGHT = 1280;
    private MediaProjectionManager mpManager;
    private MediaProjection mp;
    private static int sResultCode = 0;
    private static Intent sResultData;

    private static final int NOTIFICATION_ID = 1001;

    // 轮询
    private Handler pollHandler;
    private static final long POLL_INTERVAL = 3000;
    private boolean isTargetForeground = false;
    private String lastScreenshotResult = "";

    // 悬浮按钮
    private WindowManager wm;
    private View floatView;
    private WindowManager.LayoutParams floatParams;
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            monitorPrefs = new MonitorPrefs(this);
            myPackageName = getPackageName();
            monitorPrefs.enableIfNot(TARGET_PACKAGE);
            mpManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

            startForegroundService();
            showFloatingButton();
            startPolling();
        } catch (Exception e) {
            try { startForegroundService(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        stopPolling();
        hideFloatingButton();
        super.onDestroy();
    }

    private void startForegroundService() {
        String channelId = "exam_helper_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "考试助手", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("考试助手 🖤")
                .setContentText("悬浮按钮已显示，点击可手动截屏")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    // ===== 悬浮按钮 =====
    private void showFloatingButton() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        floatParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        floatParams.gravity = Gravity.TOP | Gravity.START;
        floatParams.x = 0;
        floatParams.y = 200;

        floatView = LayoutInflater.from(this).inflate(R.layout.float_button, null);
        ImageButton btnFloat = floatView.findViewById(R.id.btnFloatCapture);
        btnFloat.setOnClickListener(v -> takeScreenshotAndSend());

        floatView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = floatParams.x;
                    initialY = floatParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    floatParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                    floatParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                    wm.updateViewLayout(floatView, floatParams);
                    return true;
            }
            return false;
        });

        try {
            wm.addView(floatView, floatParams);
        } catch (Exception e) {
            showToast("❌ 悬浮窗权限未开启，请在设置中允许");
        }
    }

    private void hideFloatingButton() {
        if (floatView != null && wm != null) {
            try {
                wm.removeView(floatView);
            } catch (Exception ignored) {}
            floatView = null;
        }
    }

    public static void setProjection(int code, Intent data) {
        sResultCode = code;
        sResultData = data;
    }

    public static boolean hasProjection() {
        return sResultData != null;
    }

    public void triggerScreenshot() {
        takeScreenshotAndSend();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "com.example.examhelper.CAPTURE_NOW".equals(intent.getAction())) {
            takeScreenshotAndSend();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
            isTargetForeground = pkg.contains(TARGET_PACKAGE) || pkg.contains("qny");
        }
    }

    @Override
    public void onInterrupt() {
    }

    // ===== 轮询 =====
    private void startPolling() {
        pollHandler = new Handler(getMainLooper());
        pollRunnable.run();
    }

    private void stopPolling() {
        if (pollHandler != null) {
            pollHandler.removeCallbacksAndMessages(null);
            pollHandler = null;
        }
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTargetForeground && sResultData != null) {
                takeScreenshotAndSend();
            }
            if (pollHandler != null) {
                pollHandler.postDelayed(this, POLL_INTERVAL);
            }
        }
    };

    // ===== 截屏 =====
    private void takeScreenshotAndSend() {
        if (sResultData == null) {
            showToast("❌ 未获取截屏权限，请在主页面授权");
            return;
        }

        new Thread(() -> {
            try {
                if (mp != null) {
                    mp.stop();
                    mp = null;
                }

                int density = getResources().getDisplayMetrics().densityDpi;

                ImageReader reader = ImageReader.newInstance(
                        SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT,
                        PixelFormat.RGBA_8888, 2);

                mp = mpManager.getMediaProjection(sResultCode, sResultData);

                VirtualDisplay vd = mp.createVirtualDisplay(
                        "examhelper_screenshot",
                        SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader.getSurface(), null, null);

                Thread.sleep(500);

                Image image = reader.acquireLatestImage();
                if (image != null) {
                    Bitmap bitmap = imageToBitmap(image);
                    image.close();
                    String base64 = bitmapToBase64(bitmap, 80);
                    bitmap.recycle();

                    String hash = base64.length() > 100 ? base64.substring(0, 100) : base64;
                    if (!hash.equals(lastScreenshotResult)) {
                        lastScreenshotResult = hash;
                        sendScreenshotToServer(base64);
                    }
                }

                reader.close();
                vd.release();
                mp.stop();
                mp = null;

            } catch (Exception e) {
                // 静默
            }
        }).start();
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
            }
            conn.disconnect();
        } catch (Exception e) {
            // 静默
        }
    }

    private void showToast(final String msg) {
        new Handler(getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
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
        return Bitmap.createBitmap(bitmap, 0, 0, SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT);
    }

    private String bitmapToBase64(Bitmap bitmap, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }
}
