package com.example.aries;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private SettingsAdapter adapter;
    private List<SettingsAdapter.Item> items;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
        } catch (Exception e) {}
        setContentView(R.layout.activity_settings);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        ListView listView = findViewById(R.id.settingsList);

        items = new ArrayList<>();
        buildItems();

        adapter = new SettingsAdapter(this, items);
        listView.setAdapter(adapter);
        
    }
    
    private void showPetModeDialog() {
        String[] modes = {"静态呼吸 (最省电)", "序列帧 (生动)", "Live2D (最灵动)"};
        int currentMode = Prefs.getPetMode(SettingsActivity.this);
        
        new android.app.AlertDialog.Builder(SettingsActivity.this)
                .setTitle("选择桌宠模式")
                .setSingleChoiceItems(modes, currentMode, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Prefs.setPetMode(SettingsActivity.this, which);
                        android.widget.Toast.makeText(SettingsActivity.this, "已切换为：" + modes[which], android.widget.Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        
                        // 🌟 选完后立刻重启桌宠服务，让它瞬间变身！
                        stopService(new android.content.Intent(SettingsActivity.this, PetService.class));
                        android.content.Intent intent = new android.content.Intent(SettingsActivity.this, PetService.class);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(intent);
                        } else {
                            startService(intent);
                        }
                    }
                })
                .show();
    }

    private void buildItems() {
        items.add(new SettingsAdapter.Item(
                "AI 名称",
                Prefs.getAiName(this),
                new Runnable() {
                    @Override
                    public void run() {
                        editAiName();
                    }
                }
        ));
        
        // --- 新增：用户姓名 ---
        items.add(new SettingsAdapter.Item(
                "用户姓名",
                Prefs.getUserName(this),
                new Runnable() {
                    @Override
                    public void run() {
                        editUserSetting(true);
                    }
                }
        ));

        // --- 新增：用户称呼 ---
        items.add(new SettingsAdapter.Item(
                "用户称呼",
                Prefs.getUserRole(this),
                new Runnable() {
                    @Override
                    public void run() {
                        editUserSetting(false);
                    }
                }
        ));
        items.add(new SettingsAdapter.Item(
            "系统提示词", "",
            new Runnable() {
                @Override
                public void run() {
                    startActivity(new android.content.Intent(
                        SettingsActivity.this, PromptsActivity.class));
                }
            }
        ));
        
        // --- 新增的模型服务源 ---
        items.add(new SettingsAdapter.Item(
                "模型服务",
                Prefs.getApiSource(this) == 0 ? "本地 (Termux)" : "云端 (Gemini)",
                new Runnable() {
                    @Override
                    public void run() {
                        showApiSourceDialog();
                    }
                }
        ));
        
        items.add(new SettingsAdapter.Item(
                "绘图引擎",
                Prefs.getDrawSource(this) == 0 ? "云端 (Pollinations)" : "本地 (Termux)",
                new Runnable() {
                    @Override
                    public void run() {
                        showDrawSourceDialog();
                    }
                }
        ));
        
        items.add(new SettingsAdapter.Item(
                "桌宠开关",
                Prefs.isPetEnabled(this),
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                        Prefs.setPetEnabled(SettingsActivity.this, isChecked);
                        ((android.widget.Switch) buttonView).setTrackResource(isChecked ? R.drawable.switch_track_on : R.drawable.switch_track_off);
                        
                        if (isChecked) {
                            // 检查悬浮窗权限并启动
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                if (!android.provider.Settings.canDrawOverlays(SettingsActivity.this)) {
                                    android.widget.Toast.makeText(SettingsActivity.this, "请为 Aries 开启悬浮窗权限", android.widget.Toast.LENGTH_LONG).show();
                                    android.content.Intent intent = new android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:" + getPackageName()));
                                    startActivity(intent);
                                    return;
                                }
                            }
                            android.content.Intent serviceIntent = new android.content.Intent(SettingsActivity.this, PetService.class);
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent);
                            } else {
                                startService(serviceIntent);
                            }
                            android.widget.Toast.makeText(SettingsActivity.this, "大肥鱼出动！", android.widget.Toast.LENGTH_SHORT).show();
                            moveTaskToBack(true); // 回到桌面看效果
                        } else {
                            stopService(new android.content.Intent(SettingsActivity.this, PetService.class));
                            android.widget.Toast.makeText(SettingsActivity.this, "已收回大肥鱼", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        ));
        
        // 🌟 新增：把桌宠模式加进列表里
        int currentPetMode = Prefs.getPetMode(this);
        String petModeText = "静态呼吸 (最省电)";
        if (currentPetMode == 1) petModeText = "序列帧 (生动)";
        else if (currentPetMode == 2) petModeText = "Live2D (最灵动)";

        items.add(new SettingsAdapter.Item(
                "桌宠模式", 
                petModeText,
                new Runnable() {
                    @Override
                    public void run() {
                        showPetModeDialog();
                    }
                }
        ));
        
        // 在 add 之前，先动态读取一下当前设置的名字
        String currentAiName = Prefs.getAiName(this);
        if (currentAiName == null || currentAiName.isEmpty()) currentAiName = "Aries";
        
        items.add(new SettingsAdapter.Item(
                "模型参数调节",
                "掌控 " + currentAiName + " 的狂野程度",
                new Runnable() {
                    @Override
                    public void run() {
                        showModelSettingsDialog();
                }
            }
        ));

        // --- 新增的 Gemini API Key ---
        items.add(new SettingsAdapter.Item(
                "Gemini 密钥",
                Prefs.getGeminiKey(this).isEmpty() ? "未设置" : "已设置",
                new Runnable() {
                    @Override
                    public void run() {
                        editGeminiKey();
                    }
                }
        ));
        items.add(new SettingsAdapter.Item(
                "外观", getThemeText(),
                new Runnable() {
                    @Override
                    public void run() { /* TODO: 外观设置页 */ }
                }
        ));
        
        items.add(new SettingsAdapter.Item(
                "压缩图片",
                Prefs.isCompressEnabled(this),
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                        Prefs.setCompressEnabled(SettingsActivity.this, isChecked);
                        // 切换底轨样式
                        ((android.widget.Switch) buttonView).setTrackResource(isChecked ? R.drawable.switch_track_on : R.drawable.switch_track_off);
                    }
                }
        ));
        
        items.add(new SettingsAdapter.Item(
                "数据管理", "",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new android.content.Intent(
                            SettingsActivity.this, DataActivity.class));
                    }
                }
        ));
        items.add(new SettingsAdapter.Item(
                "关于", "v1.0",
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new android.content.Intent(
                            SettingsActivity.this, AboutActivity.class));
                    }
                }
        ));
    }
    private void editAiName() {
        android.view.View view = android.view.LayoutInflater.from(this)
            .inflate(R.layout.dialog_edit_name, null);

        final android.widget.EditText et = view.findViewById(R.id.etName);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnSave = view.findViewById(R.id.btnSave);

        et.setText(Prefs.getAiName(this));

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

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = et.getText().toString().trim();
                if (name.isEmpty()) name = "Aries";
                Prefs.setAiName(SettingsActivity.this, name);

                // 同步主对话标题
                Topic main = TopicStore.getMain(SettingsActivity.this);
                if (main != null) {
                    main.title = name;
                    TopicStore.update(SettingsActivity.this, main);
                }

                // 刷新设置列表
                items.clear();
                buildItems();
                adapter.notifyDataSetChanged();

                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private String getThemeText() {
        int mode = Prefs.getThemeMode(this);
        if (mode == 1) return "深色";
        if (mode == 2) return "浅色";
        return "跟随系统";
    }
    
    // 选择服务源的深色弹窗
    private void showApiSourceDialog() {
        final String[] options = {"本地 (Termux)", "云端 (Gemini)"};
        int current = Prefs.getApiSource(this);

        android.view.View view = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_topic_menu, null);
        android.widget.TextView tvTitle = view.findViewById(R.id.tvMenuTitle);
        android.widget.LinearLayout container = view.findViewById(R.id.menuContainer);
        tvTitle.setText("选择模型服务");

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.85),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            android.widget.TextView item = (android.widget.TextView)
                    android.view.LayoutInflater.from(this)
                            .inflate(R.layout.item_topic_menu, container, false);
            // 当前选中的打勾
            item.setText(options[i] + (current == i ? "  ✓" : ""));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setApiSource(SettingsActivity.this, index);
                    items.clear();
                    buildItems();
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                }
            });
            container.addView(item);
        }
        dialog.show();
    }

    // 编辑 Gemini Key 的深色弹窗
    private void editGeminiKey() {
        android.view.View view = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_name, null); // 复用之前的修改名称布局

        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle); // 如果布局里没这个ID，下面手动改
        android.widget.TextView dialogTitle = view.findViewById(R.id.tvDialogTitle);
    
        // 因为 dialog_edit_name 里可能没给标题设 ID，我们用最前面的那个 TextView，或者直接找
        // 简单点，直接找 etName 和 btnSave/btnCancel
        final android.widget.EditText et = view.findViewById(R.id.etName);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnSave = view.findViewById(R.id.btnSave);

        // 尝试修改标题文本（假设第一个子元素是标题）
        try {
            android.widget.TextView titleView = (android.widget.TextView) ((android.view.ViewGroup) view).getChildAt(0);
            titleView.setText("Gemini API 密钥");
        } catch (Exception e) {}

        et.setText(Prefs.getGeminiKey(this));
        et.setHint("输入 Gemini API Key");

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

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setGeminiKey(SettingsActivity.this, et.getText().toString().trim());
                items.clear();
                buildItems();
                adapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.show();
    }
    
    private void editUserSetting(final boolean isName) {
        android.view.View view = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_name, null);

        final android.widget.EditText et = view.findViewById(R.id.etName);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnSave = view.findViewById(R.id.btnSave);

        // 修改弹窗标题
        try {
            android.widget.TextView titleView = (android.widget.TextView) ((android.view.ViewGroup) view).getChildAt(0);
            titleView.setText(isName ? "设置用户姓名" : "设置用户称呼");
        } catch (Exception e) {}

        if (isName) {
            et.setText(Prefs.getUserName(this));
            et.setHint("输入你的名字");
        } else {
            et.setText(Prefs.getUserRole(this));
            et.setHint("输入 AI 对你的称呼");
        }

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

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String val = et.getText().toString().trim();
                if (val.isEmpty()) return;
            
                if (isName) {
                    Prefs.setUserName(SettingsActivity.this, val);
                } else {
                    Prefs.setUserRole(SettingsActivity.this, val);
                }
            
                items.clear();
                buildItems();
                adapter.notifyDataSetChanged();
                dialog.dismiss();
            }
        });

        dialog.show();
    }
    
    private void showModelSettingsDialog() {
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_model_settings, null);

        android.widget.SeekBar sbTemp = view.findViewById(R.id.sbTemp);
        android.widget.SeekBar sbTopP = view.findViewById(R.id.sbTopP);
        android.widget.SeekBar sbPenalty = view.findViewById(R.id.sbPenalty);
        android.widget.SeekBar sbMaxTokens = view.findViewById(R.id.sbMaxTokens);

        android.widget.TextView tvTempVal = view.findViewById(R.id.tvTempVal);
        android.widget.TextView tvTopPVal = view.findViewById(R.id.tvTopPVal);
        android.widget.TextView tvPenaltyVal = view.findViewById(R.id.tvPenaltyVal);
        android.widget.TextView tvMaxTokensVal = view.findViewById(R.id.tvMaxTokensVal);

        // 初始化进度
        sbTemp.setProgress((int) (Prefs.getTemperature(this) * 100));
        sbTopP.setProgress((int) (Prefs.getTopP(this) * 100));
        sbPenalty.setProgress((int) (Prefs.getRepeatPenalty(this) * 100));
        sbMaxTokens.setProgress(Prefs.getMaxTokens(this) / 10);

        // 监听滑动
        sbTemp.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                tvTempVal.setText(String.format("%.2f", progress / 100.0f));
            }
        });
        sbTopP.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                tvTopPVal.setText(String.format("%.2f", progress / 100.0f));
            }
        });
        sbPenalty.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                tvPenaltyVal.setText(String.format("%.2f", progress / 100.0f));
            }
        });
        sbMaxTokens.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                tvMaxTokensVal.setText(String.valueOf(progress * 10));
            }
        });

        // 触发一次初始文本
        tvTempVal.setText(String.format("%.2f", Prefs.getTemperature(this)));
        tvTopPVal.setText(String.format("%.2f", Prefs.getTopP(this)));
        tvPenaltyVal.setText(String.format("%.2f", Prefs.getRepeatPenalty(this)));
        tvMaxTokensVal.setText(String.valueOf(Prefs.getMaxTokens(this)));

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.88), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        view.findViewById(R.id.btnSaveParams).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setTemperature(SettingsActivity.this, sbTemp.getProgress() / 100.0f);
                Prefs.setTopP(SettingsActivity.this, sbTopP.getProgress() / 100.0f);
                Prefs.setRepeatPenalty(SettingsActivity.this, sbPenalty.getProgress() / 100.0f);
                Prefs.setMaxTokens(SettingsActivity.this, sbMaxTokens.getProgress() * 10);
                android.widget.Toast.makeText(SettingsActivity.this, "参数已保存", android.widget.Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    // 抽出个简单的监听器省代码
    private abstract class SimpleSeekBarListener implements android.widget.SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
    }
    
    private void showDrawSourceDialog() {
        final String[] options = {"云端 (Pollinations)", "本地 (Termux)"};
        int current = Prefs.getDrawSource(this);

        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_topic_menu, null);
        android.widget.TextView tvTitle = view.findViewById(R.id.tvMenuTitle);
        android.widget.LinearLayout container = view.findViewById(R.id.menuContainer);
        tvTitle.setText("选择绘图引擎");

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.85), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            android.widget.TextView item = (android.widget.TextView) android.view.LayoutInflater.from(this).inflate(R.layout.item_topic_menu, container, false);
            item.setText(options[i] + (current == i ? "  ✓" : ""));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setDrawSource(SettingsActivity.this, index);
                    items.clear();
                    buildItems();
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                }
            });
            container.addView(item);
        }
        dialog.show();
    }
    
}