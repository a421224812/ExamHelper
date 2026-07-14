package com.example.examhelper;

import android.graphics.drawable.Drawable;

/** 应用信息模型 */
public class AppInfo {
    public String packageName;
    public String appName;
    public Drawable icon;
    public boolean checked;

    public AppInfo(String packageName, String appName, Drawable icon) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.checked = false;
    }
}
