package com.example.aries;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

public class PromptsManager {

    public static JSONArray getGlobalPrompts(Context ctx) {
        try {
            return new JSONArray(Prefs.getPromptListJson(ctx));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void setGlobalPrompts(Context ctx, JSONArray arr) {
        Prefs.setPromptListJson(ctx, arr.toString());
    }

    public static boolean isEnabled(Topic topic, int index) {
        for (int i = 0; i < topic.enabledPromptIndexes.length(); i++) {
            if (topic.enabledPromptIndexes.optInt(i, -1) == index) return true;
        }
        return false;
    }

    public static void toggle(Topic topic, int index, boolean enabled) {
        try {
            java.util.List<Integer> list = new java.util.ArrayList<>();
            for (int i = 0; i < topic.enabledPromptIndexes.length(); i++) {
                list.add(topic.enabledPromptIndexes.getInt(i));
            }
            if (enabled) {
                if (!list.contains(index)) list.add(index);
            } else {
                list.remove(Integer.valueOf(index));
            }
            JSONArray newArr = new JSONArray();
            for (int i : list) newArr.put(i);
            topic.enabledPromptIndexes = newArr;
        } catch (Exception e) { /* 忽略 */ }
    }
    
    public static String buildSystemPrompt(Context ctx, Topic topic) {
        StringBuilder sb = new StringBuilder();

        // 1. 获取设置里的名字和称呼
        String aiName = Prefs.getAiName(ctx);
        if (aiName == null || aiName.isEmpty()) aiName = "Aria";
        String userName = Prefs.getUserName(ctx);
        String userRole = Prefs.getUserRole(ctx);

        // 2. 基础的身份映射（极其简短，只为了防止叫错名字）
        sb.append("你的名字是“").append(aiName).append("”。\n");
        sb.append("用户的名字是“").append(userName).append("”，你称呼用户为“").append(userRole).append("”。\n");
        sb.append("————————————————\n");

        // 3. 拼接用户勾选的提示词（设定）
        try {
            JSONArray all = getGlobalPrompts(ctx);
            for (int i = 0; i < topic.enabledPromptIndexes.length(); i++) {
                int idx = topic.enabledPromptIndexes.getInt(i);
                if (idx >= 0 && idx < all.length()) {
                    String text = all.getJSONObject(idx).optString("text", "").trim();
                    if (!text.isEmpty()) {
                        sb.append(text).append("\n");
                    }
                }
            }
        } catch (Exception e) { /* 忽略 */ }

        // 4. 如果用户什么提示词都没勾，给一个最基础的兜底
        if (sb.toString().trim().isEmpty()) {
            sb.append("你是一个友好的中文助手，请直接回答用户的问题。\n");
        }

        return sb.toString();
    }
}