package com.example.aries;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PromptsActivity extends Activity {

    public static class Prompt {
        public String title;
        public String text;
        public Prompt(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    private List<Prompt> prompts = new ArrayList<>();
    private PromptsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompts);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        TextView btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { editPrompt(-1); }
        });

        ListView listView = findViewById(R.id.promptList);
        loadPrompts();

        adapter = new PromptsAdapter(this, prompts, new PromptsAdapter.Callback() {
            @Override
            public void onEdit(int position) {
                editPrompt(position);
            }

            @Override
            public void onDelete(final int position) {
                showDeleteConfirm(position);
            }
        });
        listView.setAdapter(adapter);
    }
    
    private void showDeleteConfirm(final int position) {
        android.view.View view = android.view.LayoutInflater.from(this)
            .inflate(R.layout.dialog_confirm, null);

        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        android.widget.TextView tvMsg = view.findViewById(R.id.tvDialogMessage);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnOk = view.findViewById(R.id.btnOk);

        tvTitle.setText("删除提示词");
        tvMsg.setText("确定删除这条提示词吗？");

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
                prompts.remove(position);
                savePrompts();
                adapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void editPrompt(final int position) {
        android.view.View view = android.view.LayoutInflater.from(this)
            .inflate(R.layout.dialog_edit_prompt, null);

        final android.widget.EditText etTitle = view.findViewById(R.id.etTitle);
        final android.widget.EditText et = view.findViewById(R.id.etPrompt);
        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnSave = view.findViewById(R.id.btnSave);

        if (position >= 0) {
            tvTitle.setText("编辑提示词");
            etTitle.setText(prompts.get(position).title);
            et.setText(prompts.get(position).text);
        } else {
            tvTitle.setText("新增提示词");
        }

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                (int)(getResources().getDisplayMetrics().widthPixels * 0.9),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialog.dismiss(); }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String title = etTitle.getText().toString().trim();
                String text = et.getText().toString().trim();
                if (title.isEmpty() || text.isEmpty()) {
                    android.widget.Toast.makeText(PromptsActivity.this,
                        "标题和内容都不能为空", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (position >= 0) {
                    prompts.get(position).title = title;
                    prompts.get(position).text = text;
                } else {
                    prompts.add(new Prompt(title, text));
                }
                savePrompts();
                adapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.show();
    }
    
    private void loadPrompts() {
        prompts.clear();
        try {
            JSONArray arr = new JSONArray(Prefs.getPromptListJson(this));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String title = obj.optString("title", "");
                String text = obj.optString("text", "");
                // 兼容老数据：老数据没 title，就用 text 前几个字当标题
                if (title.isEmpty()) {
                    title = text.length() > 10 ? text.substring(0, 10) : text;
                }
                prompts.add(new Prompt(title, text));
            }
        } catch (Exception e) { /* 忽略 */ }
    }

    private void savePrompts() {
        try {
            JSONArray arr = new JSONArray();
            for (Prompt p : prompts) {
                JSONObject obj = new JSONObject();
                obj.put("title", p.title);
                obj.put("text", p.text);
                arr.put(obj);
            }
            Prefs.setPromptListJson(this, arr.toString());
        } catch (Exception e) { /* 忽略 */ }
    }
}