package com.example.examhelper;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 监听的 App 列表存到 SharedPreferences */
public class MonitorPrefs {
    private static final String PREFS_NAME = "monitor_prefs";

    private final SharedPreferences prefs;

    public MonitorPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 获取所有被选中的包名 */
    public List<String> getMonitoredPackages() {
        List<String> result = new ArrayList<>();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith("monitor_") && entry.getValue() instanceof Boolean && (Boolean) entry.getValue()) {
                result.add(entry.getKey().substring("monitor_".length()));
            }
        }
        return result;
    }

    /** 判断某个包名是否被选中 */
    public boolean isMonitored(String packageName) {
        return prefs.getBoolean("monitor_" + packageName, false);
    }

    /** 如果尚未勾选则自动勾选 */
    public void enableIfNot(String packageName) {
        if (!isMonitored(packageName)) {
            prefs.edit().putBoolean("monitor_" + packageName, true).apply();
        }
    }
}
