package com.example.examhelper;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private List<AppInfo> apps;
    private SharedPreferences prefs;

    public AppListAdapter(List<AppInfo> apps, SharedPreferences prefs) {
        this.apps = apps;
        this.prefs = prefs;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AppInfo app = apps.get(position);
        holder.tvAppName.setText(app.appName);
        holder.tvPackageName.setText(app.packageName);
        if (app.icon != null) {
            holder.ivIcon.setImageDrawable(app.icon);
        }

        // 恢复已保存的选中状态
        app.checked = prefs.getBoolean("monitor_" + app.packageName, false);
        holder.cbApp.setChecked(app.checked);

        holder.cbApp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            app.checked = isChecked;
            prefs.edit().putBoolean("monitor_" + app.packageName, isChecked).apply();
        });

        holder.itemView.setOnClickListener(v -> holder.cbApp.performClick());
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbApp;
        ImageView ivIcon;
        TextView tvAppName;
        TextView tvPackageName;

        ViewHolder(View v) {
            super(v);
            cbApp = v.findViewById(R.id.cbApp);
            ivIcon = v.findViewById(R.id.ivIcon);
            tvAppName = v.findViewById(R.id.tvAppName);
            tvPackageName = v.findViewById(R.id.tvPackageName);
        }
    }
}
