package com.example.examhelper;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class AnswerService extends AccessibilityService {

    private static final String SERVER_URL = "http://172.17.90.64:8765"; // 小黑服务器
    private String lastText = "";
    private long lastEventTime = 0;
    private static final long DEBOUNCE_MS = 1500;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
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

        // 判断是否包含题目特征（包含问号、选项 ABCD、数字编号等）
        if (isQuestion(trimmed)) {
            // 更新 UI
            MainActivity mainActivity = getMainActivity();
            if (mainActivity != null) {
                mainActivity.onQuestionDetected(trimmed);
            }

            // 显示"查询中"提示
            Toast.makeText(this, "🔍 正在查询答案...", Toast.LENGTH_SHORT).show();

            // 发到服务器获取答案
            fetchAnswer(trimmed);
        }
    }

    private boolean isQuestion(String text) {
        // 简单的题目检测：包含问号、或 ABCD 选项、或"题"字
        return text.contains("?") || text.contains("？")
                || text.contains("A.") || text.contains("A．")
                || text.contains("B.") || text.contains("B．")
                || text.contains("C.") || text.contains("C．")
                || text.contains("D.") || text.contains("D．")
                || text.contains("题")
                || text.matches(".*\\d+[.、].*");  // 数字编号
    }

    private String extractText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        extractTextRecursive(node, sb, new HashSet<>());
        return sb.toString();
    }

    private void extractTextRecursive(AccessibilityNodeInfo node, StringBuilder sb, Set<String> visited) {
        if (node == null) return;

        // 防止重复处理同一个节点
        String id = Integer.toHexString(node.hashCode());
        if (visited.contains(id)) return;
        visited.add(id);

        // 如果有文本，添加
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

        // 遍历子节点
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

                // 发送 JSON
                String json = "{\"question\":" + jsonEscape(question) + "}";
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                if (code == 200) {
                    // 读取响应
                    java.io.InputStream is = conn.getInputStream();
                    java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                    String answer = s.hasNext() ? s.next().trim() : "";
                    is.close();

                    if (!answer.isEmpty()) {
                        // 通过 Toast 显示答案
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
        // 需要从主线程显示 Toast
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 可以直接 post
            new android.os.Handler(getMainLooper()).post(() ->
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
        } else {
            new android.os.Handler(getMainLooper()).post(() ->
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show());
        }
    }

    private MainActivity getMainActivity() {
        // 简单的方式：通过 ActivityManager 获取（简化版）
        return null; // 实际通过广播或 Handler 更新
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
