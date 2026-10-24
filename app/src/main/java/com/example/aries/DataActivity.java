package com.example.aries;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public class DataActivity extends Activity {

    private ListView listView;
    private List<SettingsAdapter.Item> items;
    private SettingsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        listView = findViewById(R.id.dataList);
        items = new ArrayList<>();
        buildItems();

        adapter = new SettingsAdapter(this, items);
        listView.setAdapter(adapter);
    }

    private void buildItems() {
        items.add(new SettingsAdapter.Item(
                "清空所有对话记录",
                "",
                new Runnable() {
                    @Override
                    public void run() { confirmClearChats(); }
                }
        ));

        items.add(new SettingsAdapter.Item(
                "清空所有系统提示词",
                "",
                new Runnable() {
                    @Override
                    public void run() { confirmClearPrompts(); }
                }
        ));

        items.add(new SettingsAdapter.Item(
                "重置全部设置",
                "",
                new Runnable() {
                    @Override
                    public void run() { confirmReset(); }
                }
        ));
    }

    private void confirmClearChats() {
        showConfirm("清空对话记录", "将删除所有话题的消息内容（提示词和设置保留）。此操作不可恢复。", new Runnable() {
            @Override
            public void run() {
                List<Topic> all = TopicStore.getAll(DataActivity.this);
                for (Topic t : all) {
                    t.messages = new org.json.JSONArray();
                }
                TopicStore.saveAll(DataActivity.this, all);
                android.widget.Toast.makeText(DataActivity.this, "已清空所有对话", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmClearPrompts() {
        showConfirm("清空系统提示词", "将删除所有系统提示词。此操作不可恢复。", new Runnable() {
            @Override
            public void run() {
                Prefs.setPromptListJson(DataActivity.this, "[]");
                android.widget.Toast.makeText(DataActivity.this, "已清空系统提示词", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmReset() {
        showConfirm("重置全部设置", "将清空所有数据：对话、提示词、AI 名称、设置。此操作不可恢复。", new Runnable() {
            @Override
            public void run() {
                DataActivity.this.getSharedPreferences("aries_settings", MODE_PRIVATE)
                        .edit().clear().apply();
                android.widget.Toast.makeText(DataActivity.this, "已重置全部设置，请重启 App", android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showConfirm(String title, String message, final Runnable onConfirm) {
        android.view.View view = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_confirm, null);

        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        android.widget.TextView tvMsg = view.findViewById(R.id.tvDialogMessage);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnOk = view.findViewById(R.id.btnOk);

        tvTitle.setText(title);
        tvMsg.setText(message);

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.85),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialog.dismiss(); }
        });

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onConfirm.run();
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}