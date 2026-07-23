package com.example.examhelper;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.app.NotificationCompat;

public class AnswerService extends AccessibilityService {

    private static final String TARGET_PACKAGE = "com.qny.qnex";
    private MonitorPrefs monitorPrefs;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            monitorPrefs = new MonitorPrefs(this);
            monitorPrefs.enableIfNot(TARGET_PACKAGE);
        } catch (Exception ignored) {}
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        startForegroundService();
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
                .setContentTitle("考试助手")
                .setContentText("后台运行中")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
        startForeground(1001, notification);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
