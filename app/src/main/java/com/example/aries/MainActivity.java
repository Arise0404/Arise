package com.example.aries;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainActivity extends Activity {

    private android.net.Uri selectedImageUri = null;
    private String base64ImageData = null;
    private LinearLayout chatContainer;
    private EditText etInput;
    private ScrollView scrollView;
    private LinearLayout navDrawer;
    private View drawerMask;

    private Topic currentTopic;
    private JSONArray messages;   // 指向当前话题的 messages
    
    private List<Topic> topicList;
    private TopicAdapter topicAdapter;
    private ListView navTopicList;
    private final java.util.HashMap<String, String> draftMap = new java.util.HashMap<>();
    private TextView tvMainTopic;
    private FrameLayout previewContainer;
    private ImageView ivPreview;
    // 🌟 新增：标记当前是否正在生成中
    private boolean isGenerating = false;
    private android.graphics.drawable.Drawable originalSendIcon;
    
    private final android.content.BroadcastReceiver aiReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            String targetTopicId = intent.getStringExtra(AIChatService.EXTRA_TOPIC_ID);
        
            if (AIChatService.ACTION_UPDATE.equals(intent.getAction())) {
                if (currentTopic != null && currentTopic.id.equals(targetTopicId)) {
                    String chunk = intent.getStringExtra(AIChatService.EXTRA_CHUNK);
                    appendToLastBubble(chunk);
                }
            } else if (AIChatService.ACTION_FINISH.equals(intent.getAction())) {
                if ("REFRESH_TITLE".equals(targetTopicId)) {
                    refreshTopicList();
                } else if (currentTopic != null && currentTopic.id.equals(targetTopicId)) {
                    removeThinkingBubble();
                
                    if (streamingBubble != null && streamingBubble.getText().toString().trim().isEmpty()) {
                        chatContainer.removeView(streamingBubble);
                        streamingBubble = null;
                    }
                
                    String err = intent.getStringExtra(AIChatService.EXTRA_ERROR);
                    if (err != null) addBubble("请求失败: " + err, false);
                    
                    // 🌟 新增：生成结束（不管是成功还是失败），恢复按钮状态
                    setGeneratingState(false);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 🌟 新增：注册广播
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(AIChatService.ACTION_UPDATE);
        filter.addAction(AIChatService.ACTION_FINISH);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(aiReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(aiReceiver, filter);
        }

        chatContainer = findViewById(R.id.chatContainer);
        etInput = findViewById(R.id.etInput);
        scrollView = findViewById(R.id.scrollView);
        ImageView btnSend = findViewById(R.id.btnSend);
        originalSendIcon = btnSend.getDrawable(); // 🌟 记住主人原来用的图标
        ImageView btnImage = findViewById(R.id.btnImage);

        ImageView btnMenu = findViewById(R.id.btnMenu);
        ImageView btnNewChat = findViewById(R.id.btnNewChat);
        LinearLayout navNewChat = findViewById(R.id.navNewChat);
        LinearLayout navSettings = findViewById(R.id.navSettings);
        navDrawer = findViewById(R.id.navDrawer);
        drawerMask = findViewById(R.id.drawerMask);
        
        previewContainer = findViewById(R.id.previewContainer);
        ivPreview = findViewById(R.id.ivPreview);
        ImageView btnCancelPreview = findViewById(R.id.btnCancelPreview);

        btnCancelPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                base64ImageData = null;
                selectedImageUri = null;
                previewContainer.setVisibility(View.GONE);
            }
        });

        // 加载当前话题
        currentTopic = TopicStore.getCurrent(this);
        if (currentTopic == null) {
            currentTopic = TopicStore.getMain(this);
        }
        messages = currentTopic.messages;
        
        tvMainTopic = findViewById(R.id.tvMainTopic);
        tvMainTopic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Topic main = TopicStore.getMain(MainActivity.this);
                if (main != null) {
                    switchTopic(main);
                    closeDrawer();
                }
            }
        });
        
        navTopicList = findViewById(R.id.navTopicList);
        refreshTopicList();

        // 恢复界面上的历史消息
        restoreHistory();

        // 🌟 恢复：短按菜单键打开侧边栏
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { 
                openDrawer(); 
            }
        });

        drawerMask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { closeDrawer(); }
        });

        btnImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, 1001);
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 🌟 新增：如果当前正在生成（正在思考或画图），点击就是“停止”
                if (isGenerating) {
                    AIChatService.isCancelled = true; // 通知后台服务中断
                    stopService(new android.content.Intent(MainActivity.this, AIChatService.class)); // 停掉服务
                    
                    setGeneratingState(false); // 恢复按钮和输入框
                    removeThinkingBubble(); // 清理“意淫中”等提示
                    
                    // 如果那个打字机气泡是空的，也顺手删了
                    if (streamingBubble != null && streamingBubble.getText().toString().trim().isEmpty()) {
                        chatContainer.removeView(streamingBubble);
                        streamingBubble = null;
                    }
                    android.widget.Toast.makeText(MainActivity.this, "已停止", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                // 👇 下面是原本的发送逻辑
                String msg = etInput.getText().toString().trim();
                if (msg.isEmpty() && (base64ImageData == null || base64ImageData.isEmpty())) {
                    return;
                }

                // 🌟 新增：确认有内容要发了，立刻锁定界面状态
                setGeneratingState(true);

                // 🌟 1. 优先判断是不是画图指令！
                if (msg.startsWith("/画 ")) {
                    String prompt = msg.substring(3).trim(); // 提取提示词
    
                    // 界面显示（保留了原本的“正在为您分娩...”）
                    addBubble(msg, true);
                    etInput.setText("");
                    draftMap.put(currentTopic.id, "");
                    previewContainer.setVisibility(View.GONE);
                    addBubble("正在为您分娩...", false);
    
                    // 🌟 核心：把用户的话立刻存入数据库，防止界面刷新丢失
                    try {
                        JSONObject uMsg = new JSONObject();
                        uMsg.put("role", "user");
                        uMsg.put("content", msg);
                        messages.put(uMsg);
                        saveCurrentTopic();
                    } catch (Exception e) { }

                    sendDrawRequest(prompt);
                    return; // 拦截结束
                }

                // 🌟 2. 如果不是画图，那就是正常聊天
                // 显示气泡
                if (!msg.isEmpty()) {
                    addBubble(msg, true);
                }
                if (base64ImageData != null && !base64ImageData.isEmpty()) {
                    addImageBubble(base64ImageData, true);
                }

                etInput.setText("");
                draftMap.put(currentTopic.id, "");
                previewContainer.setVisibility(View.GONE);
                
                // 保留了原本的“意淫中...”
                addBubble("意淫中...", false);
                sendToLocalAI(msg);
            }
        });
        
        etInput.addTextChangedListener(new android.text.TextWatcher() {
           @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (currentTopic != null) {
                    draftMap.put(currentTopic.id, s.toString());
                }
            }
        });

        btnNewChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { newChat(); }
        });
        navNewChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { newChat(); closeDrawer(); }
        });
        navSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(MainActivity.this, SettingsActivity.class));
                closeDrawer();
            }
        });
    }
    
    private void togglePet() {
        // 检查是否有悬浮窗权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent); 
                android.widget.Toast.makeText(this, "请为 Aries 开启悬浮窗权限哦", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
        }
        
        // 启动桌宠
        android.content.Intent petIntent = new android.content.Intent(this, PetService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(petIntent);
        } else {
            startService(petIntent);
        }
        android.widget.Toast.makeText(this, "大肥鱼粗现啦！", android.widget.Toast.LENGTH_SHORT).show();
        
        // 退出应用到后台，方便看效果
        moveTaskToBack(true); 
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(aiReceiver); // 🌟 新增：注销广播，防止内存泄漏
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        Topic main = TopicStore.getMain(this);
        if (main != null) {
            String newName = Prefs.getAiName(this);
            if (!newName.equals(main.title)) {
                main.title = newName;
                TopicStore.update(this, main);
            }
        }
        if (tvMainTopic != null) tvMainTopic.setText(Prefs.getAiName(this));
        // 同步更新顶部栏的标题
        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setText(Prefs.getAiName(this));
        // 同步更新输入框的提示文字
        if (etInput != null) {
            etInput.setHint("给 " + Prefs.getAiName(this) + " 发送消息");
        }
        refreshTopicList();
        
        Topic reloaded = TopicStore.findById(this, currentTopic.id);
        if (reloaded != null) {
            currentTopic = reloaded;
            messages = currentTopic.messages;
            restoreHistory();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        draftMap.clear();
    }

    private void restoreHistory() {
        chatContainer.removeAllViews();
        for (int i = 0; i < messages.length(); i++) {
            try {
                JSONObject o = messages.getJSONObject(i);
                String role = o.optString("role", "");
                String content = o.optString("content", "");
                if ("null".equals(content)) content = ""; // 🌟 过滤掉历史记录里的 null
                String base64Img = o.optString("image", null);
                boolean isUser = "user".equals(role);

                if (base64Img != null && !base64Img.isEmpty()) {
                    addImageBubble(base64Img, isUser);
                } else {
                    addBubble(content, isUser);
                }
            } catch (Exception e) { /* 忽略 */ }
        }
    }

    private void saveCurrentTopic() {
        currentTopic.messages = messages;
        TopicStore.update(this, currentTopic);
    }

    private void newChat() {
        draftMap.put(currentTopic.id, etInput.getText().toString());
        showPickPromptsDialog();
    }

    private void openDrawer() {
        navDrawer.setVisibility(View.VISIBLE);
        drawerMask.setVisibility(View.VISIBLE);
    }

    private void closeDrawer() {
        navDrawer.setVisibility(View.GONE);
        drawerMask.setVisibility(View.GONE);
    }

    private void addBubble(String text, boolean isUser) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(Prefs.getFontSize(this));
        bubble.setTextColor(getResources().getColor(R.color.text_main));
        bubble.setPadding(36, 26, 36, 26);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(getResources().getColor(isUser ? R.color.bubble_user : R.color.bubble_ai));
        bg.setCornerRadius(28);
        bubble.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(8, 12, 8, 12);
        params.gravity = isUser ? android.view.Gravity.END : android.view.Gravity.START;
        bubble.setLayoutParams(params);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78));

        final String bubbleText = text;
        bubble.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showBubbleMenu(bubble, bubbleText);
                return true;
            }
        });
        
        chatContainer.addView(bubble);
        scrollView.post(new Runnable() {
            @Override
            public void run() { scrollView.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private void sendToLocalAI(final String userMsg) {
        // 1. 把用户的话存进数据库
        try {
            JSONObject userMsgObj = new JSONObject();
            userMsgObj.put("role", "user");
            userMsgObj.put("content", userMsg);
            if (base64ImageData != null && !base64ImageData.isEmpty()) {
                userMsgObj.put("image", base64ImageData);
            }
            messages.put(userMsgObj);
            saveCurrentTopic();
        } catch (Exception e) { }

        // 2. 重置打字气泡
        streamingBubble = null;

        // 3. 喊后台服务去干活！
        android.content.Intent serviceIntent = new android.content.Intent(this, AIChatService.class);
        serviceIntent.putExtra("topicId", currentTopic.id);
        serviceIntent.putExtra("userMsg", userMsg);
        serviceIntent.putExtra("base64ImageData", base64ImageData);
    
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // 4. 清理图片状态
        base64ImageData = null;
        selectedImageUri = null;
    }
    
    private void removeThinkingBubble() {
        for (int i = chatContainer.getChildCount() - 1; i >= 0; i--) {
            View child = chatContainer.getChildAt(i);
            if (child instanceof TextView) {
                String text = ((TextView) child).getText().toString();
                if (text.contains("正在思考") || text.contains("意淫中") || text.contains("正在为您绘制") || text.contains("正在为您分娩")) {
                    chatContainer.removeViewAt(i);
                }
            }
        }
    }
    
    private void refreshTopicList() {
        List<Topic> all = TopicStore.getAll(this);
        Topic mainTopic = null;
        List<Topic> branches = new java.util.ArrayList<>();
        for (Topic t : all) {
            if ("main".equals(t.type)) mainTopic = t;
            else branches.add(t);
        }

        if (mainTopic != null) {
            tvMainTopic.setText(mainTopic.title);
            if (mainTopic.id.equals(currentTopic.id)) {
                tvMainTopic.setTextColor(getResources().getColor(R.color.bubble_user));
            } else {
                tvMainTopic.setTextColor(getResources().getColor(R.color.text_main));
            }
        }

        topicList = branches;
        topicAdapter = new TopicAdapter(this, topicList, currentTopic.id,
            new TopicAdapter.Callback() {
                @Override
                public void onSelect(Topic topic) {
                    switchTopic(topic);
                    closeDrawer();
                }
                @Override
                public void onMore(Topic topic) {
                    showTopicMenu(topic);
                }
            });
        navTopicList.setAdapter(topicAdapter);
    }
    
    private void switchTopic(Topic topic) {
        draftMap.put(currentTopic.id, etInput.getText().toString());
        currentTopic = topic;
        messages = currentTopic.messages;
        TopicStore.setCurrentId(this, topic.id);
        restoreHistory();
        String draft = draftMap.get(topic.id);
        etInput.setText(draft == null ? "" : draft);
        refreshTopicList();
    }
    
    private void showTopicMenu(final Topic topic) {
        if ("main".equals(topic.type)) return;

        java.util.List<String> menuNames = new java.util.ArrayList<>();
        java.util.List<Integer> menuActions = new java.util.ArrayList<>();

        menuNames.add("编辑提示词"); menuActions.add(0);
        menuNames.add("重命名");     menuActions.add(1);

        boolean isFirst = topicList.size() > 0 && topicList.get(0).id.equals(topic.id);
        boolean isLast = topicList.size() > 0 && topicList.get(topicList.size() - 1).id.equals(topic.id);

        if (!isFirst) { menuNames.add("上移"); menuActions.add(2); }
        if (!isLast)  { menuNames.add("下移"); menuActions.add(3); }
        menuNames.add("删除"); menuActions.add(4);

        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_topic_menu, null);
        android.widget.TextView tvTitle = view.findViewById(R.id.tvMenuTitle);
        android.widget.LinearLayout container = view.findViewById(R.id.menuContainer);
        tvTitle.setText(topic.title);

        final int[] actions = new int[menuActions.size()];
        for (int i = 0; i < menuActions.size(); i++) actions[i] = menuActions.get(i);

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.85), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        for (int i = 0; i < menuNames.size(); i++) {
            final int action = actions[i];
            android.widget.TextView item = (android.widget.TextView) android.view.LayoutInflater.from(this).inflate(R.layout.item_topic_menu, container, false);
            item.setText(menuNames.get(i));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    switch (action) {
                        case 0: editTopicPrompts(topic); break;
                        case 1: renameTopic(topic); break;
                        case 2: moveTopic(topic, -1); break;
                        case 3: moveTopic(topic, 1); break;
                        case 4: deleteTopic(topic); break;
                    }
                }
            });
            container.addView(item);
        }
        dialog.show();
    }
    
    private void renameTopic(final Topic topic) {
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_rename, null);
        final android.widget.EditText et = view.findViewById(R.id.etRename);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnSave = view.findViewById(R.id.btnSave);

        et.setText(topic.title);

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.85), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialog.dismiss(); }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = et.getText().toString().trim();
                if (!t.isEmpty()) {
                    topic.title = t;
                    TopicStore.update(MainActivity.this, topic);
                    refreshTopicList();
                }
                dialog.dismiss();
            }
        });
        dialog.show();
    }
    
    private void moveTopic(Topic topic, int direction) {
        int idx = -1;
        for (int i = 0; i < topicList.size(); i++) {
            if (topicList.get(i).id.equals(topic.id)) { idx = i; break; }
        }
        if (idx < 0) return;

        int target = idx + direction;
        if (target < 0 || target >= topicList.size()) return;

        Topic tmp = topicList.get(idx);
        topicList.set(idx, topicList.get(target));
        topicList.set(target, tmp);

        List<Topic> all = TopicStore.getAll(this);
        List<Topic> newAll = new java.util.ArrayList<>();
        for (Topic t : all) {
            if ("main".equals(t.type)) newAll.add(t);
        }
        newAll.addAll(topicList);
        TopicStore.saveAll(this, newAll);
        refreshTopicList();
    }
    
    private void deleteTopic(final Topic topic) {
        if ("main".equals(topic.type)) return;

        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null);
        android.widget.TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        android.widget.TextView tvMsg = view.findViewById(R.id.tvDialogMessage);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnOk = view.findViewById(R.id.btnOk);

        tvTitle.setText("删除对话");
        tvMsg.setText("确定删除「" + topic.title + "」吗？此操作不可恢复。");

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.85), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialog.dismiss(); }
        });

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TopicStore.deleteBranch(MainActivity.this, topic.id);
                if (topic.id.equals(currentTopic.id)) {
                    currentTopic = TopicStore.getMain(MainActivity.this);
                    messages = currentTopic.messages;
                    TopicStore.setCurrentId(MainActivity.this, "main");
                    restoreHistory();
                }
                draftMap.remove(topic.id);
                refreshTopicList();
                dialog.dismiss();
            }
        });
        dialog.show();
    }
    
    private void showPickPromptsDialog() {
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_pick_prompts, null);
        final LinearLayout container = view.findViewById(R.id.promptContainer);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnCreate = view.findViewById(R.id.btnCreate);

        final org.json.JSONArray allPrompts = PromptsManager.getGlobalPrompts(this);
        final java.util.List<android.widget.CheckBox> checkBoxes = new java.util.ArrayList<>();

        for (int i = 0; i < allPrompts.length(); i++) {
            try {
                org.json.JSONObject obj = allPrompts.getJSONObject(i);
                String text = obj.optString("text", "").trim();
                String title = obj.optString("title", "");
                if (title.isEmpty()) title = text.length() > 10 ? text.substring(0, 10) : text;
                if (text.isEmpty()) continue;

                android.view.View itemView = android.view.LayoutInflater.from(this).inflate(R.layout.item_pick_prompt, container, false);
                android.widget.CheckBox cb = itemView.findViewById(R.id.cbPick);
                android.widget.TextView tv = itemView.findViewById(R.id.tvPickText);
                tv.setText(title);
                cb.setTag(i);
                checkBoxes.add(cb);

                final android.widget.CheckBox finalCb = cb;
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { finalCb.setChecked(!finalCb.isChecked()); }
                });
                container.addView(itemView);
            } catch (Exception e) { /* 忽略 */ }
        }

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.88), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialog.dismiss(); }
        });

        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                org.json.JSONArray selected = new org.json.JSONArray();
                for (android.widget.CheckBox cb : checkBoxes) {
                    if (cb.isChecked()) selected.put((Integer) cb.getTag());
                }
                Topic branch = TopicStore.createBranch(MainActivity.this);
                branch.enabledPromptIndexes = selected;
                TopicStore.update(MainActivity.this, branch);

                currentTopic = branch;
                messages = branch.messages;
                TopicStore.setCurrentId(MainActivity.this, branch.id);
                chatContainer.removeAllViews();
                etInput.setText("");
                refreshTopicList();
                closeDrawer();
                dialog.dismiss();
            }
        });
        dialog.show();
    }
    
    private void editTopicPrompts(final Topic topic) {
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_pick_prompts, null);
        final LinearLayout container = view.findViewById(R.id.promptContainer);
        android.widget.TextView btnCancel = view.findViewById(R.id.btnCancel);
        android.widget.TextView btnCreate = view.findViewById(R.id.btnCreate);

        android.widget.TextView tvTitle = (android.widget.TextView) ((android.view.ViewGroup) view).getChildAt(0);
        tvTitle.setText("编辑提示词");
        btnCreate.setText("保存");

        final org.json.JSONArray allPrompts = PromptsManager.getGlobalPrompts(this);
        final java.util.List<android.widget.CheckBox> checkBoxes = new java.util.ArrayList<>();

        for (int i = 0; i < allPrompts.length(); i++) {
            try {
                org.json.JSONObject obj = allPrompts.getJSONObject(i);
                String text = obj.optString("text", "").trim();
                String title = obj.optString("title", "");
                if (title.isEmpty()) title = text.length() > 10 ? text.substring(0, 10) : text;
                if (text.isEmpty()) continue;

                android.view.View itemView = android.view.LayoutInflater.from(this).inflate(R.layout.item_pick_prompt, container, false);
                final android.widget.CheckBox cb = itemView.findViewById(R.id.cbPick);
                android.widget.TextView tv = itemView.findViewById(R.id.tvPickText);
                tv.setText(title);
                cb.setTag(i);
                cb.setChecked(PromptsManager.isEnabled(topic, i));
                checkBoxes.add(cb);

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { cb.setChecked(!cb.isChecked()); }
                });
                container.addView(itemView);
            } catch (Exception e) { /* 忽略 */ }
        }

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.88), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialog.dismiss(); }
        });

        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                org.json.JSONArray selected = new org.json.JSONArray();
                for (android.widget.CheckBox cb : checkBoxes) {
                    if (cb.isChecked()) selected.put((Integer) cb.getTag());
                }
                topic.enabledPromptIndexes = selected;
                TopicStore.update(MainActivity.this, topic);
                if (topic.id.equals(currentTopic.id)) {
                    currentTopic.enabledPromptIndexes = selected;
                }
                dialog.dismiss();
                android.widget.Toast.makeText(MainActivity.this, "已保存", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }
    
    private void showBubbleMenu(final View bubble, final String text) {
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_topic_menu, null);
        android.widget.TextView tvTitle = view.findViewById(R.id.tvMenuTitle);
        android.widget.LinearLayout container = view.findViewById(R.id.menuContainer);
        tvTitle.setText("消息");

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.8), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        android.widget.TextView copyItem = (android.widget.TextView) android.view.LayoutInflater.from(this).inflate(R.layout.item_topic_menu, container, false);
        copyItem.setText("复制");
        copyItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("msg", text));
                android.widget.Toast.makeText(MainActivity.this, "已复制", android.widget.Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        container.addView(copyItem);

        android.widget.TextView delItem = (android.widget.TextView) android.view.LayoutInflater.from(this).inflate(R.layout.item_topic_menu, container, false);
        delItem.setText("删除");
        delItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                chatContainer.removeView(bubble);
                removeMessageFromTopic(text);
            }
        });
        container.addView(delItem);
        dialog.show();
    }
    
    private void removeMessageFromTopic(String content) {
        try {
            org.json.JSONArray newArr = new org.json.JSONArray();
            for (int i = 0; i < messages.length(); i++) {
                org.json.JSONObject o = messages.getJSONObject(i);
                String c = o.optString("content", "");
                if (c.equals(content) && newArr.length() < messages.length() - 1) continue;
                newArr.put(o);
            }
            messages = newArr;
            currentTopic.messages = messages;
            TopicStore.update(this, currentTopic);
        } catch (Exception e) { /* 忽略 */ }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
                    java.io.InputStream is = getContentResolver().openInputStream(selectedImageUri);
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);

                    // 压缩逻辑
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    if (Prefs.isCompressEnabled(MainActivity.this)) {
                        // 开启：压缩质量 50%
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, baos);
                    } else {
                        // 关闭：高质量 90%
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos);
                    }
                    byte[] imageBytes = baos.toByteArray();
                    base64ImageData = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
                
                    // 只在这里设置一次预览图
                    ivPreview.setImageBitmap(bitmap);
                    previewContainer.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, "图片处理失败", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    private void addImageBubble(String base64Str, boolean isUser) {
        byte[] bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT);
        final android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(getResources().getColor(isUser ? R.color.bubble_user : R.color.bubble_ai));
        bg.setCornerRadius(28);
        imageView.setBackground(bg);
        imageView.setPadding(10, 10, 10, 10);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(8, 6, 8, 6);
        params.gravity = isUser ? android.view.Gravity.END : android.view.Gravity.START;
        imageView.setLayoutParams(params);
    
        imageView.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.65));
        imageView.setMaxHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.4));
        
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.app.Dialog dialog = new android.app.Dialog(MainActivity.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                ImageView fullImageView = new ImageView(MainActivity.this);
                fullImageView.setImageBitmap(bitmap);
                fullImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                dialog.setContentView(fullImageView);
                fullImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { dialog.dismiss(); }
                });
                dialog.show();
            }
        });

        chatContainer.addView(imageView);
        scrollView.post(new Runnable() {
            @Override
            public void run() { scrollView.fullScroll(View.FOCUS_DOWN); }
        });
    }
    
    // 存着当前正在打字的那个气泡
    private TextView streamingBubble = null;

    private void addEmptyBubbleForStreaming(boolean isUser) {
        TextView bubble = new TextView(this);
        bubble.setText("");
        bubble.setTextSize(Prefs.getFontSize(this));
        bubble.setTextColor(getResources().getColor(R.color.text_main));
        bubble.setPadding(36, 26, 36, 26);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(getResources().getColor(isUser ? R.color.bubble_user : R.color.bubble_ai));
        bg.setCornerRadius(28);
        bubble.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(8, 12, 8, 12);
        params.gravity = isUser ? android.view.Gravity.END : android.view.Gravity.START;
        bubble.setLayoutParams(params);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78));

        chatContainer.addView(bubble);
        streamingBubble = bubble; // 记住这个气泡，后面专门给它填字
    
        scrollView.post(new Runnable() {
            @Override
            public void run() { scrollView.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private void appendToLastBubble(String text) {
        if (text == null || text.equals("null")) return;
    
        String cleanText = text.replace("**", "");
    
        // 🌟 核心：如果只是纯换行或空格，千万别建气泡！直接跳过。
        if (cleanText.trim().isEmpty()) {
            return; 
        }

        // 🌟 只有拿到实打实的字，才把“正在思考”删掉，换上空气泡准备打字
        if (streamingBubble == null) {
            removeThinkingBubble();
            addEmptyBubbleForStreaming(false);
        }
    
        // 追加文字（保留换行以便排版）
        streamingBubble.append(cleanText);
    
        scrollView.post(new Runnable() {
            @Override
            public void run() { scrollView.fullScroll(View.FOCUS_DOWN); }
        });
    }
    
    private void sendDrawRequest(String prompt) {
        setGeneratingState(true); // 🌟 锁定界面
        new Thread(() -> {
            try {
                int drawSource = Prefs.getDrawSource(MainActivity.this);
                String base64Result = null;

                if (drawSource == 0) {
                    // ==========================================
                    // 模式 0：云端 Pollinations API
                    // ==========================================
                    String encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8");
                    String animeMagic = "%2C%202d%20illustration%2C%20anime%20screencap%2C%20flat%20shading%2C%20vibrant%20colors%2C%20pretty%20face";
                    String finalUrl = "https://image.pollinations.ai/prompt/" + encodedPrompt + animeMagic + 
                            "?width=768&height=768&nologo=true&seed=" + (System.currentTimeMillis() % 10000);

                    URL url = new URL(finalUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(120000); // 2分钟超时

                    if (conn.getResponseCode() == 200) {
                        java.io.InputStream is = conn.getInputStream();
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos);
                        base64Result = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
                    } else {
                        throw new Exception("云端报错 HTTP " + conn.getResponseCode());
                    }
                } else {
                    // ==========================================
                    // 模式 1：本地 Termux stable-diffusion.cpp (SDXL 专供版)
                    // ==========================================
                    URL url = new URL("http://127.0.0.1:8081/sdapi/v1/txt2img");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(5000);

                    // 🌟 核心修改 1：SDXL 极慢，超时时间直接拉满到 10 分钟 (600000 毫秒)
                    conn.setReadTimeout(1800000); 

                    JSONObject payload = new JSONObject();
                    // SDXL 对提示词质量要求极高，强力加持画质词
                    payload.put("prompt", prompt + ", masterpiece, best quality, ultra-detailed, official art, 8k resolution");
                    // SDXL 极其讨厌 3D 和写实风，负面词加重
                    payload.put("negative_prompt", "3d, realistic, photo, bad anatomy, bad hands, missing fingers, worst quality, low quality, normal quality, jpeg artifacts, signature, watermark");

                    // 🌟 核心修改 2：步数降到 15 步（手机上跑 20 步以上大概率直接闪退），CFG 设为 7
                    payload.put("steps", 15); 
                    payload.put("cfg_scale", 7.0); 

                    // 🌟 核心修改 3：SDXL 原生分辨率必须是 1024x1024，否则构图会崩
                    payload.put("width", 512);
                    payload.put("height", 912);
                    payload.put("seed", -1);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                    }

                    if (conn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder res = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) res.append(line);
                        br.close();

                        JSONObject json = new JSONObject(res.toString());
                        base64Result = json.getJSONArray("images").getString(0);
                        if (base64Result.contains(",")) {
                            base64Result = base64Result.split(",")[1];
                        }
                    } else {
                        throw new Exception("本地 SDXL 生图失败 HTTP " + conn.getResponseCode());
                    }
                }

                // ==========================================
                // 成功后统一处理显示
                // ==========================================
                final String finalBase64 = base64Result;
                runOnUiThread(() -> {
                    removeThinkingBubble();
                    addImageBubble(finalBase64, false); 
                    setGeneratingState(false); // 🌟 恢复界面
                
                    try {
                        JSONObject aiMsg = new JSONObject();
                        aiMsg.put("role", "assistant");
                        aiMsg.put("content", "（为您诞下了一子）");
                        aiMsg.put("image", finalBase64);
                        messages.put(aiMsg);
                        saveCurrentTopic();
                    } catch (Exception e) {}
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    removeThinkingBubble();
                    addBubble("孕妇已流产: " + e.getMessage(), false);
                    setGeneratingState(false); // 🌟 恢复界面
                });
            }
        }).start();
    }
    
    private void setGeneratingState(boolean generating) {
        isGenerating = generating;
        ImageView btnSend = findViewById(R.id.btnSend);
        
        if (generating) {
            etInput.setEnabled(false);
            
            // 🌟 只换图标，不动背景。咱们的 ic_stop_custom 是白色方块，刚好配主人的蓝底！
            btnSend.setImageResource(R.drawable.ic_stop_custom); 
            
        } else {
            etInput.setEnabled(true);
            
            // 🌟 完美复原：把刚才存起来的原配图标塞回去，背景一动不动！
            btnSend.setImageDrawable(originalSendIcon);
        }
    }
}    