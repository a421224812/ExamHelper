package com.example.examhelper;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class AnswerService extends AccessibilityService {

    private static final String SERVER_URL = "http://120.78.231.177:8765"; // 小黑服务器公网
    private String lastText = "";
    private long lastEventTime = 0;
    private static final long DEBOUNCE_MS = 1500;
    private MonitorPrefs monitorPrefs;
    private String myPackageName;

    @Override
    public void onCreate() {
        super.onCreate();
        monitorPrefs = new MonitorPrefs(this);
        myPackageName = getPackageName();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 包名过滤：只监听用户选中的 App
        String eventPackage = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (eventPackage.isEmpty() || eventPackage.equals(myPackageName)) {
            return; // 跳过本应用和未知来源
        }
        if (!monitorPrefs.isMonitored(eventPackage)) {
            return; // 用户没勾选这个 App
        }

        // 只处理内容变化事件，不要太频繁
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastEventTime < DEBOUNCE_MS) return;
        lastEventTime = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // 提取屏幕上所有文本
        String screenText = extractText(root);
        root.recycle();

        if (screenText == null || screenText.trim().isEmpty()) return;
        String trimmed = screenText.trim();

        // 如果和上次一样，跳过
        if (trimmed.equals(lastText)) return;
        lastText = trimmed;

        // 放宽判断：只要有足够长的文本就认为是题目
        if (isQuestion(trimmed)) {
            showToast("🔍 正在查询答案...");
            fetchAnswer(trimmed);
        }
    }

    private boolean isQuestion(String text) {
        // 放宽规则：文本长度超过 10 个字就可能是一道题
        if (text.length() < 10) return false;

        return text.contains("?") || text.contains("？")
                || text.contains("A.") || text.contains("A．")
                || text.contains("B.") || text.contains("B．")
                || text.contains("C.") || text.contains("C．")
                || text.contains("D.") || text.contains("D．")
                || text.contains("题")
                || text.matches(".*\\d+[.、].*")
                || text.contains("正确") || text.contains("错误")
                || text.contains("单选") || text.contains("多选");
    }

    private String extractText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        extractTextRecursive(node, sb, new HashSet<>());
        return sb.toString();
    }

    private void extractTextRecursive(AccessibilityNodeInfo node, StringBuilder sb, Set<String> visited) {
        if (node == null) return;

        String id = Integer.toHexString(node.hashCode());
        if (visited.contains(id)) return;
        visited.add(id);

        if (node.getText() != null) {
            String text = node.getText().toString().trim();
            if (!text.isEmpty()) {
                sb.append(text).append("\n");
            }
        }
        if (node.getContentDescription() != null) {
            String desc = node.getContentDescription().toString().trim();
            if (!desc.isEmpty()) {
                sb.append(desc).append("\n");
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            extractTextRecursive(node.getChild(i), sb, visited);
        }
    }

    private void fetchAnswer(final String question) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/answer");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                String json = "{\"question\":\"" + jsonEscape(question) + "\"}";
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
                showToast("❌ 连接失败: " + e.getMessage());
            }
        }).start();
    }

    private void showToast(final String msg) {
        new Handler(getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
    }

    private String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void onInterrupt() {
    }
}
