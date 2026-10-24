package com.example.aries;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class SettingsAdapter extends BaseAdapter {

    // 每一项：标题 + 右侧值（值可为空）
    public static class Item {
        public String title;
        public String value;
        public Runnable onClick;
        
        // 新增：开关专属字段
        public boolean isToggle = false;
        public boolean isChecked = false;
        public CompoundButton.OnCheckedChangeListener onToggle;

        // 普通点击项构造
        public Item(String title, String value, Runnable onClick) {
            this.title = title;
            this.value = value;
            this.onClick = onClick;
        }

        // 开关项构造
        public Item(String title, boolean isChecked, CompoundButton.OnCheckedChangeListener onToggle) {
            this.title = title;
            this.isToggle = true;
            this.isChecked = isChecked;
            this.onToggle = onToggle;
        }
    }

    private final Context ctx;
    private final List<Item> items;

    public SettingsAdapter(Context ctx, List<Item> items) {
        this.ctx = ctx;
        this.items = items;
    }

    @Override
    public int getCount() { return items.size(); }
    @Override
    public Object getItem(int i) { return items.get(i); }
    @Override
    public long getItemId(int i) { return i; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_settings, parent, false);
        }
        final Item item = items.get(position);

        TextView tvTitle = convertView.findViewById(R.id.tvTitle);
        TextView tvValue = convertView.findViewById(R.id.tvValue);
        Switch switchWidget = convertView.findViewById(R.id.switchWidget);

        tvTitle.setText(item.title);

        // 核心：根据是否是开关项来切换右侧 UI
        if (item.isToggle) {
            tvValue.setVisibility(View.GONE);
            switchWidget.setVisibility(View.VISIBLE);
            
            // 防止 ListView 复用导致状态错乱，先清空监听再设置状态
            switchWidget.setOnCheckedChangeListener(null);
            switchWidget.setChecked(item.isChecked);
            
            // 动态切换轨道背景
            switchWidget.setTrackResource(item.isChecked ? R.drawable.switch_track_on : R.drawable.switch_track_off);
            
            // 设置新的监听器
            switchWidget.setOnCheckedChangeListener(item.onToggle);
            
            // 如果是一个开关项，通常不需要整行点击，所以清空整行点击事件
            convertView.setOnClickListener(null);
            
        } else {
            // 普通文字项
            switchWidget.setVisibility(View.GONE);
            if (item.value == null || item.value.isEmpty()) {
                tvValue.setVisibility(View.GONE);
            } else {
                tvValue.setVisibility(View.VISIBLE);
                tvValue.setText(item.value);
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (item.onClick != null) item.onClick.run();
                }
            });
        }
        
        return convertView;
    }
}